/*
 * ParameterStream.java
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

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Spliterators;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.BaseStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A {@link Stream} variant for query and form parameters.
 *
 * @author Rob Spoor
 */
public final class ParameterStream {

    private final Stream<Parameter> stream;

    private ParameterStream(Stream<Parameter> stream) {
        this.stream = stream;
    }

    /**
     * Returns a stream consisting of the parameters of this stream that match the given predicate.
     * <p>
     * This is an intermediate operation.
     *
     * @param predicate A predicate to apply to each parameter to determine if it should be included.
     * @return The new stream.
     * @throws NullPointerException If the given predicate is {@code null}.
     * @see Stream#filter(Predicate)
     */
    public ParameterStream filter(BiPredicate<? super String, ? super String> predicate) {
        return new ParameterStream(stream.filter(Parameter.predicate(predicate)));
    }

    /**
     * Returns a stream consisting of the results of applying the given function to the parameters of this stream.
     * <p>
     * This is an intermediate operation.
     *
     * @param <R> The element type of the new stream.
     * @param mapper A function to apply to each parameter.
     * @return The new stream.
     * @throws NullPointerException If the given mapper is {@code null}.
     * @see Stream#map(Function)
     */
    public <R> Stream<R> map(BiFunction<? super String, ? super String, ? extends R> mapper) {
        return stream.map(Parameter.function(mapper));
    }

    /**
     * Returns a stream consisting of the results of applying the given function to the parameter names of this stream. The values remain unchanged.
     * <p>
     * This is an intermediate operation.
     *
     * @param mapper A function to apply to each parameter name.
     * @return The new stream.
     * @throws NullPointerException If the given mapper is {@code null}.
     * @see Stream#map(Function)
     */
    public ParameterStream mapName(Function<? super String, String> mapper) {
        return new ParameterStream(stream.map(Parameter.nameFunction(mapper)));
    }

    /**
     * Returns a stream consisting of the results of applying the given function to the parameter values of this stream. The names remain unchanged.
     * <p>
     * This is an intermediate operation.
     *
     * @param mapper A function to apply to each parameter value.
     * @return The new stream.
     * @throws NullPointerException If the given mapper is {@code null}.
     * @see Stream#map(Function)
     */
    public ParameterStream mapValue(Function<? super String, String> mapper) {
        return new ParameterStream(stream.map(Parameter.valueFunction(mapper)));
    }

    /**
     * Returns a stream consisting of the distinct parameters (according to {@link String#equals(Object)} for both the name and value) of this stream.
     * <p>
     * This is a stateful intermediate operation.
     *
     * @return The new stream.
     * @see Stream#distinct()
     */
    public ParameterStream distinct() {
        // Usually parameters are shared and updated; use a non-shared version of each parameter because they will now be stored
        return new ParameterStream(stream.map(Parameter::nonShared).distinct());
    }

    /**
     * Returns a stream consisting of the parameters of this stream, sorted according to natural order of parameter names first, values second.
     * <p>
     * This is a stateful intermediate operation.
     *
     * @return The new stream.
     * @see Stream#sorted()
     */
    public ParameterStream sorted() {
        // Usually parameters are shared and updated; use a non-shared version of each parameter because they will now be stored
        return new ParameterStream(stream.map(Parameter::nonShared)
                .sorted(Comparator.comparing(Parameter::name)
                        .thenComparing(Parameter::value)));
    }

    /**
     * Returns a stream consisting of the parameters of this stream, sorted according to the provided parameter name comparator.
     * <p>
     * This is a stateful intermediate operation.
     *
     * @param nameComparator The comparator to use for the parameter names.
     * @return The new stream.
     * @throws NullPointerException If the comparator is {@code null}.
     * @see Stream#sorted(Comparator)
     */
    public ParameterStream sorted(Comparator<? super String> nameComparator) {
        return new ParameterStream(stream.map(Parameter::nonShared)
                .sorted(Comparator.comparing(Parameter::name, nameComparator)));
    }

