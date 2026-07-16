package com.meetingmind.demo.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestTraceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = RequestTrace.normalize(request.getHeader(RequestTrace.HEADER_NAME));
        RequestTrace.bind(traceId);
        response.setHeader(RequestTrace.HEADER_NAME, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestTrace.clear();
        }
    }
}
