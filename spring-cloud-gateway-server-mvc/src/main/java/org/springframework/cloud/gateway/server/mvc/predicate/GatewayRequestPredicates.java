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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.server.mvc.common.ArgumentSupplier;
import org.springframework.cloud.gateway.server.mvc.common.DefaultArgumentSuppliedEvent;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.cloud.gateway.server.mvc.common.Shortcut;
import org.springframework.cloud.gateway.server.mvc.common.Shortcut.Type;
import org.springframework.cloud.gateway.server.mvc.common.WeightConfig;
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

import static org.springframework.cloud.gateway.server.mvc.common.MvcUtils.GATEWAY_ROUTE_ID_ATTR;
import static org.springframework.cloud.gateway.server.mvc.common.MvcUtils.WEIGHT_ATTR;
import static org.springframework.cloud.gateway.server.mvc.common.MvcUtils.cacheAndReadBody;
import static org.springframework.cloud.gateway.server.mvc.common.MvcUtils.getAttribute;
import static org.springframework.cloud.gateway.server.mvc.common.MvcUtils.putAttribute;

public abstract class GatewayRequestPredicates {

	private static final Log log = LogFactory.getLog(GatewayRequestPredicates.class);

	private static final String X_CF_FORWARDED_URL = "X-CF-Forwarded-Url";

	private static final String X_CF_PROXY_SIGNATURE = "X-CF-Proxy-Signature";

	private static final String X_CF_PROXY_METADATA = "X-CF-Proxy-Metadata";

	private static final PathPatternParser DEFAULT_HOST_INSTANCE = new HostReadOnlyPathPatternParser();

	private static final String READ_BODY_CACHE_OBJECT_KEY = "cachedRequestBodyObject";

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

