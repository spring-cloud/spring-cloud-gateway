/*
 * Copyright 2013-2017 the original author or authors.
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
 *
 */

package org.springframework.cloud.gateway.handler.predicate;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

import javax.validation.constraints.NotEmpty;

/**
 * @author Spencer Gibb
 */
public class BetweenRoutePredicateFactory extends AbstractRoutePredicateFactory<BetweenRoutePredicateFactory.Config> {

	public static final String DATETIME1_KEY = "datetime1";
	public static final String DATETIME2_KEY = "datetime2";

	public BetweenRoutePredicateFactory() {
		super(Config.class);
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(DATETIME1_KEY, DATETIME2_KEY);
	}

	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		//TODO: figure out boot conversion
		ZonedDateTime datetime1 = getZonedDateTime(config.datetime1);
		ZonedDateTime datetime2 = getZonedDateTime(config.datetime2);
		Assert.isTrue(datetime1.isBefore(datetime2),
				config.datetime1 +
				" must be before " + config.datetime2);

		return exchange -> {
			final ZonedDateTime now = ZonedDateTime.now();
			return now.isAfter(datetime1) && now.isBefore(datetime2);
		};
	}

	@Validated
	public static class Config {
		@NotEmpty
		private String datetime1;
		@NotEmpty
		private String datetime2;

		public String getDatetime1() {
			return datetime1;
		}

		public Config setDatetime1(String datetime1) {
			this.datetime1 = datetime1;
			return this;
		}

		public String getDatetime2() {
			return datetime2;
		}

		public Config setDatetime2(String datetime2) {
			this.datetime2 = datetime2;
			return this;
		}
	}

	public static ZonedDateTime getZonedDateTime(Object value) {
		ZonedDateTime dateTime;
		if (value instanceof ZonedDateTime) {
			dateTime = ZonedDateTime.class.cast(value);
		} else {
			dateTime = parseZonedDateTime(value.toString());
		}
		return dateTime;
	}

	public static ZonedDateTime parseZonedDateTime(String dateString) {
		ZonedDateTime dateTime;
		try {
			long epoch = Long.parseLong(dateString);

			dateTime = Instant.ofEpochMilli(epoch).atOffset(ZoneOffset.ofTotalSeconds(0))
					.toZonedDateTime();
		} catch (NumberFormatException e) {
			// try ZonedDateTime instead
			dateTime = ZonedDateTime.parse(dateString);
		}

		return dateTime;
	}

}
