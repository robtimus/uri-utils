/*
 * ParameterBuilderTest.java
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingConsumer;

@SuppressWarnings("nls")
class ParameterBuilderTest {

    @Nested
    class NullChecks {

        @Test
        void testNullCharset() {
            testNullCheck(builder -> builder.withCharset(null));
        }

        @Nested
        class NullName {

            @Test
            void testWithStringValue() {
                testNullCheck(builder -> builder.withParameter(null, "value"));
            }

            @Test
            void testWithStringValues() {
                testNullCheck(builder -> builder.withParameters(null, "value1", "value2"));
            }

            @Test
            void testWithBooleanValue() {
                testNullCheck(builder -> builder.withParameter(null, true));
            }

            @Test
            void testWithBooleanValues() {
                testNullCheck(builder -> builder.withParameters(null, true, false));
            }

            @Test
            void testWithIntValue() {
                testNullCheck(builder -> builder.withParameter(null, 1));
            }

            @Test
            void testWithIntValues() {
                testNullCheck(builder -> builder.withParameters(null, 1, 2));
            }

            @Test
            void testWithLongValue() {
                testNullCheck(builder -> builder.withParameter(null, 1L));
            }

            @Test
            void testWithLongValues() {
                testNullCheck(builder -> builder.withParameters(null, 1L, 2L));
            }
        }

        @Test
        void testNullStringValue() {
            testNullCheck(builder -> builder.withParameter("name", null));
        }

        @Test
        void testNullStringValues() {
            testNullCheck(builder -> builder.withParameters("name", "value", null));
        }

        private void testNullCheck(ThrowingConsumer<ParameterBuilder> action) {
            ParameterBuilder builder = ParameterBuilder.create();

            assertThrows(NullPointerException.class, () -> action.accept(builder));
        }
    }

    @Nested
    class Count {

        @Test
        void testNonEmpty() {
            ParameterBuilder builder = addParameters(ParameterBuilder.create());

            assertEquals(expectedParameters().length, builder.count());
        }

        @Test
        void testEmpty() {
            ParameterBuilder builder = ParameterBuilder.create();

            assertEquals(0, builder.count());
        }
    }

    @Nested
    class HasParameters {

        @Test
        void testNonEmpty() {
            ParameterBuilder builder = addParameters(ParameterBuilder.create());

            assertTrue(builder.hasParameters());
        }

        @Test
        void testEmpty() {
            ParameterBuilder builder = ParameterBuilder.create();

            assertFalse(builder.hasParameters());
        }
    }

    @Nested
    class ToString {

        @Test
        void testNonEmpty() {
            String string = addParameters(ParameterBuilder.create())
                    .toString();

            String expected = String.join("&", expectedParameters());

            assertEquals(expected, string);
        }

        @Test
        void testEmpty() {
            String string = ParameterBuilder.create()
                    .toString();

            assertEquals("", string);
        }
    }

    @Nested
    class AppendToStringBuilder {

        @Test
        void testNonEmpty() {
            StringBuilder sb = new StringBuilder();

            addParameters(ParameterBuilder.create())
                    .appendTo(sb);

            String expected = String.join("&", expectedParameters());

            assertEquals(expected, sb.toString());
        }

        @Test
        void testEmpty() {
            StringBuilder sb = new StringBuilder();

            ParameterBuilder.create()
                    .appendTo(sb);

            assertEquals("", sb.toString());
        }
    }

    @Nested
    class AppendToAppendable {

        @Test
        void testNonEmpty() throws IOException {
            StringWriter writer = new StringWriter();

            addParameters(ParameterBuilder.create())
                    .appendTo(writer);

            String expected = String.join("&", expectedParameters());

            assertEquals(expected, writer.toString());
        }

        @Test
        void testEmpty() throws IOException {
            StringWriter writer = new StringWriter();

            ParameterBuilder.create()
                    .appendTo(writer);

            assertEquals("", writer.toString());
        }

        @Test
        void testError() {
            StringWriter writer = spy(new StringWriter());

            IOException error = new IOException();
            doThrow(error).when(writer).append(anyChar());
            doThrow(error).when(writer).append(any());

            ParameterBuilder builder = addParameters(ParameterBuilder.create());

            IOException exception = assertThrows(IOException.class, () -> builder.appendTo(writer));

            assertSame(error, exception);
        }
    }

    @Nested
    class AppendToUnchecked {

        @Test
        void testNonEmpty() {
            StringWriter writer = new StringWriter();

            addParameters(ParameterBuilder.create())
                    .appendToUnchecked(writer);

            String expected = String.join("&", expectedParameters());

            assertEquals(expected, writer.toString());
        }

        @Test
        void testEmpty() {
            StringWriter writer = new StringWriter();

            ParameterBuilder.create()
                    .appendToUnchecked(writer);

            assertEquals("", writer.toString());
        }

        @Test
        void testError() {
            StringWriter writer = spy(new StringWriter());

            IOException error = new IOException();
            doThrow(error).when(writer).append(anyChar());
            doThrow(error).when(writer).append(any());

            ParameterBuilder builder = addParameters(ParameterBuilder.create());

            UncheckedIOException exception = assertThrows(UncheckedIOException.class, () -> builder.appendToUnchecked(writer));

            assertSame(error, exception.getCause());
        }
    }

    private ParameterBuilder addParameters(ParameterBuilder builder) {
        return builder
                .withCharset(StandardCharsets.UTF_8)
                .withParameter("string", "s1")
                .withParameter("boolean", true)
                .withParameter("byte", (byte) 1)
                .withParameter("short", (short) 10)
                .withParameter("int", 100)
                .withParameter("long", 1000L)
                .withParameter("encoded parameter", "with + and %")
                .withParameters("string", "s2", "s3")
                .withParameters("boolean", false, true)
                .withParameters("byte", (byte) 2, (byte) 3)
                .withParameters("short", (short) 20, (short) 30)
                .withParameters("int", 200, 300)
                .withParameters("long", 2000L, 3000L)
                ;
    }

    private String[] expectedParameters() {
        return new String[] {
                "string=s1",
                "string=s2",
                "string=s3",
                "boolean=true",
                "boolean=false",
                "boolean=true",
                "byte=1",
                "byte=2",
                "byte=3",
                "short=10",
                "short=20",
                "short=30",
                "int=100",
                "int=200",
                "int=300",
                "long=1000",
                "long=2000",
                "long=3000",
                "encoded+parameter=with+%2B+and+%25",
        };
    }
}
