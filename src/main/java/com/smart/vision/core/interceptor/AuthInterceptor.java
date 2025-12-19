package com.smart.vision.core.interceptor;

import com.smart.vision.core.annotation.RequireAuth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import static com.smart.vision.core.constant.CommonConstant.TOKEN_KEY;

/**
 * Authentication Interceptor, used to verify whether a request has upload permissions.
 * This interceptor checks interface methods annotated with @RequireAuth and validates
 * if the X-Access-Token in the request header matches the configured upload token.
 *
 * @author Ryan
 * @since 2025/12/17
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod hm)) return true;

        if (hm.getMethodAnnotation(RequireAuth.class) != null) {
            String clientToken = request.getHeader("X-Access-Token");
            String serverToken = redisTemplate.opsForValue().get(TOKEN_KEY);
            if (serverToken == null || !serverToken.equals(clientToken)) {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\": 401, \"message\": \"口令无效或已过期，请联系管理员刷新\"}");
                return false;
            }
        }
        return true;
    }
}