package org.mdl.smartcare.gateway.service;

import java.util.List;
import java.util.Map;
import org.mdl.smartcare.gateway.security.PermissionConfig;
import reactor.core.publisher.Mono;

/** Service interface for loading permissions from database */
public interface DatabasePermissionLoaderService {

  /**
   * Load permissions from database on application startup
   *
   * @return Mono that completes when permissions are loaded
   */
  Mono<Void> loadPermissionsFromDatabase();

  /**
   * Reload permissions from database (can be called manually or via scheduled task)
   *
   * @return Mono<Void> for reactive completion
   */
  Mono<Void> reloadPermissions();

  /**
   * Convert database ApiPermission entities to PermissionConfig format
   *
   * @param apiPermissions List of ApiPermission from database
   * @return Map of permission name to list of PermissionRule
   */
  Map<String, List<PermissionConfig.PermissionRule>> convertToPermissionMappings(
      java.util.List<org.mdl.smartcare.gateway.model.ApiPermission> apiPermissions);
}
