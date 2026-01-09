package org.mdl.smartcare.gateway.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.ext.ContextResolver;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.mdl.smartcare.gateway.config.KeycloakConfig;
import org.mdl.smartcare.gateway.constants.GatewayConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class KeycloakAdminService {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakAdminService.class);

    @Autowired
    private KeycloakConfig keycloakConfig;

    /**
     * Get Keycloak admin client instance with custom ObjectMapper to ignore unknown
     * properties
     * 
     * @return Keycloak instance
     */
    private Keycloak getKeycloakClient() {
        try {
            // Configure Jackson ObjectMapper to ignore unknown properties (version
            // compatibility)
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);

            // Create RESTEasy Jackson2 provider with the configured ObjectMapper
            ResteasyJackson2Provider resteasyProvider = new ResteasyJackson2Provider();
            resteasyProvider.setMapper(objectMapper);

            // Create a ContextResolver for ObjectMapper
            ContextResolver<ObjectMapper> contextResolver = new ContextResolver<ObjectMapper>() {
                @Override
                public ObjectMapper getContext(Class<?> type) {
                    return objectMapper;
                }
            };

            // Create Resteasy client with Jackson provider and context resolver
            ResteasyClientBuilder clientBuilder = (ResteasyClientBuilder) ClientBuilder.newBuilder();
            clientBuilder.register(resteasyProvider);
            clientBuilder.register(contextResolver);

            ResteasyClient resteasyClient = clientBuilder.build();

            // Build Keycloak client
            Keycloak keycloak = KeycloakBuilder.builder()
                    .serverUrl(keycloakConfig.getServerUrl())
                    .realm(keycloakConfig.getRealm())
                    .clientId(keycloakConfig.getClientId())
                    .username(keycloakConfig.getUsername())
                    .password(keycloakConfig.getPassword())
                    .resteasyClient(resteasyClient)
                    .build();

            logger.info(GatewayConstants.LogMessages.KEYCLOAK_CLIENT_CREATED);
            return keycloak;
        } catch (Exception e) {
            logger.error(GatewayConstants.LogMessages.KEYCLOAK_CLIENT_FAILED,
                    keycloakConfig.getServerUrl(), keycloakConfig.getRealm(), e);
            throw new RuntimeException("Failed to connect to Keycloak: " + e.getMessage(), e);
        }
    }

    /**
     * Create or update a realm role in Keycloak (idempotent)
     * 
     * @param roleName    Role name (permission name)
     * @param description Role description
     * @return true if created/updated, false on error
     */
    public boolean createOrUpdateRealmRole(String roleName, String description, RolesResource rolesResource) {

        try {
            // Try to get existing role (idempotency check)
            RoleRepresentation existingRole = rolesResource.get(roleName).toRepresentation();

            // Role exists, update description if different
            if (!description.equals(existingRole.getDescription())) {
                existingRole.setDescription(description);
                rolesResource.get(roleName).update(existingRole);
                logger.info(GatewayConstants.LogMessages.KEYCLOAK_ROLE_UPDATED, roleName);
                return true;
            } else {
                logger.info(GatewayConstants.LogMessages.KEYCLOAK_ROLE_EXISTS, roleName);
                return true;
            }
        } catch (NotFoundException e) {
            // Role doesn't exist, create it
            RoleRepresentation newRole = new RoleRepresentation();
            newRole.setName(roleName);
            newRole.setDescription(description);

            try {
                rolesResource.create(newRole);
                logger.info(GatewayConstants.LogMessages.KEYCLOAK_ROLE_CREATED, roleName);
                return true;
            } catch (Exception createEx) {
                logger.error("Failed to create Keycloak role: {}", roleName, createEx);
                return false;
            }
        }
    }

    /**
     * Sync multiple roles to Keycloak (create, update, and delete)
     * 
     * @param roles List of role names and descriptions from database
     * @return SyncResult with success/failure counts
     */
    public SyncResult syncRolesToKeycloak(List<RoleInfo> roles) {
        int successCount = 0;
        int failureCount = 0;
        int deletedCount = 0;
        List<String> failedRoles = new ArrayList<>();
        
        try (Keycloak keycloak = getKeycloakClient()) {
            RealmResource realmResource = keycloak.realm(keycloakConfig.getTargetRealm());
            RolesResource rolesResource = realmResource.roles();
            
            // Step 1: Create or update roles from database
            logger.info("Step 1: Creating/updating {} roles in Keycloak...", roles.size());
            for (RoleInfo roleInfo : roles) {
                boolean success = createOrUpdateRealmRole(roleInfo.getName(), roleInfo.getDescription(), rolesResource);
                if (success) {
                    successCount++;
                } else {
                    failureCount++;
                    failedRoles.add(roleInfo.getName());
                }
            }

            // Step 2: Delete roles that exist in Keycloak but not in database
            logger.info("Step 2: Checking for roles to delete in Keycloak...");
            deletedCount = deleteRolesNotInDatabase(rolesResource, roles);

            logger.info("Keycloak sync completed. Created/Updated: {}, Failed: {}, Deleted: {}", 
                       successCount, failureCount, deletedCount);
            return new SyncResult(successCount, failureCount, deletedCount, failedRoles);
            
        } catch (Exception e) {
            logger.error("Failed to sync roles to Keycloak. Error: {}", e.getMessage(), e);
            throw new RuntimeException("Keycloak sync failed: " + e.getMessage(), e);
        }
    }

    /**
     * Delete realm roles that exist in Keycloak but not in database
     * 
     * @param rolesResource Keycloak roles resource
     * @param databaseRoles List of roles from database
     * @return Number of roles deleted
     */
    private int deleteRolesNotInDatabase(RolesResource rolesResource, List<RoleInfo> databaseRoles) {
        int deletedCount = 0;
        
        try {
            // Get all current roles from Keycloak
            List<RoleRepresentation> keycloakRoles = rolesResource.list();
            
            // Build set of database role names for quick lookup
            Set<String> databaseRoleNames = new HashSet<>();
            for (RoleInfo roleInfo : databaseRoles) {
                databaseRoleNames.add(roleInfo.getName());
            }
            
            // Define system roles that should never be deleted
            Set<String> systemRoles = Set.of(
                GatewayConstants.Keycloak.ROLE_PREFIX_DEFAULT + keycloakConfig.getTargetRealm().toLowerCase(),
                GatewayConstants.Keycloak.ROLE_OFFLINE_ACCESS,
                GatewayConstants.Keycloak.ROLE_UMA_AUTHORIZATION
            );
            
            // Delete roles that are in Keycloak but not in database (and not system roles)
            for (RoleRepresentation keycloakRole : keycloakRoles) {
                String roleName = keycloakRole.getName();
                
                // Skip system roles
                if (systemRoles.contains(roleName)) {
                    continue;
                }
                
                // Skip roles that start with system prefixes
                if (roleName.startsWith(GatewayConstants.Keycloak.ROLE_PREFIX_DEFAULT) || 
                    roleName.startsWith(GatewayConstants.Keycloak.ROLE_PREFIX_OFFLINE) ||
                    roleName.startsWith(GatewayConstants.Keycloak.ROLE_PREFIX_UMA)) {
                    continue;
                }
                
                // If role exists in Keycloak but not in database, delete it
                if (!databaseRoleNames.contains(roleName)) {
                    try {
                        rolesResource.deleteRole(roleName);
                        logger.info(GatewayConstants.LogMessages.KEYCLOAK_ROLE_DELETED, roleName);
                        deletedCount++;
                    } catch (Exception deleteEx) {
                        logger.warn(GatewayConstants.LogMessages.KEYCLOAK_ROLE_DELETE_FAILED, 
                                   roleName, deleteEx.getMessage());
                    }
                }
            }
            
            if (deletedCount > 0) {
                logger.info("Deleted {} roles from Keycloak that are not in database", deletedCount);
            } else {
                logger.info("No roles to delete from Keycloak");
            }
            
        } catch (Exception e) {
            logger.error("Error while checking for roles to delete", e);
        }
        
        return deletedCount;
    }

    /**
     * Get all realm roles from Keycloak
     * 
     * @return List of role names
     */
    public List<String> getAllRealmRoles() {
        try (Keycloak keycloak = getKeycloakClient()) {
            RealmResource realmResource = keycloak.realm(keycloakConfig.getTargetRealm());
            List<RoleRepresentation> roles = realmResource.roles().list();

            List<String> roleNames = new ArrayList<>();
            for (RoleRepresentation role : roles) {
                roleNames.add(role.getName());
            }

            logger.info("Retrieved {} realm roles from Keycloak", roleNames.size());
            return roleNames;
        } catch (Exception e) {
            logger.error("Error retrieving realm roles from Keycloak", e);
            return new ArrayList<>();
        }
    }

    /**
     * Check if Keycloak connection is working
     * 
     * @return true if connected, false otherwise
     */
    public boolean testConnection() {
        try (Keycloak keycloak = getKeycloakClient()) {
            RealmResource realmResource = keycloak.realm(keycloakConfig.getRealm());
            realmResource.toRepresentation(); // This will throw if connection fails
            logger.info("Keycloak connection test successful");
            return true;
        } catch (Exception e) {
            logger.error("Keycloak connection test failed", e);
            return false;
        }
    }

    // Inner classes for result types
    public static class RoleInfo {
        private String name;
        private String description;

        public RoleInfo(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }

    public static class SyncResult {
        private int successCount;
        private int failureCount;
        private int deletedCount;
        private List<String> failedRoles;

        public SyncResult(int successCount, int failureCount, int deletedCount, List<String> failedRoles) {
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.deletedCount = deletedCount;
            this.failedRoles = failedRoles;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getFailureCount() {
            return failureCount;
        }

        public int getDeletedCount() {
            return deletedCount;
        }

        public List<String> getFailedRoles() {
            return failedRoles;
        }
    }
}
