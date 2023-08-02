/*
 * Copyright 2013-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.gateway.server.mvc.handler;

import java.util.Collections;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

/**
 * A MultipartResolver that does not resolve if the current request is a Gateway request.
 */
public class GatewayMvcMultipartResolver extends StandardServletMultipartResolver {

	@Override
	public boolean isMultipart(HttpServletRequest request) {
		return super.isMultipart(request);
	}

	@Override
	public MultipartHttpServletRequest resolveMultipart(HttpServletRequest request) throws MultipartException {
		return new GatewayMultipartHttpServletRequest(request);
	}

	private static boolean isGatewayRequest(HttpServletRequest request) {
		return request.getAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR) != null
				|| request.getAttribute(MvcUtils.GATEWAY_REQUEST_URL_ATTR) != null;
	}

	/**
	 * StandardMultipartHttpServletRequest wrapper that will not parse multipart if it is
	 * a gateway request. A gateway request has certain request attributes set.
	 */
	static class GatewayMultipartHttpServletRequest extends StandardMultipartHttpServletRequest {

		GatewayMultipartHttpServletRequest(HttpServletRequest request) {
			super(request, true);
		}

		@Override
		protected void initializeMultipart() {
			if (!isGatewayRequest(getRequest())) {
				super.initializeMultipart();
			}
		}

		@Override
		public Map<String, String[]> getParameterMap() {
			if (isGatewayRequest(getRequest())) {
				return Collections.emptyMap();
			}
			return super.getParameterMap();
		}

	}

}
