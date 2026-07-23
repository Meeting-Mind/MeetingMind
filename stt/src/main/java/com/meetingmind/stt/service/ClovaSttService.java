package com.meetingmind.stt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.meetingmind.stt.config.DotenvConfig;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ClovaSttService {

    // ponytail: 언어/화자구분 옵션 고정, 다국어나 옵션 토글 필요해지면 그때 파라미터화
    private static final String CLOVA_PARAMS =
            "{\"language\":\"ko-KR\",\"completion\":\"sync\",\"fullText\":true,\"diarization\":{\"enable\":true}}";

    private final RestClient restClient = RestClient.create();

    public JsonNode transcribe(MultipartFile audio) {
        String invokeUrl = DotenvConfig.require("CLOVA_SPEECH_INVOKE_URL");
        String secretKey = DotenvConfig.require("CLOVA_SPEECH_SECRET");

        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("media", audio.getResource());
        body.part("params", CLOVA_PARAMS);
        body.part("type", "application/json");

        return restClient.post()
                .uri(invokeUrl + "/recognizer/upload")
                .header("X-CLOVASPEECH-API-KEY", secretKey)
                .body(body.build())
                .retrieve()
                .body(JsonNode.class);
    }
}
