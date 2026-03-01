package com.riskguard.infrastructure.observability;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;

@Component
public class MdcFilter implements Filter {
    @Override public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        try {
            String traceId = UUID.randomUUID().toString().substring(0,8);
            MDC.put("traceId", traceId);
            if (req instanceof HttpServletRequest r) {
                MDC.put("path", r.getRequestURI());
            }
            chain.doFilter(req, res);
        } finally {
            MDC.clear();
        }
    }
}
