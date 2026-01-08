package org.mdl.smartcare.gateway.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("api_permissions")
public class ApiPermission {

    @Id
    private Long id;

    @Column("permission_name")
    private String permissionName;

    @Column("http_method")
    private String httpMethod;

    @Column("uri_pattern")
    private String uriPattern;

    @Column("description")
    private String description;

    @Column("resource_category")
    private String resourceCategory;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    // Constructors
    public ApiPermission() {
    }

    public ApiPermission(String permissionName, String httpMethod, String uriPattern) {
        this.permissionName = permissionName;
        this.httpMethod = httpMethod;
        this.uriPattern = uriPattern;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPermissionName() {
        return permissionName;
    }

    public void setPermissionName(String permissionName) {
        this.permissionName = permissionName;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getUriPattern() {
        return uriPattern;
    }

    public void setUriPattern(String uriPattern) {
        this.uriPattern = uriPattern;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getResourceCategory() {
        return resourceCategory;
    }

    public void setResourceCategory(String resourceCategory) {
        this.resourceCategory = resourceCategory;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "ApiPermission{" +
                "id=" + id +
                ", permissionName='" + permissionName + '\'' +
                ", httpMethod='" + httpMethod + '\'' +
                ", uriPattern='" + uriPattern + '\'' +
                ", resourceCategory='" + resourceCategory + '\'' +
                '}';
    }
}


