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

package org.springframework.cloud.gateway.server.mvc.test;

import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.ServiceInstanceListSuppliers;
import org.springframework.context.annotation.Bean;

public class TestLoadBalancerConfig {

	public static class Httpbin {

		@Bean
		public ServiceInstanceListSupplier staticServiceInstanceListSupplier() {
			return ServiceInstanceListSuppliers.from("httpbin", new DefaultServiceInstance("httpbin" + "-1", "httpbin",
					HttpbinTestcontainers.getHost(), HttpbinTestcontainers.getPort(), false));
		}

	}

	public static class Local {

		@LocalServerPort
		protected int port = 0;

		@Bean
		public ServiceInstanceListSupplier staticServiceInstanceListSupplier() {
			return ServiceInstanceListSuppliers.from("testservice",
					new DefaultServiceInstance("testservice" + "-1", "testservice", "localhost", port, false));
		}

	}

}
