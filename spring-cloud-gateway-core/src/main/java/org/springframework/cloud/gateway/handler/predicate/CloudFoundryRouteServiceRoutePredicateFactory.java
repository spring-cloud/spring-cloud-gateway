package org.springframework.cloud.gateway.handler.predicate;

import java.util.function.Predicate;

import org.springframework.web.server.ServerWebExchange;

/**
 * Creates a predicate which indicates if the request is intended for a Cloud Foundry Route Service.
 * @see <a href="https://docs.cloudfoundry.org/services/route-services.html">Cloud Foundry Route Service documentation</a>.
 * @author Andrew Fitzgerald
 */
public class CloudFoundryRouteServiceRoutePredicateFactory extends
		AbstractRoutePredicateFactory<Object> {

	public static final String X_CF_FORWARDED_URL = "X-CF-Forwarded-Url";
	public static final String X_CF_PROXY_SIGNATURE = "X-CF-Proxy-Signature";
	public static final String X_CF_PROXY_METADATA = "X-CF-Proxy-Metadata";
	private final HeaderRoutePredicateFactory factory = new HeaderRoutePredicateFactory();

	public CloudFoundryRouteServiceRoutePredicateFactory() {
		super(Object.class);
	}

	@Override
	public Predicate<ServerWebExchange> apply(
			Object unused) {
		return headerPredicate(X_CF_FORWARDED_URL)
				.and(headerPredicate(X_CF_PROXY_SIGNATURE))
				.and(headerPredicate(X_CF_PROXY_METADATA));
	}

	private Predicate<ServerWebExchange> headerPredicate(String header) {
		HeaderRoutePredicateFactory.Config config = factory.newConfig();
		config.setHeader(header);
		config.setRegexp(".*");
		return factory.apply(config);
	}
}
