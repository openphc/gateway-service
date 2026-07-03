# Gateway Service

A Spring Boot Gateway service built with Spring Cloud Gateway for routing and API management.

## Prerequisites

- Java 21
- Gradle (or use the included Gradle wrapper)
- Docker (for containerized deployment)

## Local Development

### Build the Application

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test
```

### Run the Application

```bash
# Run the application locally
./gradlew bootRun
```

The application will start on port 8081. You can access:
- Gateway: http://localhost:8081
- Health check: http://localhost:8081/actuator/health
- Gateway info: http://localhost:8081/actuator/gateway

## Docker Deployment

### Build Docker Image

```bash
# First, build the JAR file
./gradlew build

# Build the Docker image
docker build -t gateway-service .
```

### Run Docker Container

```bash
# Run the container
docker run -p 8081:8081 gateway-service

# Run with environment variables (if needed)
docker run -p 8081:8081 -e ADMIN_SERVICE_URI=http://your-admin-service:8085 gateway-service
```

### Docker Compose (Optional)

Create a `docker-compose.yml` file for easier management:

```yaml
version: '3.8'
services:
gateway-service:
	build: .
	ports:
	- "8081:8081"
	environment:
	- ADMIN_SERVICE_URI=http://admin-service:8085
	healthcheck:
	test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8081/actuator/health"]
	interval: 30s
	timeout: 10s
	retries: 3
```

Then run:
```bash
docker-compose up
```

## Configuration

The service is configured via `src/main/resources/application.yml`:

- **Port**: 8081
- **Admin Service Route**: `/admin/**` routes to `${ADMIN_SERVICE_URI:http://127.0.0.1:8085}`
- **Management Endpoints**: Health, info, and gateway endpoints are exposed

## Authentication

Protected routes (those with the `JwtValidationFilter`) accept two kinds of credentials:

- **Bearer JWT** — `Authorization: Bearer <token>`. The token is verified against the Keycloak
  realm (`jwt.issuer`), and its roles are matched against the route's required permissions.
- **HTTP Basic** (optional, off by default) — `Authorization: Basic <base64(user:password)>`. When
  enabled, the gateway exchanges the credentials for a Keycloak access token via the OAuth2
  resource-owner-password grant, then runs the same JWT validation/authorization pipeline. Tokens
  are cached per credential until shortly before expiry to avoid a Keycloak round-trip on every
  request. This lets clients that can only send Basic auth (e.g. OpenHIM channels) reach
  JWT-protected routes.

  **Keycloak setup.** Create a **dedicated** client for this flow — do not reuse the
  `gateway-service` client. Configure it as:

  - Client ID: `openhim-basic`
  - Access type: **confidential** (generates a client secret)
  - **Direct Access Grants: enabled** (this is what the password grant requires)
  - Standard Flow: disabled, Service Accounts: disabled

  Then create the OpenHIM service user in the realm and assign it the `EMITTER_INBOUND_WRITE`
  client role. The resource-owner-password grant validates the user's credentials; the dedicated
  client + secret authenticate the gateway itself. Keeping this capability isolated to
  `openhim-basic` means the bearer-only `gateway-service` client keeps its tighter posture.

  Enable it with these environment variables:

  | Variable | Default | Notes |
  | --- | --- | --- |
  | `BASIC_AUTH_ENABLED` | `false` | Set `true` to accept Basic auth |
  | `BASIC_AUTH_TOKEN_URI` | `https://keycloak.mdtlabs.org/realms/smartcare/protocol/openid-connect/token` | Must target the same realm as `JWT_ISSUER` |
  | `BASIC_AUTH_CLIENT_ID` | `openhim-basic` | Dedicated confidential client with **Direct Access Grants** enabled |
  | `BASIC_AUTH_CLIENT_SECRET` | _(empty)_ | The `openhim-basic` client secret — inject as a real secret, never commit |

  > **Note:** the resource-owner-password grant is deprecated in OAuth 2.1 and treated as legacy by
  > Keycloak. It is used here only as a compatibility bridge for Basic-only clients; prefer a proper
  > token-issuing flow long-term where possible.

## Health Checks

The application includes health checks accessible at:
- http://localhost:8081/actuator/health
- http://localhost:8081/actuator/info
- http://localhost:8081/actuator/gateway
