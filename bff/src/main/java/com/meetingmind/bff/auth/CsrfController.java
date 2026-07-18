package com.meetingmind.bff.auth;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class CsrfController {

    @GetMapping("/csrf")
    ResponseEntity<CsrfResponse> csrf(CsrfToken csrfToken) {
        CsrfResponse response = new CsrfResponse(
                csrfToken.getToken(),
                csrfToken.getHeaderName(),
                csrfToken.getParameterName());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    record CsrfResponse(String token, String headerName, String parameterName) {
    }
}
