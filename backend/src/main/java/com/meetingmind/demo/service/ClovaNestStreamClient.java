package com.meetingmind.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
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
import java.util.function.Consumer;

public class ClovaNestStreamClient implements AutoCloseable {

    private static final String HOST = "clovaspeech-gw.ncloud.com";
    private static final int PORT = 50051;
    private static final ObjectMapper JSON = new ObjectMapper();

    // ponytail: diarization 옵션이 gRPC config JSON에서도 먹는지 문서에 명시 안 됨.
    // 트랙 단위로 이미 화자가 분리되므로 없어도 되지만, 실측 후 필요하면 이 JSON에 추가.
    private static final String CONFIG_JSON = "{\"transcription\":{\"language\":\"ko\"}}";

    private final ManagedChannel channel;
    private final StreamObserver<NestRequest> requestObserver;
    private final AtomicInteger seqId = new AtomicInteger(0);

    public ClovaNestStreamClient(Consumer<String> onTranscript) {
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
                String text = extractTranscriptText(response.getContents());
                if (text != null && !text.isBlank()) {
                    onTranscript.accept(text);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                onTranscript.accept("[error] " + throwable.getMessage());
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

    // ponytail: config ack 등 다른 responseType은 transcription.text가 없어 자연히 걸러짐.
    // 중간(interim) 결과까지 다 넘어오므로 문장 확정(epFlag) 기준 필터링이 필요해지면 여기에 추가.
    private static String extractTranscriptText(String contents) {
        try {
            JsonNode text = JSON.readTree(contents).path("transcription").path("text");
            return text.isTextual() ? text.asText() : null;
        } catch (Exception exception) {
            return null;
        }
    }

    public void sendAudio(byte[] pcm16leMono16k) {
        String extraContents = "{\"seqId\":" + seqId.getAndIncrement() + ",\"epFlag\":false}";

        requestObserver.onNext(NestRequest.newBuilder()
                .setType(RequestType.DATA)
                .setData(NestData.newBuilder()
                        .setChunk(ByteString.copyFrom(pcm16leMono16k))
                        .setExtraContents(extraContents))
                .build());
    }

    @Override
    public void close() {
        requestObserver.onCompleted();
        channel.shutdown();
    }
}