    /**
     * Returns a stream consisting of the parameters of this stream, sorted according to the provided parameter name and value comparators.
     * <p>
     * This is a stateful intermediate operation.
     *
     * @param nameComparator The comparator to use for the parameter names.
     * @param valueComparator The comparator to use for the parameter values.
     * @return The new stream.
     * @throws NullPointerException If either comparator is {@code null}.
     * @see Stream#sorted(Comparator)
     */
    public ParameterStream sorted(Comparator<? super String> nameComparator, Comparator<? super String> valueComparator) {
        return new ParameterStream(stream.map(Parameter::nonShared)
                .sorted(Comparator.comparing(Parameter::name, nameComparator)
                        .thenComparing(Parameter::value, valueComparator)));
    }

    /**
     * Returns a stream consisting of the parameters of this stream, additionally performing the provided action on each parameter as parameters are
     * consumed from the resulting stream.
     * <p>
     * This is an intermediate operation.
     *
     * @param action An action to perform on the parameters as they are consumed from the stream.
     * @return The new stream.
     * @throws NullPointerException If the given action is {@code null}.
     * @see Stream#peek(Consumer)
     */
    public ParameterStream peek(BiConsumer<? super String, ? super String> action) {
        return new ParameterStream(stream.peek(Parameter.consumer(action)));
    }

    /**
     * Returns a stream consisting of the parameters of this stream, truncated to be no longer than {@code maxSize} in length.
     * <p>
     * This is a short-circuiting stateful intermediate operation.
     *
     * @param maxSize The number of parameters the stream should be limited to.
     * @return The new stream.
     * @throws IllegalArgumentException If {@code maxSize} is negative.
     * @see Stream#limit(long)
     */
    public ParameterStream limit(long maxSize) {
        return new ParameterStream(stream.limit(maxSize));
    }

    /**
     * Returns a stream consisting of the remaining parameters of this stream after discarding the first {@code n} parameters of the stream.
     * If this stream contains fewer than {@code n} parameters then an empty stream will be returned.
     * <p>
     * This is a stateful intermediate operation.
     *
     * @param n The number of leading parameters to skip.
     * @return The new stream
     * @throws IllegalArgumentException If {@code n} is negative.
     * @see Stream#skip(long)
     */
    public ParameterStream skip(long n) {
        return new ParameterStream(stream.skip(n));
    }

    /**
     * Performs an action for each parameter of this stream.
     * <p>
     * This is a terminal operation.
     *
     * @param action An action to perform on the parameters.
     * @throws NullPointerException If the given action is {@code null}.
     * @see Stream#forEach(Consumer)
     */
    public void forEach(BiConsumer<? super String, ? super String> action) {
        stream.forEach(Parameter.consumer(action));
    }

    /**
     * Performs an action for each parameter of this stream, in the encounter order of the stream if the stream has a defined encounter order.
     * <p>
     * This is a terminal operation.
     *
     * @param action An action to perform on the parameters.
     * @throws NullPointerException If the given action is {@code null}.
     * @see Stream#forEachOrdered(Consumer)
     */
    public void forEachOrdered(BiConsumer<? super String, ? super String> action) {
        stream.forEachOrdered(Parameter.consumer(action));
    }

    /**
     * Returns the count of parameters in this stream.
     * <p>
     * This is a terminal operation.
     *
     * @return The count of parameters in this stream.
     * @see Stream#count()
     */
    public long count() {
        return stream.count();
    }

    /**
     * Returns whether or not any parameters of this stream match the provided predicate.
     * <p>
     * This is a short-circuiting terminal operation.
     *
     * @param predicate A predicate to apply to parameters of this stream
     * @return {@code true} if any parameters of the stream match the provided predicate, or {@code false} otherwise.
     */
    public boolean anyMatch(BiPredicate<? super String, ? super String> predicate) {
        return stream.anyMatch(Parameter.predicate(predicate));
    }

