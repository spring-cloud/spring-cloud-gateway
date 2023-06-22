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

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
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

	public static RequestPredicate method(HttpMethod method) {
		return RequestPredicates.method(method);
	}

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

	private static void traceMatch(String prefix, Object desired, @Nullable Object actual, boolean match) {
		if (logger.isTraceEnabled()) {
			logger.trace(String.format("%s \"%s\" %s against value \"%s\"", prefix, desired,
					match ? "matches" : "does not match", actual));
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
			String host = request.headers().firstHeader("Host");
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
			visitor.path(this.pattern.getPatternString());
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

}
