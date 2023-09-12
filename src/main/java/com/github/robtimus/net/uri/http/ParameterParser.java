/*
 * ParameterParser.java
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.IntBinaryOperator;
import com.github.robtimus.net.uri.http.ParameterStream.Spliterator;

/**
 * A parser for query strings and form-post bodies.
 *
 * @author Rob Spoor
 */
public abstract class ParameterParser {

    Charset charset;

    private ParameterParser() {
        charset = StandardCharsets.UTF_8;
    }

    /**
     * Creates a new parameter parser for a {@link CharSequence}.
     *
     * @param cs The {@link CharSequence} containing the parameters to parse.
     * @return The created parser.
     * @throws NullPointerException If the given {@link CharSequence} is {@code null}.
     */
    public static ParameterParser parse(CharSequence cs) {
        Objects.requireNonNull(cs);

        return new CharSequenceParser(cs, 0, cs.length());
    }

    /**
     * Creates a new parameter parser for a {@link CharSequence}.
     *
     * @param cs The {@link CharSequence} containing the parameters to parse.
     * @param start The index in the {@link CharSequence} to start parsing, inclusive.
     * @param end The index in the {@link CharSequence} to end parsing, exclusive.
     * @return The created parser.
     * @throws NullPointerException If the given {@link CharSequence} is {@code null}.
     * @throws IllegalArgumentException If the given start is negative, or the given end is smaller than the given start,
     *                                  or the given end is larger than the given {@link CharSequence}'s length.
     */
    public static ParameterParser parse(CharSequence cs, int start, int end) {
        Objects.requireNonNull(cs);
        if (start < 0) {
            throw new IllegalArgumentException(start + " < 0"); //$NON-NLS-1$
        }
        if (end < start) {
            throw new IllegalArgumentException(end + " < " + start); //$NON-NLS-1$
        }
        if (end > cs.length()) {
            throw new IllegalArgumentException(end + " > " + cs.length()); //$NON-NLS-1$
        }

        return new CharSequenceParser(cs, start, end);
    }

    /**
     * Creates a new parameter parser for a {@link Reader}.
     * Any I/O error occurs when calling a terminal operator will be thrown as an {@link UncheckedIOException}.
     *
     * @param reader The {@link Reader} containing the parameters to parse.
     * @return The created parser.
     * @throws NullPointerException If the given {@link Reader} is {@code null}.
     */
    @SuppressWarnings("resource")
    public static ParameterParser parse(Reader reader) {
        Objects.requireNonNull(reader);
        return new ReaderParser(reader);
    }

    /**
     * Sets the charset to use. The default is UTF-8.
     * <p>
     * This is an intermediary operation. If called after a terminal operation its value will be ignored.
     *
     * @param charset The charset to use.
     * @return This parser.
     * @throws NullPointerException If the given charset is {@code null}.
     */
    public ParameterParser withCharset(Charset charset) {
        this.charset = Objects.requireNonNull(charset);
        return this;
    }

    /**
     * Returns the encountered parameters as a map, where the keys are the parameter names and the values are the parameter values.
     * <p>
     * This is a terminal operation. If called after any terminal operation the result will be an empty map.
     *
     * @return The encountered parameters as a map.
     * @throws IllegalStateException If a parse error occurred, or if multiple parameters with the same name are encountered.
     */
    public Map<String, String> toMap() {
        return toMap(DuplicateNameStrategy.THROW);
    }

    /**
     * Returns the encountered parameters as a map, where the keys are the parameter names and the values are the parameter values.
     * <p>
     * This is a terminal operation. If called after any terminal operation the result will be an empty map.
     *
     * @param duplicateNameStrategy The strategy to apply when multiple parameters with the same name are encountered.
     * @return The encountered parameters as a map.
     * @throws IllegalStateException If a parse error occurred.
     */
    public Map<String, String> toMap(DuplicateNameStrategy duplicateNameStrategy) {
        Objects.requireNonNull(duplicateNameStrategy);

        Map<String, String> map = new LinkedHashMap<>();
        forEach((name, value) -> duplicateNameStrategy.add(name, value, map));
        return map;
    }

    /**
     * A strategy for handling duplicate parameter names when calling {@link ParameterParser#toMap(DuplicateNameStrategy)}.
     *
     * @author Rob Spoor
     */
    public enum DuplicateNameStrategy {
        /** Indicates the first encountered value should be used. Any subsequent values will be ignored. */
        USE_FIRST {
            @Override
            void add(String name, String value, Map<String, String> map) {
                map.putIfAbsent(name, value);
            }
        },

