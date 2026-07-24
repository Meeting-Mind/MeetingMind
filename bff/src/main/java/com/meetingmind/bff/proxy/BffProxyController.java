package com.meetingmind.bff.proxy;

import com.meetingmind.bff.auth.BffTokenManager;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

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
            HttpServletRequest request) throws IOException {
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        String path = request.getRequestURI();
        ProxyRoute route = routeRegistry.resolve(method, path)
                .orElseThrow(BffProxyException::routeNotAllowed);
        ForwardedBody forwardedBody = forwardedBody(request);
        ProxyRequest proxyRequest = new ProxyRequest(
                method,
                path,
                query(request),
                forwardedBody.contentType(),
                request.getHeader(HttpHeaders.ACCEPT),
                forwardedBody.body());
        ProxyResponse response = tokenManager.execute(
                request,
                route.service().audience(),
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
        return new ResponseEntity<>(response.body(), responseHeaders, response.status());
    }

    private ForwardedBody forwardedBody(HttpServletRequest request) throws IOException {
        if (!(request instanceof MultipartHttpServletRequest multipart)) {
            return new ForwardedBody(request.getHeader(HttpHeaders.CONTENT_TYPE), request.getInputStream().readAllBytes());
        }

        String boundary = "----MeetingMindBff" + UUID.randomUUID();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        multipart.getFileMap().values().forEach(file -> writeFilePart(output, boundary, file));
        multipart.getParameterMap().forEach((name, values) -> Arrays.stream(values).forEach(value -> writeFieldPart(output, boundary, name, value)));
        output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return new ForwardedBody("multipart/form-data; boundary=" + boundary, output.toByteArray());
    }

    private void writeFilePart(ByteArrayOutputStream output, String boundary, MultipartFile file) {
        try {
            output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(("Content-Disposition: form-data; name=\"" + file.getName() + "\"; filename=\"" + file.getOriginalFilename() + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(("Content-Type: " + (file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType()) + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(file.getBytes());
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("multipart 파일을 전달하지 못했습니다.", exception);
        }
    }

    private void writeFieldPart(ByteArrayOutputStream output, String boundary, String name, String value) {
        try {
            output.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("multipart 필드를 전달하지 못했습니다.", exception);
        }
    }

    private record ForwardedBody(String contentType, byte[] body) {}

    private MultiValueMap<String, String> query(HttpServletRequest request) {
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        request.getParameterMap().forEach((name, values) -> query.put(name, Arrays.asList(values)));
        return query;
    }
}
