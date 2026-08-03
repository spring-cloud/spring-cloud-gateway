/*
 * Copyright 2013-present the original author or authors.
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

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

public abstract class PathUtils {

	private static final Log logger = LogFactory.getLog(PathUtils.class);

	private PathUtils() {
	}

	/**
	 * Check whether the given path contains invalid escape sequences.
	 * @param path the path to validate
	 * @return {@code true} if the path is invalid, {@code false} otherwise
	 */
	public static boolean isInvalidEncodedPath(String path) {
		if (path.contains("%")) {
			try {
				// Use URLDecoder (vs UriUtils) to preserve potentially decoded UTF-8
				// chars
				String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);
				if (isInvalidPath(decodedPath)) {
					return true;
				}
				decodedPath = processPath(decodedPath);
				if (isInvalidPath(decodedPath)) {
					return true;
				}
			}
			catch (IllegalArgumentException ex) {
				// Should never happen...
			}
		}
		return false;
	}

	/**
	 * Process the given resource path.
	 * <p>
	 * The default implementation replaces:
	 * <ul>
	 * <li>Backslash with forward slash.
	 * <li>Duplicate occurrences of slash with a single slash.
	 * <li>Any combination of leading slash and control characters (00-1F and 7F) with a
	 * single "/" or "". For example {@code "  / // foo/bar"} becomes {@code "/foo/bar"}.
	 * </ul>
	 * @param path path to process
	 * @return the processed path
	 * @since 3.2.12
	 */
	protected static String processPath(String path) {
		path = StringUtils.replace(path, "\\", "/");
		path = cleanDuplicateSlashes(path);
		return cleanLeadingSlash(path);
	}

	private static String cleanDuplicateSlashes(String path) {
		StringBuilder sb = null;
		char prev = 0;
		for (int i = 0; i < path.length(); i++) {
			char curr = path.charAt(i);
			try {
				if ((curr == '/') && (prev == '/')) {
					if (sb == null) {
						sb = new StringBuilder(path.substring(0, i));
					}
					continue;
				}
				if (sb != null) {
					sb.append(path.charAt(i));
				}
			}
			finally {
				prev = curr;
			}
		}
		return sb != null ? sb.toString() : path;
	}

	private static String cleanLeadingSlash(String path) {
		boolean slash = false;
		for (int i = 0; i < path.length(); i++) {
			if (path.charAt(i) == '/') {
				slash = true;
			}
			else if (path.charAt(i) > ' ' && path.charAt(i) != 127) {
				if (i == 0 || (i == 1 && slash)) {
					return path;
				}
				return (slash ? "/" + path.substring(i) : path.substring(i));
			}
		}
		return (slash ? "/" : "");
	}

	/**
	 * Identifies invalid resource paths. By default rejects:
	 * <ul>
	 * <li>Paths that contain "WEB-INF" or "META-INF"
	 * <li>Paths that contain "../" after a call to {@link StringUtils#cleanPath}.
	 * <li>Paths that represent a {@link ResourceUtils#isUrl valid URL} or would represent
	 * one after the leading slash is removed.
	 * </ul>
	 * <p>
	 * <strong>Note:</strong> this method assumes that leading, duplicate '/' or control
	 * characters (e.g. white space) have been trimmed so that the path starts predictably
	 * with a single '/' or does not have one.
	 * @param path the path to validate
	 * @return {@code true} if the path is invalid, {@code false} otherwise
	 * @since 3.0.6
	 */
	public static boolean isInvalidPath(String path) {
		if (path.contains("WEB-INF") || path.contains("META-INF")) {
			if (logger.isWarnEnabled()) {
				logger.warn("Path with \"WEB-INF\" or \"META-INF\": [" + path + "]");
			}
			return true;
		}
		if (path.contains(":/")) {
			String relativePath = (path.charAt(0) == '/' ? path.substring(1) : path);
			if (ResourceUtils.isUrl(relativePath) || relativePath.startsWith("url:")) {
				if (logger.isWarnEnabled()) {
					logger.warn("Path represents URL or has \"url:\" prefix: [" + path + "]");
				}
				return true;
			}
		}
		if (path.contains("..") && StringUtils.cleanPath(path).contains("../")) {
			if (logger.isWarnEnabled()) {
				logger.warn("Path contains \"../\" after call to StringUtils#cleanPath: [" + path + "]");
			}
			return true;
		}
		return false;
	}

	/**
	 * Perform additional checks on a resolved resource beyond checking whether the
	 * resources exists and is readable. The default implementation also verifies the
	 * resource is either under the location relative to which it was found or is under
	 * one of the {@link #setAllowedLocations allowed locations}.
	 * @param resource the resource to check
	 * @param location the location relative to which the resource was found
	 * @param allowedLocations set of allowed locations
	 * @return "true" if resource is in a valid location, "false" otherwise.
	 * @throws IOException if Resource URLS fail to parse.
	 * @since 4.1.2
	 */
	public static boolean checkResource(Resource resource, Resource location, List<Resource> allowedLocations)
			throws IOException {
		if (isResourceUnderLocation(resource, location)) {
			return true;
		}
		if (allowedLocations != null) {
			for (Resource current : allowedLocations) {
				if (isResourceUnderLocation(resource, current)) {
					return true;
				}
			}
		}
		if (logger.isWarnEnabled()) {
			logger.warn("Resource path \"" + location.getURI() + "\" was successfully resolved " + "but resource \""
					+ resource.getURL() + "\" is neither under the " + "current location \"" + location.getURL()
					+ "\" nor under any of the " + "allowed locations "
					+ (allowedLocations != null ? allowedLocations : "[]"));
		}
		return false;
	}

	private static boolean isResourceUnderLocation(Resource resource, Resource location) throws IOException {
		if (resource.getClass() != location.getClass()) {
			return false;
		}

		String resourcePath;
		String locationPath;

		if (resource instanceof UrlResource) {
			resourcePath = resource.getURL().toExternalForm();
			locationPath = StringUtils.cleanPath(location.getURL().toString());
		}
		else if (resource instanceof ClassPathResource) {
			resourcePath = ((ClassPathResource) resource).getPath();
			locationPath = StringUtils.cleanPath(((ClassPathResource) location).getPath());
		}
		else {
			resourcePath = resource.getURL().getPath();
			locationPath = StringUtils.cleanPath(location.getURL().getPath());
		}

		if (locationPath.equals(resourcePath)) {
			return true;
		}
		locationPath = (locationPath.endsWith("/") || locationPath.isEmpty() ? locationPath : locationPath + "/");
		String encodedLocationPath = locationPath.endsWith("/")
				? locationPath.substring(0, locationPath.length() - 1) + URLEncoder.encode("/", StandardCharsets.UTF_8)
				: locationPath;
		return ((resourcePath.startsWith(locationPath) || resourcePath.startsWith(encodedLocationPath))
				&& !isInvalidEncodedPath(resourcePath));
	}

}
