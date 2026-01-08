package org.mdl.smartcare.gateway.repository;

import org.mdl.smartcare.gateway.model.ApiPermission;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface ApiPermissionRepository extends ReactiveCrudRepository<ApiPermission, Long> {

    /**
     * Find all permissions by permission name
     * 
     * @param permissionName The permission name (e.g., "USER_READ")
     * @return Flux of ApiPermission
     */
    Flux<ApiPermission> findByPermissionName(String permissionName);

    /**
     * Find all permissions ordered by permission name
     * 
     * @return Flux of all ApiPermission
     */
    @Query("SELECT * FROM api_permissions ORDER BY permission_name, http_method")
    Flux<ApiPermission> findAllOrdered();
}


