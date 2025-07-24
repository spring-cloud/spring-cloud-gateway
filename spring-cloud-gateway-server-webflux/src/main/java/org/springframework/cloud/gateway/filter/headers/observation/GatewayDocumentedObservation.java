/*
 * Copyright 2013-2022 the original author or authors.
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

package org.springframework.cloud.gateway.filter.headers.observation;

import io.micrometer.common.docs.KeyName;
import io.micrometer.common.lang.NonNullApi;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

enum GatewayDocumentedObservation implements ObservationDocumentation {

	/**
	 * Observation created when sending a request through the gateway.
	 */
	GATEWAY_HTTP_CLIENT_OBSERVATION {
		@Override
		public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
			return DefaultGatewayObservationConvention.class;
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return LowCardinalityKeys.values();
		}

		@Override
		public KeyName[] getHighCardinalityKeyNames() {
			return HighCardinalityKeys.values();
		}

	};

	@NonNullApi
	enum LowCardinalityKeys implements KeyName {

		/**
		 * Route ID.
		 */
		ROUTE_ID {
			@Override
			public String asString() {
				return "spring.cloud.gateway.route.id";
			}
		},

		/**
		 * HTTP Method.
		 */
		METHOD {
			@Override
			public String asString() {
				return "http.method";
			}
		},

		/**
		 * HTTP Status.
		 */
		STATUS {
			@Override
			public String asString() {
				return "http.status_code";
			}
		},

		/**
		 * HTTP URI taken from the Route.
		 */
		ROUTE_URI {
			@Override
			public String asString() {
				return "spring.cloud.gateway.route.uri";
			}
		}

	}

	@NonNullApi
	enum HighCardinalityKeys implements KeyName {

		/**
		 * Full HTTP URI.
		 */
		URI {
			@Override
			public String asString() {
				return "http.uri";
			}
		}

	}

}
