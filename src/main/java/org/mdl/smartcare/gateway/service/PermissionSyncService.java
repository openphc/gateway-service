package org.mdl.smartcare.gateway.service;

import java.util.List;
import reactor.core.publisher.Mono;

/** Service interface for permission synchronization operations */
public interface PermissionSyncService {

  /**
   * Sync all unique permissions from database to Keycloak as realm roles AND reload them into the
   * gateway's PermissionConfig
   *
   * @return Mono of SyncResponse with results
   */
  Mono<SyncResponse> syncPermissionsToKeycloak();

  /**
   * Test Keycloak connection
   *
   * @return Mono of connection status
   */
  Mono<ConnectionStatus> testKeycloakConnection();

  /**
   * Get all realm roles from Keycloak
   *
   * @return Mono of list of role names
   */
  Mono<List<String>> getKeycloakRoles();

  /** Response for sync operation */
  class SyncResponse {
    private int successCount;
    private int failureCount;
    private int deletedCount;
    private List<String> failedRoles;
    private String message;

    public SyncResponse(
        int successCount,
        int failureCount,
        int deletedCount,
        List<String> failedRoles,
        String message) {
      this.successCount = successCount;
      this.failureCount = failureCount;
      this.deletedCount = deletedCount;
      this.failedRoles = failedRoles;
      this.message = message;
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

    public String getMessage() {
      return message;
    }
  }

  /** Connection status */
  class ConnectionStatus {
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
