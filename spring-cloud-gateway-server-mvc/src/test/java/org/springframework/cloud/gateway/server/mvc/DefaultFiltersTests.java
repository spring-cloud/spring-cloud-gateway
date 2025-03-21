package org.springframework.cloud.gateway.server.mvc;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.config.FilterProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.servlet.function.RouterFunctions.route;

@SpringBootTest
@ActiveProfiles("test")
public class DefaultFiltersTests {

    @Autowired
    private GatewayMvcProperties properties;

    @Test
    public void testDefaultFilters() {
        List<FilterProperties> defaultFilters = properties.getDefaultFilters();
        assertThat(defaultFilters).isNotEmpty();
        assertThat(defaultFilters).hasSize(1);
        assertThat(defaultFilters.get(0).getName()).isEqualTo("AddRequestHeader");
        assertThat(defaultFilters.get(0).getArgs().get("name")).isEqualTo("X-Request-Default");
        assertThat(defaultFilters.get(0).getArgs().get("value")).isEqualTo("Default");
    }

    @Configuration
    static class TestConfig {

        @Bean
        public GatewayMvcProperties gatewayMvcProperties() {
            GatewayMvcProperties properties = new GatewayMvcProperties();
            FilterProperties filter = new FilterProperties();
            filter.setName("AddRequestHeader");
            filter.addArg("name", "X-Request-Default");
            filter.addArg("value", "Default");
            properties.setDefaultFilters(List.of(filter));
            return properties;
        }

        @Bean
        public RouterFunction<ServerResponse> testRoute() {
            return route()
                    .GET("/test", request -> ServerResponse.ok().build())
                    .build();
        }
    }
}
