package com.meetingmind.bff.proxy;

import java.util.regex.Pattern;
import org.springframework.http.HttpMethod;

public record ProxyRoute(HttpMethod method, Pattern pathPattern, DownstreamService service) {

    public ProxyRoute {
        if (method == null || pathPattern == null || service == null) {
            throw new IllegalArgumentException("proxy route fields are required");
        }
    }

    boolean matches(HttpMethod requestMethod, String path) {
        return method.equals(requestMethod) && pathPattern.matcher(path).matches();
    }
}
