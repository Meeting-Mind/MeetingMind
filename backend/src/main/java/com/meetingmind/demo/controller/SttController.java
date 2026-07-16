package com.meetingmind.demo.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.meetingmind.demo.service.ClovaSttService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("legacy-stt")
@RequestMapping("/api/stt")
public class SttController {

    private final ClovaSttService clovaSttService;

    public SttController(ClovaSttService clovaSttService) {
        this.clovaSttService = clovaSttService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public JsonNode transcribe(@RequestPart("audio") MultipartFile audio) {
        try {
            return clovaSttService.transcribe(audio);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), exception);
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Clova STT 호출에 실패했습니다.", exception);
        }
    }
}
