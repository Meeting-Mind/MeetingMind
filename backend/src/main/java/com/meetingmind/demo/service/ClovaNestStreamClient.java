package com.meetingmind.demo.service;

import com.google.protobuf.ByteString;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingmind.demo.clova.grpc.NestConfig;
import com.meetingmind.demo.clova.grpc.NestData;
import com.meetingmind.demo.clova.grpc.NestRequest;
import com.meetingmind.demo.clova.grpc.NestResponse;
import com.meetingmind.demo.clova.grpc.NestServiceGrpc;
import com.meetingmind.demo.clova.grpc.RequestType;
import com.meetingmind.demo.config.DotenvConfig;
import io.grpc.Metadata;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClovaNestStreamClient implements SttStreamClient {

    private static final Logger log = LoggerFactory.getLogger(ClovaNestStreamClient.class);
    private static final String HOST = "clovaspeech-gw.ncloud.com";
    private static final int PORT = 50051;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ponytail: diarization 옵션이 gRPC config JSON에서도 먹는지 문서에 명시 안 됨.
    // 트랙 단위로 이미 화자가 분리되므로 없어도 되지만, 실측 후 필요하면 이 JSON에 추가.
    private static final String CONFIG_JSON = "{\"transcription\":{\"language\":\"ko\"}}";

    private final ManagedChannel channel;
    private final StreamObserver<NestRequest> requestObserver;
    private final AtomicInteger seqId = new AtomicInteger(0);
    private byte[] pendingAudio;

    public ClovaNestStreamClient(Consumer<String> onFinalTranscript, Consumer<String> onPartialTranscript, Consumer<Throwable> onError) {
        String secretKey = DotenvConfig.require("CLOVA_SPEECH_SECRET");

        this.channel = NettyChannelBuilder.forAddress(HOST, PORT)
                .useTransportSecurity()
                .build();

        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + secretKey);

        NestServiceGrpc.NestServiceStub stub = NestServiceGrpc.newStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));

        this.requestObserver = stub.recognize(new StreamObserver<NestResponse>() {
            @Override
            public void onNext(NestResponse response) {
                String contents = response.getContents();
                log.info("CLOVA nest response: {}", contents);
                Transcription transcription = extractTranscription(contents);
                if (transcription != null) {
                    if (transcription.epFlag()) {
                        onFinalTranscript.accept(transcription.text());
                    } else {
                        onPartialTranscript.accept(transcription.text());
                    }
                } else if (isRecognitionFailure(contents)) {
                    onError.accept(new IllegalStateException("CLOVA recognition request failed"));
                }
            }

            @Override
            public void onError(Throwable throwable) {
                log.warn("CLOVA nest stream error", throwable);
                onError.accept(throwable);
            }

            @Override
            public void onCompleted() {
            }
        });

        requestObserver.onNext(NestRequest.newBuilder()
                .setType(RequestType.CONFIG)
                .setConfig(NestConfig.newBuilder().setConfig(CONFIG_JSON))
                .build());
    }

    public synchronized void sendAudio(byte[] pcm16leMono16k) {
        if (pcm16leMono16k == null || pcm16leMono16k.length == 0) {
            return;
        }
        if (pendingAudio != null) {
            sendData(pendingAudio, false);
        }
        pendingAudio = pcm16leMono16k;
    }

    @Override
    public synchronized void finishAudio() {
        if (pendingAudio != null) {
            sendData(pendingAudio, true);
            pendingAudio = null;
        }
    }

    private void sendData(byte[] pcm16leMono16k, boolean endOfPhrase) {
        String extraContents = "{\"seqId\":" + seqId.getAndIncrement() + ",\"epFlag\":" + endOfPhrase + "}";

        requestObserver.onNext(NestRequest.newBuilder()
                .setType(RequestType.DATA)
                .setData(NestData.newBuilder()
                        .setChunk(ByteString.copyFrom(pcm16leMono16k))
                        .setExtraContents(extraContents))
                .build());
    }

    @Override
    public void close() {
        finishAudio();
        requestObserver.onCompleted();
        channel.shutdown();
        try {
            channel.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    record Transcription(String text, boolean epFlag) {
    }

    // epFlag=true는 Clova가 자연스러운 발화 경계(pause)를 감지해 문장을 확정했다는 뜻.
    // false는 아직 듣는 중인 중간(partial) 결과. 확정 응답이 text 없이 epFlag만 오는 경우가
    // 실측 확인됐으므로(경계 신호만 오고 실제 텍스트는 그 직전 partial에 있음), text가 비어도
    // epFlag=true면 버리지 않고 그대로 넘긴다 - 호출부(SttSessionRegistry)가 마지막 partial로 채운다.
    static Transcription extractTranscription(String contents) {
        try {
            JsonNode response = OBJECT_MAPPER.readTree(contents);
            if (!hasResponseType(response, "transcription")) {
                return null;
            }
            JsonNode transcriptionNode = response.path("transcription");
            String text = transcriptionNode.path("text").asText("").trim();
            boolean epFlag = transcriptionNode.path("epFlag").asBoolean(true);
            if (text.isEmpty() && !epFlag) {
                return null;
            }
            return new Transcription(text, epFlag);
        } catch (Exception ignored) {
            return null;
        }
    }

    static boolean isRecognitionFailure(String contents) {
        try {
            return hasResponseType(OBJECT_MAPPER.readTree(contents), "recognize");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean hasResponseType(JsonNode response, String expectedType) {
        for (JsonNode type : response.path("responseType")) {
            if (expectedType.equals(type.asText())) {
                return true;
            }
        }
        return false;
    }
}
