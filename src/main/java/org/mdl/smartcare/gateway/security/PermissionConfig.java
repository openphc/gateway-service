package org.mdl.smartcare.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "authorization")
public class PermissionConfig {

    private boolean enabled = true;
    private Map<String, List<PermissionRule>> permissionMappings = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, List<PermissionRule>> getPermissionMappings() {
        return permissionMappings;
    }

    public void setPermissionMappings(Map<String, List<PermissionRule>> permissionMappings) {
        this.permissionMappings = permissionMappings;
    }

    public static class PermissionRule {
        private String method;
        private String uri;

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }
    }
}