    /**
     * Returns whether or not all parameters of this stream match the provided predicate.
     * <p>
     * This is a short-circuiting terminal operation.
     *
     * @param predicate A predicate to apply to parameters of this stream.
     * @return {@code true} if the stream is empty or all parameters of the stream match the provided predicate, or {@code false} otherwise.
     * @throws NullPointerException If the given predicate is {@code null}.
     * @see Stream#allMatch(Predicate)
     */
    public boolean allMatch(BiPredicate<? super String, ? super String> predicate) {
        return stream.allMatch(Parameter.predicate(predicate));
    }

    /**
     * Returns whether or not no parameters of this stream match the provided predicate.
     * <p>
     * This is a short-circuiting terminal operation.
     *
     * @param predicate A predicate to apply to parameters of this stream
     * @return {@code true} if the stream is empty or no parameters of the stream match the provided predicate, or {@code false} otherwise.
     * @see Stream#noneMatch(Predicate)
     */
    public boolean noneMatch(BiPredicate<? super String, ? super String> predicate) {
        return stream.noneMatch(Parameter.predicate(predicate));
    }

    /**
     * Returns whether or not this stream, if a terminal operation were to be executed, would execute in parallel.
     *
     * @return {@code true} if this stream would execute in parallel if executed, or {@code false} otherwise.
     * @see BaseStream#isParallel()
     */
    public boolean isParallel() {
        return stream.isParallel();
    }

    /**
     * Returns an equivalent stream that is sequential.
     * <p>
     * This is an intermediate operation.
     *
     * @return A sequential stream.
     */
    public ParameterStream sequential() {
        return new ParameterStream(stream.sequential());
    }

    /**
     * Returns an equivalent stream that is parallel.
     * <p>
     * This is an intermediate operation.
     *
     * @return A parallel stream.
     */
    public ParameterStream parallel() {
        return new ParameterStream(stream.parallel());
    }

    /**
     * Returns an equivalent stream that is unordered.
     * <p>
     * This is an intermediate operation.
     *
     * @return An unordered stream.
     */
    public ParameterStream unordered() {
        return new ParameterStream(stream.unordered());
    }

    /**
     * Creates a lazily concatenated stream whose parameters are all the parameters of the first stream followed by all the parameters of the second
     * stream.
     *
     * @param a The first stream.
     * @param b The second stream.
     * @return The concatenation of the two streams
     * @throws NullPointerException If either stream is {@code null}.
     * @see Stream#concat(Stream, Stream)
     */
    public static ParameterStream concat(ParameterStream a, ParameterStream b) {
        Objects.requireNonNull(a);
        Objects.requireNonNull(b);

        Stream<Parameter> streamA = a.stream;
        Stream<Parameter> streamB = b.stream;
        return new ParameterStream(Stream.concat(streamA, streamB));
    }

    /**
     * Returns an object that can be used to create parameter streams from maps.
     *
     * @return An object that can be used to create parameter streams from maps.
     */
    public static FromMap fromMap() {
        return FromMap.INSTANCE;
    }

    /**
     * An object that can be used to create parameter streams from maps.
     *
     * @author Rob Spoor
     */
    public static final class FromMap {

        private static final FromMap INSTANCE = new FromMap();

        private FromMap() {
        }

        /**
         * Returns a parameter stream for the entries of a map, where keys are the parameter names and the values are the parameter values.
         * <p>
         * If the given map contains {@code null} keys or values, any terminal operation on the returned stream will fail with a
         * {@link NullPointerException}.
         *
         * @param map The map to return a parameter stream for.
         * @return A parameter stream for the entries of the given map.
         * @throws NullPointerException If the given map is {@code null}.
         */
        public ParameterStream withStringValues(Map<String, String> map) {
            return of(new StringValueSpliterator(map.entrySet().spliterator()));
        }

        private static final class StringValueSpliterator implements Spliterator {

            private final java.util.Spliterator<Map.Entry<String, String>> spliterator;

            private StringValueSpliterator(java.util.Spliterator<Entry<String, String>> spliterator) {
                this.spliterator = spliterator;
            }

            @Override
            public boolean tryAdvance(BiConsumer<? super String, ? super String> action) {
                Objects.requireNonNull(action);
                return spliterator.tryAdvance(entry -> action.accept(entry.getKey(), entry.getValue()));
            }

