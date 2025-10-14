package org.mdl.smartcare.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Map;

@Service
public class AuthorizationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationService.class);

    @Autowired
    private PermissionConfig permissionConfig;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * Check if the user has permission to access the requested resource
     * 
     * @param permissions List of permission strings from JWT (e.g., ["USER_READ", "USER_WRITE"])
     * @param method HTTP method (GET, POST, PUT, DELETE, etc.)
     * @param uri Request URI path
     * @return true if authorized, false otherwise
     */
    public boolean isAuthorized(List<String> permissions, String method, String uri) {
        // Check if authorization is enabled
        if (!permissionConfig.isEnabled()) {
            logger.info("Authorization is disabled - allowing request {} {}", method, uri);
            return true;
        }

        if (permissions == null || permissions.isEmpty()) {
            logger.warn("Authorization failed: No permissions found for request {} {}", method, uri);
            return false;
        }

        Map<String, List<PermissionConfig.PermissionRule>> permissionMappings = 
            permissionConfig.getPermissionMappings();

        if (permissionMappings == null || permissionMappings.isEmpty()) {
            logger.warn("Authorization failed: No permission mappings configured");
            return false;
        }

        // Check each permission the user has
        for (String permission : permissions) {
            List<PermissionConfig.PermissionRule> rules = permissionMappings.get(permission);
            
            if (rules != null) {
                // Check each rule for this permission
                for (PermissionConfig.PermissionRule rule : rules) {
                    if (matchesRule(method, uri, rule)) {
                        logger.info("Authorization granted: Permission '{}' allows {} {}", 
                                  permission, method, uri);
                        return true;
                    }
                }
            }
        }

        logger.warn("Authorization denied: No matching permission found for {} {} with permissions {}", 
                   method, uri, permissions);
        return false;
    }

    /**
     * Check if the request matches a permission rule
     * 
     * @param method HTTP method
     * @param uri Request URI
     * @param rule Permission rule to check against
     * @return true if matches, false otherwise
     */
    private boolean matchesRule(String method, String uri, PermissionConfig.PermissionRule rule) {
        // Check HTTP method match
        if (!method.equalsIgnoreCase(rule.getMethod())) {
            return false;
        }

        // Check URI pattern match using AntPathMatcher
        // This supports patterns like:
        // - /admin/users/* matches /admin/users/123 but not /admin/users/123/profile
        // - /admin/users/** matches /admin/users/123 and /admin/users/123/profile
        // - /admin/users/123 exact match
        return pathMatcher.match(rule.getUri(), uri);
    }
}

