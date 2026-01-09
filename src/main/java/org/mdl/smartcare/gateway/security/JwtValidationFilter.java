package org.mdl.smartcare.gateway.security;

import org.mdl.smartcare.gateway.constants.GatewayConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class JwtValidationFilter extends AbstractGatewayFilterFactory<JwtValidationFilter.Config> {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AuthorizationService authorizationService;

    public JwtValidationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();

            // Skip validation for public endpoints
            String path = request.getURI().getPath();
            if (isPublicEndpoint(path)) {
                return chain.filter(exchange);
            }

            // Extract token from Authorization header
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            String token = jwtUtil.extractTokenFromHeader(authHeader);

            if (token == null) {
                return handleUnauthorized(response, GatewayConstants.Messages.MISSING_AUTH_HEADER);
            }

            // Validate token
            if (!jwtUtil.validateToken(token)) {
                return handleUnauthorized(response, GatewayConstants.Messages.INVALID_TOKEN);
            }

            // Perform authorization check
            try {
                List<String> permissions = jwtUtil.extractPermissions(token);
                String method = request.getMethod().name();
                String uri = path;

                if (!authorizationService.isAuthorized(permissions, method, uri)) {
                    return handleForbidden(response, GatewayConstants.Messages.ACCESS_DENIED);
                }

                // Extract username from JWT token
                String username = jwtUtil.extractUsername(token);

                // Add user info to request headers for downstream services
                ServerHttpRequest modifiedRequest = request.mutate()
                        .header(GatewayConstants.Headers.X_USER_NAME, username)
                        .header(GatewayConstants.Headers.X_AUTH_TOKEN, token)
                        .build();

                return chain.filter(exchange.mutate().request(modifiedRequest).build());
            } catch (Exception e) {
                return handleUnauthorized(response, GatewayConstants.Messages.TOKEN_VALIDATION_FAILED + e.getMessage());
            }
        };
    }

    private boolean isPublicEndpoint(String path) {
        // Define public endpoints that don't require authentication
        return path.startsWith(GatewayConstants.PublicPaths.ACTUATOR) || 
               path.startsWith(GatewayConstants.PublicPaths.HEALTH) || 
               path.startsWith(GatewayConstants.PublicPaths.INFO) ||
               path.equals(GatewayConstants.PublicPaths.ROOT) ||
               path.startsWith(GatewayConstants.PublicPaths.PUBLIC);
    }

    private Mono<Void> handleUnauthorized(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add(GatewayConstants.Headers.CONTENT_TYPE, "application/json");
        
        String body = String.format("{\"error\":\"%s\",\"message\":\"%s\",\"status\":401}", 
                                   GatewayConstants.Messages.UNAUTHORIZED, message);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }

    private Mono<Void> handleForbidden(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().add(GatewayConstants.Headers.CONTENT_TYPE, "application/json");
        
        String body = String.format("{\"error\":\"%s\",\"message\":\"%s\",\"status\":403}", 
                                   GatewayConstants.Messages.FORBIDDEN, message);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }

    public static class Config {
        // Configuration properties can be added here if needed
    }
}