	public static RequestPredicate cloudFoundryRouteService() {
		return header(X_CF_FORWARDED_URL).and(header(X_CF_PROXY_METADATA)).and(header(X_CF_PROXY_SIGNATURE));
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
	@Shortcut(type = Type.LIST)
	public static RequestPredicate method(HttpMethod... methods) {
		return RequestPredicates.methods(methods);
	}

	public static RequestPredicate host(String pattern) {
		Assert.notNull(pattern, "'pattern' must not be null");
		return hostPredicates(DEFAULT_HOST_INSTANCE).apply(pattern);
	}

	@Shortcut(type = Type.LIST)
	public static RequestPredicate host(String... patterns) {
		Assert.notEmpty(patterns, "'patterns' must not be empty");
		RequestPredicate requestPredicate = hostPredicates(DEFAULT_HOST_INSTANCE).apply(patterns[0]);
		// I'm sure there's a functional way to do this, I'm just tired...
		for (int i = 1; i < patterns.length; i++) {
			requestPredicate = requestPredicate.or(hostPredicates(DEFAULT_HOST_INSTANCE).apply(patterns[i]));
		}
		return requestPredicate;
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
	public static RequestPredicate path(String pattern) {
		return RequestPredicates.path(pattern);
	}

	/**
	 * Return a {@code RequestPredicate} that tests the request path against the given
	 * path pattern.
	 * @param patterns the list of patterns to match
	 * @return a predicate that tests against the given path pattern
	 */
	@Shortcut(type = Type.LIST)
	public static RequestPredicate path(String... patterns) {
		Assert.notEmpty(patterns, "'patterns' must not be empty");
		RequestPredicate requestPredicate = RequestPredicates.path(patterns[0]);
		// I'm sure there's a functional way to do this, I'm just tired...
		for (int i = 1; i < patterns.length; i++) {
			requestPredicate = requestPredicate.or(RequestPredicates.path(patterns[i]));
		}
		return requestPredicate;
	}

	/**
	 * Return a {@code RequestPredicate} that tests the presence of a request parameter.
	 * @param param the name of the query parameter
	 * @return a predicate that tests for the presence of a given param
	 */
	@Shortcut
	public static RequestPredicate query(String param) {
		return query(param, null);
	}

	/**
	 * Return a {@code RequestPredicate} that tests the presence of a request parameter if
	 * the regexp is empty, or, otherwise finds if any value of the parameter matches the
	 * regexp.
	 * @param param the name of the query parameter
	 * @param regexp an optional regular expression to match.
	 * @return a predicate that tests for the given param and regexp.
	 */
	@Shortcut
	public static RequestPredicate query(String param, String regexp) {
		if (!StringUtils.hasText(regexp)) {
			return request -> request.param(param).isPresent();
		}
		return request -> request.param(param).stream().anyMatch(value -> value.matches(regexp));
	}

	public static <T> RequestPredicate readBody(Class<T> inClass, Predicate<T> predicate) {
		return new ReadBodyPredicate<>(inClass, predicate);
	}

	/**
	 * A predicate which will select a route based on its assigned weight.
	 * @param group the group the route belongs to
	 * @param weight the weight for the route
	 * @return a predicate that tests against the given group and weight.
	 */
	@Shortcut
	public static RequestPredicate weight(String group, int weight) {
		return new WeightPredicate(group, weight);
	}

	private static void traceMatch(String prefix, Object desired, @Nullable Object actual, boolean match) {
		if (log.isTraceEnabled()) {
			log.trace(String.format("%s \"%s\" %s against value \"%s\"", prefix, desired,
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
			if (pattern != null) {
				visitor.header(header, pattern.pattern());
			}
			else {
				visitor.header(header, "");
			}
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
			if (host == null) {
				host = "";
			}
			PathContainer pathContainer = PathContainer.parsePath(host, PathContainer.Options.MESSAGE_ROUTE);
			PathPattern.PathMatchInfo info = this.pattern.matchAndExtract(pathContainer);
			traceMatch("Pattern", this.pattern.getPatternString(), host, info != null);
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

	private static final class ReadBodyPredicate<T> implements RequestPredicate {

		private final Class<T> toRead;

		private final Predicate<T> predicate;

		ReadBodyPredicate(Class<T> toRead, Predicate<T> predicate) {
			this.toRead = toRead;
			this.predicate = predicate;
		}

		@Override
		@SuppressWarnings("unchecked")
		public boolean test(ServerRequest request) {
			try {
				Object cachedBody = getAttribute(request, READ_BODY_CACHE_OBJECT_KEY);

				if (cachedBody != null) {
					return predicate.test((T) cachedBody);
				}
			}
			catch (ClassCastException e) {
				if (log.isDebugEnabled()) {
					log.debug("Predicate test failed because class in predicate "
							+ "does not match the cached body object", e);
				}
			}

			return cacheAndReadBody(request, toRead).map(body -> {
				putAttribute(request, READ_BODY_CACHE_OBJECT_KEY, body);
				return predicate.test(body);
			}).orElse(false);
		}

		@Override
		public void accept(RequestPredicates.Visitor visitor) {
			visitor.unknown(this);
		}

		@Override
		public String toString() {
			return String.format("ReadBody=%s predicate=%s", toRead.getSimpleName(), predicate);
		}

	}

	private static final class WeightPredicate implements RequestPredicate, ArgumentSupplier<WeightConfig> {

		final String group;

		final int weight;

		private WeightPredicate(String group, int weight) {
			this.group = group;
			this.weight = weight;
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean test(ServerRequest request) {
			Map<String, String> weights = (Map<String, String>) request.attributes()
				.getOrDefault(WEIGHT_ATTR, Collections.emptyMap());

			String routeId = (String) request.attributes().get(GATEWAY_ROUTE_ID_ATTR);
			if (ObjectUtils.isEmpty(routeId)) {
				// no routeId to test against
				// TODO: maybe log a warning
				return false;
			}

			// all calculations and comparison against random num happened in
			// WeightCalculatorHandlerInterceptor
			if (weights.containsKey(group)) {

				String chosenRoute = weights.get(group);
				if (log.isTraceEnabled()) {
					log.trace("in group weight: " + group + ", current route: " + routeId + ", chosen route: "
							+ chosenRoute);
				}

				return routeId.equals(chosenRoute);
			}
			else if (log.isTraceEnabled()) {
				log.trace("no weights found for group: " + group + ", current route: " + routeId);
			}

			return false;
		}

		@Override
		public void accept(RequestPredicates.Visitor visitor) {
			visitor.unknown(this);
		}

		@Override
		public ArgumentSuppliedEvent<WeightConfig> getArgumentSuppliedEvent() {
			return new DefaultArgumentSuppliedEvent<>(this, WeightConfig.class, new WeightConfig(null, group, weight));
		}

		@Override
		public String toString() {
			return String.format("Weight=%d group=%s", weight, group);
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
