package com.meetingmind.auth.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

class JwksControllerTest {

    @Test
    void returnsFiveMinutePublicCacheAndEtagConditionalResponse() {
        JwksDocumentService service = mock(JwksDocumentService.class);
        byte[] body = "{\"keys\":[]}".getBytes(StandardCharsets.UTF_8);
        when(service.current()).thenReturn(new JwksDocumentService.Document(body, "\"etag-1\""));
        JwksController controller = new JwksController(service);
        MockHttpServletRequest firstRequest = new MockHttpServletRequest(
                "GET",
                "/.well-known/jwks.json"
        );

        var first = controller.jwks(firstRequest);

        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(first.getHeaders().getCacheControl()).isEqualTo("max-age=300, public");
        assertThat(first.getHeaders().getETag()).isEqualTo("\"etag-1\"");
        assertThat(first.getBody()).isEqualTo(body);

        MockHttpServletRequest cachedRequest = new MockHttpServletRequest(
                "GET",
                "/.well-known/jwks.json"
        );
        cachedRequest.addHeader(HttpHeaders.IF_NONE_MATCH, "\"etag-1\"");
        var cached = controller.jwks(cachedRequest);

        assertThat(cached.getStatusCode().value()).isEqualTo(304);
        assertThat(cached.getBody()).isNull();
        assertThat(cached.getHeaders().getETag()).isEqualTo("\"etag-1\"");
    }
}
