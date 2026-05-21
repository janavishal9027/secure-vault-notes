package com.application.notes.configuration;

import com.application.notes.exceptions.AuthenticationException;
import com.application.notes.feignService.AuthenticationClient;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private AuthenticationClient authenticationClient;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return true;
        }

        String authorization = request.getHeader(AUTHORIZATION);

        if (!StringUtils.hasText(authorization)) {
            throw new AuthenticationException("Authorization header is empty");
        }

        if (!authorization.startsWith(BEARER_PREFIX)) {
            throw new AuthenticationException("Authorization header must start with Bearer");
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();

        if (!StringUtils.hasText(token)) {
            throw new AuthenticationException("Bearer token is empty");
        }

        try {
            Boolean isTokenValid = authenticationClient.validateToken(token);

            if (!Boolean.TRUE.equals(isTokenValid)) {
                throw new AuthenticationException("Invalid or expired token");
            }

            String ownerUserId = authenticationClient.extractUserId(token);
            if(!StringUtils.hasText(ownerUserId)) {
                throw new AuthenticationException("Failed to extract Owner user ID from token");
            }

            request.setAttribute("ownerUserId", ownerUserId);

        } catch (FeignException.Unauthorized e) {
            throw new AuthenticationException("Unauthorized: invalid token");
        } catch (FeignException.Forbidden e) {
            throw new AuthenticationException("Forbidden: access denied");
        } catch (FeignException e) {
            throw new AuthenticationException("Authentication service error: " + e.getMessage());
        } catch (Exception e) {
            throw new AuthenticationException("Token validation failed");
        }

        return true;
    }
}
