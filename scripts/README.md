# Permission Sync Scripts for CI/CD

These scripts sync permissions from the database to Keycloak via the Gateway API. They include health checks, retry logic, and proper error handling.

---

## 📁 Available Scripts

| Script | Platform | Best For |
|--------|----------|----------|
| `sync-permissions.sh` | Linux/macOS/Git Bash | Jenkins, GitLab CI, Linux-based CI/CD |
| `sync-permissions.py` | Cross-platform | Any CI/CD with Python available |

---

## ⚙️ Configuration

All scripts use the same environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `SYNC_PERMISSIONS` | Enable sync (`true` or `false`) | `false` |
| `GATEWAY_URL` | Gateway service URL | `http://localhost:8090` |
| `SYNC_TIMEOUT` | Request timeout in seconds | `60` |
| `SYNC_RETRIES` | Number of retry attempts | `3` |
| `SYNC_RETRY_DELAY` | Delay between retries (seconds) | `5` |

---

## 🚀 Usage

### Bash Script (Linux/macOS/Git Bash)

```bash
# Make executable
chmod +x scripts/sync-permissions.sh

# Run with sync enabled
SYNC_PERMISSIONS=true \
GATEWAY_URL=http://gateway:8090 \
./scripts/sync-permissions.sh
```

### Python Script (Cross-platform)

```bash
# Install dependencies
pip install requests

# Run with sync enabled
SYNC_PERMISSIONS=true \
GATEWAY_URL=http://gateway:8090 \
python scripts/sync-permissions.py
```

---

## 🔧 CI/CD Integration Examples

### GitHub Actions

```yaml
name: Deploy and Sync Permissions

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Deploy Gateway Service
        run: |
          kubectl apply -f k8s/gateway-deployment.yaml
          kubectl rollout status deployment/gateway-service
      
      - name: Sync Permissions
        env:
          SYNC_PERMISSIONS: 'true'
          GATEWAY_URL: ${{ secrets.GATEWAY_URL }}
          SYNC_TIMEOUT: '120'
          SYNC_RETRIES: '5'
        run: |
          chmod +x scripts/sync-permissions.sh
          ./scripts/sync-permissions.sh
```

### GitLab CI

```yaml
stages:
  - deploy
  - sync

deploy:
  stage: deploy
  script:
    - kubectl apply -f k8s/gateway-deployment.yaml
    - kubectl rollout status deployment/gateway-service

sync-permissions:
  stage: sync
  variables:
    SYNC_PERMISSIONS: "true"
    GATEWAY_URL: "${GATEWAY_SERVICE_URL}"
    SYNC_TIMEOUT: "120"
  script:
    - chmod +x scripts/sync-permissions.sh
    - ./scripts/sync-permissions.sh
  only:
    - main
```

## 🛡️ Best Practices

### 1. **Use Secrets for URLs**

```yaml
# Don't hardcode URLs in scripts
env:
  GATEWAY_URL: ${{ secrets.GATEWAY_URL }}
```

### 2. **Increase Timeout for Large Syncs**

```bash
# If you have many permissions
export SYNC_TIMEOUT=180  # 3 minutes
export SYNC_RETRIES=5
```

### 3. **Run After Database Migrations**

```yaml
stages:
  - build
  - database
  - deploy
  - sync  # Run after deploy

sync-permissions:
  stage: sync
  needs: ['deploy']  # Wait for deployment
```

### 4. **Only Sync on Main Branch**

```bash
# In your CI script
if [ "$CI_BRANCH" = "main" ]; then
  SYNC_PERMISSIONS=true ./scripts/sync-permissions.sh
fi
```

### 5. **Add to Deployment Script**

```bash
#!/bin/bash
# deploy.sh

# Deploy services
kubectl apply -f k8s/

# Wait for rollout
kubectl rollout status deployment/gateway-service

# Sync permissions
SYNC_PERMISSIONS=true \
GATEWAY_URL=http://gateway-service:8090 \
./scripts/sync-permissions.sh

echo "Deployment and sync complete!"
```

---

## 📝 Troubleshooting

### Issue: "Connection refused"

**Cause:** Gateway not accessible

**Solution:**
```bash
# Check gateway is running
curl http://gateway:8090/api/permissions/health

# Check network/firewall
ping gateway

# Use correct URL
export GATEWAY_URL=http://correct-gateway-url:8090
```

### Issue: "Timeout"

**Cause:** Sync taking too long

**Solution:**
```bash
# Increase timeout
export SYNC_TIMEOUT=180
export SYNC_RETRIES=5
```

### Issue: "Script not executable"

**Solution:**
```bash
chmod +x scripts/sync-permissions.sh
```

---

**Last Updated:** January 7, 2026  
**Version:** 1.0.0

