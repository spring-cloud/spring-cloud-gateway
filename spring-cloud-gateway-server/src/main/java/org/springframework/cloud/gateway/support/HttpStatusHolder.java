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

package org.springframework.cloud.gateway.support;

import org.springframework.core.style.ToStringCreator;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;

public class HttpStatusHolder {

	private final HttpStatus httpStatus;

	private final Integer status;

	public HttpStatusHolder(HttpStatus httpStatus, Integer status) {
		Assert.isTrue(httpStatus != null || status != null, "httpStatus and status may not both be null");
		this.httpStatus = httpStatus;
		this.status = status;
	}

	public static HttpStatusHolder parse(String status) {
		final HttpStatus httpStatus = ServerWebExchangeUtils.parse(status);
		final Integer intStatus;
		if (httpStatus == null) {
			intStatus = Integer.parseInt(status);
		}
		else {
			intStatus = null;
		}

		return new HttpStatusHolder(httpStatus, intStatus);
	}

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

	public Integer getStatus() {
		return status;
	}

	/**
	 * Whether this status code is in the HTTP series
	 * {@link org.springframework.http.HttpStatus.Series#INFORMATIONAL}.
	 * @return <code>true</code> if status code is in the INFORMATIONAL http series
	 */
	public boolean is1xxInformational() {
		return HttpStatus.Series.INFORMATIONAL.equals(getSeries());
	}

	/**
	 * Whether this status code is in the HTTP series
	 * {@link org.springframework.http.HttpStatus.Series#SUCCESSFUL}.
	 * @return <code>true</code> if status code is in the SUCCESSFUL http series
	 */
	public boolean is2xxSuccessful() {
		return HttpStatus.Series.SUCCESSFUL.equals(getSeries());
	}

	/**
	 * Whether this status code is in the HTTP series
	 * {@link org.springframework.http.HttpStatus.Series#REDIRECTION}.
	 * @return <code>true</code> if status code is in the REDIRECTION http series
	 */
	public boolean is3xxRedirection() {
		return HttpStatus.Series.REDIRECTION.equals(getSeries());
	}

	/**
	 * Whether this status code is in the HTTP series
	 * {@link org.springframework.http.HttpStatus.Series#CLIENT_ERROR}.
	 * @return <code>true</code> if status code is in the CLIENT_ERROR http series
	 */
	public boolean is4xxClientError() {
		return HttpStatus.Series.CLIENT_ERROR.equals(getSeries());
	}

	/**
	 * Whether this status code is in the HTTP series
	 * {@link org.springframework.http.HttpStatus.Series#SERVER_ERROR}.
	 * @return <code>true</code> if status code is in the SERVER_ERROR http series
	 */
	public boolean is5xxServerError() {
		return HttpStatus.Series.SERVER_ERROR.equals(getSeries());
	}

	public HttpStatus.Series getSeries() {
		if (httpStatus != null) {
			return httpStatus.series();
		}
		if (status != null) {
			return HttpStatus.Series.valueOf(status);
		}
		return null;
	}

	/**
	 * Whether this status code is in the HTTP series
	 * {@link org.springframework.http.HttpStatus.Series#CLIENT_ERROR} or
	 * {@link org.springframework.http.HttpStatus.Series#SERVER_ERROR}.
	 * @return <code>true</code> if is either CLIENT_ERROR or SERVER_ERROR
	 */
	public boolean isError() {
		return is4xxClientError() || is5xxServerError();
	}

	@Override
	public String toString() {
		return new ToStringCreator(this).append("httpStatus", httpStatus).append("status", status).toString();
	}

}
