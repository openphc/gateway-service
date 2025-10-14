# Coarse Authorization Implementation

This document describes the implementation of coarse-grained authorization for the Gateway Service.

## Overview

The gateway service now performs three main functions:
1. **Reverse Proxy** - Routes requests to downstream services
2. **JWT Validation** - Validates access tokens from Keycloak
3. **Coarse Authorization** - Checks if users have permissions to access requested resources

## Architecture

### Flow Sequence

1. Request arrives at gateway
2. Skip public endpoints (actuator, health, etc.)
3. **Extract JWT** from Authorization header
4. **Validate JWT** against Keycloak JWKS
5. **Extract Permissions** from `realm_access.roles` claim
6. **Check Authorization** against configured permission mappings
7. Forward to downstream service (if authorized) OR return 403 Forbidden

### Components

#### 1. PermissionConfig.java
- Reads permission mappings from `application.yml`
- Uses Spring Boot's `@ConfigurationProperties` annotation
- Maps permission names (e.g., `USER_READ`) to HTTP method + URI patterns

#### 2. AuthorizationService.java
- Core authorization logic
- Extracts user permissions from JWT claims
- Matches request (HTTP method + URI) against configured permission rules
- Uses `AntPathMatcher` for flexible URI pattern matching
- Logs authorization decisions for audit purposes

#### 3. JwtUtil.java (Enhanced)
- **New Method**: `extractPermissions(String token)`
- Decodes JWT and extracts permissions from `realm_access.roles` claim
- Returns list of permission strings (e.g., `["USER_READ", "USER_WRITE"]`)

#### 4. JwtValidationFilter.java (Enhanced)
- Integrated authorization check after JWT validation
- **New Method**: `handleForbidden()` - Returns HTTP 403 for authorization failures
- Calls `AuthorizationService.isAuthorized()` before forwarding requests

## Configuration

### Feature Toggle

Authorization can be enabled or disabled using the `authorization.enabled` property:

```yaml
authorization:
  enabled: true  # Set to false to disable authorization checks
```

This can also be controlled via environment variable:
```bash
AUTHORIZATION_ENABLED=false
```

When disabled:
- JWT validation is still performed (authentication)
- Authorization checks are bypassed
- All authenticated requests are allowed through
- Useful for testing, debugging, or gradual rollout

### Permission Mappings (application.yml)

```yaml
authorization:
  enabled: true
  permission-mappings:
    USER_READ:
      - method: GET
        uri: /admin/users/*
      - method: GET
        uri: /admin/users
    USER_WRITE:
      - method: POST
        uri: /admin/users
    USER_UPDATE:
      - method: PUT
        uri: /admin/users/*
      - method: PATCH
        uri: /admin/users/*
    USER_DELETE:
      - method: DELETE
        uri: /admin/users/*
```

### URI Pattern Matching

The implementation uses Ant-style pattern matching:

- **Exact Match**: `/admin/users` matches only `/admin/users`
- **Single Level**: `/admin/users/*` matches `/admin/users/123` but NOT `/admin/users/123/profile`
- **Multi Level**: `/admin/users/**` matches `/admin/users/123` AND `/admin/users/123/profile`

### JWT Structure Expected

```json
{
  "realm_access": {
    "roles": [
      "USER_READ",
      "USER_WRITE",
      "USER_DELETE"
    ]
  },
  "iss": "https://keycloak.mdtlabs.org/realms/smartcare",
  "aud": "account"
}
```

## HTTP Response Codes

| Scenario | Status Code | Description |
|----------|-------------|-------------|
| Missing/Invalid JWT | 401 Unauthorized | Authentication failed |
| Expired JWT | 401 Unauthorized | Token expired |
| No matching permission | 403 Forbidden | User authenticated but lacks permission |
| Valid permission | 200/2XX | Request forwarded to downstream service |

## Example Scenarios

### Scenario 1: Authorized Access
**Request**: `GET /admin/users/123`  
**JWT Permissions**: `["USER_READ", "ORDER_CREATE"]`  
**Result**: ✅ Authorized (USER_READ allows GET /admin/users/*)

### Scenario 2: Unauthorized Access
**Request**: `DELETE /admin/users/123`  
**JWT Permissions**: `["USER_READ"]`  
**Result**: ❌ 403 Forbidden (USER_READ doesn't allow DELETE)

### Scenario 3: Multiple Permissions
**Request**: `PUT /admin/users/456`  
**JWT Permissions**: `["USER_READ", "USER_UPDATE"]`  
**Result**: ✅ Authorized (USER_UPDATE allows PUT /admin/users/*)

## Logging

Authorization decisions are logged at the following levels:

- **INFO**: Successful authorization with granted permission
- **WARN**: Authorization failures with attempted method, URI, and user permissions

Example log entries:
```
INFO  - Authorization granted: Permission 'USER_READ' allows GET /admin/users/123
WARN  - Authorization denied: No matching permission found for DELETE /admin/users/123 with permissions [USER_READ]
```

## Future Enhancements

The current implementation stores permission mappings in `application.yml`. As mentioned, future enhancements will include:

1. **Database-backed Permissions**: Move permission definitions to a database table
2. **Dynamic Reload**: Allow permission changes without restarting the service
3. **Permission Management API**: CRUD endpoints for managing permissions
4. **Role Hierarchy**: Support for hierarchical roles (e.g., ADMIN inherits all USER permissions)
5. **Fine-grained Authorization**: Support for resource-level permissions (e.g., user can only modify their own profile)

## Testing

To test the authorization layer:

1. **Generate a Keycloak access token** with specific roles in `realm_access.roles`
2. **Make requests** to protected endpoints
3. **Verify responses**:
   - With correct permissions: Request forwarded (2XX)
   - Without permissions: 403 Forbidden
   - Without valid token: 401 Unauthorized

## Configuration Changes Required

To add new permissions:

1. Add the permission to Keycloak realm roles
2. Assign the role to appropriate users/groups
3. Add permission mapping to `application.yml`:
   ```yaml
   authorization:
     permission-mappings:
       NEW_PERMISSION:
         - method: POST
           uri: /api/resource/*
   ```
4. Restart the gateway service (until dynamic reload is implemented)

