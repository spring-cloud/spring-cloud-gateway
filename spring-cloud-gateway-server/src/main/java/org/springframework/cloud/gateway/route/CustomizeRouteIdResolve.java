package org.springframework.cloud.gateway.route;

import org.springframework.web.server.ServerWebExchange;

/**
 * If you implement the custom route resolution interface,
 * the implementation class will be called before the original SCG route resolution
 *
 * @author: yizhenqiang
 * @date: 2021/4/28 11:34 下午
 */
public interface CustomizeRouteIdResolve {

	/**
	 * The user-defined routing information is parsed through {@link ServerWebExchange}
	 *
	 * @param serverWebExchange
	 * @return Route definition ID resolved according to custom routing rules
	 */
	String resolveRouteId(ServerWebExchange serverWebExchange);
}
