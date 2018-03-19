package org.springframework.cloud.gateway.support.ipresolver;

import java.net.InetSocketAddress;

import org.springframework.web.server.ServerWebExchange;

/**
 * @author Andrew Fitzgerald
 */
public interface RemoteAddressResolver {

	default InetSocketAddress resolve(ServerWebExchange exchange) {
		return exchange.getRequest().getRemoteAddress();
	}
}