            @Override
            public Spliterator trySplit() {
                java.util.Spliterator<Map.Entry<String, String>> split = spliterator.trySplit();
                return split == null ? null : new StringValueSpliterator(split);
            }
        }

        /**
         * Returns a parameter stream for the entries of a map, where keys are the parameter names and the values are array with the parameter values.
         * <p>
         * If the given map contains any empty arrays as values, the matching parameter name will be discarded.
         * If the given map contains {@code null} keys, values or value elements, any terminal operation on the returned stream will fail with a
         * {@link NullPointerException}.
         *
         * @param map The map to return a parameter stream for.
         * @return A parameter stream for the entries of the given map.
         * @throws NullPointerException If the given map is {@code null}.
         */
        public ParameterStream withArrayValues(Map<String, String[]> map) {
            return of(new ArrayValueSpliterator(map.entrySet().spliterator()));
        }

        private static final class ArrayValueSpliterator extends ValuesSpliterator<String[]> {

            private ArrayValueSpliterator(java.util.Spliterator<Entry<String, String[]>> spliterator) {
                super(spliterator);
            }

            @Override
            java.util.Spliterator<String> newSpliterator(String[] values) {
                return Spliterators.spliterator(values, 0);
            }

            @Override
            ArrayValueSpliterator newSpliterator(java.util.Spliterator<Entry<String, String[]>> spliterator) {
                return new ArrayValueSpliterator(spliterator);
            }
        }

        /**
         * Returns a parameter stream for the entries of a map, where keys are the parameter names and the values are collections with the parameter
         * values.
         * <p>
         * If the given map contains any empty collections as values, the matching parameter name will be discarded.
         * If the given map contains {@code null} keys, values or value elements, any terminal operation on the returned stream will fail with a
         * {@link NullPointerException}.
         *
         * @param map The map to return a parameter stream for.
         * @return A parameter stream for the entries of the given map.
         * @throws NullPointerException If the given map is {@code null}.
         */
        public ParameterStream withCollectionValues(Map<String, ? extends Collection<String>> map) {
            return of(new CollectionValueSpliterator<>(map.entrySet().spliterator()));
        }

        private static final class CollectionValueSpliterator<C extends Collection<String>> extends ValuesSpliterator<C> {

            private CollectionValueSpliterator(java.util.Spliterator<Entry<String, C>> spliterator) {
                super(spliterator);
            }

            @Override
            java.util.Spliterator<String> newSpliterator(C values) {
                return values.spliterator();
            }

            @Override
            CollectionValueSpliterator<C> newSpliterator(java.util.Spliterator<Entry<String, C>> spliterator) {
                return new CollectionValueSpliterator<>(spliterator);
            }
        }

        private abstract static class ValuesSpliterator<V> implements Spliterator {

            private final java.util.Spliterator<Map.Entry<String, V>> spliterator;
            private String currentKey;
            private java.util.Spliterator<String> currentValues;

            private ValuesSpliterator(java.util.Spliterator<Entry<String, V>> spliterator) {
                this.spliterator = spliterator;
            }

            @Override
            public boolean tryAdvance(BiConsumer<? super String, ? super String> action) {
                Objects.requireNonNull(action);
                while (currentValues == null || !currentValues.tryAdvance(value -> action.accept(currentKey, value))) {
                    if (!spliterator.tryAdvance(this::setCurrent)) {
                        return false;
                    }
                }
                // currentValues advanced
                return true;
            }

            private void setCurrent(Map.Entry<String, V> entry) {
                currentKey = entry.getKey();
                V values = entry.getValue();
                currentValues = newSpliterator(values);
            }

            @Override
            public Spliterator trySplit() {
                java.util.Spliterator<Map.Entry<String, V>> split = spliterator.trySplit();
                return split == null ? null : newSpliterator(split);
            }

            abstract java.util.Spliterator<String> newSpliterator(V values);

            abstract ValuesSpliterator<V> newSpliterator(java.util.Spliterator<Map.Entry<String, V>> spliterator);
        }
    }

