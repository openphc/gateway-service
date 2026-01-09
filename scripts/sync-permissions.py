#!/usr/bin/env python3
################################################################################
# Permission Sync Script for CI/CD (Python)
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
# Requirements:
#   pip install requests
#
# Usage:
#   python sync-permissions.py
#   SYNC_PERMISSIONS=true GATEWAY_URL=http://gateway:8090 python sync-permissions.py
#
# Exit Codes:
#   0 - Success
#   1 - Sync disabled (not an error)
#   2 - Health check failed
#   3 - Sync failed
################################################################################

import os
import sys
import time
import requests
from typing import Optional

# Configuration from environment variables
SYNC_PERMISSIONS = os.getenv('SYNC_PERMISSIONS', 'false')
GATEWAY_URL = os.getenv('GATEWAY_URL', 'http://localhost:8090')
SYNC_TIMEOUT = int(os.getenv('SYNC_TIMEOUT', '60'))
SYNC_RETRIES = int(os.getenv('SYNC_RETRIES', '3'))
SYNC_RETRY_DELAY = int(os.getenv('SYNC_RETRY_DELAY', '5'))

# ANSI color codes
class Colors:
    BLUE = '\033[0;34m'
    GREEN = '\033[0;32m'
    YELLOW = '\033[1;33m'
    RED = '\033[0;31m'
    NC = '\033[0m'  # No Color

def log_info(message: str):
    print(f"{Colors.BLUE}[INFO]{Colors.NC} {message}")

def log_success(message: str):
    print(f"{Colors.GREEN}[SUCCESS]{Colors.NC} {message}")

def log_warning(message: str):
    print(f"{Colors.YELLOW}[WARNING]{Colors.NC} {message}")

def log_error(message: str):
    print(f"{Colors.RED}[ERROR]{Colors.NC} {message}")

def check_health() -> bool:
    """Check if gateway is healthy"""
    health_url = f"{GATEWAY_URL}/api/permissions/health"
    try:
        response = requests.get(health_url, timeout=10)
        return response.status_code == 200
    except Exception as e:
        log_error(f"Health check failed: {e}")
        return False

def sync_permissions() -> Optional[dict]:
    """Sync permissions to Keycloak"""
    sync_url = f"{GATEWAY_URL}/api/permissions/sync-to-keycloak"
    try:
        response = requests.post(sync_url, timeout=SYNC_TIMEOUT)
        response.raise_for_status()
        return response.json()
    except Exception as e:
        log_error(f"Sync request failed: {e}")
        return None

def main():
    # Check if sync is enabled
    if SYNC_PERMISSIONS != 'true':
        log_warning("Permission sync is disabled (SYNC_PERMISSIONS != true)")
        log_info("To enable, set: export SYNC_PERMISSIONS=true")
        sys.exit(1)

    log_info("Permission sync enabled")
    log_info(f"Gateway URL: {GATEWAY_URL}")
    log_info(f"Timeout: {SYNC_TIMEOUT}s, Retries: {SYNC_RETRIES}, Retry Delay: {SYNC_RETRY_DELAY}s")

    # Health check
    log_info("Checking gateway health...")
    if not check_health():
        log_error(f"Gateway health check failed at {GATEWAY_URL}/api/permissions/health")
        log_error("Make sure the gateway service is running and accessible")
        sys.exit(2)
    
    log_success("Gateway is healthy")

    # Sync permissions with retry logic
    log_info("Syncing permissions to Keycloak...")
    
    for attempt in range(1, SYNC_RETRIES + 1):
        log_info(f"Attempt {attempt}/{SYNC_RETRIES}...")
        
        result = sync_permissions()
        
        if result:
            if result.get('success'):
                log_success("Permission sync completed!")
                log_info(f"  Created/Updated: {result.get('successCount', 0)}")
                log_info(f"  Deleted: {result.get('deletedCount', 0)}")
                log_info(f"  Failed: {result.get('failureCount', 0)}")
                log_info(f"  Message: {result.get('message', '')}")
                sys.exit(0)
            else:
                log_error(f"Sync reported failure: {result.get('message', '')}")
                log_error(f"  Failed count: {result.get('failureCount', 0)}")
        
        # Retry if not last attempt
        if attempt < SYNC_RETRIES:
            log_warning(f"Retrying in {SYNC_RETRY_DELAY}s...")
            time.sleep(SYNC_RETRY_DELAY)
    
    log_error(f"Permission sync failed after {SYNC_RETRIES} attempts")
    sys.exit(3)

if __name__ == '__main__':
    main()

