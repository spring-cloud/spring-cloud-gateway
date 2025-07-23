/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.gateway.filter.factory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;

import javax.net.ssl.SSLException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufFactory;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchema;
import com.fasterxml.jackson.dataformat.protobuf.schema.ProtobufSchemaLoader;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.netty.buffer.PooledByteBufAllocator;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.config.GrpcSslConfigurer;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.GatewayToStringStyler.filterToStringCreator;

/**
 * This filter takes a JSON payload, transform it into a protobuf object, send it to a
 * given gRPC channel, and transform the response back to JSON.
 *
 * Making it transparent for the consumer that the service under the gateway is a gRPC
 * one.
 *
 * @author Alberto C. Ríos
 */
public class JsonToGrpcGatewayFilterFactory
		extends AbstractGatewayFilterFactory<JsonToGrpcGatewayFilterFactory.Config> {

	private final GrpcSslConfigurer grpcSslConfigurer;

	private final ResourceLoader resourceLoader;

	public JsonToGrpcGatewayFilterFactory(GrpcSslConfigurer grpcSslConfigurer, ResourceLoader resourceLoader) {
		super(Config.class);
		this.grpcSslConfigurer = grpcSslConfigurer;
		this.resourceLoader = resourceLoader;
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList("protoDescriptor", "protoFile", "service", "method");
	}

	@Override
	public GatewayFilter apply(Config config) {
		GatewayFilter filter = new GatewayFilter() {
			@Override
			public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
				GRPCResponseDecorator modifiedResponse = new GRPCResponseDecorator(exchange, config);

				ServerWebExchangeUtils.setAlreadyRouted(exchange);
				return modifiedResponse.writeWith(exchange.getRequest().getBody())
					.then(chain.filter(exchange.mutate().response(modifiedResponse).build()));
			}

			@Override
			public String toString() {
				return filterToStringCreator(JsonToGrpcGatewayFilterFactory.this).toString();
			}
		};

		int order = NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
		return new OrderedGatewayFilter(filter, order);
	}

	public static class Config {

		private String protoDescriptor;

		private String protoFile;

		private String service;

		private String method;

		public String getProtoDescriptor() {
			return protoDescriptor;
		}

		public Config setProtoDescriptor(String protoDescriptor) {
			this.protoDescriptor = protoDescriptor;
			return this;
		}

		public String getProtoFile() {
			return protoFile;
		}

		public Config setProtoFile(String protoFile) {
			this.protoFile = protoFile;
			return this;
		}

		public String getService() {
			return service;
		}

		public Config setService(String service) {
			this.service = service;
			return this;
		}

		public String getMethod() {
			return method;
		}

		public Config setMethod(String method) {
			this.method = method;
			return this;
		}

	}

	class GRPCResponseDecorator extends ServerHttpResponseDecorator {

		private final ServerWebExchange exchange;

		private final Descriptors.Descriptor descriptor;

		private final ObjectWriter objectWriter;

		private final ObjectReader objectReader;

		private final ClientCall<DynamicMessage, DynamicMessage> clientCall;

		private final ObjectNode objectNode;

		GRPCResponseDecorator(ServerWebExchange exchange, Config config) {
			super(exchange.getResponse());
			this.exchange = exchange;
			try {
				Resource descriptorFile = resourceLoader.getResource(config.getProtoDescriptor());
				Resource protoFile = resourceLoader.getResource(config.getProtoFile());

				descriptor = DescriptorProtos.FileDescriptorProto.parseFrom(descriptorFile.getInputStream())
					.getDescriptorForType();

				Descriptors.MethodDescriptor methodDescriptor = getMethodDescriptor(config,
						descriptorFile.getInputStream());
				Descriptors.ServiceDescriptor serviceDescriptor = methodDescriptor.getService();
				Descriptors.Descriptor outputType = methodDescriptor.getOutputType();

				clientCall = createClientCallForType(config, serviceDescriptor, outputType);

				ProtobufSchema schema = ProtobufSchemaLoader.std.load(protoFile.getInputStream());
				ProtobufSchema responseType = schema.withRootType(outputType.getName());

				ObjectMapper objectMapper = new ObjectMapper(new ProtobufFactory());
				objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
				objectWriter = objectMapper.writer(schema);
				objectReader = objectMapper.readerFor(JsonNode.class).with(responseType);
				objectNode = objectMapper.createObjectNode();

			}
			catch (IOException | Descriptors.DescriptorValidationException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
			exchange.getResponse().getHeaders().set("Content-Type", "application/json");

			return getDelegate().writeWith(deserializeJSONRequest().map(callGRPCServer())
				.map(serialiseGRPCResponse())
				.map(wrapGRPCResponse())
				.cast(DataBuffer.class)
				.last());
		}

		private ClientCall<DynamicMessage, DynamicMessage> createClientCallForType(Config config,
				Descriptors.ServiceDescriptor serviceDescriptor, Descriptors.Descriptor outputType) {
			MethodDescriptor.Marshaller<DynamicMessage> marshaller = ProtoUtils
				.marshaller(DynamicMessage.newBuilder(outputType).build());
			MethodDescriptor<DynamicMessage, DynamicMessage> methodDescriptor = MethodDescriptor
				.<DynamicMessage, DynamicMessage>newBuilder()
				.setType(MethodDescriptor.MethodType.UNKNOWN)
				.setFullMethodName(
						MethodDescriptor.generateFullMethodName(serviceDescriptor.getFullName(), config.getMethod()))
				.setRequestMarshaller(marshaller)
				.setResponseMarshaller(marshaller)
				.build();
			Channel channel = createChannel();
			return channel.newCall(methodDescriptor, CallOptions.DEFAULT);
		}

		private Descriptors.MethodDescriptor getMethodDescriptor(Config config, InputStream descriptorFile)
				throws IOException, Descriptors.DescriptorValidationException {
			DescriptorProtos.FileDescriptorSet fileDescriptorSet = DescriptorProtos.FileDescriptorSet
				.parseFrom(descriptorFile);
			DescriptorProtos.FileDescriptorProto fileProto = fileDescriptorSet.getFile(0);
			Descriptors.FileDescriptor fileDescriptor = Descriptors.FileDescriptor.buildFrom(fileProto,
					new Descriptors.FileDescriptor[0]);

			Descriptors.ServiceDescriptor serviceDescriptor = fileDescriptor.findServiceByName(config.getService());
			if (serviceDescriptor == null) {
				throw new NoSuchElementException("No Service found");
			}

			List<Descriptors.MethodDescriptor> methods = serviceDescriptor.getMethods();

			return methods.stream()
				.filter(method -> method.getName().equals(config.getMethod()))
				.findFirst()
				.orElseThrow(() -> new NoSuchElementException("No Method found"));
		}

		private ManagedChannel createChannel() {
			URI requestURI = ((Route) exchange.getAttributes().get(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR)).getUri();
			return createChannelChannel(requestURI.getHost(), requestURI.getPort());
		}

		private Function<JsonNode, DynamicMessage> callGRPCServer() {
			return jsonRequest -> {
				try {
					byte[] request = objectWriter.writeValueAsBytes(jsonRequest);
					return ClientCalls.blockingUnaryCall(clientCall, DynamicMessage.parseFrom(descriptor, request));
				}
				catch (IOException e) {
					throw new RuntimeException(e);
				}
			};
		}

		private Function<DynamicMessage, Object> serialiseGRPCResponse() {
			return gRPCResponse -> {
				try {
					return objectReader.readValue(gRPCResponse.toByteArray());
				}
				catch (IOException e) {
					throw new RuntimeException(e);
				}
			};
		}

		private Flux<JsonNode> deserializeJSONRequest() {
			return exchange.getRequest().getBody().mapNotNull(dataBufferBody -> {
				if (dataBufferBody.capacity() == 0) {
					return objectNode;
				}
				ResolvableType targetType = ResolvableType.forType(JsonNode.class);
				return new Jackson2JsonDecoder().decode(dataBufferBody, targetType, null, null);
			}).cast(JsonNode.class);
		}

		private Function<Object, DataBuffer> wrapGRPCResponse() {
			return jsonResponse -> {
				try {
					return new NettyDataBufferFactory(new PooledByteBufAllocator())
						.wrap(Objects.requireNonNull(new ObjectMapper().writeValueAsBytes(jsonResponse)));
				}
				catch (JsonProcessingException e) {
					return new NettyDataBufferFactory(new PooledByteBufAllocator()).allocateBuffer();
				}
			};
		}

		// We are creating this on every call, should optimize?
		private ManagedChannel createChannelChannel(String host, int port) {
			NettyChannelBuilder nettyChannelBuilder = NettyChannelBuilder.forAddress(host, port);
			try {
				return grpcSslConfigurer.configureSsl(nettyChannelBuilder);
			}
			catch (SSLException e) {
				throw new RuntimeException(e);
			}
		}

	}

}
