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

package org.springframework.cloud.gateway.server.mvc.predicate;

import java.lang.reflect.Method;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.common.Shortcut;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

public abstract class GatewayRequestPredicates {

	private static final Log logger = LogFactory.getLog(GatewayRequestPredicates.class);

	private static final PathPatternParser DEFAULT_HOST_INSTANCE = new HostReadOnlyPathPatternParser();

	private GatewayRequestPredicates() {
	}

	@Shortcut
	public static RequestPredicate after(ZonedDateTime dateTime) {
		return request -> ZonedDateTime.now().isAfter(dateTime);
	}

	@Shortcut
	public static RequestPredicate before(ZonedDateTime dateTime) {
		return request -> ZonedDateTime.now().isBefore(dateTime);
	}

	// TODO: accept and test datetime predicates (including yaml config)
	@Shortcut
	public static RequestPredicate between(ZonedDateTime dateTime1, ZonedDateTime dateTime2) {
		return request -> {
			ZonedDateTime now = ZonedDateTime.now();
			return now.isAfter(dateTime1) && now.isBefore(dateTime2);
		};
	}

	public static RequestPredicate cookie(String name) {
		return cookie(name, null);
	}

	@Shortcut
	public static RequestPredicate cookie(String name, String regexp) {
		return new CookieRequestPredicate(name, regexp);
	}

	public static RequestPredicate header(String header) {
		return header(header, null);
	}

	@Shortcut
	public static RequestPredicate header(String header, String regexp) {
		return new HeaderRequestPredicate(header, regexp);
	}

	// TODO: implement parameter aliases for predicates in RequestPredicates for webflux
	// compatibility?
	@Shortcut(type = Shortcut.Type.LIST)
	public static RequestPredicate method(HttpMethod... methods) {
		return RequestPredicates.methods(methods);
	}

	@Shortcut
	public static RequestPredicate host(String pattern) {
		Assert.notNull(pattern, "'pattern' must not be null");
		return hostPredicates(DEFAULT_HOST_INSTANCE).apply(pattern);
	}

	/**
	 * Return a function that creates new host-matching {@code RequestPredicates} from
	 * pattern Strings using the given {@link PathPatternParser}.
	 * <p>
	 * This method can be used to specify a non-default, customized
	 * {@code PathPatternParser} when resolving path patterns.
	 * @param patternParser the parser used to parse patterns given to the returned
	 * function
	 * @return a function that resolves a pattern String into a path-matching
	 * {@code RequestPredicates} instance
	 */
	public static Function<String, RequestPredicate> hostPredicates(PathPatternParser patternParser) {
		Assert.notNull(patternParser, "PathPatternParser must not be null");
		return pattern -> new HostPatternPredicate(patternParser.parse(pattern));
	}

	/**
	 * Return a {@code RequestPredicate} that tests the request path against the given
	 * path pattern.
	 * @param pattern the pattern to match to
	 * @return a predicate that tests against the given path pattern
	 */
	// TODO: find a different way to add shortcut to RequestPredicates.*
	@Shortcut
	public static RequestPredicate path(String pattern) {
		return RequestPredicates.path(pattern);
	}

	private static void traceMatch(String prefix, Object desired, @Nullable Object actual, boolean match) {
		if (logger.isTraceEnabled()) {
			logger.trace(String.format("%s \"%s\" %s against value \"%s\"", prefix, desired,
					match ? "matches" : "does not match", actual));
		}
	}

	private static class CookieRequestPredicate implements RequestPredicate {

		private final String name;

		private final Pattern pattern;

		CookieRequestPredicate(String name, String regexp) {
			this.name = name;
			this.pattern = (StringUtils.hasText(regexp)) ? Pattern.compile(regexp) : null;
		}

		@Override
		public boolean test(ServerRequest request) {
			if (CorsUtils.isPreFlightRequest(request.servletRequest())) {
				return true;
			}
			List<Cookie> cookies = request.cookies().get(name);
			if (ObjectUtils.isEmpty(cookies)) {
				return false;
			}
			// values is now guaranteed to not be empty
			if (pattern != null) {
				// check if a header value matches
				for (Cookie cookie : cookies) {
					if (pattern.asMatchPredicate().test(cookie.getValue())) {
						return true;
					}
				}
				return false;
			}

			// there is a value and since regexp is empty, we only check existence.
			return true;
		}

		@Override
		public void accept(RequestPredicates.Visitor visitor) {
			visitor.header(name, pattern.pattern());
		}

		@Override
		public String toString() {
			return String.format("Cookie: %s regexp=%s", name, pattern);
		}

	}

	private static class HeaderRequestPredicate implements RequestPredicate {

		private final String header;

		private final Pattern pattern;

		HeaderRequestPredicate(String header, String regexp) {
			this.header = header;
			this.pattern = (StringUtils.hasText(regexp)) ? Pattern.compile(regexp) : null;
		}

