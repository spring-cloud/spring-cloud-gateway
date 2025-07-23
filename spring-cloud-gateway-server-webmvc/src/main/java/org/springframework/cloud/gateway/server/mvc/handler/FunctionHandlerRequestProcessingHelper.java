/*
 * Copyright 2019-2021 the original author or authors.
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry.FunctionInvocationWrapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.servlet.function.ServerResponse.BodyBuilder;

import static org.springframework.cloud.gateway.server.mvc.handler.FunctionHandlerHeaderUtils.fromMessage;
import static org.springframework.cloud.gateway.server.mvc.handler.FunctionHandlerHeaderUtils.sanitize;

/**
 * !INTERNAL USE ONLY!
 *
 * @author Oleg Zhurakousky
 *
 */
final class FunctionHandlerRequestProcessingHelper {

	private static Log logger = LogFactory.getLog(FunctionHandlerRequestProcessingHelper.class);

	private FunctionHandlerRequestProcessingHelper() {

	}

	static ServerResponse processRequest(ServerRequest request, FunctionInvocationWrapper function, Object argument,
			boolean eventStream, List<String> ignoredHeaders, List<String> requestOnlyHeaders) {
		return processRequest(request, function, argument, eventStream, ignoredHeaders, requestOnlyHeaders, null);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	static ServerResponse processRequest(ServerRequest request, FunctionInvocationWrapper function, Object argument,
			boolean eventStream, List<String> ignoredHeaders, List<String> requestOnlyHeaders,
			Map<String, String> additionalHeaders) {

		if (argument == null) {
			argument = "";
		}

		if (function == null) {
			return ServerResponse.notFound().build();
		}

		HttpHeaders headers = request.headers().asHttpHeaders();

		Message<?> inputMessage = null;

		MessageBuilder builder = MessageBuilder.withPayload(argument);
		if (!CollectionUtils.isEmpty(request.params())) {
			builder = builder.setHeader(FunctionHandlerHeaderUtils.HTTP_REQUEST_PARAM,
					request.params().toSingleValueMap());
		}

		if (!CollectionUtils.isEmpty(additionalHeaders)) {
			builder.copyHeaders(additionalHeaders);
		}
		inputMessage = builder.copyHeaders(headers.toSingleValueMap()).build();

		if (function.isRoutingFunction()) {
			function.setSkipOutputConversion(true);
		}

		if (logger.isDebugEnabled()) {
			logger.debug("Sending request to " + function + " with argument: " + inputMessage);
		}
		Object result = function.apply(inputMessage);
		if (function.isConsumer()) {
			return HttpMethod.DELETE.equals(request.method()) ? ServerResponse.ok().build()
					: ServerResponse.accepted()
						.headers(h -> h.addAll(sanitize(headers, ignoredHeaders, requestOnlyHeaders)))
						.build();
		}

		BodyBuilder responseOkBuilder = ServerResponse.ok()
			.headers(h -> h.addAll(sanitize(headers, ignoredHeaders, requestOnlyHeaders)));
		if (result instanceof Iterable i) {
			List aggregatedResult = (List) StreamSupport.stream(i.spliterator(), false).map(m -> {
				return m instanceof Message ? processMessage(responseOkBuilder, (Message<?>) m, ignoredHeaders) : m;
			}).collect(Collectors.toList());
			return responseOkBuilder.header("content-type", "application/json").body(aggregatedResult);
		}
		else if (result instanceof Message message) {
			return responseOkBuilder.body(processMessage(responseOkBuilder, message, ignoredHeaders));
		}
		else {
			return responseOkBuilder.body(result);
		}
	}

	private static Object processMessage(BodyBuilder responseOkBuilder, Message<?> message,
			List<String> ignoredHeaders) {
		responseOkBuilder.headers(h -> h.addAll(fromMessage(message.getHeaders(), ignoredHeaders)));
		return message.getPayload();
	}

}
