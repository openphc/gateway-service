package org.mdl.smartcare.gateway.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.mdl.smartcare.gateway.config.BasicAuthConfig;
import org.mdl.smartcare.gateway.constants.GatewayConstants;
import org.mdl.smartcare.gateway.exception.SmartcareBusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Exchanges HTTP Basic credentials for a Keycloak access token using the OAuth2
 * resource-owner-password grant.
 *
 * <p>Tokens are cached per credential (keyed by a SHA-256 hash of the raw {@code user:password}
 * pair — the plaintext is never retained) and reused until shortly before expiry, so a client that
 * replays Basic auth on every request does not trigger a token call to Keycloak each time.
 */
@Component
public class BasicAuthTokenService {

  private static final Logger logger = LoggerFactory.getLogger(BasicAuthTokenService.class);

  /** Refresh a cached token this many milliseconds before its actual expiry. */
  private static final long EXPIRY_BUFFER_MILLIS = 30_000L;

  @Autowired private WebClient webClient;

  @Autowired private BasicAuthConfig basicAuthConfig;

  private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

  /**
   * Decode a Base64 {@code Basic} credential and exchange it for a Keycloak access token.
   *
   * @param base64Credentials the portion of the Authorization header after the {@code "Basic "}
   *     prefix
   * @return a {@link Mono} emitting the access token, or an error if decoding or the exchange fails
   */
  public Mono<String> exchangeForToken(String base64Credentials) {
    final String[] userPass;
    try {
      userPass = decodeCredentials(base64Credentials);
    } catch (RuntimeException e) {
      return Mono.error(new SmartcareBusinessException(GatewayConstants.Messages.INVALID_BASIC_HEADER));
    }

    String cacheKey = hash(base64Credentials);
    CachedToken cached = cache.get(cacheKey);
    if (cached != null && !cached.isExpired()) {
      logger.debug("Reusing cached token for Basic credential");
      return Mono.just(cached.token());
    }

    return requestToken(userPass[0], userPass[1])
        .doOnNext(resp -> cache.put(cacheKey, CachedToken.from(resp)))
        .map(TokenResponse::accessToken);
  }

  private Mono<TokenResponse> requestToken(String username, String password) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add(GatewayConstants.Keycloak.PARAM_GRANT_TYPE, GatewayConstants.Keycloak.GRANT_TYPE_PASSWORD);
    form.add(GatewayConstants.Keycloak.PARAM_CLIENT_ID, basicAuthConfig.getClientId());
    if (StringUtils.hasText(basicAuthConfig.getClientSecret())) {
      form.add(GatewayConstants.Keycloak.PARAM_CLIENT_SECRET, basicAuthConfig.getClientSecret());
    }
    form.add(GatewayConstants.Keycloak.PARAM_USERNAME, username);
    form.add(GatewayConstants.Keycloak.PARAM_PASSWORD, password);

    return webClient
        .post()
        .uri(basicAuthConfig.getTokenUri())
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(BodyInserters.fromFormData(form))
        .retrieve()
        .bodyToMono(TokenResponse.class)
        .doOnError(e -> logger.error("Keycloak token exchange failed for user '{}'", username, e));
  }

  private String[] decodeCredentials(String base64Credentials) {
    byte[] decoded = Base64.getDecoder().decode(base64Credentials.trim());
    String pair = new String(decoded, StandardCharsets.UTF_8);
    int sep = pair.indexOf(':');
    if (sep < 0) {
      throw new IllegalArgumentException("Missing ':' separator in Basic credentials");
    }
    return new String[] {pair.substring(0, sep), pair.substring(sep + 1)};
  }

  private String hash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      // SHA-256 is always available; fall back to identity so caching still works.
      return value;
    }
  }

  /** Cached access token with an absolute expiry timestamp (epoch millis). */
  private record CachedToken(String token, long expiresAtMillis) {

    boolean isExpired() {
      return System.currentTimeMillis() >= expiresAtMillis - EXPIRY_BUFFER_MILLIS;
    }

    static CachedToken from(TokenResponse resp) {
      long ttlMillis = Math.max(0L, resp.expiresIn()) * 1000L;
      return new CachedToken(resp.accessToken(), System.currentTimeMillis() + ttlMillis);
    }
  }
}
