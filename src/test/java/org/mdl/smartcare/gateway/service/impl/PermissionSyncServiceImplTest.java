package org.mdl.smartcare.gateway.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mdl.smartcare.gateway.model.ApiPermission;
import org.mdl.smartcare.gateway.repository.ApiPermissionRepository;
import org.mdl.smartcare.gateway.security.PermissionConfig;
import org.mdl.smartcare.gateway.service.DatabasePermissionLoaderService;
import org.mdl.smartcare.gateway.service.KeycloakAdminService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PermissionSyncServiceImplTest {

  @Mock private ApiPermissionRepository permissionRepository;

  @Mock private KeycloakAdminService keycloakAdminService;

  @Mock private PermissionConfig permissionConfig;

  @Mock private DatabasePermissionLoaderService databasePermissionLoaderService;

  @InjectMocks private PermissionSyncServiceImpl permissionSyncService;

  private List<ApiPermission> mockPermissions;

  @BeforeEach
  void setUp() {
    mockPermissions = new ArrayList<>();
    ApiPermission perm1 = new ApiPermission();
    perm1.setPermissionName("USER_READ");
    perm1.setHttpMethod("GET");
    perm1.setUriPattern("/admin/users/*");

    ApiPermission perm2 = new ApiPermission();
    perm2.setPermissionName("USER_READ");
    perm2.setHttpMethod("GET");
    perm2.setUriPattern("/admin/users");

    ApiPermission perm3 = new ApiPermission();
    perm3.setPermissionName("USER_WRITE");
    perm3.setHttpMethod("POST");
    perm3.setUriPattern("/admin/users");

    mockPermissions = Arrays.asList(perm1, perm2, perm3);
  }

  @Test
  void testSyncPermissionsToKeycloak_WhenPermissionsExist_ShouldSyncSuccessfully() {
    // Arrange
    when(permissionRepository.findAllOrdered()).thenReturn(Flux.fromIterable(mockPermissions));

    PermissionConfig.PermissionRule rule1 = new PermissionConfig.PermissionRule();
    rule1.setMethod("GET");
    rule1.setUri("/admin/users/*");
    PermissionConfig.PermissionRule rule2 = new PermissionConfig.PermissionRule();
    rule2.setMethod("GET");
    rule2.setUri("/admin/users");

    List<PermissionConfig.PermissionRule> rules = Arrays.asList(rule1, rule2);
    java.util.Map<String, List<PermissionConfig.PermissionRule>> mappings =
        new java.util.HashMap<>();
    mappings.put("USER_READ", rules);

    when(databasePermissionLoaderService.convertToPermissionMappings(anyList()))
        .thenReturn(mappings);

    KeycloakAdminService.SyncResult syncResult =
        new KeycloakAdminService.SyncResult(2, 0, 0, new ArrayList<>());
    when(keycloakAdminService.syncRolesToKeycloak(anyList())).thenReturn(syncResult);

    // Act & Assert
    StepVerifier.create(permissionSyncService.syncPermissionsToKeycloak())
        .assertNext(
            response -> {
              assertNotNull(response);
              assertEquals(2, response.getSuccessCount());
              assertEquals(0, response.getFailureCount());
              assertNotNull(response.getMessage());
            })
        .verifyComplete();

    verify(permissionConfig).setPermissionMappings(any());
    verify(keycloakAdminService).syncRolesToKeycloak(anyList());
  }

  @Test
  void testSyncPermissionsToKeycloak_WhenNoPermissions_ShouldReturnEmptyResult() {
    // Arrange
    when(permissionRepository.findAllOrdered()).thenReturn(Flux.empty());

    // Act & Assert
    StepVerifier.create(permissionSyncService.syncPermissionsToKeycloak())
        .assertNext(
            response -> {
              assertNotNull(response);
              assertEquals(0, response.getSuccessCount());
              assertEquals(0, response.getFailureCount());
              assertTrue(response.getMessage().contains("No permissions found"));
            })
        .verifyComplete();

    verify(permissionConfig, never()).setPermissionMappings(any());
    verify(keycloakAdminService, never()).syncRolesToKeycloak(anyList());
  }

  @Test
  void testSyncPermissionsToKeycloak_WhenKeycloakSyncFails_ShouldHandleError() {
    // Arrange
    when(permissionRepository.findAllOrdered()).thenReturn(Flux.fromIterable(mockPermissions));

    java.util.Map<String, List<PermissionConfig.PermissionRule>> mappings =
        new java.util.HashMap<>();
    when(databasePermissionLoaderService.convertToPermissionMappings(anyList()))
        .thenReturn(mappings);

    when(keycloakAdminService.syncRolesToKeycloak(anyList()))
        .thenThrow(new RuntimeException("Keycloak sync failed"));

    // Act & Assert
    StepVerifier.create(permissionSyncService.syncPermissionsToKeycloak())
        .expectError(RuntimeException.class)
        .verify();
  }

  @Test
  void testTestKeycloakConnection_WhenConnected_ShouldReturnTrue() {
    // Arrange
    when(keycloakAdminService.testConnection()).thenReturn(true);

    // Act & Assert
    StepVerifier.create(permissionSyncService.testKeycloakConnection())
        .assertNext(
            status -> {
              assertNotNull(status);
              assertTrue(status.isConnected());
              assertTrue(status.getMessage().contains("Connected"));
            })
        .verifyComplete();
  }

  @Test
  void testTestKeycloakConnection_WhenNotConnected_ShouldReturnFalse() {
    // Arrange
    when(keycloakAdminService.testConnection()).thenReturn(false);

    // Act & Assert
    StepVerifier.create(permissionSyncService.testKeycloakConnection())
        .assertNext(
            status -> {
              assertNotNull(status);
              assertFalse(status.isConnected());
              assertTrue(status.getMessage().contains("Failed"));
            })
        .verifyComplete();
  }

  @Test
  void testGetKeycloakRoles_ShouldReturnListOfRoles() {
    // Arrange
    List<String> roles = Arrays.asList("USER_READ", "USER_WRITE", "ROLE_READ");
    when(keycloakAdminService.getAllRealmRoles()).thenReturn(roles);

    // Act & Assert
    StepVerifier.create(permissionSyncService.getKeycloakRoles())
        .assertNext(
            result -> {
              assertNotNull(result);
              assertEquals(3, result.size());
              assertTrue(result.contains("USER_READ"));
              assertTrue(result.contains("USER_WRITE"));
              assertTrue(result.contains("ROLE_READ"));
            })
        .verifyComplete();
  }

  @Test
  void testGetKeycloakRoles_WhenEmpty_ShouldReturnEmptyList() {
    // Arrange
    when(keycloakAdminService.getAllRealmRoles()).thenReturn(new ArrayList<>());

    // Act & Assert
    StepVerifier.create(permissionSyncService.getKeycloakRoles())
        .assertNext(result -> assertTrue(result.isEmpty()))
        .verifyComplete();
  }
}
