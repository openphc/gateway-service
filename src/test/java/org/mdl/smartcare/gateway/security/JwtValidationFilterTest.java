package org.mdl.smartcare.gateway.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mdl.smartcare.gateway.constants.GatewayConstants;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link JwtValidationFilter}, the gateway's per-request authentication/authorization
 * filter.
 *
 * <p>{@link JwtUtil} and {@link AuthorizationService} are mocked so this exercises only the filter's
 * own control flow: public-path bypass, the 401 paths (missing/invalid token, mid-flight
 * exception), the 403 path (authenticated but unauthorized), and the success path (downstream
 * identity headers added and the chain invoked). Real token verification and real routing to a
 * backend are integration concerns covered in Phase 5.
 */
class JwtValidationFilterTest {

  private JwtUtil jwtUtil;
  private AuthorizationService authorizationService;
  private GatewayFilter filter;
  private GatewayFilterChain chain;

  @BeforeEach
  void setUp() {
    jwtUtil = mock(JwtUtil.class);
    authorizationService = mock(AuthorizationService.class);

    JwtValidationFilter factory = new JwtValidationFilter();
    ReflectionTestUtils.setField(factory, "jwtUtil", jwtUtil);
    ReflectionTestUtils.setField(factory, "authorizationService", authorizationService);
    filter = factory.apply(new JwtValidationFilter.Config());

    chain = mock(GatewayFilterChain.class);
  }

  private static MockServerWebExchange exchange(MockServerHttpRequest request) {
    return MockServerWebExchange.from(request);
  }

  // ── Public endpoints bypass authentication ──────────────────────────────────

  @Test
  void testFilter_WhenPublicActuatorPath_ShouldBypassAuthAndForward() {
    // Arrange
    MockServerWebExchange exchange =
        exchange(MockServerHttpRequest.get("/actuator/health").build());
    when(chain.filter(exchange)).thenReturn(Mono.empty());

    // Act / Assert
    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    verify(chain).filter(exchange);
    verifyNoInteractions(jwtUtil, authorizationService);
  }

  @Test
  void testFilter_WhenRootPath_ShouldBypassAuthAndForward() {
    // Arrange
    MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/").build());
    when(chain.filter(exchange)).thenReturn(Mono.empty());

    // Act / Assert
    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    verify(chain).filter(exchange);
  }

  // ── 401 paths ───────────────────────────────────────────────────────────────

  @Test
  void testFilter_WhenNoToken_ShouldReturnUnauthorized() {
    // Arrange — no Authorization header, so the extractor yields null.
    MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/admin/users").build());
    when(jwtUtil.extractTokenFromHeader(any())).thenReturn(null);

    // Act / Assert
    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    verify(chain, never()).filter(any());
  }

  @Test
  void testFilter_WhenInvalidToken_ShouldReturnUnauthorized() {
    // Arrange
    MockServerWebExchange exchange =
        exchange(
            MockServerHttpRequest.get("/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer bad")
                .build());
    when(jwtUtil.extractTokenFromHeader(any())).thenReturn("bad");
    when(jwtUtil.validateToken("bad")).thenReturn(false);

    // Act / Assert
    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    verify(chain, never()).filter(any());
  }

  @Test
  void testFilter_WhenAuthorizationThrows_ShouldReturnUnauthorized() {
    // Arrange — a valid token, but permission extraction blows up mid-flight.
    MockServerWebExchange exchange =
        exchange(
            MockServerHttpRequest.get("/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer good")
                .build());
    when(jwtUtil.extractTokenFromHeader(any())).thenReturn("good");
    when(jwtUtil.validateToken("good")).thenReturn(true);
    when(jwtUtil.extractPermissions("good")).thenThrow(new RuntimeException("boom"));

    // Act / Assert
    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    verify(chain, never()).filter(any());
  }

  // ── 403 path ────────────────────────────────────────────────────────────────

  @Test
  void testFilter_WhenValidTokenButNotAuthorized_ShouldReturnForbidden() {
    // Arrange
    MockServerWebExchange exchange =
        exchange(
            MockServerHttpRequest.get("/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer good")
                .build());
    when(jwtUtil.extractTokenFromHeader(any())).thenReturn("good");
    when(jwtUtil.validateToken("good")).thenReturn(true);
    when(jwtUtil.extractPermissions("good")).thenReturn(List.of("WRONG_PERM"));
    when(authorizationService.isAuthorized(anyList(), eq("GET"), eq("/admin/users")))
        .thenReturn(false);

    // Act / Assert
    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    verify(chain, never()).filter(any());
  }

  // ── Success path ────────────────────────────────────────────────────────────

  @Test
  void testFilter_WhenAuthorized_ShouldForwardWithIdentityHeaders() {
    // Arrange
    MockServerWebExchange exchange =
        exchange(
            MockServerHttpRequest.get("/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer good")
                .build());
    when(jwtUtil.extractTokenFromHeader(any())).thenReturn("good");
    when(jwtUtil.validateToken("good")).thenReturn(true);
    when(jwtUtil.extractPermissions("good")).thenReturn(List.of("USER_READ"));
    when(authorizationService.isAuthorized(anyList(), eq("GET"), eq("/admin/users")))
        .thenReturn(true);
    when(jwtUtil.extractUsername("good")).thenReturn("alice");
    when(chain.filter(any())).thenReturn(Mono.empty());

    // Act / Assert
    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
    verify(chain).filter(captor.capture());

    HttpHeaders forwardedHeaders = captor.getValue().getRequest().getHeaders();
    assertEquals("alice", forwardedHeaders.getFirst(GatewayConstants.Headers.X_USER_NAME));
    assertEquals("good", forwardedHeaders.getFirst(GatewayConstants.Headers.X_AUTH_TOKEN));
  }
}
