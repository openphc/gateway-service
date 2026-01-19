package org.mdl.smartcare.gateway.service;

import java.util.List;

/** Service interface for Keycloak Admin operations */
public interface KeycloakAdminService {

  /**
   * Create or update a realm role in Keycloak (idempotent)
   *
   * @param roleName Role name (permission name)
   * @param description Role description
   * @param rolesResource Keycloak roles resource
   * @return true if created/updated, false on error
   */
  boolean createOrUpdateRealmRole(
      String roleName,
      String description,
      org.keycloak.admin.client.resource.RolesResource rolesResource);

  /**
   * Sync multiple roles to Keycloak (create, update, and delete)
   *
   * @param roles List of role names and descriptions from database
   * @return SyncResult with success/failure counts
   */
  SyncResult syncRolesToKeycloak(List<RoleInfo> roles);

  /**
   * Get all realm roles from Keycloak
   *
   * @return List of role names
   */
  List<String> getAllRealmRoles();

  /**
   * Check if Keycloak connection is working
   *
   * @return true if connected, false otherwise
   */
  boolean testConnection();

  /** Role information for syncing */
  class RoleInfo {
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

  /** Result of sync operation */
  class SyncResult {
    private int successCount;
    private int failureCount;
    private int deletedCount;
    private List<String> failedRoles;

    public SyncResult(
        int successCount, int failureCount, int deletedCount, List<String> failedRoles) {
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
