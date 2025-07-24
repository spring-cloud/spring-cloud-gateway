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

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.ErrorResponse;
import org.springframework.web.servlet.function.ServerResponse;

public interface GatewayServerResponse extends ServerResponse {

	void setStatusCode(HttpStatusCode statusCode);

	// Static methods

	/**
	 * Create a builder with the status code and headers of the given response.
	 * @param other the response to copy the status and headers from
	 * @return the created builder
	 */
	static ServerResponse.BodyBuilder from(ServerResponse other) {
		return new GatewayServerResponseBuilder(other);
	}

	/**
	 * Create a {@code ServerResponse} from the given {@link ErrorResponse}.
	 * @param response the {@link ErrorResponse} to initialize from
	 * @return the built response
	 * @since 6.0
	 */
	static ServerResponse from(ErrorResponse response) {
		return status(response.getStatusCode()).headers(headers -> headers.putAll(response.getHeaders()))
			.body(response.getBody());
	}

	/**
	 * Create a builder with the given HTTP status.
	 * @param status the response status
	 * @return the created builder
	 */
	static ServerResponse.BodyBuilder status(HttpStatusCode status) {
		return new GatewayServerResponseBuilder(status);
	}

	/**
	 * Create a builder with the given HTTP status.
	 * @param status the response status
	 * @return the created builder
	 */
	static ServerResponse.BodyBuilder status(int status) {
		return new GatewayServerResponseBuilder(HttpStatusCode.valueOf(status));
	}

	/**
	 * Create a builder with the status set to {@linkplain HttpStatus#OK 200 OK}.
	 * @return the created builder
	 */
	static ServerResponse.BodyBuilder ok() {
		return status(HttpStatus.OK);
	}

	/**
	 * Create a builder with a {@linkplain HttpStatus#CREATED 201 Created} status and a
	 * location header set to the given URI.
	 * @param location the location URI
	 * @return the created builder
	 */
	static ServerResponse.BodyBuilder created(URI location) {
		ServerResponse.BodyBuilder builder = status(HttpStatus.CREATED);
		return builder.location(location);
	}

	/**
	 * Create a builder with a {@linkplain HttpStatus#ACCEPTED 202 Accepted} status.
	 * @return the created builder
	 */
	static ServerResponse.BodyBuilder accepted() {
		return status(HttpStatus.ACCEPTED);
	}

	/**
	 * Create a builder with a {@linkplain HttpStatus#NO_CONTENT 204 No Content} status.
	 * @return the created builder
	 */
	static ServerResponse.HeadersBuilder<?> noContent() {
		return status(HttpStatus.NO_CONTENT);
	}

	/**
	 * Create a builder with a {@linkplain HttpStatus#SEE_OTHER 303 See Other} status and
	 * a location header set to the given URI.
	 * @param location the location URI
	 * @return the created builder
	 */
	static ServerResponse.BodyBuilder seeOther(URI location) {
		ServerResponse.BodyBuilder builder = status(HttpStatus.SEE_OTHER);
		return builder.location(location);
	}

	/**
	 * Create a builder with a {@linkplain HttpStatus#TEMPORARY_REDIRECT 307 Temporary
	 * Redirect} status and a location header set to the given URI.
	 * @param location the location URI
	 * @return the created builder
	 */
	static ServerResponse.BodyBuilder temporaryRedirect(URI location) {
		ServerResponse.BodyBuilder builder = status(HttpStatus.TEMPORARY_REDIRECT);
		return builder.location(location);
	}

	/**
	 * Create a builder with a {@linkplain HttpStatus#PERMANENT_REDIRECT 308 Permanent
	 * Redirect} status and a location header set to the given URI.
	 * @param location the location URI
	 * @return the created builder
	 */
	static ServerResponse.BodyBuilder permanentRedirect(URI location) {
		ServerResponse.BodyBuilder builder = status(HttpStatus.PERMANENT_REDIRECT);
		return builder.location(location);
	}

	/**
	 * Create a builder with a {@linkplain HttpStatus#BAD_REQUEST 400 Bad Request} status.
	 * @return the created builder
	 */
	static ServerResponse.BodyBuilder badRequest() {
		return status(HttpStatus.BAD_REQUEST);
	}

	/**
	 * Create a builder with a {@linkplain HttpStatus#NOT_FOUND 404 Not Found} status.
	 * @return the created builder
	 */
	static ServerResponse.HeadersBuilder<?> notFound() {
		return status(HttpStatus.NOT_FOUND);
	}

	/**
	 * Create a builder with a {@linkplain HttpStatus#UNPROCESSABLE_ENTITY 422
	 * Unprocessable Entity} status.
	 * @return the created builder
	 */
	static ServerResponse.BodyBuilder unprocessableEntity() {
		return status(HttpStatus.UNPROCESSABLE_ENTITY);
	}

	/**
	 * Create a (built) response with the given asynchronous response. Parameter
	 * {@code asyncResponse} can be a {@link CompletableFuture
	 * CompletableFuture&lt;ServerResponse&gt;} or {@link Publisher
	 * Publisher&lt;ServerResponse&gt;} (or any asynchronous producer of a single
	 * {@code ServerResponse} that can be adapted via the
	 * {@link ReactiveAdapterRegistry}).
	 *
	 * <p>
	 * This method can be used to set the response status code, headers, and body based on
	 * an asynchronous result. If only the body is asynchronous,
	 * {@link ServerResponse.BodyBuilder#body(Object)} can be used instead.
	 * @param asyncResponse a {@code CompletableFuture<ServerResponse>} or
	 * {@code Publisher<ServerResponse>}
	 * @return the asynchronous response
	 * @since 5.3
	 */
	static ServerResponse async(Object asyncResponse) {
		return GatewayAsyncServerResponse.create(asyncResponse, null);
	}

	/**
	 * Create a (built) response with the given asynchronous response. Parameter
	 * {@code asyncResponse} can be a {@link CompletableFuture
	 * CompletableFuture&lt;ServerResponse&gt;} or {@link Publisher
	 * Publisher&lt;ServerResponse&gt;} (or any asynchronous producer of a single
	 * {@code ServerResponse} that can be adapted via the
	 * {@link ReactiveAdapterRegistry}).
	 *
	 * <p>
	 * This method can be used to set the response status code, headers, and body based on
	 * an asynchronous result. If only the body is asynchronous,
	 * {@link ServerResponse.BodyBuilder#body(Object)} can be used instead.
	 * @param asyncResponse a {@code CompletableFuture<ServerResponse>} or
	 * {@code Publisher<ServerResponse>}
	 * @param timeout maximum time period to wait for before timing out
	 * @return the asynchronous response
	 * @since 5.3.2
	 */
	static ServerResponse async(Object asyncResponse, Duration timeout) {
		return GatewayAsyncServerResponse.create(asyncResponse, timeout);
	}

}
