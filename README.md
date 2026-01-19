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

## Health Checks

The application includes health checks accessible at:
- http://localhost:8081/actuator/health
- http://localhost:8081/actuator/info
- http://localhost:8081/actuator/gateway
