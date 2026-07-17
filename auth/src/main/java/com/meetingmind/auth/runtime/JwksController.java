package com.meetingmind.auth.runtime;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "meetingmind.auth.signing.provider", havingValue = "aws-kms")
final class JwksController {

    private final JwksDocumentService service;

    JwksController(JwksDocumentService service) {
        this.service = service;
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<byte[]> jwks(HttpServletRequest request) {
        JwksDocumentService.Document document = service.current();
        HttpHeaders headers = headers(document);
        if (etagMatches(request.getHeader(HttpHeaders.IF_NONE_MATCH), document.etag())) {
            return ResponseEntity.status(304).headers(headers).build();
        }
        return ResponseEntity.ok().headers(headers).body(document.body());
    }

    private HttpHeaders headers(JwksDocumentService.Document document) {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePublic());
        headers.setETag(document.etag());
        return headers;
    }

    private boolean etagMatches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            if (candidate.trim().equals(etag) || candidate.trim().equals("*")) {
                return true;
            }
        }
        return false;
    }
}
