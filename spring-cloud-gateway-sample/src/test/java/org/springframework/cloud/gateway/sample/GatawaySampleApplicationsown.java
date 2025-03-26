/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.gateway.sample;

import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * @author Beeshma
 */

@SpringBootTest(classes = { GatawaySampleApplicationsown.class },
		properties = { "management.endpoint.gateway.enabled=true", "management.server.port=${test.port}" })
@ExtendWith(OutputCaptureExtension.class)
public class GatawaySampleApplicationsown {

	protected static int managementPort;

	public static void beforeClass() {
	}

}
