package org.springframework.cloud.gateway.server.mvc.filter;

import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Contract for interception-style, chained processing of gateway requests that may be
 * used to implement cross-cutting, application-agnostic requirements such as security,
 * timeouts, and others.
 *
 * Only applies to matched gateway routes.
 *
 * Copied from reactive gateway server
 *
 * @author Andre Sustac
 * @since 4.1
 */
public interface GlobalFilter {

	/**
	 * Process the web request and (optionally) delegate to the next
	 * filter
	 * @return HandlerFilterFunction that will be used to register gateway RouterFunctions
	 */
	HandlerFilterFunction<ServerResponse, ServerResponse> filter();

}
