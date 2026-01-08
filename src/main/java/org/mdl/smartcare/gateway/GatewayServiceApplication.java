package org.mdl.smartcare.gateway;

import org.mdl.smartcare.gateway.config.KeycloakConfig;
import org.mdl.smartcare.gateway.security.PermissionConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@SpringBootApplication
@EnableConfigurationProperties({PermissionConfig.class, KeycloakConfig.class})
@EnableR2dbcRepositories
public class GatewayServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayServiceApplication.class, args);
	}

}
