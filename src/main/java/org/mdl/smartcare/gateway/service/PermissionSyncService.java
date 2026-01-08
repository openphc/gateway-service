package org.mdl.smartcare.gateway.service;

import org.mdl.smartcare.gateway.model.ApiPermission;
import org.mdl.smartcare.gateway.repository.ApiPermissionRepository;
import org.mdl.smartcare.gateway.security.PermissionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PermissionSyncService {

    private static final Logger logger = LoggerFactory.getLogger(PermissionSyncService.class);

    @Autowired
    private ApiPermissionRepository permissionRepository;

    @Autowired
    private KeycloakAdminService keycloakAdminService;

    @Autowired
    private PermissionConfig permissionConfig;

    /**
     * Sync all unique permissions from database to Keycloak as realm roles
     * AND reload them into the gateway's PermissionConfig
     * 
     * @return Mono of SyncResponse with results
     */
    public Mono<SyncResponse> syncPermissionsToKeycloak() {
        logger.info("Starting permission sync from database to Keycloak and Gateway...");

        return permissionRepository.findAllOrdered()
            .collectList()
            .map(permissions -> {
                if (permissions.isEmpty()) {
                    logger.warn("No permissions found in database to sync");
                    return new SyncResponse(0, 0, new ArrayList<>(), "No permissions found in database");
                }

                // Step 1: Reload permissions into gateway's PermissionConfig
                logger.info("Step 1: Reloading permissions into gateway configuration...");
                Map<String, List<PermissionConfig.PermissionRule>> permissionMappings = 
                    convertToPermissionMappings(permissions);
                
                permissionConfig.setPermissionMappings(permissionMappings);
                logger.info("Successfully reloaded {} permissions into gateway covering {} unique permission names", 
                           permissions.size(), permissionMappings.size());

                // Step 2: Sync to Keycloak
                logger.info("Step 2: Syncing {} unique permissions to Keycloak...", permissionMappings.size());
                
                // Extract unique permission names and build descriptions for Keycloak
                Map<String, List<String>> permissionDescriptions = new HashMap<>();
                
                for (ApiPermission permission : permissions) {
                    String permName = permission.getPermissionName();
                    permissionDescriptions
                        .computeIfAbsent(permName, k -> new ArrayList<>())
                        .add(String.format("%s %s", 
                            permission.getHttpMethod(), 
                            permission.getUriPattern()));
                }

                // Build role list for Keycloak
                List<KeycloakAdminService.RoleInfo> roles = new ArrayList<>();
                for (Map.Entry<String, List<String>> entry : permissionDescriptions.entrySet()) {
                    String roleName = entry.getKey();
                    String description = buildRoleDescription(entry.getValue());
                    roles.add(new KeycloakAdminService.RoleInfo(roleName, description));
                }

                // Sync to Keycloak
                KeycloakAdminService.SyncResult result = keycloakAdminService.syncRolesToKeycloak(roles);

                return new SyncResponse(
                    result.getSuccessCount(),
                    result.getFailureCount(),
                    result.getFailedRoles(),
                    String.format("Synced %d permissions to Keycloak and reloaded %d permissions in gateway", 
                                 result.getSuccessCount(), permissions.size())
                );
            })
            .doOnSuccess(response -> {
                logger.info("Complete sync finished. Keycloak: {} success, {} failed. Gateway: reloaded", 
                           response.getSuccessCount(), response.getFailureCount());
            })
            .doOnError(error -> {
                logger.error("Error during permission sync", error);
            });
    }

    /**
     * Convert database ApiPermission entities to PermissionConfig format
     * (Same logic as DatabasePermissionLoader)
     * 
     * @param apiPermissions List of ApiPermission from database
     * @return Map of permission name to list of PermissionRule
     */
    private Map<String, List<PermissionConfig.PermissionRule>> convertToPermissionMappings(
            List<ApiPermission> apiPermissions) {
        
        Map<String, List<PermissionConfig.PermissionRule>> mappings = new HashMap<>();

        for (ApiPermission apiPermission : apiPermissions) {
            String permissionName = apiPermission.getPermissionName();
            
            // Create PermissionRule
            PermissionConfig.PermissionRule rule = new PermissionConfig.PermissionRule();
            rule.setMethod(apiPermission.getHttpMethod());
            rule.setUri(apiPermission.getUriPattern());

            // Add to mappings
            mappings.computeIfAbsent(permissionName, k -> new ArrayList<>()).add(rule);
        }

        return mappings;
    }

    /**
     * Build a description for a role based on its rules
     * 
     * @param rules List of HTTP method + URI patterns
     * @return Formatted description
     */
    private String buildRoleDescription(List<String> rules) {
        if (rules.size() == 1) {
            return "Allows: " + rules.get(0);
        } else if (rules.size() <= 3) {
            return "Allows: " + String.join(", ", rules);
        } else {
            return String.format("Allows %d operations: %s, ...", 
                rules.size(), 
                String.join(", ", rules.subList(0, 2)));
        }
    }

    /**
     * Test Keycloak connection
     * 
     * @return Mono of connection status
     */
    public Mono<ConnectionStatus> testKeycloakConnection() {
        return Mono.fromCallable(() -> {
            boolean connected = keycloakAdminService.testConnection();
            return new ConnectionStatus(connected, 
                connected ? "Connected to Keycloak" : "Failed to connect to Keycloak");
        });
    }

    /**
     * Get all realm roles from Keycloak
     * 
     * @return Mono of list of role names
     */
    public Mono<List<String>> getKeycloakRoles() {
        return Mono.fromCallable(() -> keycloakAdminService.getAllRealmRoles());
    }

    // Response classes
    public static class SyncResponse {
        private int successCount;
        private int failureCount;
        private List<String> failedRoles;
        private String message;

        public SyncResponse(int successCount, int failureCount, List<String> failedRoles, String message) {
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.failedRoles = failedRoles;
            this.message = message;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getFailureCount() {
            return failureCount;
        }

        public List<String> getFailedRoles() {
            return failedRoles;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class ConnectionStatus {
        private boolean connected;
        private String message;

        public ConnectionStatus(boolean connected, String message) {
            this.connected = connected;
            this.message = message;
        }

        public boolean isConnected() {
            return connected;
        }

        public String getMessage() {
            return message;
        }
    }
}