        /** Indicates the last encountered value should be used. Any previous values will be discarded. */
        USE_LAST {
            @Override
            void add(String name, String value, Map<String, String> map) {
                map.put(name, value);
            }
        },

        /** Indicates an {@link IllegalStateException} should be thrown. */
        THROW {
            @Override
            void add(String name, String value, Map<String, String> map) {
                map.merge(name, value, (existingName, newName) -> {
                    throw new IllegalStateException(Messages.ParameterParser.duplicateParameterName(name, existingName, newName));
                });
            }
        },
        ;

        abstract void add(String name, String value, Map<String, String> map);
    }

    /**
     * Returns the encountered parameters as a map, where the keys are the parameter names and the values are the parameter values.
     * <p>
     * This is a terminal operation. If called after any terminal operation the result will be an empty map.
     *
     * @return The encountered parameters as a map.
     * @throws IllegalStateException If a parse error occurred.
     */
    public Map<String, List<String>> toMultiMap() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        forEach((name, value) -> map.computeIfAbsent(name, k -> new ArrayList<>()).add(value));
        return map;
    }

    /**
     * Performs an action for each encountered parameter.
     * <p>
     * This is a terminal operation. If called after any terminal operation the action will not be performed.
     *
     * @param action The action to perform.
     * @throws NullPointerException If the given action is {@code null}.
     * @throws IllegalStateException If a parse error occurred.
     */
    public abstract void forEach(BiConsumer<? super String, ? super String> action);

    /**
     * Returns the encountered parameters as a stream.
     * <p>
     * This is an intermediate operation, but any terminal operation on the stream will be considered a terminal operation on this parser.
     * If this method is called after any terminal operation the result will be an empty stream.
     * <p>
     * Calling a terminal operation on the stream will throw an {@link IllegalStateException} if a parse error occurrs.
     *
     * @return The encountered parameters as a stream.
     */
    public abstract ParameterStream stream();

    private static final class CharSequenceParser extends ParameterParser {

        private final CharSequence cs;
        private final int start;
        private final int end;
        private final IntBinaryOperator indexOf;

        private CharSequenceParser(CharSequence cs, int start, int end) {
            this.cs = cs;
            this.start = start;
            this.end = end;
            this.indexOf = indexOfOperator(cs);
        }

        @Override
        public void forEach(BiConsumer<? super String, ? super String> action) {
            int index = start;
            while (index < end) {
                int parameterEnd = parameterEnd(index);
                if (parameterEnd > index) {
                    int nameEnd = nameEnd(index, parameterEnd);

                    String name = name(index, nameEnd);
                    String value = value(nameEnd + 1, parameterEnd);

                    action.accept(name, value);
                }
                // either the parameters start with &, or contains &&
                // don't add a parameter

                index = parameterEnd + 1;
            }
        }

        @Override
        public ParameterStream stream() {
            return ParameterStream.of(new CharSequenceParameterSpliterator(start, end));
        }

        private IntBinaryOperator indexOfOperator(CharSequence cs) {
            if (cs instanceof String) {
                String s = (String) cs;
                return s::indexOf;
            }
            return this::indexOf;
        }

        private int indexOf(int ch, int fromIndex) {
            for (int i = fromIndex, len = end; i < len; i++) {
                if (cs.charAt(i) == ch) {
                    return i;
                }
            }
            return -1;
        }

        private int parameterEnd(int fromIndex) {
            return parameterEnd(fromIndex, end);
        }

        private int parameterEnd(int fromIndex, int toIndex) {
            int parameterEnd = indexOf.applyAsInt('&', fromIndex);
            if (parameterEnd == -1 || parameterEnd > toIndex) {
                // no & found in the CharSequence range
                return toIndex;
            }
            return parameterEnd;
        }

        private int nameEnd(int fromIndex, int toIndex) {
            int nameEnd = indexOf.applyAsInt('=', fromIndex);
            if (nameEnd == -1 || nameEnd > toIndex) {
                // no = found in the current name-value pair
                return toIndex;
            }
            return nameEnd;
        }

        private String name(int nameStart, int nameEnd) {
            try {
                return URLDecoder.decode(cs.subSequence(nameStart, nameEnd).toString(), charset);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(e);
            }
        }

        private String value(int valueStart, int valueEnd) {
            try {
                return valueStart >= valueEnd
                        ? "" //$NON-NLS-1$
                        : URLDecoder.decode(cs.subSequence(valueStart, valueEnd).toString(), charset);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(e);
            }
        }

        private final class CharSequenceParameterSpliterator implements ParameterStream.Spliterator {

            private final int end;
            private int index;

            private CharSequenceParameterSpliterator(int start, int end) {
                this.end = end;
                this.index = start;
            }

            @Override
            public boolean tryAdvance(BiConsumer<? super String, ? super String> action) {
                if (index >= end) {
                    return false;
                }
                int parameterEnd = parameterEnd(index, end);
                if (parameterEnd == index) {
                    // either the parameters start with &, or contains &&
                    // don't add a parameter
                    index = parameterEnd + 1;
                    return tryAdvance(action);
                }
                int nameEnd = nameEnd(index, parameterEnd);

                String name = name(index, nameEnd);
                String value = value(nameEnd + 1, parameterEnd);

                action.accept(name, value);

                index = parameterEnd + 1;
                return true;
            }

            @Override
            public ParameterStream.Spliterator trySplit() {
                int mid = (end + index) / 2;
                int parameterEnd = parameterEnd(mid, end);
                if (parameterEnd == end) {
                    return null;
                }
                ParameterStream.Spliterator split = new CharSequenceParameterSpliterator(index, parameterEnd);
                index = parameterEnd + 1;
                return split;
            }
        }
    }

    private static final class ReaderParser extends ParameterParser {

        private final Reader reader;
        private final StringBuilder name;
        private final StringBuilder value;

        private ReaderParser(Reader reader) {
            this.reader = reader instanceof BufferedReader ? reader : new BufferedReader(reader);
            this.name = new StringBuilder();
            this.value = new StringBuilder();
        }

        @Override
        public void forEach(BiConsumer<? super String, ? super String> action) {
            StringBuilder current = name;
            int c;
            while ((c = nextChar()) != -1) {
                if (c == '&') {
                    // end the current parameter
                    if (hasParameter(current)) {
                        action.accept(name(), value());
                    }
                    // else either the parameters start with &, or contains &&
                    // don't add a parameter

                    clear(name);
                    clear(value);
                    current = name;
                } else if (c == '=') {
                    // end the name, start the value
                    current = value;
                } else {
                    current.append((char) c);
                }
            }
            if (hasParameter(current)) {
                action.accept(name(), value());
            }
        }

        @Override
        public ParameterStream stream() {
            return ParameterStream.of(new ReaderParameterSpliterator());
        }

        private int nextChar() {
            try {
                return reader.read();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private String name() {
            try {
                return URLDecoder.decode(name.toString(), charset);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(e);
            }
        }

        private String value() {
            try {
                return URLDecoder.decode(value.toString(), charset);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(e);
            }
        }

        private boolean hasParameter(StringBuilder current) {
            // if name and value are empty but current == value, after the last parameter only a single = was encountered
            return name.length() > 0 || value.length() > 0 || current == value;
        }

        private void clear(StringBuilder buffer) {
            buffer.delete(0, buffer.length());
        }

        private class ReaderParameterSpliterator implements ParameterStream.Spliterator {

            @Override
            public boolean tryAdvance(BiConsumer<? super String, ? super String> action) {
                StringBuilder current = name;
                int c;
                while ((c = nextChar()) != -1) {
                    if (c == '&') {
                        // end the current parameter
                        if (endParameter(current, action)) {
                            return true;
                        }
                        // the current parameter was skipped
                        current = name;
                    } else if (c == '=') {
                        // end the name, start the value
                        current = value;
                    } else {
                        current.append((char) c);
                    }
                }
                // if name and value are empty but current == value, after the last parameter only a single = was encountered
                return endParameter(current, action);
            }

            private boolean endParameter(StringBuilder current, BiConsumer<? super String, ? super String> action) {
                boolean result = hasParameter(current);
                if (result) {
                    action.accept(name(), value());
                }
                // else either the parameters start with &, or contains &&
                // don't add a parameter

                clear(name);
                clear(value);
                return result;
            }

            @Override
            public Spliterator trySplit() {
                return null;
            }
        }
    }
}
