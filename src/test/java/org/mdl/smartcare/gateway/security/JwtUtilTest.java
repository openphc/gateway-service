package org.mdl.smartcare.gateway.security;

import static org.junit.jupiter.api.Assertions.*;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mdl.smartcare.gateway.exception.SmartcareBusinessException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link JwtUtil}.
 *
 * <p>These cover the claim-extraction and header-parsing logic that runs on every request, plus the
 * deterministic failure path of {@link JwtUtil#validateToken}. Tokens are built with auth0
 * {@code JWT.create()} and an HMAC signature purely so they are well-formed for {@code JWT.decode()}
 * — the extraction methods never verify the signature, so the signing key is irrelevant here.
 *
 * <p>The full positive {@code validateToken} path (JWKS retrieval from Keycloak, issuer/audience
 * verification, RS/ES algorithm selection) requires a real or stubbed Keycloak JWKS endpoint and is
 * intentionally left to the Testcontainers-backed integration tier (Phase 5), per
 * TESTCONTAINERS_POLICY.md — it is not a unit concern.
 */
class JwtUtilTest {

  private JwtUtil jwtUtil;

  private static final Algorithm SIGNING = Algorithm.HMAC256("test-signing-secret");

  @BeforeEach
  void setUp() {
    jwtUtil = new JwtUtil();
    // Default Keycloak realm-role claim path; overridden per-test where needed.
    ReflectionTestUtils.setField(jwtUtil, "rolesClaimPath", "realm_access.roles");
  }

  // ── extractTokenFromHeader ──────────────────────────────────────────────────

  @Test
  void testExtractTokenFromHeader_WhenValidBearer_ShouldReturnToken() {
    // Act
    String token = jwtUtil.extractTokenFromHeader("Bearer abc.def.ghi");

    // Assert
    assertEquals("abc.def.ghi", token);
  }

  @Test
  void testExtractTokenFromHeader_WhenNullHeader_ShouldThrow() {
    // Act / Assert
    assertThrows(
        SmartcareBusinessException.class, () -> jwtUtil.extractTokenFromHeader(null));
  }

  @Test
  void testExtractTokenFromHeader_WhenNoBearerPrefix_ShouldThrow() {
    // Act / Assert — a raw token without the "Bearer " prefix is rejected.
    assertThrows(
        SmartcareBusinessException.class, () -> jwtUtil.extractTokenFromHeader("abc.def.ghi"));
  }

  // ── extractPermissions ──────────────────────────────────────────────────────

  @Test
  void testExtractPermissions_WhenRealmAccessRoles_ShouldReturnRoles() {
    // Arrange
    String token =
        JWT.create()
            .withClaim("realm_access", Map.of("roles", List.of("USER_READ", "USER_WRITE")))
            .sign(SIGNING);

    // Act
    List<String> permissions = jwtUtil.extractPermissions(token);

    // Assert
    assertEquals(List.of("USER_READ", "USER_WRITE"), permissions);
  }

  @Test
  void testExtractPermissions_WhenNestedClientRolesPath_ShouldTraverseAndReturnRoles() {
    // Arrange — client-level roles under resource_access.<client>.roles.
    ReflectionTestUtils.setField(
        jwtUtil, "rolesClaimPath", "resource_access.gateway-service.roles");
    String token =
        JWT.create()
            .withClaim(
                "resource_access",
                Map.of("gateway-service", Map.of("roles", List.of("HTTPBIN_READ"))))
            .sign(SIGNING);

    // Act
    List<String> permissions = jwtUtil.extractPermissions(token);

    // Assert
    assertEquals(List.of("HTTPBIN_READ"), permissions);
  }

  @Test
  void testExtractPermissions_WhenClaimMissing_ShouldReturnEmpty() {
    // Arrange — token has no realm_access claim at all.
    String token = JWT.create().withSubject("user-1").sign(SIGNING);

    // Act
    List<String> permissions = jwtUtil.extractPermissions(token);

    // Assert
    assertTrue(permissions.isEmpty());
  }

  @Test
  void testExtractPermissions_WhenPathHasSingleSegment_ShouldReturnEmpty() {
    // Arrange — an invalid path with fewer than two segments cannot resolve to a role list.
    ReflectionTestUtils.setField(jwtUtil, "rolesClaimPath", "realm_access");
    String token =
        JWT.create().withClaim("realm_access", Map.of("roles", List.of("X"))).sign(SIGNING);

    // Act
    List<String> permissions = jwtUtil.extractPermissions(token);

    // Assert
    assertTrue(permissions.isEmpty());
  }

  @Test
  void testExtractPermissions_WhenIntermediateSegmentNotAMap_ShouldReturnEmpty() {
    // Arrange — path expects resource_access.gateway-service.roles, but "gateway-service"
    // resolves to a scalar rather than a nested map, so traversal cannot continue.
    ReflectionTestUtils.setField(
        jwtUtil, "rolesClaimPath", "resource_access.gateway-service.roles");
    String token =
        JWT.create()
            .withClaim("resource_access", Map.of("gateway-service", "not-a-map"))
            .sign(SIGNING);

    // Act
    List<String> permissions = jwtUtil.extractPermissions(token);

    // Assert
    assertTrue(permissions.isEmpty());
  }

  @Test
  void testExtractPermissions_WhenTargetNotAList_ShouldReturnEmpty() {
    // Arrange — the claim path resolves to a scalar, not a list of roles.
    String token =
        JWT.create().withClaim("realm_access", Map.of("roles", "not-a-list")).sign(SIGNING);

    // Act
    List<String> permissions = jwtUtil.extractPermissions(token);

    // Assert
    assertTrue(permissions.isEmpty());
  }

  @Test
  void testExtractPermissions_WhenMalformedToken_ShouldReturnEmpty() {
    // Act — a non-JWT string is handled gracefully rather than thrown.
    List<String> permissions = jwtUtil.extractPermissions("this-is-not-a-jwt");

    // Assert
    assertTrue(permissions.isEmpty());
  }

  // ── extractUsername ─────────────────────────────────────────────────────────

  @Test
  void testExtractUsername_WhenPreferredUsernamePresent_ShouldReturnIt() {
    // Arrange
    String token =
        JWT.create()
            .withClaim("preferred_username", "alice")
            .withSubject("sub-123")
            .withClaim("email", "alice@example.org")
            .sign(SIGNING);

    // Act
    String username = jwtUtil.extractUsername(token);

    // Assert
    assertEquals("alice", username);
  }

  @Test
  void testExtractUsername_WhenNoPreferredUsername_ShouldFallBackToSubject() {
    // Arrange
    String token =
        JWT.create().withSubject("sub-123").withClaim("email", "a@example.org").sign(SIGNING);

    // Act
    String username = jwtUtil.extractUsername(token);

    // Assert
    assertEquals("sub-123", username);
  }

  @Test
  void testExtractUsername_WhenOnlyEmail_ShouldFallBackToEmail() {
    // Arrange
    String token = JWT.create().withClaim("email", "bob@example.org").sign(SIGNING);

    // Act
    String username = jwtUtil.extractUsername(token);

    // Assert
    assertEquals("bob@example.org", username);
  }

  @Test
  void testExtractUsername_WhenNoIdentifyingClaims_ShouldReturnUnknown() {
    // Arrange — no preferred_username, sub, or email.
    String token = JWT.create().withClaim("foo", "bar").sign(SIGNING);

    // Act
    String username = jwtUtil.extractUsername(token);

    // Assert
    assertEquals("unknown", username);
  }

  @Test
  void testExtractUsername_WhenMalformedToken_ShouldReturnUnknown() {
    // Act
    String username = jwtUtil.extractUsername("not-a-jwt");

    // Assert
    assertEquals("unknown", username);
  }

  // ── validateToken (deterministic failure path) ──────────────────────────────

  @Test
  void testValidateToken_WhenMalformedToken_ShouldReturnFalse() {
    // Act — decode/verification fails fast and is swallowed into a false result.
    Boolean valid = jwtUtil.validateToken("not-a-jwt");

    // Assert
    assertFalse(valid);
  }
}
