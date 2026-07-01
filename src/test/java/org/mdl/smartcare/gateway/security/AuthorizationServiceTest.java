package org.mdl.smartcare.gateway.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AuthorizationService}.
 *
 * <p>Covers the gateway's actual authorization model: a permission name (carried as a JWT role)
 * maps to one or more {@code {method, uri-pattern}} rules, matched with Spring's {@link
 * org.springframework.util.AntPathMatcher}. This is the real "Authorization Matrix" for this repo —
 * it is permission/role-driven against {@code /admin/**} style URIs, NOT the OAuth-scope model
 * (e.g. {@code events:write}) described in the platform-level spec, which predates direct
 * inspection of this service.
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

  @Mock private PermissionConfig permissionConfig;

  @InjectMocks private AuthorizationService authorizationService;

  private Map<String, List<PermissionConfig.PermissionRule>> mappings;

  @BeforeEach
  void setUp() {
    mappings = new HashMap<>();
  }

  private static PermissionConfig.PermissionRule rule(String method, String uri) {
    PermissionConfig.PermissionRule r = new PermissionConfig.PermissionRule();
    r.setMethod(method);
    r.setUri(uri);
    return r;
  }

  // ── Authorization toggle ────────────────────────────────────────────────────

  @Test
  void testIsAuthorized_WhenAuthorizationDisabled_ShouldAllow() {
    // Arrange
    when(permissionConfig.isEnabled()).thenReturn(false);

    // Act
    boolean result = authorizationService.isAuthorized(null, "GET", "/admin/users/1");

    // Assert
    assertTrue(result);
    // When disabled, the mappings should never even be consulted.
    verify(permissionConfig, never()).getPermissionMappings();
  }

  // ── Missing / empty inputs ──────────────────────────────────────────────────

  @Test
  void testIsAuthorized_WhenPermissionsNull_ShouldDeny() {
    // Arrange
    when(permissionConfig.isEnabled()).thenReturn(true);

    // Act
    boolean result = authorizationService.isAuthorized(null, "GET", "/admin/users");

    // Assert
    assertFalse(result);
  }

  @Test
  void testIsAuthorized_WhenPermissionsEmpty_ShouldDeny() {
    // Arrange
    when(permissionConfig.isEnabled()).thenReturn(true);

    // Act
    boolean result =
        authorizationService.isAuthorized(Collections.emptyList(), "GET", "/admin/users");

    // Assert
    assertFalse(result);
  }

  @Test
  void testIsAuthorized_WhenMappingsNull_ShouldDeny() {
    // Arrange
    when(permissionConfig.isEnabled()).thenReturn(true);
    when(permissionConfig.getPermissionMappings()).thenReturn(null);

    // Act
    boolean result = authorizationService.isAuthorized(List.of("USER_READ"), "GET", "/admin/users");

    // Assert
    assertFalse(result);
  }

  @Test
  void testIsAuthorized_WhenMappingsEmpty_ShouldDeny() {
    // Arrange
    when(permissionConfig.isEnabled()).thenReturn(true);
    when(permissionConfig.getPermissionMappings()).thenReturn(mappings);

    // Act
    boolean result = authorizationService.isAuthorized(List.of("USER_READ"), "GET", "/admin/users");

    // Assert
    assertFalse(result);
  }

  // ── Positive matches ────────────────────────────────────────────────────────

  @Test
  void testIsAuthorized_WhenExactRuleMatches_ShouldAllow() {
    // Arrange
    mappings.put("USER_WRITE", List.of(rule("POST", "/admin/users")));
    when(permissionConfig.isEnabled()).thenReturn(true);
    when(permissionConfig.getPermissionMappings()).thenReturn(mappings);

    // Act
    boolean result =
        authorizationService.isAuthorized(List.of("USER_WRITE"), "POST", "/admin/users");

    // Assert
    assertTrue(result);
  }

  @Test
  void testIsAuthorized_WhenMethodCaseDiffers_ShouldAllow() {
    // Arrange — rule method is matched case-insensitively (equalsIgnoreCase in the service).
    mappings.put("USER_READ", List.of(rule("get", "/admin/users")));
    when(permissionConfig.isEnabled()).thenReturn(true);
    when(permissionConfig.getPermissionMappings()).thenReturn(mappings);

    // Act
    boolean result = authorizationService.isAuthorized(List.of("USER_READ"), "GET", "/admin/users");

    // Assert
    assertTrue(result);
  }

  @Test
  void testIsAuthorized_WhenSingleStarPattern_ShouldMatchOneSegment() {
    // Arrange — "/admin/users/*" matches a single path segment.
    mappings.put("USER_READ", List.of(rule("GET", "/admin/users/*")));
    when(permissionConfig.isEnabled()).thenReturn(true);
    when(permissionConfig.getPermissionMappings()).thenReturn(mappings);

    // Act
    boolean result =
        authorizationService.isAuthorized(List.of("USER_READ"), "GET", "/admin/users/123");

    // Assert
    assertTrue(result);
  }

  @Test
  void testIsAuthorized_WhenSingleStarPattern_ShouldNotMatchMultipleSegments() {
    // Arrange — "/admin/users/*" must NOT match a deeper path.
    mappings.put("USER_READ", List.of(rule("GET", "/admin/users/*")));
    when(permissionConfig.isEnabled()).thenReturn(true);
    when(permissionConfig.getPermissionMappings()).thenReturn(mappings);

    // Act
    boolean result =
        authorizationService.isAuthorized(List.of("USER_READ"), "GET", "/admin/users/123/profile");

    // Assert
    assertFalse(result);
  }

  @Test
  void testIsAuthorized_WhenDoubleStarPattern_ShouldMatchMultipleSegments() {
    // Arrange — "/admin/users/**" matches arbitrarily deep paths.
    mappings.put("USER_READ", List.of(rule("GET", "/admin/users/**")));
    when(permissionConfig.isEnabled()).thenReturn(true);
    when(permissionConfig.getPermissionMappings()).thenReturn(mappings);

    // Act
    boolean result =
        authorizationService.isAuthorized(List.of("USER_READ"), "GET", "/admin/users/123/profile");

    // Assert
    assertTrue(result);
  }

  @Test
  void testIsAuthorized_WhenOneOfMultipleRulesMatches_ShouldAllow() {
    // Arrange — a permission can carry several rules; any match grants access.
    mappings.put("USER_READ", List.of(rule("GET", "/admin/users"), rule("GET", "/admin/users/*")));
    when(permissionConfig.isEnabled()).thenReturn(true);
    when(permissionConfig.getPermissionMappings()).thenReturn(mappings);

    // Act
    boolean result =
        authorizationService.isAuthorized(List.of("USER_READ"), "GET", "/admin/users/42");

    // Assert
    assertTrue(result);
  }

  @Test
  void testIsAuthorized_WhenOneOfMultiplePermissionsMatches_ShouldAllow() {
    // Arrange — the user holds several permissions; one of them grants the request.
    mappings.put("USER_WRITE", List.of(rule("POST", "/admin/users")));
    when(permissionConfig.isEnabled()).thenReturn(true);
    when(permissionConfig.getPermissionMappings()).thenReturn(mappings);

    // Act
    boolean result =
        authorizationService.isAuthorized(
            List.of("UNRELATED_PERM", "USER_WRITE"), "POST", "/admin/users");

    // Assert
    assertTrue(result);
  }

  // ── Negative matches ────────────────────────────────────────────────────────

  @Test
  void testIsAuthorized_WhenMethodDiffers_ShouldDeny() {
    // Arrange — correct URI, wrong HTTP method.
    mappings.put("USER_READ", List.of(rule("GET", "/admin/users")));
    when(permissionConfig.isEnabled()).thenReturn(true);
    when(permissionConfig.getPermissionMappings()).thenReturn(mappings);

    // Act
    boolean result =
        authorizationService.isAuthorized(List.of("USER_READ"), "DELETE", "/admin/users");

    // Assert
    assertFalse(result);
  }

  @Test
  void testIsAuthorized_WhenUriDiffers_ShouldDeny() {
    // Arrange — correct method, non-matching URI.
    mappings.put("USER_READ", List.of(rule("GET", "/admin/users")));
    when(permissionConfig.isEnabled()).thenReturn(true);
    when(permissionConfig.getPermissionMappings()).thenReturn(mappings);

    // Act
    boolean result =
        authorizationService.isAuthorized(List.of("USER_READ"), "GET", "/admin/roles");

    // Assert
    assertFalse(result);
  }

  @Test
  void testIsAuthorized_WhenPermissionNotInMappings_ShouldDeny() {
    // Arrange — the user's permission has no rules configured at all.
    mappings.put("USER_READ", List.of(rule("GET", "/admin/users")));
    when(permissionConfig.isEnabled()).thenReturn(true);
    when(permissionConfig.getPermissionMappings()).thenReturn(mappings);

    // Act
    boolean result =
        authorizationService.isAuthorized(List.of("SOME_OTHER_PERM"), "GET", "/admin/users");

    // Assert
    assertFalse(result);
  }
}
