/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.gateway.tests.grpc;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import io.grpc.Grpc;
import io.grpc.Server;
import io.grpc.ServerCredentials;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.TlsServerCredentials;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * @author Alberto C. Ríos
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class GRPCApplication {

	protected GRPCApplication() {
	}

	public static void main(String[] args) {
		SpringApplication.run(GRPCApplication.class, args);
	}

	@Component
	static class GRPCServer implements ApplicationRunner, DisposableBean {

		private static final Logger log = LoggerFactory.getLogger(GRPCServer.class);

		private Server server;

		@Override
		public void run(ApplicationArguments args) throws Exception {
			start();
		}

		private void start() throws IOException {
			ServerCredentials creds = createServerCredentials();
			server = Grpc.newServerBuilderForPort(0, creds)
				.addService(new HelloService())
				.addService(new StreamService())
				.build()
				.start();

			log.info("Starting gRPC server in port " + server.getPort());
		}

		int getPort() {
			return server.getPort();
		}

		private ServerCredentials createServerCredentials() throws IOException {
			File certChain = new ClassPathResource("public.cert").getFile();
			File privateKey = new ClassPathResource("private.key").getFile();

			return TlsServerCredentials.create(certChain, privateKey);
		}

		@Override
		public void destroy() throws InterruptedException {
			if (server != null) {
				server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
			}
			log.info("gRPC server stopped");
		}

		static class StreamService extends StreamServiceGrpc.StreamServiceImplBase {

			@Override
			public void more(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
				int count = 0;
				while (count < 3) {
					HelloResponse reply = HelloResponse.newBuilder()
						.setGreeting("Hello(" + count + ") ==> " + request.getFirstName())
						.build();
					if ("failWithRuntimeExceptionAfterData!".equals(request.getFirstName()) && count == 2) {
						StatusRuntimeException exception = Status.RESOURCE_EXHAUSTED
							.withDescription("Too long firstNames?")
							.asRuntimeException();
						responseObserver.onError(exception);
						return;
					}
					responseObserver.onNext(reply);
					count++;
					try {
						Thread.sleep(200L);
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						responseObserver.onError(e);
						return;
					}
				}
				responseObserver.onCompleted();
			}

		}

		static class HelloService extends HelloServiceGrpc.HelloServiceImplBase {

			@Override
			public void hello(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
				if ("failWithRuntimeException!".equals(request.getFirstName())) {
					StatusRuntimeException exception = Status.FAILED_PRECONDITION.withDescription("Invalid firstName")
						.asRuntimeException();
					responseObserver.onError(exception);
					responseObserver.onCompleted();
					return;
				}

				String greeting = String.format("Hello, %s %s", request.getFirstName(), request.getLastName());
				log.info("Sending response: " + greeting);

				HelloResponse response = HelloResponse.newBuilder().setGreeting(greeting).build();

				responseObserver.onNext(response);

				if ("failWithRuntimeExceptionAfterData!".equals(request.getFirstName())) {
					StatusRuntimeException exception = Status.RESOURCE_EXHAUSTED.withDescription("Too long firstNames?")
						.asRuntimeException();
					responseObserver.onError(exception);
					return;
				}

				responseObserver.onCompleted();
			}

		}

	}

}
