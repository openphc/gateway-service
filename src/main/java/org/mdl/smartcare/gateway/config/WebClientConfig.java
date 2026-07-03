package org.mdl.smartcare.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/** Provides a shared reactive {@link WebClient} for outbound calls (e.g. Keycloak token exchange). */
@Configuration
public class WebClientConfig {

  @Bean
  public WebClient webClient(WebClient.Builder builder) {
    return builder.build();
  }
}
