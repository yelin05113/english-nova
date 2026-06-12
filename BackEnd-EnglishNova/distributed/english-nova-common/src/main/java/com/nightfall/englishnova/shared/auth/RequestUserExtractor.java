package com.nightfall.englishnova.shared.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

public final class RequestUserExtractor {

    private RequestUserExtractor() {
    }

    public static CurrentUser require(HttpServletRequest request) {
        return resolveVerifier(request).require(request);
    }

    public static CurrentUser optional(HttpServletRequest request) {
        return resolveVerifier(request).optional(request);
    }

    private static InternalAuthVerifier resolveVerifier(HttpServletRequest request) {
        WebApplicationContext context =
                WebApplicationContextUtils.getRequiredWebApplicationContext(request.getServletContext());
        return context.getBean(InternalAuthVerifier.class);
    }
}
