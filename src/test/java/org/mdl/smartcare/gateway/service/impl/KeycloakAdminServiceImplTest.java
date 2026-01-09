package org.mdl.smartcare.gateway.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.mdl.smartcare.gateway.config.KeycloakConfig;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KeycloakAdminServiceImplTest {

  @Mock private KeycloakConfig keycloakConfig;

  @InjectMocks private KeycloakAdminServiceImpl keycloakAdminService;

  @Mock private RolesResource rolesResource;

  @Mock private RoleResource roleResource;

  @BeforeEach
  void setUp() {
    lenient().when(keycloakConfig.getServerUrl()).thenReturn("https://keycloak.test.org");
    lenient().when(keycloakConfig.getRealm()).thenReturn("master");
    lenient().when(keycloakConfig.getTargetRealm()).thenReturn("test-realm");
    lenient().when(keycloakConfig.getClientId()).thenReturn("admin-cli");
    lenient().when(keycloakConfig.getUsername()).thenReturn("admin");
    lenient().when(keycloakConfig.getPassword()).thenReturn("password");
  }

  @Test
  void testCreateOrUpdateRealmRole_WhenRoleExists_ShouldUpdateDescription() {
    // Arrange
    String roleName = "USER_READ";
    String description = "Updated description";
    RoleRepresentation existingRole = new RoleRepresentation();
    existingRole.setName(roleName);
    existingRole.setDescription("Old description");

    when(rolesResource.get(roleName)).thenReturn(roleResource);
    when(roleResource.toRepresentation()).thenReturn(existingRole);
    doNothing().when(roleResource).update(any(RoleRepresentation.class));

    // Act
    boolean result =
        keycloakAdminService.createOrUpdateRealmRole(roleName, description, rolesResource);

    // Assert
    assertTrue(result);
    verify(roleResource).update(any(RoleRepresentation.class));
  }

  @Test
  void testCreateOrUpdateRealmRole_WhenRoleExistsWithSameDescription_ShouldNotUpdate() {
    // Arrange
    String roleName = "USER_READ";
    String description = "Same description";
    RoleRepresentation existingRole = new RoleRepresentation();
    existingRole.setName(roleName);
    existingRole.setDescription(description);

    when(rolesResource.get(roleName)).thenReturn(roleResource);
    when(roleResource.toRepresentation()).thenReturn(existingRole);

    // Act
    boolean result =
        keycloakAdminService.createOrUpdateRealmRole(roleName, description, rolesResource);

    // Assert
    assertTrue(result);
    verify(roleResource, never()).update(any(RoleRepresentation.class));
  }

  @Test
  void testCreateOrUpdateRealmRole_WhenRoleNotExists_ShouldCreate() {
    // Arrange
    String roleName = "NEW_ROLE";
    String description = "New role description";

    when(rolesResource.get(roleName)).thenReturn(roleResource);
    when(roleResource.toRepresentation()).thenThrow(new NotFoundException("Role not found"));
    doNothing().when(rolesResource).create(any(RoleRepresentation.class));

    // Act
    boolean result =
        keycloakAdminService.createOrUpdateRealmRole(roleName, description, rolesResource);

    // Assert
    assertTrue(result);
    verify(rolesResource).create(any(RoleRepresentation.class));
  }

  @Test
  void testCreateOrUpdateRealmRole_WhenCreateFails_ShouldReturnFalse() {
    // Arrange
    String roleName = "NEW_ROLE";
    String description = "New role description";

    when(rolesResource.get(roleName)).thenReturn(roleResource);
    when(roleResource.toRepresentation()).thenThrow(new NotFoundException("Role not found"));
    doThrow(new RuntimeException("Create failed"))
        .when(rolesResource)
        .create(any(RoleRepresentation.class));

    // Act
    boolean result =
        keycloakAdminService.createOrUpdateRealmRole(roleName, description, rolesResource);

    // Assert
    assertFalse(result);
    verify(rolesResource).create(any(RoleRepresentation.class));
  }
}
