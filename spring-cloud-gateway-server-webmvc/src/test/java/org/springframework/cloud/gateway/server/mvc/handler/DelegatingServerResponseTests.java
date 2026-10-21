/*
 * Copyright 2026-present the original author or authors.
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.ResourceRegionHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.function.EntityResponse;
import org.springframework.web.servlet.function.RenderingResponse;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DelegatingServerResponseTests {

	private static final long LAST_MODIFIED = 1445412480000L;

	private final ServerResponse.Context context = () -> List.of(new StringHttpMessageConverter(),
			new ByteArrayHttpMessageConverter(), new ResourceHttpMessageConverter(),
			new ResourceRegionHttpMessageConverter());

	@Test
	void adaptationIsIdempotentAndDoesNotAddGatewayStatusMutability() {
		ServerResponse delegate = ServerResponse.ok().body("body");
		ServerResponse adapted = DelegatingServerResponse.adapt(delegate);
		ServerResponse gateway = GatewayServerResponse.ok().body("gateway");

		assertThat(adapted).isNotInstanceOf(GatewayServerResponse.class);
		assertThat(DelegatingServerResponse.adapt(adapted)).isSameAs(adapted);
		assertThat(DelegatingServerResponse.adapt(gateway)).isSameAs(gateway);
	}

	@Test
	void unaccessedHeadersPassTheOriginalServletObjectsToTheDelegate() throws Exception {
		AtomicReference<HttpServletRequest> writtenRequest = new AtomicReference<>();
		AtomicReference<HttpServletResponse> writtenResponse = new AtomicReference<>();
		ServerResponse delegate = ServerResponse.ok().build((request, response) -> {
			writtenRequest.set(request);
			writtenResponse.set(response);
			return null;
		});
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
		MockHttpServletResponse response = new MockHttpServletResponse();

		DelegatingServerResponse.adapt(delegate).writeTo(request, response, this.context);

		assertThat(writtenRequest.get()).isSameAs(request);
		assertThat(writtenResponse.get()).isSameAs(response);
	}

	@Test
	void unchangedHeadersPassTheOriginalServletResponseToTheDelegate() throws Exception {
		AtomicReference<HttpServletResponse> writtenResponse = new AtomicReference<>();
		ServerResponse delegate = ServerResponse.ok().header("X-Test", "original").build((request, response) -> {
			writtenResponse.set(response);
			return null;
		});
		ServerResponse adapted = DelegatingServerResponse.adapt(delegate);
		adapted.headers().set("x-test", "temporary");
		adapted.headers().set("X-TEST", "original");
		MockHttpServletResponse response = write(adapted);

		assertThat(writtenResponse.get()).isSameAs(response);
		assertThat(response.getHeaders("X-Test")).containsExactly("original");
	}

	@Test
	void headersAreMutableDeepCopies() {
		ServerResponse delegate = ServerResponse.ok().header("X-Test", "one", "two").body("body");
		ServerResponse adapted = DelegatingServerResponse.adapt(delegate);

		adapted.headers().get("X-Test").remove(0);
		adapted.headers().get("X-Test").add("three");

		assertThat(adapted.headers().get("X-Test")).containsExactly("two", "three");
		assertThat(delegate.headers().get("X-Test")).containsExactly("one", "two");
		assertThat(adapted.headers()).isSameAs(adapted.headers());
	}

	@Test
	void addingHeadersDoesNotDuplicateOriginalValuesAndPreservesBody() throws Exception {
		ServerResponse adapted = DelegatingServerResponse
			.adapt(ServerResponse.ok().header("X-Test", "one").body("original body"));
		adapted.headers().add("X-Test", "two");
		adapted.headers().add("X-New", "three");

		MockHttpServletResponse response = write(adapted);

		assertThat(response.getHeaders("X-Test")).containsExactly("one", "two");
		assertThat(response.getHeaders("X-New")).containsExactly("three");
		assertThat(response.getContentAsString()).isEqualTo("original body");
		assertThat(response.getContentType()).startsWith("text/plain");
		assertThat(response.getHeader(HttpHeaders.CONTENT_LENGTH)).isEqualTo("13");
	}

	@Test
	void settingHeadersReplacesAllOriginalValuesIgnoringCase() throws Exception {
		ServerResponse adapted = DelegatingServerResponse
			.adapt(ServerResponse.ok().header("X-Test", "one", "two").body("body"));
		adapted.headers().set("x-TEST", "replacement");

		MockHttpServletResponse response = write(adapted);

		assertThat(response.getHeaders("X-Test")).containsExactly("replacement");
		assertThat(response.getContentAsString()).isEqualTo("body");
	}

	@Test
	void removingHeadersPreventsTheDelegateFromRestoringThem() throws Exception {
		ServerResponse adapted = DelegatingServerResponse
			.adapt(ServerResponse.ok().header("X-Test", "one").body("body"));
		adapted.headers().remove("x-test");

		MockHttpServletResponse response = write(adapted);

		assertThat(response.containsHeader("X-Test")).isFalse();
		assertThat(response.getContentAsString()).isEqualTo("body");
	}

	@Test
	void deduplicatingHeadersDoesNotReintroduceOriginalDuplicates() throws Exception {
		ServerResponse adapted = DelegatingServerResponse
			.adapt(ServerResponse.ok().header("X-Test", "one", "one", "two").body("body"));
		adapted.headers().put("x-test", List.copyOf(new LinkedHashSet<>(adapted.headers().get("X-Test"))));

		assertThat(write(adapted).getHeaders("X-Test")).containsExactly("one", "two");
	}

	@Test
	void changedHeadersReplaceValuesAlreadyPresentOnTheServletResponse() throws Exception {
		ServerResponse adapted = DelegatingServerResponse
			.adapt(ServerResponse.ok().header("X-Test", "original").header("X-Remove", "original").body("body"));
		adapted.headers().set("X-Test", "replacement");
		adapted.headers().remove("X-Remove");
		MockHttpServletResponse response = new MockHttpServletResponse();
		response.addHeader("X-Test", "earlier");
		response.addHeader("X-Remove", "earlier");

		adapted.writeTo(new MockHttpServletRequest("GET", "/"), response, this.context);

		assertThat(response.getHeaders("X-Test")).containsExactly("replacement");
		assertThat(response.containsHeader("X-Remove")).isFalse();
	}

	@Test
	void cookiesAndStatusAreDelegatedRatherThanFrozen() throws Exception {
		Cookie originalCookie = new Cookie("original", "one");
		Cookie dynamicCookie = new Cookie("dynamic", "two");
		ServerResponse delegate = ServerResponse.accepted().cookie(originalCookie).build((request, response) -> {
			response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
			response.addCookie(dynamicCookie);
			return null;
		});
		ServerResponse adapted = DelegatingServerResponse.adapt(delegate);
		adapted.headers().add("X-Test", "value");

		MockHttpServletResponse response = write(adapted);

		assertThat(adapted.statusCode()).isSameAs(delegate.statusCode());
		assertThat(adapted.cookies()).isSameAs(delegate.cookies());
		assertThat(response.getStatus()).isEqualTo(HttpStatus.PARTIAL_CONTENT.value());
		assertThat(response.getCookies()).containsExactly(originalCookie, dynamicCookie);
	}

	@Test
	void contentLengthChangesSuppressBothServletPropertySetters() throws Exception {
		ServerResponse adapted = DelegatingServerResponse
			.adapt(ServerResponse.ok().contentLength(111).build((request, response) -> {
				response.setContentLength(222);
				response.setContentLengthLong(333);
				response.getOutputStream().write("body".getBytes(StandardCharsets.UTF_8));
				return null;
			}));
		adapted.headers().setContentLength(4);

		MockHttpServletResponse response = write(adapted);

		assertThat(response.getHeaders(HttpHeaders.CONTENT_LENGTH)).containsExactly("4");
		assertThat(response.getContentAsString()).isEqualTo("body");
	}

	@Test
	void removingContentLengthSuppressesOriginalAndConverterLengths() throws Exception {
		ServerResponse adapted = DelegatingServerResponse.adapt(ServerResponse.ok().contentLength(111).body("body"));
		adapted.headers().remove(HttpHeaders.CONTENT_LENGTH);

		MockHttpServletResponse response = write(adapted);

		assertThat(response.containsHeader(HttpHeaders.CONTENT_LENGTH)).isFalse();
		assertThat(response.getContentAsString()).isEqualTo("body");
	}

	@Test
	void contentTypeAndCharsetChangesAreNotOverwrittenByServletPropertySetters() throws Exception {
		ServerResponse adapted = DelegatingServerResponse
			.adapt(ServerResponse.ok().contentType(MediaType.TEXT_PLAIN).build((request, response) -> {
				response.setContentType("text/plain;charset=ISO-8859-1");
				response.setCharacterEncoding("ISO-8859-1");
				response.setCharacterEncoding(StandardCharsets.US_ASCII);
				response.getWriter().write("你好");
				return null;
			}));
		adapted.headers().setContentType(new MediaType("text", "html", StandardCharsets.UTF_8));

		MockHttpServletResponse response = write(adapted);

		assertThat(response.getContentType()).isEqualTo("text/html;charset=UTF-8");
		assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
		assertThat(response.getContentAsString()).isEqualTo("你好");
	}

	@Test
	void removingContentTypeSuppressesConverterDefaults() throws Exception {
		ServerResponse adapted = DelegatingServerResponse
			.adapt(ServerResponse.ok().contentType(MediaType.TEXT_PLAIN).body("body"));
		adapted.headers().remove(HttpHeaders.CONTENT_TYPE);

		MockHttpServletResponse response = write(adapted);

		assertThat(response.getContentType()).isNull();
		assertThat(response.containsHeader(HttpHeaders.CONTENT_TYPE)).isFalse();
		assertThat(response.getContentAsString()).isEqualTo("body");
	}

	@Test
	void changedHeadersSuppressDateAndIntSettersButLeaveUnchangedHeadersDynamic() throws Exception {
		ServerResponse adapted = DelegatingServerResponse.adapt(ServerResponse.ok()
			.header("X-Date", "original")
			.header("X-Int", "original")
			.build((request, response) -> {
				response.addDateHeader("x-DATE", LAST_MODIFIED);
				response.setDateHeader("X-Date", LAST_MODIFIED);
				response.addIntHeader("x-INT", 1);
				response.setIntHeader("X-Int", 2);
				response.setDateHeader("X-Dynamic-Date", LAST_MODIFIED);
				response.addIntHeader("X-Dynamic-Int", 3);
				response.setContentType("text/plain;charset=UTF-8");
				response.setContentLength(4);
				response.setCharacterEncoding(StandardCharsets.UTF_8);
				return null;
			}));
		adapted.headers().remove("X-Date");
		adapted.headers().set("X-Int", "replacement");

		MockHttpServletResponse response = write(adapted);

		assertThat(response.containsHeader("X-Date")).isFalse();
		assertThat(response.getHeaders("X-Int")).containsExactly("replacement");
		assertThat(response.getHeader("X-Dynamic-Date")).isEqualTo("Wed, 21 Oct 2015 07:28:00 GMT");
		assertThat(response.getHeader("X-Dynamic-Int")).isEqualTo("3");
		assertThat(response.getContentType()).isEqualTo("text/plain;charset=UTF-8");
		assertThat(response.getHeader(HttpHeaders.CONTENT_LENGTH)).isEqualTo("4");
	}

	@ParameterizedTest
	@ValueSource(strings = { "GET", "HEAD" })
	void changedEtagDoesNotReturnNotModifiedForTheOriginalEtag(String method) throws Exception {
		ServerResponse adapted = DelegatingServerResponse
			.adapt(ServerResponse.ok().eTag("original").lastModified(Instant.ofEpochMilli(LAST_MODIFIED)).body("body"));
		adapted.headers().setETag("\"replacement\"");
		MockHttpServletRequest request = new MockHttpServletRequest(method, "/");
		request.addHeader(HttpHeaders.IF_NONE_MATCH, "\"original\"");

		MockHttpServletResponse response = write(adapted, request);

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.getHeaders(HttpHeaders.ETAG)).containsExactly("\"replacement\"");
		assertThat(response.getHeaders(HttpHeaders.LAST_MODIFIED)).hasSize(1);
		assertThat(response.getContentAsString()).isEqualTo("body");
	}

	@ParameterizedTest
	@ValueSource(strings = { "GET", "HEAD" })
	void changedEtagReturnsNotModifiedForTheEffectiveEtagAndKeepsCookies(String method) throws Exception {
		Cookie cookie = new Cookie("session", "value");
		ServerResponse adapted = DelegatingServerResponse.adapt(ServerResponse.ok()
			.eTag("original")
			.lastModified(Instant.ofEpochMilli(LAST_MODIFIED))
			.header("X-Test", "value")
			.cookie(cookie)
			.body("body"));
		adapted.headers().setETag("\"replacement\"");
		MockHttpServletRequest request = new MockHttpServletRequest(method, "/");
		request.addHeader(HttpHeaders.IF_NONE_MATCH, "W/\"replacement\"");

		MockHttpServletResponse response = write(adapted, request);

		assertThat(response.getStatus()).isEqualTo(304);
		assertThat(response.getHeaders(HttpHeaders.ETAG)).containsExactly("\"replacement\"");
		assertThat(response.getHeaders(HttpHeaders.LAST_MODIFIED)).hasSize(1);
		assertThat(response.getHeaders("X-Test")).containsExactly("value");
		assertThat(response.getCookies()).containsExactly(cookie);
		assertThat(response.getContentAsByteArray()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "GET", "HEAD" })
	void removedEtagDoesNotReturnNotModifiedForTheOriginalEtag(String method) throws Exception {
		ServerResponse adapted = DelegatingServerResponse.adapt(ServerResponse.ok().eTag("original").body("body"));
		adapted.headers().remove("etag");
		MockHttpServletRequest request = new MockHttpServletRequest(method, "/");
		request.addHeader(HttpHeaders.IF_NONE_MATCH, "\"original\"");

		MockHttpServletResponse response = write(adapted, request);

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.containsHeader(HttpHeaders.ETAG)).isFalse();
		assertThat(response.getContentAsString()).isEqualTo("body");
	}

	@Test
	void changedLastModifiedDoesNotReturnNotModifiedForTheOriginalDate() throws Exception {
		ServerResponse adapted = DelegatingServerResponse
			.adapt(ServerResponse.ok().eTag("original").lastModified(Instant.ofEpochMilli(LAST_MODIFIED)).body("body"));
		adapted.headers().setLastModified(LAST_MODIFIED + 1000);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
		request.addHeader(HttpHeaders.IF_MODIFIED_SINCE, "Wed, 21 Oct 2015 07:28:00 GMT");

		MockHttpServletResponse response = write(adapted, request);

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.getHeaders(HttpHeaders.ETAG)).containsExactly("\"original\"");
		assertThat(response.getHeaders(HttpHeaders.LAST_MODIFIED)).containsExactly("Wed, 21 Oct 2015 07:28:01 GMT");
		assertThat(response.getContentAsString()).isEqualTo("body");
	}

	@Test
	void changedLastModifiedReturnsNotModifiedForTheEffectiveDate() throws Exception {
		ServerResponse adapted = DelegatingServerResponse
			.adapt(ServerResponse.ok().lastModified(Instant.ofEpochMilli(LAST_MODIFIED)).body("body"));
		adapted.headers().setLastModified(LAST_MODIFIED + 1000);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
		request.addHeader(HttpHeaders.IF_MODIFIED_SINCE, "Wed, 21 Oct 2015 07:28:01 GMT");

		MockHttpServletResponse response = write(adapted, request);

		assertThat(response.getStatus()).isEqualTo(304);
		assertThat(response.getHeaders(HttpHeaders.LAST_MODIFIED)).containsExactly("Wed, 21 Oct 2015 07:28:01 GMT");
		assertThat(response.getContentAsByteArray()).isEmpty();
	}

	@Test
	void removedLastModifiedDoesNotReturnNotModifiedForTheOriginalDate() throws Exception {
		ServerResponse adapted = DelegatingServerResponse
			.adapt(ServerResponse.ok().lastModified(Instant.ofEpochMilli(LAST_MODIFIED)).body("body"));
		adapted.headers().remove("last-modified");
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
		request.addHeader(HttpHeaders.IF_MODIFIED_SINCE, "Wed, 21 Oct 2015 07:28:00 GMT");

		MockHttpServletResponse response = write(adapted, request);

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.containsHeader(HttpHeaders.LAST_MODIFIED)).isFalse();
		assertThat(response.getContentAsString()).isEqualTo("body");
	}

	@Test
	void unchangedValidatorsStillUseTheOriginalConditionalHandling() throws Exception {
		ServerResponse adapted = DelegatingServerResponse.adapt(ServerResponse.ok().eTag("original").body("body"));
		adapted.headers().add("X-Test", "value");
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
		request.addHeader(HttpHeaders.IF_NONE_MATCH, "\"original\"");

		MockHttpServletResponse response = write(adapted, request);

		assertThat(response.getStatus()).isEqualTo(304);
		assertThat(response.getHeaders("X-Test")).containsExactly("value");
		assertThat(response.getHeaders(HttpHeaders.ETAG)).containsExactly("\"original\"");
		assertThat(response.getContentAsByteArray()).isEmpty();
	}

	@Test
	void changedValidatorsDoNotApplySafeMethodConditionalHandlingToPost() throws Exception {
		ServerResponse adapted = DelegatingServerResponse.adapt(ServerResponse.ok().eTag("original").body("body"));
		adapted.headers().setETag("\"replacement\"");
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/");
		request.addHeader(HttpHeaders.IF_NONE_MATCH, "\"replacement\"");

		MockHttpServletResponse response = write(adapted, request);

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.getContentAsString()).isEqualTo("body");
	}

	@Test
	void conditionalRequestHeaderViewsHideOldConditionsButRetainRangeAndIfRange() throws Exception {
		ServerResponse adapted = DelegatingServerResponse
			.adapt(ServerResponse.ok().eTag("original").build((request, response) -> {
				for (String name : List.of(HttpHeaders.IF_MATCH, HttpHeaders.IF_NONE_MATCH,
						HttpHeaders.IF_MODIFIED_SINCE, HttpHeaders.IF_UNMODIFIED_SINCE)) {
					assertThat(request.getHeader(name.toLowerCase(Locale.ROOT))).isNull();
					assertThat(Collections.list(request.getHeaders(name))).isEmpty();
					assertThat(request.getDateHeader(name)).isEqualTo(-1);
					assertThat(request.getIntHeader(name)).isEqualTo(-1);
					assertThat(Collections.list(request.getHeaderNames())).doesNotContain(name);
				}
				assertThat(request.getHeader(HttpHeaders.RANGE)).isEqualTo("bytes=0-3");
				assertThat(request.getHeader(HttpHeaders.IF_RANGE)).isEqualTo("\"original\"");
				assertThat(Collections.list(request.getHeaders(HttpHeaders.IF_RANGE))).containsExactly("\"original\"");
				assertThat(Collections.list(request.getHeaderNames())).contains(HttpHeaders.RANGE,
						HttpHeaders.IF_RANGE);
				return null;
			}));
		adapted.headers().remove(HttpHeaders.ETAG);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
		request.addHeader(HttpHeaders.IF_MATCH, "\"original\"");
		request.addHeader(HttpHeaders.IF_NONE_MATCH, "\"original\"");
		request.addHeader(HttpHeaders.IF_MODIFIED_SINCE, "Wed, 21 Oct 2015 07:28:00 GMT");
		request.addHeader(HttpHeaders.IF_UNMODIFIED_SINCE, "Wed, 21 Oct 2015 07:28:00 GMT");
		request.addHeader(HttpHeaders.RANGE, "bytes=0-3");
		request.addHeader(HttpHeaders.IF_RANGE, "\"original\"");

		assertThat(write(adapted, request).getStatus()).isEqualTo(200);
	}

	@Test
	void entityAdaptationPreservesTheEntityInterfaceAndOriginalEntity() throws Exception {
		byte[] entity = "body".getBytes(StandardCharsets.UTF_8);
		EntityResponse<byte[]> delegate = EntityResponse.fromObject(entity).build();
		ServerResponse adapted = DelegatingServerResponse.adapt(delegate);

		assertThat(adapted).isInstanceOf(EntityResponse.class);
		assertThat(((EntityResponse<?>) adapted).entity()).isSameAs(entity);
		adapted.headers().add("X-Test", "value");
		assertThat(write(adapted).getContentAsByteArray()).isEqualTo(entity);
	}

	@Test
	void renderingAdaptationPreservesTheRenderingInterfaceAndModelAndView() throws Exception {
		Object modelValue = new Object();
		RenderingResponse delegate = RenderingResponse.create("template").modelAttribute("value", modelValue).build();
		ServerResponse adapted = DelegatingServerResponse.adapt(delegate);

		assertThat(adapted).isInstanceOf(RenderingResponse.class);
		RenderingResponse renderingResponse = (RenderingResponse) adapted;
		assertThat(renderingResponse.name()).isEqualTo(delegate.name());
		assertThat(renderingResponse.model()).isSameAs(delegate.model());
		adapted.headers().add("X-Test", "value");
		MockHttpServletResponse response = new MockHttpServletResponse();
		ModelAndView result = adapted.writeTo(new MockHttpServletRequest("GET", "/"), response, this.context);

		assertThat(result).isNotNull();
		assertThat(result.getViewName()).isEqualTo("template");
		assertThat(result.getModel()).containsEntry("value", modelValue);
		assertThat(response.getHeaders("X-Test")).containsExactly("value");
	}

	@Test
	void arbitraryWriteFunctionsReturnTheOriginalModelAndView() throws Exception {
		ModelAndView modelAndView = new ModelAndView("template");
		ServerResponse adapted = DelegatingServerResponse
			.adapt(ServerResponse.ok().build((request, response) -> modelAndView));
		adapted.headers().add("X-Test", "value");

		assertThat(adapted.writeTo(new MockHttpServletRequest("GET", "/"), new MockHttpServletResponse(), this.context))
			.isSameAs(modelAndView);
	}

	@Test
	void resourceRangeStatusAndDynamicHeadersRemainIntact() throws Exception {
		ServerResponse adapted = DelegatingServerResponse.adapt(ServerResponse.ok()
			.contentType(MediaType.APPLICATION_OCTET_STREAM)
			.body(new ByteArrayResource("abcdef".getBytes(StandardCharsets.UTF_8))));
		adapted.headers().add("X-Test", "value");
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
		request.addHeader(HttpHeaders.RANGE, "bytes=1-3");

		MockHttpServletResponse response = write(adapted, request);

		assertThat(response.getStatus()).isEqualTo(206);
		assertThat(response.getHeader(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
		assertThat(response.getHeader(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 1-3/6");
		assertThat(response.getContentAsString()).isEqualTo("bcd");
		assertThat(response.getHeaders("X-Test")).containsExactly("value");
	}

	@Test
	void unfinishedWholeAsyncResponseCanBeAdaptedAndWrittenWithoutReadingHeaders() throws Exception {
		CompletableFuture<ServerResponse> future = new CompletableFuture<>();
		ServerResponse delegate = ServerResponse.async(future);
		ServerResponse adapted = DelegatingServerResponse.adapt(delegate);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
		request.setAsyncSupported(true);
		MockHttpServletResponse response = new MockHttpServletResponse();

		adapted.writeTo(request, response, this.context);

		assertThat(request.isAsyncStarted()).isTrue();
		ServerResponse completed = ServerResponse.ok().body("async");
		future.complete(completed);
		assertThat(WebAsyncUtils.getAsyncManager(request).getConcurrentResult()).isSameAs(completed);
	}

	@Test
	void unfinishedWholeAsyncMetadataRetainsTheOriginalFailureInsteadOfBlocking() {
		ServerResponse adapted = DelegatingServerResponse.adapt(ServerResponse.async(new CompletableFuture<>()));

		assertThatThrownBy(adapted::headers).isInstanceOf(IllegalStateException.class)
			.hasMessage("Future ServerResponse has not yet completed");
	}

	@Test
	void asynchronousBodyRetainsTheChangedHeaderWrapper() throws Exception {
		CompletableFuture<String> body = new CompletableFuture<>();
		ServerResponse adapted = DelegatingServerResponse.adapt(ServerResponse.ok().header("X-Test", "one").body(body));
		adapted.headers().add("X-Test", "two");
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
		request.setAsyncSupported(true);
		MockHttpServletResponse response = new MockHttpServletResponse();

		adapted.writeTo(request, response, this.context);
		body.complete("async body");

		assertThat(response.getHeaders("X-Test")).containsExactly("one", "two");
		assertThat(response.getContentAsString()).isEqualTo("async body");
		assertThat(response.getHeader(HttpHeaders.CONTENT_LENGTH)).isEqualTo("10");
	}

	private MockHttpServletResponse write(ServerResponse response) throws Exception {
		return write(response, new MockHttpServletRequest("GET", "/"));
	}

	private MockHttpServletResponse write(ServerResponse response, MockHttpServletRequest request) throws Exception {
		MockHttpServletResponse servletResponse = new MockHttpServletResponse();
		response.writeTo(request, servletResponse, this.context);
		return servletResponse;
	}

}
