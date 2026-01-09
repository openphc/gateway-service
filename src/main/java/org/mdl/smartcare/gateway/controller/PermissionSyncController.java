package org.mdl.smartcare.gateway.controller;

import org.mdl.smartcare.gateway.service.PermissionSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/permissions")
public class PermissionSyncController {

    private static final Logger logger = LoggerFactory.getLogger(PermissionSyncController.class);

    @Autowired
    private PermissionSyncService permissionSyncService;

    /**
     * Sync permissions from database to Keycloak as realm roles
     * This operation is idempotent - it will create new roles or update existing
     * ones
     * 
     * @return Sync result with success/failure counts
     */
    @PostMapping("/sync-to-keycloak")
    public Mono<ResponseEntity<Map<String, Object>>> syncToKeycloak() {
        logger.info("Received request to sync permissions to Keycloak");

        return permissionSyncService.syncPermissionsToKeycloak()
                .map(response -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", response.getFailureCount() == 0);
                    result.put("message", response.getMessage());
                    result.put("successCount", response.getSuccessCount());
                    result.put("failureCount", response.getFailureCount());
                    result.put("deletedCount", response.getDeletedCount());

                    if (!response.getFailedRoles().isEmpty()) {
                        result.put("failedRoles", response.getFailedRoles());
                    }

                    HttpStatus status = response.getFailureCount() > 0
                            ? HttpStatus.PARTIAL_CONTENT
                            : HttpStatus.OK;

                    return ResponseEntity.status(status).body(result);
                })
                .onErrorResume(error -> {
                    logger.error("Error syncing permissions to Keycloak", error);

                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("message", "Error syncing permissions: " + error.getMessage());
                    errorResponse.put("error", error.getClass().getSimpleName());

                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(errorResponse));
                });
    }

    /**
     * Test Keycloak connection
     * 
     * @return Connection status
     */
    @GetMapping("/keycloak/test-connection")
    public Mono<ResponseEntity<Map<String, Object>>> testConnection() {
        logger.info("Testing Keycloak connection");

        return permissionSyncService.testKeycloakConnection()
                .map(status -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("connected", status.isConnected());
                    result.put("message", status.getMessage());

                    HttpStatus httpStatus = status.isConnected()
                            ? HttpStatus.OK
                            : HttpStatus.SERVICE_UNAVAILABLE;

                    return ResponseEntity.status(httpStatus).body(result);
                })
                .onErrorResume(error -> {
                    logger.error("Error testing Keycloak connection", error);

                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("connected", false);
                    errorResponse.put("message", "Connection test failed: " + error.getMessage());

                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(errorResponse));
                });
    }

}
