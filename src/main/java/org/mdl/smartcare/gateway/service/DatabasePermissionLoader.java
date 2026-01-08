package org.mdl.smartcare.gateway.service;

import org.mdl.smartcare.gateway.model.ApiPermission;
import org.mdl.smartcare.gateway.repository.ApiPermissionRepository;
import org.mdl.smartcare.gateway.security.PermissionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DatabasePermissionLoader {

    private static final Logger logger = LoggerFactory.getLogger(DatabasePermissionLoader.class);

    @Autowired
    private ApiPermissionRepository permissionRepository;

    @Autowired
    private PermissionConfig permissionConfig;

    /**
     * Load permissions from database on application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadPermissionsFromDatabase() {
        logger.info("Loading permissions from database...");

        permissionRepository.findAllOrdered()
            .collectList()
            .doOnSuccess(permissions -> {
                if (permissions.isEmpty()) {
                    logger.warn("No permissions found in database. Using YAML configuration as fallback.");
                    return;
                }

                Map<String, List<PermissionConfig.PermissionRule>> permissionMappings = 
                    convertToPermissionMappings(permissions);

                permissionConfig.setPermissionMappings(permissionMappings);
                
                logger.info("Successfully loaded {} permissions from database covering {} unique permission names", 
                           permissions.size(), permissionMappings.size());
                
                // Log summary
                permissionMappings.forEach((permName, rules) -> {
                    logger.debug("Permission '{}' has {} rules", permName, rules.size());
                });
            })
            .doOnError(error -> {
                logger.error("Failed to load permissions from database. Using YAML configuration as fallback.", error);
            })
            .onErrorResume(error -> {
                // On error, keep existing YAML configuration
                return Mono.empty();
            })
            .subscribe();
    }

    /**
     * Convert database ApiPermission entities to PermissionConfig format
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
     * Reload permissions from database (can be called manually or via scheduled task)
     * 
     * @return Mono<Void> for reactive completion
     */
    public Mono<Void> reloadPermissions() {
        logger.info("Manually reloading permissions from database...");
        
        return permissionRepository.findAllOrdered()
            .collectList()
            .doOnSuccess(permissions -> {
                if (permissions.isEmpty()) {
                    logger.warn("No permissions found in database during reload.");
                    return;
                }

                Map<String, List<PermissionConfig.PermissionRule>> permissionMappings = 
                    convertToPermissionMappings(permissions);

                permissionConfig.setPermissionMappings(permissionMappings);
                
                logger.info("Successfully reloaded {} permissions from database", permissions.size());
            })
            .doOnError(error -> {
                logger.error("Failed to reload permissions from database", error);
            })
            .then();
    }
}


