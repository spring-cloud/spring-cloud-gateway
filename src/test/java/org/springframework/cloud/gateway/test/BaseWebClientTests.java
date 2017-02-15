package org.springframework.cloud.gateway.test;

import java.time.Duration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.cloud.gateway.api.Route;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.reactive.function.client.WebClient;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_HANDLER_MAPPER_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * @author Spencer Gibb
 */
public class BaseWebClientTests {

	protected static final String HANDLER_MAPPER_HEADER = "X-Gateway-Handler-Mapper-Class";
	protected static final String ROUTE_ID_HEADER = "X-Gateway-Route-Id";
	protected static final Duration DURATION = Duration.ofSeconds(5);

	static {
		//TODO: wait for option in boot 2.0
		System.setProperty("java.net.preferIPv4Stack", "true");
	}

	@LocalServerPort
	protected int port = 0;

	protected WebClient webClient;
	protected String baseUri;

	@Before
	public void setup() {
		//TODO: how to set new ReactorClientHttpConnector()
		baseUri = "http://localhost:" + port;
		this.webClient = WebClient.create(baseUri);
	}

	@Configuration
	protected static class DefaultTestConfig {

		private static final Log log = LogFactory.getLog(DefaultTestConfig.class);

		@Bean
		@Order(500)
		public GlobalFilter modifyResponseFilter() {
			return (exchange, chain) -> {
				log.info("modifyResponseFilter start");
				String value = (String) exchange.getAttribute(GATEWAY_HANDLER_MAPPER_ATTR).orElse("N/A");
				exchange.getResponse().getHeaders().add(HANDLER_MAPPER_HEADER, value);
				Route route = (Route) exchange.getAttribute(GATEWAY_ROUTE_ATTR).orElse(null);
				if (route != null) {
					exchange.getResponse().getHeaders().add(ROUTE_ID_HEADER, route.getId());
				}
				return chain.filter(exchange);
			};
		}
	}

}
