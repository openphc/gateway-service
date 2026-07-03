package org.mdl.smartcare.gateway.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.mdl.smartcare.gateway.constants.GatewayConstants;

/**
 * Subset of a Keycloak OAuth2 token-endpoint response. Only the fields the gateway needs are
 * mapped; any others are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TokenResponse(
    @JsonProperty(GatewayConstants.Keycloak.FIELD_ACCESS_TOKEN) String accessToken,
    @JsonProperty(GatewayConstants.Keycloak.FIELD_EXPIRES_IN) long expiresIn) {}
