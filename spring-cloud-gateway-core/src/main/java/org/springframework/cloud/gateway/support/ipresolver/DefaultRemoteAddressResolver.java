package org.springframework.cloud.gateway.support.ipresolver;

import java.net.InetSocketAddress;

import org.springframework.web.server.ServerWebExchange;

/**
 * @author Andrew Fitzgerald
 */
public class DefaultRemoteAddressResolver implements RemoteAddressResolver {

	@Override
	public InetSocketAddress resolve(ServerWebExchange exchange) {
		return exchange.getRequest().getRemoteAddress();
	}
}
