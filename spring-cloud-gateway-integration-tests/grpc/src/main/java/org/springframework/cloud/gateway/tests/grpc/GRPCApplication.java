/*
 * Copyright 2013-2021 the original author or authors.
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

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * @author Alberto C. Ríos
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class GRPCApplication {

	public static void main(String[] args) {
		SpringApplication.run(GRPCApplication.class, args);
	}

	@Component
	static class GRPCServer implements ApplicationRunner {

		private static final Logger log = LoggerFactory.getLogger(GRPCServer.class);

		private final Environment environment;

		private Server server;

		GRPCServer(Environment environment) {
			this.environment = environment;
		}

		@Override
		public void run(ApplicationArguments args) throws Exception {
			final GRPCServer server = new GRPCServer(environment);
			server.start();
		}

		private void start() throws IOException {
			Integer serverPort = environment.getProperty("local.server.port", Integer.class);
			int grpcPort = serverPort + 1;
			ServerCredentials creds = createServerCredentials();
			server = Grpc.newServerBuilderForPort(grpcPort, creds).addService(new HelloService()).build().start();

			log.info("Starting gRPC server in port " + grpcPort);

			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					GRPCServer.this.stop();
				}
				catch (InterruptedException e) {
					e.printStackTrace(System.err);
				}
			}));
		}

		private ServerCredentials createServerCredentials() throws IOException {
			File certChain = new ClassPathResource("public.cert").getFile();
			File privateKey = new ClassPathResource("private.key").getFile();

			return TlsServerCredentials.create(certChain, privateKey);
		}

		private void stop() throws InterruptedException {
			if (server != null) {
				server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
			}
			log.info("gRPC server stopped");
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
				responseObserver.onCompleted();
			}

		}

	}

}