    static ParameterStream of(Spliterator spliterator) {
        Stream<Parameter> stream = StreamSupport.stream(new ParameterSpliterator(spliterator), false);
        return new ParameterStream(stream);
    }

    interface Spliterator {

        boolean tryAdvance(BiConsumer<? super String, ? super String> action);

        Spliterator trySplit();
    }

    private static final class Parameter {

        private String name;
        private String value;
        private final boolean isShared;

        private Parameter(String name, String value, boolean isShared) {
            this.name = name;
            this.value = value;
            this.isShared = isShared;
        }

        private static Parameter sharedParameter() {
            return new Parameter(null, null, true);
        }

        private static Parameter nonSharedParameter(String name, String value) {
            return new Parameter(name, value, false);
        }

        private String name() {
            return name;
        }

        private String value() {
            return value;
        }

        private Parameter nonShared() {
            return isShared ? nonSharedParameter(name, value) : this;
        }

        private void update(String name, String value) {
            this.name = Objects.requireNonNull(name);
            this.value = Objects.requireNonNull(value);
        }

        private Parameter withName(String name) {
            if (isShared) {
                this.name = Objects.requireNonNull(name);
                return this;
            }
            if (this.name.equals(name)) {
                return this;
            }
            return nonSharedParameter(name, value);
        }

        private Parameter withValue(String value) {
            if (isShared) {
                this.value = Objects.requireNonNull(value);
                return this;
            }
            if (this.value.equals(value)) {
                return this;
            }
            return nonSharedParameter(name, value);
        }

        // equals and hashCode only exist for distinct() support
        // These will never be called before update will be called, and therefore a name and value will be available

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || o.getClass() != getClass()) {
                return false;
            }
            Parameter other = (Parameter) o;
            return name.equals(other.name)
                    && value.equals(other.value);
        }

        @Override
        public int hashCode() {
            return name.hashCode() + 31 * value.hashCode();
        }

        private static Predicate<Parameter> predicate(BiPredicate<? super String, ? super String> predicate) {
            Objects.requireNonNull(predicate);
            return parameter -> predicate.test(parameter.name, parameter.value);
        }

        private static <R> Function<Parameter, R> function(BiFunction<? super String, ? super String, ? extends R> function) {
            Objects.requireNonNull(function);
            return parameter -> function.apply(parameter.name, parameter.value);
        }

        private static UnaryOperator<Parameter> nameFunction(Function<? super String, String> function) {
            Objects.requireNonNull(function);
            return parameter -> parameter.withName(function.apply(parameter.name));
        }

        private static UnaryOperator<Parameter> valueFunction(Function<? super String, String> function) {
            Objects.requireNonNull(function);
            return parameter -> parameter.withValue(function.apply(parameter.value));
        }

        private static Consumer<Parameter> consumer(BiConsumer<? super String, ? super String> consumer) {
            Objects.requireNonNull(consumer);
            return parameter -> consumer.accept(parameter.name, parameter.value);
        }
    }

    private static final class ParameterSpliterator implements java.util.Spliterator<Parameter> {

        private static final int CHARACTERISTICS = java.util.Spliterator.ORDERED | java.util.Spliterator.NONNULL;

        private final Spliterator spliterator;

        // Use a single shared instance that gets updated for each parameter
        // When parameters need to be stored (distinct, sorted) it will get replaced by a non-shared copy
        private final Parameter parameter;

        private ParameterSpliterator(Spliterator spliterator) {
            this.spliterator = spliterator;
            this.parameter = Parameter.sharedParameter();
        }

        @Override
        public boolean tryAdvance(Consumer<? super Parameter> action) {
            Objects.requireNonNull(action);
            if (spliterator.tryAdvance(parameter::update)) {
                action.accept(parameter);
                return true;
            }
            return false;
        }

        @Override
        public java.util.Spliterator<Parameter> trySplit() {
            Spliterator split = spliterator.trySplit();
            return split == null ? null : new ParameterSpliterator(split);
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public long getExactSizeIfKnown() {
            return -1;
        }

        @Override
        public int characteristics() {
            return CHARACTERISTICS;
        }
    }
}
