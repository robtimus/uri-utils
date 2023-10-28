/*
 * ParameterStreamTest.java
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("nls")
class ParameterStreamTest {

    @Test
    void testFilter() {
        BiPredicate<String, String> predicate = mockPredicate();
        when(predicate.test(anyString(), anyString())).thenReturn(true);
        when(predicate.test(eq(""), anyString())).thenReturn(false);
        when(predicate.test(anyString(), eq(""))).thenReturn(false);

        Stream<Parameter> stream = createStream()
                .filter(predicate)
                .map(Parameter::new);

        verify(predicate, never()).test(anyString(), anyString());

        List<Parameter> expected = parameters().stream()
                .filter(p -> !p.name.isEmpty() && !p.value.isEmpty())
                .collect(Collectors.toList());

        List<Parameter> parameters = stream.collect(Collectors.toList());

        assertEquals(expected, parameters);

        parameters().stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .forEach(entry -> verify(predicate, times(entry.getValue().intValue())).test(entry.getKey().name, entry.getKey().value));
        verifyNoMoreInteractions(predicate);
    }

    @Test
    void testMap() {
        BiFunction<String, String, String> mapper = mockFunction();
        when(mapper.apply(anyString(), anyString())).thenAnswer(i -> i.getArgument(0) + "=" + i.getArgument(1));

        Stream<String> stream = createStream()
                .map(mapper);

        verify(mapper, never()).apply(anyString(), anyString());

        List<String> expected = parameters().stream()
                .map(p -> p.name + "=" + p.value)
                .collect(Collectors.toList());

        List<String> strings = stream.collect(Collectors.toList());

        assertEquals(expected, strings);

        parameters().stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .forEach(entry -> verify(mapper, times(entry.getValue().intValue())).apply(entry.getKey().name, entry.getKey().value));
        verifyNoMoreInteractions(mapper);
    }

    @Nested
    class MapName {

        @Test
        void testWithSharedParameters() {
            UnaryOperator<String> mapper = mockUnaryOperator();
            when(mapper.apply(anyString())).thenAnswer(i -> i.getArgument(0, String.class).toUpperCase());

            Stream<Parameter> stream = createStream()
                    .mapName(mapper)
                    .map(Parameter::new);

            verify(mapper, never()).apply(anyString());

            List<Parameter> expected = parameters()
                    .stream()
                    .map(p -> new Parameter(p.name.toUpperCase(), p.value))
                    .collect(Collectors.toList());

            List<Parameter> parameters = stream.collect(Collectors.toList());

            assertEquals(expected, parameters);

            parameters().stream()
                    .map(Parameter::name)
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                    .entrySet()
                    .stream()
                    .forEach(entry -> verify(mapper, times(entry.getValue().intValue())).apply(entry.getKey()));
            verifyNoMoreInteractions(mapper);
        }

        @Test
        void testWithNonSharedParameters() {
            UnaryOperator<String> mapper = mockUnaryOperator();
            when(mapper.apply(anyString())).thenAnswer(i -> i.getArgument(0, String.class).toUpperCase());

            Stream<Parameter> stream = createStream()
                    .distinct()
                    .mapName(mapper)
                    .map(Parameter::new);

            verify(mapper, never()).apply(anyString());

            List<Parameter> expected = parameters()
                    .stream()
                    .distinct()
                    .map(p -> new Parameter(p.name.toUpperCase(), p.value))
                    .collect(Collectors.toList());

            List<Parameter> parameters = stream.collect(Collectors.toList());

            assertEquals(expected, parameters);

            parameters().stream()
                    .distinct()
                    .map(Parameter::name)
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                    .entrySet()
                    .stream()
                    .forEach(entry -> verify(mapper, times(entry.getValue().intValue())).apply(entry.getKey()));
            verifyNoMoreInteractions(mapper);
        }

        @Test
        void testWithNoChanges() {
            UnaryOperator<String> mapper = mockUnaryOperator();
            when(mapper.apply(anyString())).thenAnswer(i -> i.getArgument(0));

            Stream<Parameter> stream = createStream()
                    .mapName(mapper)
                    .map(Parameter::new);

            verify(mapper, never()).apply(anyString());

            List<Parameter> expected = parameters();

            List<Parameter> parameters = stream.collect(Collectors.toList());

            assertEquals(expected, parameters);

            parameters().stream()
                    .map(Parameter::name)
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                    .entrySet()
                    .stream()
                    .forEach(entry -> verify(mapper, times(entry.getValue().intValue())).apply(entry.getKey()));
            verifyNoMoreInteractions(mapper);
        }
    }

    @Nested
    class MapValue {

        @Test
        void testWithSharedParameters() {
            UnaryOperator<String> mapper = mockUnaryOperator();
            when(mapper.apply(anyString())).thenAnswer(i -> i.getArgument(0, String.class).toUpperCase());

            Stream<Parameter> stream = createStream()
                    .mapValue(mapper)
                    .map(Parameter::new);

            verify(mapper, never()).apply(anyString());

            List<Parameter> expected = parameters()
                    .stream()
                    .map(p -> new Parameter(p.name, p.value.toUpperCase()))
                    .collect(Collectors.toList());

            List<Parameter> parameters = stream.collect(Collectors.toList());

            assertEquals(expected, parameters);

            parameters().stream()
                    .map(Parameter::value)
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                    .entrySet()
                    .stream()
                    .forEach(entry -> verify(mapper, times(entry.getValue().intValue())).apply(entry.getKey()));
            verifyNoMoreInteractions(mapper);
        }

        @Test
        void testWithNonSharedParameters() {
            UnaryOperator<String> mapper = mockUnaryOperator();
            when(mapper.apply(anyString())).thenAnswer(i -> i.getArgument(0, String.class).toUpperCase());

            Stream<Parameter> stream = createStream()
                    .distinct()
                    .mapValue(mapper)
                    .map(Parameter::new);

            verify(mapper, never()).apply(anyString());

            List<Parameter> expected = parameters()
                    .stream()
                    .distinct()
                    .map(p -> new Parameter(p.name, p.value.toUpperCase()))
                    .collect(Collectors.toList());

            List<Parameter> parameters = stream.collect(Collectors.toList());

            assertEquals(expected, parameters);

            parameters().stream()
                    .distinct()
                    .map(Parameter::value)
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                    .entrySet()
                    .stream()
                    .forEach(entry -> verify(mapper, times(entry.getValue().intValue())).apply(entry.getKey()));
            verifyNoMoreInteractions(mapper);
        }

        @Test
        void testWithNoChanges() {
            UnaryOperator<String> mapper = mockUnaryOperator();
            when(mapper.apply(anyString())).thenAnswer(i -> i.getArgument(0));

            Stream<Parameter> stream = createStream()
                    .mapValue(mapper)
                    .map(Parameter::new);

            verify(mapper, never()).apply(anyString());

            List<Parameter> expected = parameters();

            List<Parameter> parameters = stream.collect(Collectors.toList());

            assertEquals(expected, parameters);

            parameters().stream()
                    .map(Parameter::value)
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                    .entrySet()
                    .stream()
                    .forEach(entry -> verify(mapper, times(entry.getValue().intValue())).apply(entry.getKey()));
            verifyNoMoreInteractions(mapper);
        }
    }

    @Test
    void testDistinct() {
        Stream<Parameter> stream = createStream()
                .distinct()
                .map(Parameter::new);

        List<Parameter> expected = parameters().stream()
                .distinct()
                .collect(Collectors.toList());

        List<Parameter> parameters = stream.collect(Collectors.toList());

        assertEquals(expected, parameters);
    }

    @Nested
    class Sorted {

        @Test
        void testNaturalOrder() {
            Stream<Parameter> stream = createStream()
                    .sorted()
                    .map(Parameter::new);

            List<Parameter> expected = parameters().stream()
                    .sorted(Comparator.comparing(Parameter::name).thenComparing(Parameter::value))
                    .collect(Collectors.toList());

            List<Parameter> parameters = stream.collect(Collectors.toList());

            assertEquals(expected, parameters);
        }

        @Test
        void testSortOnKeys() {
            Stream<Parameter> stream = createStream()
                    .sorted(Comparator.reverseOrder())
                    .map(Parameter::new);

            List<Parameter> expected = parameters().stream()
                    .sorted(Comparator.comparing(Parameter::name, Comparator.reverseOrder()))
                    .collect(Collectors.toList());

            List<Parameter> parameters = stream.collect(Collectors.toList());

            assertEquals(expected, parameters);
        }

        @Test
        void testSortOnKeysAndValues() {
            Stream<Parameter> stream = createStream()
                    .sorted(Comparator.reverseOrder(), Comparator.reverseOrder())
                    .map(Parameter::new);

            List<Parameter> expected = parameters().stream()
                    .sorted(Comparator.comparing(Parameter::name, Comparator.reverseOrder())
                            .thenComparing(Parameter::value, Comparator.reverseOrder()))
                    .collect(Collectors.toList());

            List<Parameter> parameters = stream.collect(Collectors.toList());

            assertEquals(expected, parameters);
        }
    }

    @Test
    void testPeek() {
        BiConsumer<String, String> action = mockConsumer();

        Stream<Parameter> stream = createStream()
                .peek(action)
                .map(Parameter::new);

        verify(action, never()).accept(anyString(), anyString());

        stream.count();

        parameters().stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .forEach(entry -> verify(action, times(entry.getValue().intValue())).accept(entry.getKey().name, entry.getKey().value));
        verifyNoMoreInteractions(action);
    }

    @Test
    void testLimit() {
        Stream<Parameter> stream = createStream()
                .limit(3)
                .map(Parameter::new);

        List<Parameter> expected = parameters().stream()
                .limit(3)
                .collect(Collectors.toList());

        List<Parameter> parameters = stream.collect(Collectors.toList());

        assertEquals(expected, parameters);
    }

    @Test
    void testSkip() {
        Stream<Parameter> stream = createStream()
                .skip(3)
                .map(Parameter::new);

        List<Parameter> expected = parameters().stream()
                .skip(3)
                .collect(Collectors.toList());

        List<Parameter> parameters = stream.collect(Collectors.toList());

        assertEquals(expected, parameters);
    }

    @Test
    void testForEach() {
        BiConsumer<String, String> action = mockConsumer();

        ParameterStream stream = createStream()
                .parallel();

        verify(action, never()).accept(anyString(), anyString());

        stream.forEach(action);

        parameters().stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .forEach(entry -> verify(action, times(entry.getValue().intValue())).accept(entry.getKey().name, entry.getKey().value));
        verifyNoMoreInteractions(action);
    }

    @Test
    void testForEachOrdered() {
        List<Parameter> parameters = new ArrayList<>();

        BiConsumer<String, String> action = mockConsumer();
        doAnswer(i -> parameters.add(new Parameter(i.getArgument(0), i.getArgument(1)))).when(action).accept(anyString(), anyString());

        ParameterStream stream = createStream()
                .parallel();

        verify(action, never()).accept(anyString(), anyString());

        stream.forEachOrdered(action);

        parameters().stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .forEach(entry -> verify(action, times(entry.getValue().intValue())).accept(entry.getKey().name, entry.getKey().value));
        verifyNoMoreInteractions(action);

        List<Parameter> expected = parameters();

        assertEquals(expected, parameters);
    }

    @Test
    void testCount() {
        long count = createStream().count();

        long expected = parameters().size();

        assertEquals(expected, count);
    }

    @Nested
    class AllMatch {

        @ParameterizedTest(name = "name = {0}")
        @ValueSource(strings = { "foo", "q", "bar" })
        @EmptySource
        void testSomeMatch(String nameToMatch) {
            testAllMatch((name, value) -> nameToMatch.equals(name));
        }

        @Test
        void testAllMatch() {
            testAllMatch((name, value) -> true);
        }

        @Test
        void testNoneMatch() {
            testAllMatch((name, value) -> false);
        }

        private void testAllMatch(BiPredicate<String, String> predicate) {
            boolean allMatch = createStream().allMatch(predicate);

            boolean expected = parameters().stream()
                    .allMatch(p -> predicate.test(p.name, p.value));

            assertEquals(expected, allMatch);
        }
    }

    @Nested
    class AnyMatch {

        @ParameterizedTest(name = "name = {0}")
        @ValueSource(strings = { "foo", "q", "bar" })
        @EmptySource
        void testSomeMatch(String nameToMatch) {
            testAnyMatch((name, value) -> nameToMatch.equals(name));
        }

        @Test
        void testAllMatch() {
            testAnyMatch((name, value) -> true);
        }

        @Test
        void testNoneMatch() {
            testAnyMatch((name, value) -> false);
        }

        private void testAnyMatch(BiPredicate<String, String> predicate) {
            boolean anyMatch = createStream().anyMatch(predicate);

            boolean expected = parameters().stream()
                    .anyMatch(p -> predicate.test(p.name, p.value));

            assertEquals(expected, anyMatch);
        }
    }

    @Nested
    class NoneMatch {

        @ParameterizedTest(name = "name = {0}")
        @ValueSource(strings = { "foo", "q", "bar" })
        @EmptySource
        void testOnlySomeMatch(String nameToMatch) {
            testNoneMatch((name, value) -> nameToMatch.equals(name));
        }

        @Test
        void testAllMatch() {
            testNoneMatch((name, value) -> true);
        }

        @Test
        void testNoneMatch() {
            testNoneMatch((name, value) -> false);
        }

        private void testNoneMatch(BiPredicate<String, String> predicate) {
            boolean noneMatch = createStream().noneMatch(predicate);

            boolean expected = parameters().stream()
                    .noneMatch(p -> predicate.test(p.name, p.value));

            assertEquals(expected, noneMatch);
        }
    }

    @Nested
    class IsParallel {

        @Test
        void testInitiallySequential() {
            ParameterStream stream = createStream();

            assertFalse(stream.isParallel());
        }

        @Test
        void testParallel() {
            ParameterStream stream = createStream()
                    .parallel();

            assertTrue(stream.isParallel());
        }

        @Test
        void testParallelThenSequential() {
            ParameterStream stream = createStream()
                    .parallel()
                    .sequential();

            assertFalse(stream.isParallel());
        }
    }

    @Test
    void testUnOrdered() {
        Spliterator<Parameter> spliterator = createStream()
                .parallel()
                .map(Parameter::new)
                .spliterator();

        assertTrue(spliterator.hasCharacteristics(Spliterator.ORDERED));

        spliterator = createStream()
                .unordered()
                .parallel()
                .map(Parameter::new)
                .spliterator();

        assertFalse(spliterator.hasCharacteristics(Spliterator.ORDERED));
    }

    @Test
    void testConcat() {
        List<Parameter> parameters = ParameterStream.concat(createStream(), createStream())
                .map(Parameter::new)
                .collect(Collectors.toList());

        List<Parameter> expected = Stream.concat(parameters().stream(), parameters().stream())
                .collect(Collectors.toList());

        assertEquals(expected, parameters);
    }

    @Test
    void testFullFlow() {
        List<Parameter> input = Stream.concat(parameters().stream(), parameters().stream())
                .collect(Collectors.toList());
        input = Stream.concat(input.stream(), input.stream())
                .collect(Collectors.toList());

        List<Parameter> parameters = new ArrayList<>();
        createStream(input)
                .filter((name, value) -> !name.isEmpty() && !value.isEmpty())
                .distinct()
                .sorted()
                .skip(3)
                .limit(10)
                .forEach((name, value) -> parameters.add(new Parameter(name, value)));

        List<Parameter> expected = input.stream()
                .filter(p -> !p.name.isEmpty() && !p.value.isEmpty())
                .distinct()
                .sorted(Comparator.comparing(Parameter::name).thenComparing(Parameter::value))
                .skip(3)
                .limit(10)
                .collect(Collectors.toList());

        assertEquals(expected, parameters);
    }

    @Nested
    class FromMap {

        @Nested
        class WithStringValues {

            @Test
            void testSequential() {
                Map<String, String> map = new LinkedHashMap<>();
                map.put("foo", "bar");
                map.put("empty-value", "");
                map.put("", "empty-name");
                map.put("q", "a");

                BiConsumer<String, String> action = mockConsumer();

                ParameterStream.fromMap()
                        .withStringValues(map)
                        .forEach(action);

                map.forEach((key, value) -> verify(action).accept(key, value));
                verifyNoMoreInteractions(action);
            }

            @Test
            void testParallel() {
                Map<String, String> map = new LinkedHashMap<>();
                map.put("foo", "bar");
                map.put("empty-value", "");
                map.put("", "empty-name");
                map.put("q", "a");

                BiConsumer<String, String> action = mockConsumer();

                ParameterStream.fromMap()
                        .withStringValues(map)
                        .parallel()
                        .forEach(action);

                map.forEach((key, value) -> verify(action).accept(key, value));
                verifyNoMoreInteractions(action);
            }

            @Test
            void testWithNullKey() {
                Map<String, String> map = new LinkedHashMap<>();
                map.put("foo", "bar");
                map.put("empty-value", "");
                map.put("", "empty-name");
                map.put(null, "null-name");
                map.put("q", "a");

                testWithNull(map);
            }

            @Test
            void testWithNullValue() {
                Map<String, String> map = new LinkedHashMap<>();
                map.put("foo", "bar");
                map.put("empty-value", "");
                map.put("null-value", null);
                map.put("", "empty-name");
                map.put("q", "a");

                testWithNull(map);
            }

            private void testWithNull(Map<String, String> map) {
                BiConsumer<String, String> action = mockConsumer();

                ParameterStream stream = ParameterStream.fromMap()
                        .withStringValues(map);

                assertThrows(NullPointerException.class, () -> stream.forEach(action));
            }
        }

        @Nested
        class WithArrayValues {

            @Test
            void testSequential() {
                Map<String, String[]> map = new LinkedHashMap<>();
                map.put("foo", values("bar", "bar"));
                map.put("empty-value", values(""));
                map.put("empty-values", values());
                map.put("", values("empty-name"));
                map.put("q", values("a"));

                BiConsumer<String, String> action = mockConsumer();

                ParameterStream.fromMap()
                        .withArrayValues(map)
                        .forEach(action);

                map.entrySet().stream()
                        .filter(e -> e.getValue() != null)
                        .forEach(e -> Arrays.stream(e.getValue())
                                .forEach(value -> verify(action, times(count(value, e.getValue()))).accept(e.getKey(), value)));
                verifyNoMoreInteractions(action);
            }

            @Test
            void testParallel() {
                Map<String, String[]> map = new LinkedHashMap<>();
                map.put("foo", values("bar", "bar"));
                map.put("empty-value", values(""));
                map.put("empty-values", values());
                map.put("", values("empty-name"));
                map.put("q", values("a"));

                BiConsumer<String, String> action = mockConsumer();

                ParameterStream.fromMap()
                        .withArrayValues(map)
                        .parallel()
                        .forEach(action);

                map.entrySet().stream()
                        .filter(e -> e.getValue() != null)
                        .forEach(e -> Arrays.stream(e.getValue())
                                .forEach(value -> verify(action, times(count(value, e.getValue()))).accept(e.getKey(), value)));
                verifyNoMoreInteractions(action);
            }

            @Test
            void testWithNullKey() {
                Map<String, String[]> map = new LinkedHashMap<>();
                map.put("foo", values("bar", "bar"));
                map.put("empty-value", values(""));
                map.put("empty-values", values());
                map.put("", values("empty-name"));
                map.put(null, values("null-name"));
                map.put("q", values("a"));

                testWithNull(map);
            }

            @Test
            void testWithNullArray() {
                Map<String, String[]> map = new LinkedHashMap<>();
                map.put("foo", values("bar", "bar"));
                map.put("empty-value", values(""));
                map.put("empty-values", values());
                map.put("null-values", null);
                map.put("", values("empty-name"));
                map.put("q", values("a"));

                testWithNull(map);
            }

            @Test
            void testWithNullValue() {
                Map<String, String[]> map = new LinkedHashMap<>();
                map.put("foo", values("bar", "bar"));
                map.put("empty-value", values(""));
                map.put("empty-values", values());
                map.put("null-value", values((String) null));
                map.put("", values("empty-name"));
                map.put("q", values("a"));

                testWithNull(map);
            }

            private void testWithNull(Map<String, String[]> map) {
                BiConsumer<String, String> action = mockConsumer();

                ParameterStream stream = ParameterStream.fromMap()
                        .withArrayValues(map);

                assertThrows(NullPointerException.class, () -> stream.forEach(action));
            }

            private String[] values(String... values) {
                return values;
            }

            private int count(String value, String[] array) {
                return (int) Arrays.stream(array)
                        .filter(v -> Objects.equals(value, v))
                        .count();
            }
        }

        @Nested
        class WithCollectionValues {

            @Test
            void testSequential() {
                Map<String, List<String>> map = new LinkedHashMap<>();
                map.put("foo", List.of("bar", "bar"));
                map.put("empty-value", List.of(""));
                map.put("empty-values", List.of());
                map.put("", List.of("empty-name"));
                map.put("q", List.of("a"));

                BiConsumer<String, String> action = mockConsumer();

                ParameterStream.fromMap()
                        .withCollectionValues(map)
                        .forEach(action);

                map.entrySet().stream()
                        .filter(e -> e.getValue() != null)
                        .forEach(e -> e.getValue().stream()
                                .forEach(value -> verify(action, times(count(value, e.getValue()))).accept(e.getKey(), value)));
                verifyNoMoreInteractions(action);
            }

            @Test
            void testParallel() {
                Map<String, List<String>> map = new LinkedHashMap<>();
                map.put("foo", List.of("bar", "bar"));
                map.put("empty-value", List.of(""));
                map.put("empty-values", List.of());
                map.put("", List.of("empty-name"));
                map.put("q", List.of("a"));

                BiConsumer<String, String> action = mockConsumer();

                ParameterStream.fromMap()
                        .withCollectionValues(map)
                        .parallel()
                        .forEach(action);

                map.entrySet().stream()
                        .filter(e -> e.getValue() != null)
                        .forEach(e -> e.getValue().stream()
                                .forEach(value -> verify(action, times(count(value, e.getValue()))).accept(e.getKey(), value)));
                verifyNoMoreInteractions(action);
            }

            @Test
            void testWithNullKey() {
                Map<String, List<String>> map = new LinkedHashMap<>();
                map.put("foo", List.of("bar", "bar"));
                map.put("empty-value", List.of(""));
                map.put("empty-values", List.of());
                map.put("", List.of("empty-name"));
                map.put(null, List.of("null-name"));
                map.put("q", List.of("a"));

                testWithNull(map);
            }

            @Test
            void testWithNullCollection() {
                Map<String, List<String>> map = new LinkedHashMap<>();
                map.put("foo", List.of("bar", "bar"));
                map.put("empty-value", List.of(""));
                map.put("empty-values", List.of());
                map.put("null-values", null);
                map.put("", List.of("empty-name"));
                map.put("q", List.of("a"));

                testWithNull(map);
            }

            @Test
            void testWithNullValue() {
                Map<String, List<String>> map = new LinkedHashMap<>();
                map.put("foo", List.of("bar", "bar"));
                map.put("empty-value", List.of(""));
                map.put("empty-values", List.of());
                map.put("null-value", Collections.singletonList(null));
                map.put("", List.of("empty-name"));
                map.put("q", List.of("a"));

                testWithNull(map);
            }

            private void testWithNull(Map<String, List<String>> map) {
                BiConsumer<String, String> action = mockConsumer();

                ParameterStream stream = ParameterStream.fromMap()
                        .withCollectionValues(map);

                assertThrows(NullPointerException.class, () -> stream.forEach(action));
            }

            private int count(String value, Collection<String> collection) {
                return (int) collection.stream()
                        .filter(v -> Objects.equals(value, v))
                        .count();
            }
        }
    }

    private List<Parameter> parameters() {
        return List.of(new Parameter("foo", "bar"),
                new Parameter("foo", "baz"),
                new Parameter("empty-value", ""),
                new Parameter("", "empty-name"),
                new Parameter("", ""),
                new Parameter("q", "a"),
                new Parameter("foo", "bar"));
    }

    private ParameterStream createStream() {
        return createStream(parameters());
    }

    private ParameterStream createStream(List<Parameter> parameters) {
        return ParameterStream.of(new ParameterStream.Spliterator() {

            private final Iterator<Parameter> iterator = parameters.iterator();

            @Override
            public boolean tryAdvance(BiConsumer<? super String, ? super String> action) {
                if (iterator.hasNext()) {
                    Parameter parameter = iterator.next();
                    action.accept(parameter.name, parameter.value);
                    return true;
                }
                return false;
            }

            @Override
            public ParameterStream.Spliterator trySplit() {
                return null;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private BiPredicate<String, String> mockPredicate() {
        return mock(BiPredicate.class);
    }

    @SuppressWarnings("unchecked")
    private <R> BiFunction<String, String, R> mockFunction() {
        return mock(BiFunction.class);
    }

    @SuppressWarnings("unchecked")
    private UnaryOperator<String> mockUnaryOperator() {
        return mock(UnaryOperator.class);
    }

    @SuppressWarnings("unchecked")
    private BiConsumer<String, String> mockConsumer() {
        return mock(BiConsumer.class);
    }

    private static final class Parameter {

        private final String name;
        private final String value;

        private Parameter(String name, String value) {
            this.name = name;
            this.value = value;
        }

        private String name() {
            return name;
        }

        private String value() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || o.getClass() != getClass()) {
                return false;
            }
            Parameter other = (Parameter) o;
            return name.equals(other.name) && value.equals(other.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }

        @Override
        public String toString() {
            return name + "=" + value;
        }
    }
}
