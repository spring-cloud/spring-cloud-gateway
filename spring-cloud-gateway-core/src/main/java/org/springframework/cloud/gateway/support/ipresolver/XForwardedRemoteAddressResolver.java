package org.springframework.cloud.gateway.support.ipresolver;

import java.net.InetSocketAddress;
import java.util.List;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

/**
 * Parses the client address from the X-Forwarded-For header.
 * If header is not present, falls back to {@link DefaultRemoteAddressResolver} and {@link ServerHttpRequest#getRemoteAddress()}.
 * Note that this implementation is potentially vulnerable to spoofing,
 * as an untrusted client could manually set an initial value of the X-Forwarded-For header.
 *
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-For">X-Forwarded-For reference</a>
 * @author Andrew Fitzgerald
 */
public class XForwardedRemoteAddressResolver implements RemoteAddressResolver {

	public static final String X_FORWARDED_FOR = "X-Forwarded-For";

	private final DefaultRemoteAddressResolver defaultRemoteIpResolver = new DefaultRemoteAddressResolver();

	@Override
	public InetSocketAddress resolve(ServerWebExchange exchange) {
		List<String> xForwardedValues = exchange.getRequest().getHeaders()
				.get(X_FORWARDED_FOR);
		if (xForwardedValues != null && xForwardedValues.size() != 0) {
			String remoteAddress = xForwardedValues.get(0).split(", ")[0];
			return InetSocketAddress.createUnresolved(remoteAddress, 0);
		}
		return defaultRemoteIpResolver.resolve(exchange);
	}
}
