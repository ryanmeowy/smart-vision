package com.smart.vision.core.interceptor;

import com.smart.vision.core.annotation.RequireAuth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Authentication Interceptor, used to verify whether a request has upload permissions.
 * This interceptor checks interface methods annotated with @RequireAuth and validates
 * if the X-Access-Token in the request header matches the configured upload token.
 *
 * @author Ryan
 * @since 2025/12/17
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Value("${app.security.upload-token}")
    private String validToken;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        if (handlerMethod.getMethodAnnotation(RequireAuth.class) != null) {
            String token = request.getHeader("X-Access-Token");
            if (!validToken.equals(token)) {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\": 401, \"message\": \"No upload permission, please enter access code\"}");
                return false;
            }
        }
        return true;
    }
}