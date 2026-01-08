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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

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

            logger.info("Successfully created Keycloak client with custom Jackson configuration");
            return keycloak;
        } catch (Exception e) {
            logger.error("Failed to create Keycloak client. Server: {}, Realm: {}",
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
    public boolean createOrUpdateRealmRole(String roleName, String description) {
        try (Keycloak keycloak = getKeycloakClient()) {
            RealmResource realmResource = keycloak.realm(keycloakConfig.getTargetRealm());
            RolesResource rolesResource = realmResource.roles();

            try {
                // Try to get existing role (idempotency check)
                RoleRepresentation existingRole = rolesResource.get(roleName).toRepresentation();

                // Role exists, update description if different
                if (!description.equals(existingRole.getDescription())) {
                    existingRole.setDescription(description);
                    rolesResource.get(roleName).update(existingRole);
                    logger.info("Updated existing Keycloak role: {}", roleName);
                    return true;
                } else {
                    logger.info("Keycloak role already exists with same description: {}", roleName);
                    return true;
                }
            } catch (NotFoundException e) {
                // Role doesn't exist, create it
                RoleRepresentation newRole = new RoleRepresentation();
                newRole.setName(roleName);
                newRole.setDescription(description);

                try {
                    rolesResource.create(newRole);
                    logger.info("Created new Keycloak role: {}", roleName);
                    return true;
                } catch (Exception createEx) {
                    logger.error("Failed to create Keycloak role: {}", roleName, createEx);
                    return false;
                }
            }
        } catch (Exception e) {
            logger.error("Error creating/updating Keycloak role: {}", roleName, e);
            return false;
        }
    }

    /**
     * Sync multiple roles to Keycloak
     * 
     * @param roles List of role names and descriptions
     * @return SyncResult with success/failure counts
     */
    public SyncResult syncRolesToKeycloak(List<RoleInfo> roles) {
        int successCount = 0;
        int failureCount = 0;
        List<String> failedRoles = new ArrayList<>();

        for (RoleInfo roleInfo : roles) {
            boolean success = createOrUpdateRealmRole(roleInfo.getName(), roleInfo.getDescription());
            if (success) {
                successCount++;
            } else {
                failureCount++;
                failedRoles.add(roleInfo.getName());
            }
        }

        logger.info("Keycloak sync completed. Success: {}, Failed: {}", successCount, failureCount);
        return new SyncResult(successCount, failureCount, failedRoles);
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
        private List<String> failedRoles;

        public SyncResult(int successCount, int failureCount, List<String> failedRoles) {
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.failedRoles = failedRoles;
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
    }
}
