package org.springframework.cloud.gateway.support.ipresolver;

import java.net.InetSocketAddress;

import org.springframework.web.server.ServerWebExchange;

/**
 * @author Andrew Fitzgerald
 */
public interface RemoteAddressResolver {

	InetSocketAddress resolve(ServerWebExchange exchange);
}
