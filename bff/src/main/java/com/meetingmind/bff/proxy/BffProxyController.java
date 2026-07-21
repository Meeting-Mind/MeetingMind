package com.meetingmind.bff.proxy;

import com.meetingmind.bff.auth.BffTokenManager;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BffProxyController {

    private final ProxyRouteRegistry routeRegistry;
    private final DownstreamHttpClient downstreamClient;
    private final BffTokenManager tokenManager;

    public BffProxyController(
            ProxyRouteRegistry routeRegistry,
            DownstreamHttpClient downstreamClient,
            BffTokenManager tokenManager) {
        this.routeRegistry = routeRegistry;
        this.downstreamClient = downstreamClient;
        this.tokenManager = tokenManager;
    }

    @RequestMapping("/api/v1/**")
    public ResponseEntity<byte[]> proxy(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        String path = request.getRequestURI();
        ProxyRoute route = routeRegistry.resolve(method, path)
                .orElseThrow(BffProxyException::routeNotAllowed);
        ProxyRequest proxyRequest = new ProxyRequest(
                method,
                path,
                query(request),
                request.getHeader(HttpHeaders.CONTENT_TYPE),
                request.getHeader(HttpHeaders.ACCEPT),
                body);
        ProxyResponse response = tokenManager.execute(
                request,
                route.service(),
                authorization -> downstreamClient.execute(route.service(), proxyRequest, authorization));
        HttpHeaders responseHeaders = new HttpHeaders();
        if (response.contentType() != null) {
            responseHeaders.setContentType(response.contentType());
        }
        if (response.cacheControl() != null && !response.cacheControl().isBlank()) {
            responseHeaders.set(HttpHeaders.CACHE_CONTROL, response.cacheControl());
        }
        if (response.etag() != null && !response.etag().isBlank()) {
            responseHeaders.set(HttpHeaders.ETAG, response.etag());
        }
        if (response.contentDisposition() != null && !response.contentDisposition().isBlank()) {
            responseHeaders.set(HttpHeaders.CONTENT_DISPOSITION, response.contentDisposition());
        }
        return new ResponseEntity<>(response.body(), responseHeaders, response.status());
    }

    private MultiValueMap<String, String> query(HttpServletRequest request) {
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        request.getParameterMap().forEach((name, values) -> query.put(name, Arrays.asList(values)));
        return query;
    }
}
