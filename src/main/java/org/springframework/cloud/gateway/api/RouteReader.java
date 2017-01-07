package org.springframework.cloud.gateway.api;

import org.springframework.cloud.gateway.config.Route;

import java.util.List;

/**
 * @author Spencer Gibb
 */
public interface RouteReader {

	List<Route> getRoutes();
}