		@Override
		public boolean test(ServerRequest request) {
			if (CorsUtils.isPreFlightRequest(request.servletRequest())) {
				return true;
			}
			List<String> values = request.headers().header(header);
			if (values.isEmpty()) {
				return false;
			}
			// values is now guaranteed to not be empty
			if (pattern != null) {
				// check if a header value matches
				for (String value : values) {
					if (pattern.asMatchPredicate().test(value)) {
						return true;
					}
				}
				return false;
			}

			// there is a value and since regexp is empty, we only check existence.
			return true;
		}

		@Override
		public void accept(RequestPredicates.Visitor visitor) {
			visitor.header(header, pattern.pattern());
		}

		@Override
		public String toString() {
			return String.format("Header: %s regexp=%s", header, pattern);
		}

	}

	private static class HostReadOnlyPathPatternParser extends PathPatternParser {

		HostReadOnlyPathPatternParser() {
			super.setPathOptions(PathContainer.Options.MESSAGE_ROUTE);
		}

		@SuppressWarnings("deprecation")
		@Override
		public void setMatchOptionalTrailingSeparator(boolean matchOptionalTrailingSeparator) {
			raiseError();
		}

		@Override
		public void setCaseSensitive(boolean caseSensitive) {
			raiseError();
		}

		@Override
		public void setPathOptions(PathContainer.Options pathOptions) {
			raiseError();
		}

		private void raiseError() {
			throw new UnsupportedOperationException("This is a read-only, shared instance that cannot be modified");
		}

	}

	private static class HostPatternPredicate implements RequestPredicate, ChangePathPatternParserVisitor.Target {

		private PathPattern pattern;

		HostPatternPredicate(PathPattern pattern) {
			Assert.notNull(pattern, "'pattern' must not be null");
			this.pattern = pattern;
		}

		@Override
		public boolean test(ServerRequest request) {
			String host = request.headers().firstHeader(HttpHeaders.HOST);
			PathContainer pathContainer = PathContainer.parsePath(host, PathContainer.Options.MESSAGE_ROUTE);
			PathPattern.PathMatchInfo info = this.pattern.matchAndExtract(pathContainer);
			traceMatch("Pattern", this.pattern.getPatternString(), request.path(), info != null);
			if (info != null) {
				MvcUtils.putUriTemplateVariables(request, info.getUriVariables());
				return true;
			}
			else {
				return false;
			}
		}

		@Override
		public Optional<ServerRequest> nest(ServerRequest request) {
			throw new UnsupportedOperationException("nest is not supported");
			// return
			// Optional.ofNullable(this.pattern.matchStartOfPath(request.requestPath().pathWithinApplication()))
			// .map(info -> new RequestPredicates.SubPathServerRequestWrapper(request,
			// info, this.pattern));
		}

		@Override
		public void accept(RequestPredicates.Visitor visitor) {
			visitor.header(HttpHeaders.HOST, this.pattern.getPatternString());
		}

		@Override
		public void changeParser(PathPatternParser parser) {
			String patternString = this.pattern.getPatternString();
			this.pattern = parser.parse(patternString);
		}

		@Override
		public String toString() {
			return this.pattern.getPatternString();
		}

	}

	/**
	 * Implementation of {@link RouterFunctions.Visitor} that changes the
	 * {@link PathPatternParser} on path-related request predicates (i.e.
	 * {@code RequestPredicates.PathPatternPredicate}.
	 *
	 * @author Arjen Poutsma
	 * @since 5.3
	 */
	private static class ChangePathPatternParserVisitor implements RouterFunctions.Visitor {

		private final PathPatternParser parser;

		ChangePathPatternParserVisitor(PathPatternParser parser) {
			Assert.notNull(parser, "Parser must not be null");
			this.parser = parser;
		}

		@Override
		public void startNested(RequestPredicate predicate) {
			changeParser(predicate);
		}

		@Override
		public void endNested(RequestPredicate predicate) {
		}

		@Override
		public void route(RequestPredicate predicate, HandlerFunction<?> handlerFunction) {
			changeParser(predicate);
		}

		@Override
		public void resources(Function<ServerRequest, Optional<Resource>> lookupFunction) {
		}

		@Override
		public void attributes(Map<String, Object> attributes) {
		}

		@Override
		public void unknown(RouterFunction<?> routerFunction) {
		}

		private void changeParser(RequestPredicate predicate) {
			if (predicate instanceof ChangePathPatternParserVisitor.Target target) {
				target.changeParser(this.parser);
			}
		}

		/**
		 * Interface implemented by predicates that can change the parser.
		 */
		public interface Target {

			void changeParser(PathPatternParser parser);

		}

	}

	public static class PredicateSupplier
			implements org.springframework.cloud.gateway.server.mvc.predicate.PredicateSupplier {

		@Override
		public Collection<Method> get() {
			return Arrays.asList(GatewayRequestPredicates.class.getMethods());
		}

	}

}
