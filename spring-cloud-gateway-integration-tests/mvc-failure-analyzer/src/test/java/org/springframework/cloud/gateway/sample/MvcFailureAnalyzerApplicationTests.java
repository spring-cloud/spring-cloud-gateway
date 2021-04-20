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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.gateway.support.MvcFoundOnClasspathException;
import org.springframework.cloud.gateway.support.MvcFoundOnClasspathFailureAnalyzer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Spencer Gibb
 */
@ExtendWith(OutputCaptureExtension.class)
public class MvcFailureAnalyzerApplicationTests {

	@Test
	public void exceptionThrown(CapturedOutput output) {
		assertThatThrownBy(
				() -> new SpringApplication(MvcFailureAnalyzerApplication.class)
						.run("--server.port=0")).hasRootCauseInstanceOf(
								MvcFoundOnClasspathException.class);
		assertThat(output).contains(MvcFoundOnClasspathFailureAnalyzer.MESSAGE,
				MvcFoundOnClasspathFailureAnalyzer.ACTION);
	}

	@Test
	public void exceptionNotThrownWhenDisabled(CapturedOutput output) {
		assertThatCode(() -> new SpringApplication(MvcFailureAnalyzerApplication.class)
				.run("--spring.cloud.gateway.enabled=false", "--server.port=0"))
						.doesNotThrowAnyException();
		assertThat(output).doesNotContain(MvcFoundOnClasspathFailureAnalyzer.MESSAGE,
				MvcFoundOnClasspathFailureAnalyzer.ACTION);
	}

	@Test
	public void exceptionNotThrownWhenReactiveTypeSet(CapturedOutput output) {
		assertThatCode(() -> {
			ConfigurableApplicationContext context = new SpringApplication(
					MvcFailureAnalyzerApplication.class).run(
							"--spring.main.web-application-type=reactive",
							"--server.port=0", "--debug=true");
			Integer port = context.getEnvironment().getProperty("local.server.port",
					Integer.class);
			WebTestClient client = WebTestClient.bindToServer()
					.baseUrl("http://localhost:" + port).build();
			client.get().uri("/myprefix/hello").exchange().expectStatus().isOk()
					.expectBody(String.class).isEqualTo("Hello");
			context.close();
		}).doesNotThrowAnyException();
		assertThat(output).doesNotContain(MvcFoundOnClasspathFailureAnalyzer.MESSAGE,
				MvcFoundOnClasspathFailureAnalyzer.ACTION);

	}

}
