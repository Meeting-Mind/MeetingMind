package com.meetingmind.bff.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

public final class BffAuthErrorWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private BffAuthErrorWriter() {
    }

    public static void writeSessionInvalid(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        OBJECT_MAPPER.writeValue(
                response.getOutputStream(),
                new BffAuthErrorResponse(
                        "SESSION_INVALID",
                        "로그인이 만료되었습니다. 다시 로그인해 주세요.",
                        List.of(),
                        UUID.randomUUID().toString().replace("-", "")));
    }
}
