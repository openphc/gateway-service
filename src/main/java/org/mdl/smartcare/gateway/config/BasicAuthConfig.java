package org.mdl.smartcare.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for inbound HTTP Basic authentication support.
 *
 * <p>When {@link #enabled} is {@code true}, the gateway accepts {@code Authorization: Basic
 * <base64(user:password)>} on protected routes and exchanges the credentials for a Keycloak access
 * token via the OAuth2 resource-owner-password grant. The resulting Bearer token then flows through
 * the normal validation/authorization pipeline.
 *
 * <p>The client identified by {@link #clientId} must have <em>Direct Access Grants</em> enabled in
 * Keycloak, and {@link #tokenUri} must point at the token endpoint of the same realm referenced by
 * {@code jwt.issuer}.
 */
@Component
@ConfigurationProperties(prefix = "basic-auth")
public class BasicAuthConfig {

  /** Whether inbound Basic authentication is accepted. Disabled by default. */
  private boolean enabled = false;

  /** Keycloak token endpoint, e.g. {@code https://host/realms/<realm>/protocol/openid-connect/token}. */
  private String tokenUri;

  /** Client used for the password grant. Must have Direct Access Grants enabled. */
  private String clientId;

  /** Client secret. Leave blank for a public client. */
  private String clientSecret;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getTokenUri() {
    return tokenUri;
  }

  public void setTokenUri(String tokenUri) {
    this.tokenUri = tokenUri;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
  }
}
