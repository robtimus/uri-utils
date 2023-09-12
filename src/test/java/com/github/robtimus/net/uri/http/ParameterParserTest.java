/*
 * ParameterParserTest.java
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import com.github.robtimus.net.uri.http.ParameterParser.DuplicateNameStrategy;

@SuppressWarnings("nls")
class ParameterParserTest {

    @Nested
    class ForCharSequence {

        @Nested
        class FullCharSequence {

            @Nested
            class ForString extends ParserTest {

                ForString() {
                    super(ParameterParser::parse);
                }
            }

            @Nested
            class ForNonString extends ParserTest {

                ForNonString() {
                    super(s -> ParameterParser.parse(new StringBuilder(s)));
                }
            }
        }

        @Nested
        class PartialCharSequence {

            @Test
            void testNegativeStart() {
                assertThrows(IllegalArgumentException.class, () -> ParameterParser.parse("foo", -1, 3));
            }

            @Test
            void testEndSmallerThanStart() {
                assertThrows(IllegalArgumentException.class, () -> ParameterParser.parse("foo", 1, 0));
            }

            @Test
            void testEndLargerThanLength() {
                assertThrows(IllegalArgumentException.class, () -> ParameterParser.parse("foo", 0, 4));
            }

            @Nested
            class ForString extends ParserTest {

                ForString() {
                    super(s -> ParameterParser.parse("xx" + s + "xx&=", 2, s.length() + 2));
                }
            }

            @Nested
            class ForNonString extends ParserTest {

                ForNonString() {
                    super(s -> ParameterParser.parse(new StringBuilder("xx").append(s).append("xx&="), 2, s.length() + 2));
                }
            }
        }
    }

    @Nested
    class ForReader {

        @Nested
        class Buffered extends ParserTest {

            Buffered() {
                super(s -> ParameterParser.parse(new BufferedReader(new StringReader(s))));
            }
        }

        @Nested
        class NonBuffered extends ParserTest {

            NonBuffered() {
                super(s -> ParameterParser.parse(new StringReader(s)));
            }
        }

        @Nested
        @SuppressWarnings("resource")
        class ReadError {

            private ParameterParser parser;
            private IOException error;

            @BeforeEach
            void initParser() throws IOException {
                String parameterString = "foo=bar";

                Reader reader = spy(new StringReader(parameterString));

                error = new IOException();
                doThrow(error).when(reader).read();
                doThrow(error).when(reader).read(any(char[].class));
                doThrow(error).when(reader).read(any(CharBuffer.class));
                doThrow(error).when(reader).read(any(), anyInt(), anyInt());

                parser = ParameterParser.parse(reader);
            }

            @Test
            void testToMap() {
                testReadError(parser::toMap);
            }

            @Test
            void testToMultiMap() {
                testReadError(parser::toMultiMap);
            }

            @Test
            void testForEach() {
                @SuppressWarnings("unchecked")
                BiConsumer<String, String> action = mock(BiConsumer.class);

                testReadError(() -> parser.forEach(action));
            }

            @Test
            void testStream() {
                BiConsumer<String, String> action = mockAction();

                ParameterStream stream = parser.stream();

                testReadError(() -> stream.forEach(action));
            }

            private void testReadError(Executable executable) {
                UncheckedIOException exception = assertThrows(UncheckedIOException.class, executable);

                assertSame(error, exception.getCause());
            }
        }
    }

    private abstract static class ParserTest {

        private final Function<String, ParameterParser> parserFactory;

        private ParserTest(Function<String, ParameterParser> parserFactory) {
            this.parserFactory = parserFactory;
        }

        @Nested
        class ToMap {

            @Test
            void testEmptyInput() {
                String parameterString = "";

                Map<String, String> parameterMap = parserFactory.apply(parameterString)
                        .withCharset(StandardCharsets.UTF_8)
                        .toMap();

                Map<String, String> expected = Collections.emptyMap();

                assertEquals(expected, parameterMap);
            }

            @Test
            void testNoDuplicates() {
                String parameterString = "foo=bar&name-only&&empty-value=&=empty-name&q=a&trailing-empty";

                Map<String, String> parameterMap = parserFactory.apply(parameterString)
                        .withCharset(StandardCharsets.UTF_8)
                        .toMap();

                Map<String, String> expected = Map.of(
                        "foo", "bar",
                        "name-only", "",
                        "empty-value", "",
                        "", "empty-name",
                        "q", "a",
                        "trailing-empty", "");

                assertEquals(expected, parameterMap);
            }

            @Test
            void testThrowOnDuplicates() {
                String parameterString = "foo=bar&name-only&&empty-value=&=&=empty-name&q=a&trailing-empty";

                ParameterParser parser = parserFactory.apply(parameterString)
                        .withCharset(StandardCharsets.UTF_8);

                IllegalStateException exception = assertThrows(IllegalStateException.class, parser::toMap);

                assertEquals(Messages.ParameterParser.duplicateParameterName("", "", "empty-name"), exception.getMessage());
            }

            @Test
            void testUseFirstOnDuplicates() {
                String parameterString = "foo=bar&name-only&&empty-value=&=&=empty-name&q=a&trailing-empty";

                Map<String, String> parameterMap = parserFactory.apply(parameterString)
                        .withCharset(StandardCharsets.UTF_8)
                        .toMap(DuplicateNameStrategy.USE_FIRST);

                Map<String, String> expected = Map.of(
                        "foo", "bar",
                        "name-only", "",
                        "empty-value", "",
                        "", "",
                        "q", "a",
                        "trailing-empty", "");

                assertEquals(expected, parameterMap);
            }

            @Test
            void testUseLastOnDuplicates() {
                String parameterString = "foo=bar&name-only&&empty-value=&=&=empty-name&q=a&trailing-empty";

                Map<String, String> parameterMap = parserFactory.apply(parameterString)
                        .withCharset(StandardCharsets.UTF_8)
                        .toMap(DuplicateNameStrategy.USE_LAST);

                Map<String, String> expected = Map.of(
                        "foo", "bar",
                        "name-only", "",
                        "empty-value", "",
                        "", "empty-name",
                        "q", "a",
                        "trailing-empty", "");

                assertEquals(expected, parameterMap);
            }

            @Test
            void testInvalidName() {
                String parameterString = "foo=bar&name-only&&empty-value=&=empty-name&%2=a&trailing-empty";

                ParameterParser parser = parserFactory.apply(parameterString)
                        .withCharset(StandardCharsets.UTF_8);

                IllegalStateException exception = assertThrows(IllegalStateException.class, parser::toMap);

                assertInstanceOf(IllegalArgumentException.class, exception.getCause());
            }

            @Test
            void testInvalidValue() {
                String parameterString = "foo=bar&name-only&&empty-value=&=empty-name&q=%2&trailing-empty";

                ParameterParser parser = parserFactory.apply(parameterString)
                        .withCharset(StandardCharsets.UTF_8);

                IllegalStateException exception = assertThrows(IllegalStateException.class, parser::toMap);

                assertInstanceOf(IllegalArgumentException.class, exception.getCause());
            }
        }

        @Nested
        class ToMultiMap {

            @Test
            void testEmptyInput() {
                String parameterString = "";

                Map<String, List<String>> parameterMap = parserFactory.apply(parameterString)
                        .withCharset(StandardCharsets.UTF_8)
                        .toMultiMap();

                Map<String, String> expected = Collections.emptyMap();

                assertEquals(expected, parameterMap);
            }

            @Test
            void testNonEmptyInput() {
                String parameterString = "foo=bar&name-only&&empty-value=&=&=empty-name&q=a&trailing-empty";

                Map<String, List<String>> parameterMap = parserFactory.apply(parameterString)
                        .withCharset(StandardCharsets.UTF_8)
                        .toMultiMap();

                Map<String, List<String>> expected = Map.of(
                        "foo", List.of("bar"),
                        "name-only", List.of(""),
                        "empty-value", List.of(""),
                        "", List.of("", "empty-name"),
                        "q", List.of("a"),
                        "trailing-empty", List.of(""));

                assertEquals(expected, parameterMap);
            }

            @Test
            void testInvalidName() {
                String parameterString = "foo=bar&name-only&&empty-value=&=&=empty-name&%2=a&trailing-empty";

                ParameterParser parser = parserFactory.apply(parameterString)
                        .withCharset(StandardCharsets.UTF_8);

                IllegalStateException exception = assertThrows(IllegalStateException.class, parser::toMultiMap);

                assertInstanceOf(IllegalArgumentException.class, exception.getCause());
            }

            @Test
            void testInvalidValue() {
                String parameterString = "foo=bar&name-only&&empty-value=&=&=empty-name&q=%2&trailing-empty";

                ParameterParser parser = parserFactory.apply(parameterString)
                        .withCharset(StandardCharsets.UTF_8);

                IllegalStateException exception = assertThrows(IllegalStateException.class, parser::toMultiMap);

                assertInstanceOf(IllegalArgumentException.class, exception.getCause());
            }
        }

        @Nested
        class ForEach {

            @Test
            void testNullAction() {
                String parameterString = "foo=bar&name-only&&empty-value=&=&=empty-name&q=a&trailing-empty";

                ParameterParser parser = parserFactory.apply(parameterString)
                        .withCharset(StandardCharsets.UTF_8);

                assertThrows(NullPointerException.class, () -> parser.forEach(null));
            }

            @Test
            void testSuccess() {
                String parameterString = "foo=bar&name-only&&empty-value=&=&=empty-name&q=a&trailing-empty";

                BiConsumer<String, String> action = mockAction();

                parserFactory.apply(parameterString)
                        .withCharset(StandardCharsets.UTF_8)
                        .forEach(action);

                verify(action).accept("foo", "bar");
                verify(action).accept("name-only", "");
                verify(action).accept("empty-value", "");
                verify(action).accept("", "");
                verify(action).accept("", "empty-name");
                verify(action).accept("q", "a");
                verify(action).accept("trailing-empty", "");
                verifyNoMoreInteractions(action);
            }

            @Test
            void testInvalidName() {
                String parameterString = "foo=bar&name-only&&empty-value=&=&=empty-name&%2=a&trailing-empty";

                BiConsumer<String, String> action = mockAction();

                ParameterParser parser = parserFactory.apply(parameterString)
                        .withCharset(StandardCharsets.UTF_8);

                IllegalStateException exception = assertThrows(IllegalStateException.class, () -> parser.forEach(action));

                assertInstanceOf(IllegalArgumentException.class, exception.getCause());

                verify(action).accept("foo", "bar");
                verify(action).accept("name-only", "");
                verify(action).accept("empty-value", "");
                verify(action).accept("", "");
                verify(action).accept("", "empty-name");
                verifyNoMoreInteractions(action);
            }

            @Test
            void testInvalidValue() {
                String parameterString = "foo=bar&name-only&&empty-value=&=&=empty-name&q=%2&trailing-empty";

                BiConsumer<String, String> action = mockAction();

                ParameterParser parser = parserFactory.apply(parameterString)
                        .withCharset(StandardCharsets.UTF_8);

                IllegalStateException exception = assertThrows(IllegalStateException.class, () -> parser.forEach(action));

                assertInstanceOf(IllegalArgumentException.class, exception.getCause());

                verify(action).accept("foo", "bar");
                verify(action).accept("name-only", "");
                verify(action).accept("empty-value", "");
                verify(action).accept("", "");
                verify(action).accept("", "empty-name");
                verifyNoMoreInteractions(action);
            }
        }

        @Nested
        class StreamTest {

            @Test
            void testSequential() {
                String parameterString = "foo=bar&name-only&&empty-value=&=&=empty-name&q=a&trailing-empty";

                BiConsumer<String, String> action = mockAction();

                parserFactory.apply(parameterString)
                        .withCharset(StandardCharsets.UTF_8)
                        .stream()
                        .forEach(action);

                verify(action).accept("foo", "bar");
                verify(action).accept("name-only", "");
                verify(action).accept("empty-value", "");
                verify(action).accept("", "");
                verify(action).accept("", "empty-name");
                verify(action).accept("q", "a");
                verify(action).accept("trailing-empty", "");
                verifyNoMoreInteractions(action);
            }

            @Test
            void testParallel() {
                String parameterString = "foo=bar&name-only&&empty-value=&=&=empty-name&q=a&trailing-empty";

                BiConsumer<String, String> action = mockAction();

                parserFactory.apply(parameterString)
                        .withCharset(StandardCharsets.UTF_8)
                        .stream()
                        .parallel()
                        .forEach(action);

                verify(action).accept("foo", "bar");
                verify(action).accept("name-only", "");
                verify(action).accept("empty-value", "");
                verify(action).accept("", "");
                verify(action).accept("", "empty-name");
                verify(action).accept("q", "a");
                verify(action).accept("trailing-empty", "");
                verifyNoMoreInteractions(action);
            }

            @Test
            void testInvalidName() {
                String parameterString = "foo=bar&name-only&&empty-value=&=&=empty-name&%2=a&trailing-empty";

                BiConsumer<String, String> action = mockAction();

                ParameterStream stream = parserFactory.apply(parameterString)
                        .withCharset(StandardCharsets.UTF_8)
                        .stream();

                IllegalStateException exception = assertThrows(IllegalStateException.class, () -> stream.forEach(action));

                assertInstanceOf(IllegalArgumentException.class, exception.getCause());

                verify(action).accept("foo", "bar");
                verify(action).accept("name-only", "");
                verify(action).accept("empty-value", "");
                verify(action).accept("", "");
                verify(action).accept("", "empty-name");
                verifyNoMoreInteractions(action);
            }

            @Test
            void testInvalidValue() {
                String parameterString = "foo=bar&name-only&&empty-value=&=&=empty-name&q=%2&trailing-empty";

                BiConsumer<String, String> action = mockAction();

                ParameterStream stream = parserFactory.apply(parameterString)
                        .withCharset(StandardCharsets.UTF_8)
                        .stream();

                IllegalStateException exception = assertThrows(IllegalStateException.class, () -> stream.forEach(action));

                assertInstanceOf(IllegalArgumentException.class, exception.getCause());

                verify(action).accept("foo", "bar");
                verify(action).accept("name-only", "");
                verify(action).accept("empty-value", "");
                verify(action).accept("", "");
                verify(action).accept("", "empty-name");
                verifyNoMoreInteractions(action);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static BiConsumer<String, String> mockAction() {
        return mock(BiConsumer.class);
    }
}
