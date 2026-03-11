package org.mdl.smartcare.gateway.security;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import java.net.URI;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.mdl.smartcare.gateway.constants.GatewayConstants;
import org.mdl.smartcare.gateway.exception.SmartcareBusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

  @Value("${jwt.issuer:issuer}")
  private String issuer;

  @Value("${jwt.audience:audience}")
  private String audience;

  @Value("${jwt.roles-claim-path:realm_access.roles}")
  private String rolesClaimPath;

  private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

  private static final Map<String, Function<Object, Algorithm>> ALGORITHM_MAP =
      Map.of(
          "RS256", key -> Algorithm.RSA256((RSAPublicKey) key, null),
          "RS384", key -> Algorithm.RSA384((RSAPublicKey) key, null),
          "RS512", key -> Algorithm.RSA512((RSAPublicKey) key, null),
          "ES256", key -> Algorithm.ECDSA256((ECPublicKey) key, null),
          "ES384", key -> Algorithm.ECDSA384((ECPublicKey) key, null),
          "ES512", key -> Algorithm.ECDSA512((ECPublicKey) key, null));

  public Boolean validateToken(String token) {
    try {
      URI uri = new URI(issuer + "/protocol/openid-connect/certs");
      JwkProvider jwkProvider = new JwkProviderBuilder(uri.toURL()).build();

      DecodedJWT decodedJWT = JWT.decode(token);

      // start verification process
      Jwk jwk = jwkProvider.get(decodedJWT.getKeyId());

      // Extract algorithm from JWK dynamically
      String algorithmName = jwk.getAlgorithm();
      Function<Object, Algorithm> algorithmFactory = ALGORITHM_MAP.get(algorithmName);

      if (algorithmFactory == null) {
        throw new IllegalArgumentException("Unsupported algorithm: " + algorithmName);
      }

      Algorithm algorithm = algorithmFactory.apply(jwk.getPublicKey());

      // verify the token
      JWTVerifier verifier =
          JWT.require(algorithm).withIssuer(issuer).withAudience(audience).build();
      verifier.verify(decodedJWT);

      return true;
    } catch (Exception e) {
      logger.error("Error validating token", e);
      return false;
    }
  }

  public String extractTokenFromHeader(String authHeader) {
    if (authHeader != null && authHeader.startsWith(GatewayConstants.Headers.BEARER_PREFIX)) {
      return authHeader.substring(GatewayConstants.Headers.BEARER_PREFIX.length());
    }

    throw new SmartcareBusinessException(GatewayConstants.Messages.INVALID_AUTH_HEADER);
  }

  /**
   * Extract permissions from a configurable JWT claim path.
   *
   * <p>The path is configured via {@code jwt.roles-claim-path} and supports dot-separated
   * traversal into nested claims. The last segment must resolve to a list of strings.
   *
   * <p>Examples:
   * <ul>
   *   <li>{@code realm_access.roles} — reads {@code realm_access.roles}
   *   <li>{@code resource_access.gateway-service.roles} — reads client-level roles
   * </ul>
   *
   * @param token JWT token
   * @return List of permission strings (e.g., ["USER_READ", "USER_WRITE"])
   */
  public List<String> extractPermissions(String token) {
    try {
      DecodedJWT decodedJWT = JWT.decode(token);
      // The rolesClaimPath is split into a top-level JWT claim and a remaining nested path.
      // The first split(limit=2) ensures only the first dot is used, so keys containing dots
      // or hyphens (like "gateway-service") in deeper segments are handled correctly.
      //
      // Example: rolesClaimPath = "resource_access.gateway-service.roles"
      //   pathSegments[0] = "resource_access"        → top-level JWT claim
      //   pathSegments[1] = "gateway-service.roles"   → nested path within that claim
      //
      // The JWT claim "resource_access" resolves to:
      //   { "gateway-service": { "roles": ["HTTPBIN_READ", "HTTPBIN_WRITE"] }, ... }
      //
      // The loop then walks the remaining segments to reach the "roles" list:
      //   Step 1: traverse into "gateway-service" map
      //   Step 2: read "roles" list from that map
      //
      // For a simpler path like "realm_access.roles", the loop is skipped entirely
      // since there are no intermediate segments — "roles" is read directly.
      String[] pathSegments = rolesClaimPath.split("\\.", 2);

      if (pathSegments.length < 2) {
        logger.warn("Invalid roles-claim-path '{}': expected at least two segments", rolesClaimPath);
        return new ArrayList<>();
      }

      Map<String, Object> currentMap = decodedJWT.getClaim(pathSegments[0]).asMap();
      if (currentMap == null) {
        logger.warn("Claim '{}' not found in token", pathSegments[0]);
        return new ArrayList<>();
      }

      String[] remainingSegments = pathSegments[1].split("\\.");
      for (int i = 0; i < remainingSegments.length - 1; i++) {
        Object nested = currentMap.get(remainingSegments[i]);
        if (nested instanceof Map) {
          @SuppressWarnings("unchecked")
          Map<String, Object> nestedMap = (Map<String, Object>) nested;
          currentMap = nestedMap;
        } else {
          logger.warn("Segment '{}' in path '{}' did not resolve to a map", remainingSegments[i], rolesClaimPath);
          return new ArrayList<>();
        }
      }

      String lastSegment = remainingSegments[remainingSegments.length - 1];
      Object rolesObj = currentMap.get(lastSegment);

      if (rolesObj instanceof List) {
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) rolesObj;
        logger.debug("Extracted permissions from '{}': {}", rolesClaimPath, roles);
        return roles;
      }

      logger.warn("No roles found at path '{}' in token", rolesClaimPath);
      return new ArrayList<>();
    } catch (Exception e) {
      logger.error("Error extracting permissions from token using path '{}'", rolesClaimPath, e);
      return new ArrayList<>();
    }
  }

  /**
   * Extract username from JWT token
   *
   * @param token JWT token
   * @return Username from token (tries preferred_username, then sub, then email)
   */
  public String extractUsername(String token) {
    try {
      DecodedJWT decodedJWT = JWT.decode(token);

      // Try preferred_username (most common in Keycloak)
      String username =
          decodedJWT.getClaim(GatewayConstants.JwtClaims.PREFERRED_USERNAME).asString();
      if (username != null && !username.isEmpty()) {
        logger.debug("Extracted username from preferred_username: {}", username);
        return username;
      }

      // Fallback to subject (user ID)
      String subject = decodedJWT.getSubject();
      if (subject != null && !subject.isEmpty()) {
        logger.debug("Extracted username from subject: {}", subject);
        return subject;
      }

      // Fallback to email
      String email = decodedJWT.getClaim(GatewayConstants.JwtClaims.EMAIL).asString();
      if (email != null && !email.isEmpty()) {
        logger.debug("Extracted username from email: {}", email);
        return email;
      }

      logger.warn("No username claim found in token");
      return GatewayConstants.Messages.UNKNOWN_USER;
    } catch (Exception e) {
      logger.error("Error extracting username from token", e);
      return "unknown";
    }
  }
}
