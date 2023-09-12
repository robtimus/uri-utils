/*
 * ParameterBuilder.java
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * A class for creating query strings and form-post bodies.
 *
 * @author Rob Spoor
 */
public final class ParameterBuilder {

    private Charset charset;
    private final Map<String, List<Object>> parameters;
    private long count;

    private ParameterBuilder() {
        charset = StandardCharsets.UTF_8;
        parameters = new LinkedHashMap<>();
        count = 0;
    }

    /**
     * Creates a new parameter builder.
     *
     * @return The created parameter builder.
     */
    public static ParameterBuilder create() {
        return new ParameterBuilder();
    }

    /**
     * Sets the charset to use. The default is UTF-8.
     *
     * @param charset The charset to use.
     * @return This builder.
     * @throws NullPointerException If the given charset is {@code null}.
     */
    public ParameterBuilder withCharset(Charset charset) {
        this.charset = Objects.requireNonNull(charset);
        return this;
    }

    /**
     * Adds a single parameter.
     *
     * @param name The parameter name.
     * @param value The parameter value. This will be converted to string using {@link Object#toString()}.
     * @return This builder.
     * @throws NullPointerException If the given name or value is {@code null}.
     */
    public ParameterBuilder withParameter(String name, Object value) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(value);
        addParameter(name, value);
        return this;
    }

    /**
     * Adds several parameters.
     *
     * @param name The parameter name.
     * @param values The parameter values. These will be converted to string using {@link Object#toString()}.
     * @return This builder.
     * @throws NullPointerException If the given name or any of the given values is {@code null}.
     */
    public ParameterBuilder withParameters(String name, Object... values) {
        return withParameters(name, Arrays.asList(values));
    }

    /**
     * Adds several parameters.
     *
     * @param name The parameter name.
     * @param values The parameter values. These will be converted to string using {@link Object#toString()}.
     * @return This builder.
     * @throws NullPointerException If the given name or any of the given values is {@code null}.
     */
    public ParameterBuilder withParameters(String name, Iterable<?> values) {
        Objects.requireNonNull(name);
        for (Object value : values) {
            Objects.requireNonNull(value);
            addParameter(name, value);
        }
        return this;
    }

    private void addParameter(String name, Object value) {
        parameters.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        count++;
    }

    /**
     * Returns the number of parameters that were added.
     *
     * @return The number of parameters that were added.
     */
    public long count() {
        return count;
    }

    /**
     * Returns whether or not any parameters were added.
     *
     * @return {@code true} if any parameters were added, or {@code false} otherwise.
     */
    public boolean hasParameters() {
        return count > 0;
    }

    /**
     * Returns all parameters as a single string.
     *
     * @return All parameters as a single string.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        appendTo(sb);
        return sb.toString();
    }

    /**
     * Appends all parameters to a {@link StringBuilder}.
     *
     * @param sb The {@link StringBuilder} to append all
     */
    public void appendTo(StringBuilder sb) {
        // IOExceptions should not occur
        appendTo(sb, IllegalStateException::new);
    }

    /**
     * Appends all parameters to an {@link Appendable}.
     *
     * @param appendable The {@link Appendable} to append all
     * @throws IOException If an I/O error occurs.
     */
    public void appendTo(Appendable appendable) throws IOException {
        appendTo(appendable, Function.identity());
    }

    /**
     * Appends all parameters to an {@link Appendable}.
     *
     * @param appendable The {@link Appendable} to append all
     * @throws UncheckedIOException If an I/O error occurs.
     */
    public void appendToUnchecked(Appendable appendable) {
        appendTo(appendable, UncheckedIOException::new);
    }

    private <X extends Exception> void appendTo(Appendable appendable, Function<IOException, X> errorMapper) throws X {
        Objects.requireNonNull(appendable);
        if (parameters.isEmpty()) {
            return;
        }
        try {
            boolean first = true;
            for (Map.Entry<String, List<Object>> entry : parameters.entrySet()) {
                String name = entry.getKey();
                List<?> values = entry.getValue();
                for (Object value : values) {
                    if (first) {
                        first = false;
                    } else {
                        appendable.append('&');
                    }
                    appendable.append(encoded(name)).append('=').append(encoded(value));
                }
            }
        } catch (IOException e) {
            throw errorMapper.apply(e);
        }
    }

    private String encoded(Object value) {
        if (value instanceof Boolean || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            // These types will never contain any characters that need to be encoded, so include as-is
            // Other types, including Character, Float and Double, can contain characters that need to be encoded
            return value.toString();
        }
        return URLEncoder.encode(value.toString(), charset);
    }
}
