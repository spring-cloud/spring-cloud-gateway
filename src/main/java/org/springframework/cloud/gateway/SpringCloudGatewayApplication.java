package org.springframework.cloud.gateway;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

//TODO: move to autoconfig
@Import(GatewayConfiguration.class)
@SpringBootConfiguration
@EnableAutoConfiguration
public class SpringCloudGatewayApplication {

	private static final Log log = LogFactory.getLog(SpringCloudGatewayApplication.class);

	// TODO: only apply filters to zuul?
	@Bean
	@Order(501)
	public WebFilter modifyResponseFilter() {
		return (exchange, chain) -> {
			log.info("modifyResponseFilter start");
			exchange.getResponse().getHeaders().add("X-My-Custom", "MyCustomValue");
			return chain.filter(exchange);
		};
	}

	@Bean
	@Order(502)
	public WebFilter postFilter() {
		return (exchange, chain) -> {
			log.info("postFilter start");
			return chain.filter(exchange).then(postFilterWork(exchange));
		};
	}

	private static Mono<Void> postFilterWork(ServerWebExchange exchange) {
		log.info("postFilterWork");
		exchange.getResponse().getHeaders().add("X-Post-Header", "AddedAfterRoute");
		return Mono.empty();
	}

	public static void main(String[] args) {
		new SpringApplicationBuilder()
				.sources(SpringCloudGatewayApplication.class)
				//TODO: howto do programatically
				.run(args);
	}
}
