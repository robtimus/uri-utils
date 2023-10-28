/*
 * HttpUriBuilder.java
 * Copyright 2023 Rob Spoor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.robtimus.net.uri.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class for creating HTTP and HTTPS URIs.
 *
 * @author Rob Spoor
 */
public final class HttpUriBuilder {

    private static final String SCHEME_HTTP = "http"; //$NON-NLS-1$
    private static final String SCHEME_HTTPS = "https"; //$NON-NLS-1$

    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;
    private static final int DEFAULT_PORT = -1;

    private static final String CURRENT_DIR = "."; //$NON-NLS-1$
    private static final String PARENT_DIR = ".."; //$NON-NLS-1$

    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{([^}]*)\\}"); //$NON-NLS-1$

    private Charset charset = StandardCharsets.UTF_8;

    private final String host;
    private String scheme;
    private int port;
    private String userInfo;
    private final List<String> path;
    private final ParameterBuilder queryParams;
    private String fragment;

    private HttpUriBuilder(String host) {
        this.host = host;
        this.scheme = SCHEME_HTTPS;
        this.port = -1;
        this.userInfo = null;
        this.path = new ArrayList<>();
        this.queryParams = ParameterBuilder.create();
        this.fragment = null;
    }

    /**
     * Creates a new HTTP URI builder for a specific host.
     *
     * @param host The host or IP address to create an new HTTP URI builder for.
     * @return The created builder.
     */
    public static HttpUriBuilder forHost(String host) {
        Objects.requireNonNull(host);
        return new HttpUriBuilder(host);
    }

    /**
     * Sets the charset to use. The default is UTF-8.
     *
     * @param charset The charset to use.
     * @return This parser.
     * @throws NullPointerException If the given charset is {@code null}.
     */
    public HttpUriBuilder charset(Charset charset) {
        this.charset = Objects.requireNonNull(charset);
        return this;
    }

    /**
     * Sets the scheme. The default is {@code https}.
     *
     * @param scheme The scheme to set.
     * @return This builder.
     * @throws IllegalArgumentException If the given scheme is not {@code http} or {@code https}.
     */
    public HttpUriBuilder scheme(String scheme) {
        if (SCHEME_HTTP.equalsIgnoreCase(scheme) || SCHEME_HTTPS.equalsIgnoreCase(scheme)) {
            this.scheme = scheme.toLowerCase();
            return this;
        }
        throw new IllegalArgumentException(Messages.HttpUriBuilder.invalidScheme(scheme));
    }

    /**
     * Sets the port. The default is -1, which means the URL should not define a specific port.
     *
     * @param port The port to set. Use -1 to not include a specific port.
     * @return This builder.
     * @throws IllegalArgumentException If the port is less than 1 or larger than 65535, and is not -1.
     */
    public HttpUriBuilder port(int port) {
        if (port < MIN_PORT && port != DEFAULT_PORT) {
            throw new IllegalArgumentException(port + " < " + MIN_PORT); //$NON-NLS-1$
        }
        if (port > MAX_PORT) {
            throw new IllegalArgumentException(port + " > " + MAX_PORT); //$NON-NLS-1$
        }
        this.port = port;
        return this;
    }

    /**
     * Sets the user info. The default is no user info.
     *
     * @param userInfo The optional user info to set.
     * @return This builder.
     */
    public HttpUriBuilder userInfo(String userInfo) {
        this.userInfo = userInfo;
        return this;
    }

    /**
     * Sets the user info. The default is no user info.
     * <ul>
     *   <li>If the user name and password are both {@code null}, no user info will be set.</li>
     *   <li>If the user name is not {@code null} and the password is {@code null}, the user info will be set to only the user name.</li>
     *   <li>If the user name and the password are both not {@code null}, the user info will be {@code <userName>:<password>}.</li>
     * </ul>
     *
     * @param userName The optional user name to set.
     * @param password The optional password to set. If not {@code null} the user name must be set as well.
     * @return This builder.
     */
    public HttpUriBuilder userInfo(String userName, String password) {
        if (password == null) {
            // If userName is null then so userInfo will be
            this.userInfo = userName;
        } else {
            Objects.requireNonNull(userName);
            this.userInfo = userName + ":" + password; //$NON-NLS-1$
        }
        return this;
    }

    /**
     * Adds one or more path segments. The given path can contain {@code /} to separate it into multiple path segments.
     *
     * @param path The path to add.
     * @return This builder.
     * @throws NullPointerException If the given path is {@code null}.
     * @throws IllegalArgumentException If the given path contains any path segment that is {@code .} or {@code ..}.
     */
    public HttpUriBuilder path(String path) {
        addPath(path);
        removeUnnecessaryEmptyPathSegments();
        return this;
    }

    /**
     * Adds one or more path segments. The given paths can contain {@code /} to separate them into multiple path segments.
     *
     * @param path The path to add.
     * @param additionalPaths Optional additional paths to add.
     * @return This builder.
     * @throws NullPointerException If any of the given paths is {@code null}.
     * @throws IllegalArgumentException If any of the given paths contains any path segment that is {@code .} or {@code ..}.
     */
    public HttpUriBuilder path(String path, String... additionalPaths) {
        addPath(path);
        for (String additionalPath : additionalPaths) {
            addPath(additionalPath);
        }
        removeUnnecessaryEmptyPathSegments();
        return this;
    }

    private void addPath(String path) {
        int segmentStart = skipSlashes(path, 0);
        int segmentEnd = path.indexOf('/', segmentStart);
        while (segmentEnd != -1) {
            addPathSegment(path.substring(segmentStart, segmentEnd));
            segmentStart = segmentEnd + 1;
            segmentEnd = path.indexOf('/', segmentStart);
        }
        addPathSegment(path.substring(segmentStart));
    }

    private int skipSlashes(String path, int from) {
        for (int i = from; i < path.length(); i++) {
            if (path.charAt(i) != '/') {
                return i;
            }
        }
        return path.length();
    }

    /**
     * Adds a single path segment.
     * Unlike {@link #path(String)}, the given segment will not be split on {@code /}.
     * Instead, any occurrence of {@code /} will be encoded.
     *
     * @param pathSegment The path segment to add.
     * @return This builder.
     * @throws NullPointerException If the given path segment is {@code null}.
     * @throws IllegalArgumentException If the given path segment is {@code .} or {@code ..}.
     */
    public HttpUriBuilder pathSegment(String pathSegment) {
        addPathSegment(pathSegment);
        removeUnnecessaryEmptyPathSegments();
        return this;
    }

    /**
     * Adds one or more path segment.
     * Unlike {@link #path(String, String...)}, the given segment will not be split on {@code /}.
     * Instead, any occurrence of {@code /} will be encoded.
     *
     * @param pathSegment The path segment to add.
     * @param additionalPathSegments Optional additional path segments to add.
     * @return This builder.
     * @throws NullPointerException If any of the given path segments is {@code null}.
     * @throws IllegalArgumentException If any of the given path segments is {@code .} or {@code ..}.
     */
    public HttpUriBuilder pathSegment(String pathSegment, String... additionalPathSegments) {
        addPathSegment(pathSegment);
        for (String additionalPathSegment : additionalPathSegments) {
            addPathSegment(additionalPathSegment);
        }
        removeUnnecessaryEmptyPathSegments();
        return this;
    }

    private void addPathSegment(String pathSegment) {
        Objects.requireNonNull(pathSegment);
        if (CURRENT_DIR.equals(pathSegment) || PARENT_DIR.equals(pathSegment)) {
            throw new IllegalArgumentException(Messages.HttpUriBuilder.invalidPathSegment());
        }
        path.add(pathSegment);
    }

    private void removeUnnecessaryEmptyPathSegments() {
        // A final empty path segment adds value, to add a final / to the URI
        // Any others don't
        path.subList(0, path.size() - 1).removeIf(String::isEmpty);
    }

    /**
     * Removes all previously added path segments.
     *
     * @return This builder.
     */
    public HttpUriBuilder clearPath() {
        path.clear();
        return this;
    }

    /**
     * Adds a single query parameter.
     *
     * @param name The query parameter name.
     * @param value The query parameter value. This will be converted to string using {@link Object#toString()}.
     * @return This builder.
     * @throws NullPointerException If the given name or value is {@code null}.
     */
    public HttpUriBuilder queryParameter(String name, Object value) {
        queryParams.withParameter(name, value);
        return this;
    }

    /**
     * Adds several query parameters.
     *
     * @param name The query parameter name.
     * @param values The query parameter values. These will be converted to string using {@link Object#toString()}.
     * @return This builder.
     * @throws NullPointerException If the given name or any of the given values is {@code null}.
     */
    public HttpUriBuilder queryParameters(String name, Object... values) {
        return queryParameters(name, Arrays.asList(values));
    }

    /**
     * Adds several query parameters.
     *
     * @param name The query parameter name.
     * @param values The query parameter values. These will be converted to string using {@link Object#toString()}.
     * @return This builder.
     * @throws NullPointerException If the given name or any of the given values is {@code null}.
     */
    public HttpUriBuilder queryParameters(String name, Iterable<?> values) {
        queryParams.withParameters(name, values);
        return this;
    }

    /**
     * Sets the fragment. The default is no fragment.
     *
     * @param fragment The optional fragment.
     * @return This builder.
     */
    public HttpUriBuilder fragment(String fragment) {
        this.fragment = fragment;
        return this;
    }

    /**
     * Returns a a URI with all components added to this builder.
     * All path segments will be encoded.
     *
     * @return A URI with all components added to this builder.
     */
    public URI toURI() {
        URI uri = uriWithoutPathOrQueryParams();

        StringBuilder result = new StringBuilder();
        addSchemeAndAuthority(result, uri);
        addPath(result);
        addQueryParams(result);
        addFragment(result, uri);

        return URI.create(result.toString());
    }

    /**
     * Returns a URI with all components added to this builder.
     * Any occurrence of <code>{<em>param</em>}</code> will be replaced by calling the given replacement operator with argument <em>param</em>.
     * Afterwards, all path segments will be encoded.
     *
     * @param pathParamReplacer A function for replacing path parameters.
     * @return A with all components added to this builder.
     * @throws NullPointerException If the given function is {@code null}.
     */
    public URI toURI(UnaryOperator<String> pathParamReplacer) {
        URI uri = uriWithoutPathOrQueryParams();

        StringBuilder result = new StringBuilder();
        addSchemeAndAuthority(result, uri);
        addPath(result, pathParamReplacer);
        addQueryParams(result);
        addFragment(result, uri);

        return URI.create(result.toString());
    }

    private URI uriWithoutPathOrQueryParams() {
        // Using the URI constructor that takes a scheme, userInfo, host, port, path, query and fragment will not properly encode the path and query
        // parameters when the encoding is left to URI
        // If the encoding is not left to URI, some characters will be encoded twice
        // Therefore, create a URI without part and query parameters, than add those manually
        try {
            return new URI(scheme, userInfo(), host, port, null, null, fragment);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private String userInfo() {
        return userInfo != null && userInfo.isEmpty() ? null : userInfo;
    }

    private void addSchemeAndAuthority(StringBuilder dest, URI uri) {
        dest.append(scheme);
        dest.append("://"); //$NON-NLS-1$
        dest.append(uri.getRawAuthority());
    }

    private void addPath(StringBuilder dest) {
        if (path.isEmpty()) {
            dest.append('/');
            return;
        }
        for (String pathSegment : path) {
            dest.append('/').append(URLEncoder.encode(pathSegment, charset));
        }
    }

    private void addPath(StringBuilder dest, UnaryOperator<String> pathParamReplacer) {
        if (path.isEmpty()) {
            dest.append('/');
            return;
        }
        for (String pathSegment : path) {
            dest.append('/').append(URLEncoder.encode(replacePathParams(pathSegment, pathParamReplacer), charset));
        }
    }

    private String replacePathParams(String pathSegment, UnaryOperator<String> pathParamReplacer) {
        return PATH_PARAM_PATTERN.matcher(pathSegment).replaceAll(result -> Matcher.quoteReplacement(pathParamReplacer.apply(result.group(1))));
    }

    private void addQueryParams(StringBuilder dest) {
        if (queryParams.hasParameters()) {
            dest.append('?');
            queryParams.appendTo(dest);
        }
    }

    private void addFragment(StringBuilder dest, URI uri) {
        if (fragment != null) {
            dest.append('#').append(uri.getRawFragment());
        }
    }
}
