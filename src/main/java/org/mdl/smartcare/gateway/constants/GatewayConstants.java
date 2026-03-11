package org.mdl.smartcare.gateway.constants;

/** Constants used across the Gateway Service */
public final class GatewayConstants {

  private GatewayConstants() {
    // Prevent instantiation
  }

  /** Keycloak-related constants */
  public static final class Keycloak {
    private Keycloak() {}

    // System roles that should never be deleted
    public static final String ROLE_OFFLINE_ACCESS = "offline_access";
    public static final String ROLE_UMA_AUTHORIZATION = "uma_authorization";
    public static final String ROLE_PREFIX_DEFAULT = "default-roles-";
    public static final String ROLE_PREFIX_OFFLINE = "offline_";
    public static final String ROLE_PREFIX_UMA = "uma_";

    // Keycloak endpoints
    public static final String ENDPOINT_OPENID_CERTS = "/protocol/openid-connect/certs";
    public static final String ENDPOINT_TOKEN_INTROSPECT =
        "/protocol/openid-connect/token/introspect";

    // Grant types
    public static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";
    public static final String GRANT_TYPE_PASSWORD = "password";

    // Realms
    public static final String REALM_MASTER = "master";
  }

  /** HTTP Headers */
  public static final class Headers {
    private Headers() {}

    public static final String AUTHORIZATION = "Authorization";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String BEARER_PREFIX = "Bearer ";

    // Custom headers for downstream services
    public static final String X_USER_NAME = "X-User-Name";
    public static final String X_AUTH_TOKEN = "X-Auth-Token";
  }

  /** HTTP Status messages */
  public static final class Messages {
    private Messages() {}

    public static final String UNAUTHORIZED = "Unauthorized";
    public static final String FORBIDDEN = "Forbidden";
    public static final String INVALID_AUTH_HEADER = "Invalid Authorization header";
    public static final String MISSING_AUTH_HEADER = "Missing or invalid Authorization header";
    public static final String INVALID_TOKEN = "Invalid or expired token";
    public static final String ACCESS_DENIED = "Access denied: Insufficient permissions";
    public static final String TOKEN_VALIDATION_FAILED = "Token validation failed: ";
    public static final String UNKNOWN_USER = "unknown";
  }

  /** JWT Claim names */
  public static final class JwtClaims {
    private JwtClaims() {}

    public static final String PREFERRED_USERNAME = "preferred_username";
    public static final String SUB = "sub";
    public static final String EMAIL = "email";
  }

  /** Public endpoints that don't require authentication */
  public static final class PublicPaths {
    private PublicPaths() {}

    public static final String ACTUATOR = "/actuator";
    public static final String HEALTH = "/health";
    public static final String INFO = "/info";
    public static final String ROOT = "/";
    public static final String PUBLIC = "/public";
  }

  /** API endpoints */
  public static final class ApiPaths {
    private ApiPaths() {}

    public static final String PERMISSIONS_BASE = "/api/permissions";
    public static final String SYNC_TO_KEYCLOAK = "/sync-to-keycloak";
    public static final String TEST_CONNECTION = "/keycloak/test-connection";
    public static final String KEYCLOAK_ROLES = "/keycloak/roles";
    public static final String HEALTH_CHECK = "/health";
  }

  /** Response messages */
  public static final class ResponseMessages {
    private ResponseMessages() {}

    public static final String CONNECTED_TO_KEYCLOAK = "Connected to Keycloak";
    public static final String CONNECTION_FAILED = "Connection test failed: ";
    public static final String SYNC_SUCCESS = "Synced %d permissions successfully";
    public static final String SYNC_FAILED = "Error syncing permissions: ";
    public static final String NO_PERMISSIONS_FOUND = "No permissions found in database";
    public static final String PERMISSIONS_RELOADED = "Permissions reloaded successfully";
  }

  /** Log messages */
  public static final class LogMessages {
    private LogMessages() {}

    public static final String AUTH_DISABLED = "Authorization is disabled - allowing request {} {}";
    public static final String AUTH_NO_PERMISSIONS =
        "Authorization failed: No permissions found for request {} {}";
    public static final String AUTH_NO_MAPPINGS =
        "Authorization failed: No permission mappings configured";
    public static final String AUTH_GRANTED = "Authorization granted: Permission '{}' allows {} {}";
    public static final String AUTH_DENIED =
        "Authorization denied: No matching permission found for {} {} with permissions {}";

    public static final String LOADING_PERMISSIONS = "Loading permissions from database...";
    public static final String PERMISSIONS_LOADED =
        "Successfully loaded {} permissions from database covering {} unique permission names";
    public static final String NO_PERMISSIONS_IN_DB =
        "No permissions found in database. Using YAML configuration as fallback.";

    public static final String KEYCLOAK_CLIENT_CREATED =
        "Successfully created Keycloak client with custom Jackson configuration";
    public static final String KEYCLOAK_CLIENT_FAILED =
        "Failed to create Keycloak client. Server: {}, Realm: {}";
    public static final String KEYCLOAK_ROLE_CREATED = "Created new Keycloak role: {}";
    public static final String KEYCLOAK_ROLE_UPDATED = "Updated existing Keycloak role: {}";
    public static final String KEYCLOAK_ROLE_EXISTS =
        "Keycloak role already exists with same description: {}";
    public static final String KEYCLOAK_ROLE_DELETED =
        "Deleted Keycloak role (not in database): {}";
    public static final String KEYCLOAK_ROLE_DELETE_FAILED =
        "Failed to delete Keycloak role: {}. Error: {}";
    public static final String KEYCLOAK_SYNC_COMPLETED =
        "Keycloak sync completed. Success: {}, Failed: {}";
  }
}
