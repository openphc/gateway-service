#!/bin/bash
################################################################################
# Permission Sync Script for CI/CD
# 
# This script syncs permissions from database to Keycloak via the Gateway API.
# It includes health checks, retry logic, and proper error handling.
#
# Environment Variables:
#   SYNC_PERMISSIONS     - Set to 'true' to enable sync (default: false)
#   GATEWAY_URL          - Gateway service URL (default: http://localhost:8090)
#   SYNC_TIMEOUT         - Timeout in seconds (default: 60)
#   SYNC_RETRIES         - Number of retries (default: 3)
#   SYNC_RETRY_DELAY     - Delay between retries in seconds (default: 5)
#
# Usage:
#   ./sync-permissions.sh
#   SYNC_PERMISSIONS=true GATEWAY_URL=http://gateway:8090 ./sync-permissions.sh
#
# Exit Codes:
#   0 - Success
#   1 - Sync disabled (not an error)
#   2 - Health check failed
#   3 - Sync failed
################################################################################

set -e  # Exit on error

# Configuration from environment variables
SYNC_PERMISSIONS="${SYNC_PERMISSIONS:-false}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8090}"
SYNC_TIMEOUT="${SYNC_TIMEOUT:-60}"
SYNC_RETRIES="${SYNC_RETRIES:-3}"
SYNC_RETRY_DELAY="${SYNC_RETRY_DELAY:-5}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if sync is enabled
if [ "$SYNC_PERMISSIONS" != "true" ]; then
    log_warning "Permission sync is disabled (SYNC_PERMISSIONS != true)"
    log_info "To enable, set: export SYNC_PERMISSIONS=true"
    exit 1
fi

log_info "Permission sync enabled"
log_info "Gateway URL: $GATEWAY_URL"
log_info "Timeout: ${SYNC_TIMEOUT}s, Retries: $SYNC_RETRIES, Retry Delay: ${SYNC_RETRY_DELAY}s"

# Health check
log_info "Checking gateway health..."
HEALTH_URL="${GATEWAY_URL}/api/permissions/health"

if ! curl -sf --max-time 10 "$HEALTH_URL" > /dev/null 2>&1; then
    log_error "Gateway health check failed at $HEALTH_URL"
    log_error "Make sure the gateway service is running and accessible"
    exit 2
fi

log_success "Gateway is healthy"

# Sync permissions with retry logic
SYNC_URL="${GATEWAY_URL}/api/permissions/sync-to-keycloak"
log_info "Syncing permissions to Keycloak..."

attempt=1
while [ $attempt -le $SYNC_RETRIES ]; do
    log_info "Attempt $attempt/$SYNC_RETRIES..."
    
    # Call sync API
    response=$(curl -sf --max-time "$SYNC_TIMEOUT" -X POST "$SYNC_URL" 2>&1) || sync_failed=true
    
    if [ -z "$sync_failed" ]; then
        # Parse response
        success=$(echo "$response" | grep -o '"success":[^,]*' | cut -d':' -f2 | tr -d ' ')
        message=$(echo "$response" | grep -o '"message":"[^"]*"' | cut -d'"' -f4)
        successCount=$(echo "$response" | grep -o '"successCount":[0-9]*' | cut -d':' -f2)
        failureCount=$(echo "$response" | grep -o '"failureCount":[0-9]*' | cut -d':' -f2)
        deletedCount=$(echo "$response" | grep -o '"deletedCount":[0-9]*' | cut -d':' -f2)
        
        # Check if sync was successful
        if [ "$success" = "true" ]; then
            log_success "Permission sync completed!"
            log_info "  Created/Updated: $successCount"
            log_info "  Deleted: $deletedCount"
            log_info "  Failed: $failureCount"
            log_info "  Message: $message"
            exit 0
        else
            log_error "Sync reported failure: $message"
            log_error "  Failed count: $failureCount"
        fi
    else
        log_error "Sync request failed (attempt $attempt/$SYNC_RETRIES)"
    fi
    
    # Retry if not last attempt
    if [ $attempt -lt $SYNC_RETRIES ]; then
        log_warning "Retrying in ${SYNC_RETRY_DELAY}s..."
        sleep "$SYNC_RETRY_DELAY"
    fi
    
    attempt=$((attempt + 1))
    unset sync_failed
done

log_error "Permission sync failed after $SYNC_RETRIES attempts"
exit 3

