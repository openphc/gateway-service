package org.mdl.smartcare.gateway.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mdl.smartcare.gateway.model.ApiPermission;
import org.mdl.smartcare.gateway.repository.ApiPermissionRepository;
import org.mdl.smartcare.gateway.security.PermissionConfig;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DatabasePermissionLoaderServiceImplTest {

  @Mock private ApiPermissionRepository permissionRepository;

  @Mock private PermissionConfig permissionConfig;

  @InjectMocks private DatabasePermissionLoaderServiceImpl databasePermissionLoaderService;

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

    ApiPermission perm4 = new ApiPermission();
    perm4.setPermissionName("USER_DELETE");
    perm4.setHttpMethod("DELETE");
    perm4.setUriPattern("/admin/users/*");

    mockPermissions = Arrays.asList(perm1, perm2, perm3, perm4);
  }

  @Test
  void testLoadPermissionsFromDatabase_WhenPermissionsExist_ShouldLoadSuccessfully() {
    // Arrange
    when(permissionRepository.findAllOrdered()).thenReturn(Flux.fromIterable(mockPermissions));

    // Act & Assert
    StepVerifier.create(databasePermissionLoaderService.loadPermissionsFromDatabase())
        .verifyComplete();

    verify(permissionConfig).setPermissionMappings(any());
  }

  @Test
  void testLoadPermissionsFromDatabase_WhenNoPermissions_ShouldNotUpdateConfig() {
    // Arrange
    when(permissionRepository.findAllOrdered()).thenReturn(Flux.empty());

    // Act & Assert
    StepVerifier.create(databasePermissionLoaderService.loadPermissionsFromDatabase())
        .verifyComplete();

    verify(permissionConfig, never()).setPermissionMappings(any());
  }

  @Test
  void testLoadPermissionsFromDatabase_WhenError_ShouldHandleGracefully() {
    // Arrange
    when(permissionRepository.findAllOrdered())
        .thenReturn(Flux.error(new RuntimeException("Database error")));

    // Act & Assert
    StepVerifier.create(databasePermissionLoaderService.loadPermissionsFromDatabase())
        .verifyComplete();

    // Should not throw exception, just log error
    verify(permissionConfig, never()).setPermissionMappings(any());
  }

  @Test
  void testReloadPermissions_WhenPermissionsExist_ShouldReloadSuccessfully() {
    // Arrange
    when(permissionRepository.findAllOrdered()).thenReturn(Flux.fromIterable(mockPermissions));

    // Act & Assert
    StepVerifier.create(databasePermissionLoaderService.reloadPermissions()).verifyComplete();

    verify(permissionConfig).setPermissionMappings(any());
  }

  @Test
  void testReloadPermissions_WhenNoPermissions_ShouldHandleGracefully() {
    // Arrange
    when(permissionRepository.findAllOrdered()).thenReturn(Flux.empty());

    // Act & Assert
    StepVerifier.create(databasePermissionLoaderService.reloadPermissions()).verifyComplete();

    verify(permissionConfig, never()).setPermissionMappings(any());
  }

  @Test
  void testReloadPermissions_WhenError_ShouldHandleGracefully() {
    // Arrange
    when(permissionRepository.findAllOrdered())
        .thenReturn(Flux.error(new RuntimeException("Database error")));

    // Act & Assert
    StepVerifier.create(databasePermissionLoaderService.reloadPermissions())
        .expectComplete()
        .verify();

    // Should not throw exception, just log error
    verify(permissionConfig, never()).setPermissionMappings(any());
  }

  @Test
  void testConvertToPermissionMappings_ShouldGroupByPermissionName() {
    // Act
    Map<String, List<PermissionConfig.PermissionRule>> result =
        databasePermissionLoaderService.convertToPermissionMappings(mockPermissions);

    // Assert
    assertNotNull(result);
    assertEquals(3, result.size()); // USER_READ, USER_WRITE, USER_DELETE

    // Check USER_READ has 2 rules
    assertTrue(result.containsKey("USER_READ"));
    List<PermissionConfig.PermissionRule> userReadRules = result.get("USER_READ");
    assertEquals(2, userReadRules.size());

    // Check USER_WRITE has 1 rule
    assertTrue(result.containsKey("USER_WRITE"));
    List<PermissionConfig.PermissionRule> userWriteRules = result.get("USER_WRITE");
    assertEquals(1, userWriteRules.size());
    assertEquals("POST", userWriteRules.get(0).getMethod());
    assertEquals("/admin/users", userWriteRules.get(0).getUri());

    // Check USER_DELETE has 1 rule
    assertTrue(result.containsKey("USER_DELETE"));
    List<PermissionConfig.PermissionRule> userDeleteRules = result.get("USER_DELETE");
    assertEquals(1, userDeleteRules.size());
    assertEquals("DELETE", userDeleteRules.get(0).getMethod());
    assertEquals("/admin/users/*", userDeleteRules.get(0).getUri());
  }

  @Test
  void testConvertToPermissionMappings_WhenEmptyList_ShouldReturnEmptyMap() {
    // Act
    Map<String, List<PermissionConfig.PermissionRule>> result =
        databasePermissionLoaderService.convertToPermissionMappings(new ArrayList<>());

    // Assert
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  void testConvertToPermissionMappings_ShouldPreserveMethodAndUri() {
    // Act
    Map<String, List<PermissionConfig.PermissionRule>> result =
        databasePermissionLoaderService.convertToPermissionMappings(mockPermissions);

    // Assert
    List<PermissionConfig.PermissionRule> userReadRules = result.get("USER_READ");
    assertNotNull(userReadRules);

    // Verify first rule
    PermissionConfig.PermissionRule rule1 = userReadRules.get(0);
    assertEquals("GET", rule1.getMethod());
    assertEquals("/admin/users/*", rule1.getUri());

    // Verify second rule
    PermissionConfig.PermissionRule rule2 = userReadRules.get(1);
    assertEquals("GET", rule2.getMethod());
    assertEquals("/admin/users", rule2.getUri());
  }
}
