/*
 * HttpUriBuilderTest.java
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("nls")
class HttpUriBuilderTest {

    private static final URI DEFAULT_URI = HttpUriBuilder.forHost("localhost").toURI();

    @Nested
    class Scheme {

        @Test
        void testDefaultScheme() {
            assertEquals("https", DEFAULT_URI.getScheme());
        }

        @ParameterizedTest
        @ValueSource(strings = { "https", "http", "HTTPS", "HTTP" })
        void testValidScheme(String scheme) {
            URI uri = HttpUriBuilder.forHost("localhost")
                    .scheme(scheme)
                    .toURI();

            assertEquals(scheme.toLowerCase(), uri.getScheme());
        }

        @ParameterizedTest
        @ValueSource(strings = { "ftp", "https:" })
        @EmptySource
        @NullSource
        void testInvalidScheme(String scheme) {
            HttpUriBuilder builder = HttpUriBuilder.forHost("localhost");

            assertThrows(IllegalArgumentException.class, () -> builder.scheme(scheme));

            URI uri = builder.toURI();

            assertEquals("https", uri.getScheme());
        }
    }

    @Nested
    class Port {

        @Test
        void testDefaultPort() {
            assertEquals(-1, DEFAULT_URI.getPort());
        }

        @ParameterizedTest
        @ValueSource(ints = { -1, 1, 22, 80, 65535 })
        void testValidPort(int port) {
            URI uri = HttpUriBuilder.forHost("localhost")
                    .port(port)
                    .toURI();

            assertEquals(port, uri.getPort());
        }

        @ParameterizedTest
        @ValueSource(ints = { -2, 0, 65536 })
        void testInvalidPort(int port) {
            HttpUriBuilder builder = HttpUriBuilder.forHost("localhost");

            assertThrows(IllegalArgumentException.class, () -> builder.port(port));

            URI uri = builder.toURI();

            assertEquals(-1, uri.getPort());
        }
    }

    @Nested
    class UserInfo {

        @Test
        void testDefaultUserInfo() {
            assertNull(DEFAULT_URI.getUserInfo());
        }

        @ParameterizedTest
        @ValueSource(strings = { "user", "user:pass", "\u03bb" })
        @NullSource
        void testUserInfo(String userInfo) {
            URI uri = HttpUriBuilder.forHost("localhost")
                    .userInfo(userInfo)
                    .toURI();

            assertEquals(userInfo, uri.getUserInfo());
        }

        @Test
        void testEmptyUserInfo() {
            URI uri = HttpUriBuilder.forHost("localhost")
                    .userInfo("")
                    .toURI();

            assertNull(uri.getUserInfo());
        }

        @Test
        void testNullUsernameAndNullPassword() {
            URI uri = HttpUriBuilder.forHost("localhost")
                    .userInfo(null, null)
                    .toURI();

            assertNull(uri.getUserInfo());
        }

        @Test
        void testNonNullUsernameAndNullPassword() {
            URI uri = HttpUriBuilder.forHost("localhost")
                    .userInfo("user", null)
                    .toURI();

            assertEquals("user", uri.getUserInfo());
        }

        @Test
        void testNullUsernameAndNonNullPassword() {
            HttpUriBuilder builder = HttpUriBuilder.forHost("localhost");

            assertThrows(NullPointerException.class, () -> builder.userInfo(null, ""));

            URI uri = builder.toURI();

            assertNull(uri.getUserInfo());
        }

        @Test
        void testNonNullUsernameAndNonNullPassword() {
            URI uri = HttpUriBuilder.forHost("localhost")
                    .userInfo("user", "pass")
                    .toURI();

            assertEquals("user:pass", uri.getUserInfo());
        }
    }

    @Nested
    class Path {

        @Nested
        class SinglePath {

            @Test
            void testSimplePath() {
                testSinglePath("test 1&2", "/test+1%262");
            }

            @Test
            void testWithLeadingSlashes() {
                testSinglePath("///test 1&2", "/test+1%262");
            }

            @Test
            void testWithTrailingSlashes() {
                testSinglePath("test 1&2///", "/test+1%262/");
            }

            @Test
            void testOnlySlashes() {
                testSinglePath("/", "/");
                testSinglePath("///", "/");
            }

            private void testSinglePath(String path, String expectedPath) {
                URI uri = HttpUriBuilder.forHost("localhost")
                        .path(path)
                        .toURI();

                assertEquals(expectedPath, uri.getRawPath());
            }

            @Nested
            class WithDisallowedSegment {

                @ParameterizedTest
                @ValueSource(strings = { ".", "./test", "..", "../test" })
                void testFirstSegmentFails(String path) {
                    HttpUriBuilder builder = HttpUriBuilder.forHost("localhost");

                    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> builder.path(path));

                    assertEquals(Messages.HttpUriBuilder.invalidPathSegment(), exception.getMessage());

                    assertEquals("/", builder.toURI().getRawPath());
                }

                @ParameterizedTest
                @ValueSource(strings = { "test/.", "test/./test", "test/..", "test/../test" })
                void testNonFirstSegmentFails(String path) {
                    HttpUriBuilder builder = HttpUriBuilder.forHost("localhost");

                    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> builder.path(path));

                    assertEquals(Messages.HttpUriBuilder.invalidPathSegment(), exception.getMessage());

                    assertEquals("/test", builder.toURI().getRawPath());
                }
            }
        }

        @Nested
        class MultiplePaths {

            @Test
            void testSimplePaths() {
                testMultiplePaths("test 1&2", "test 3&4", "test 5&6", "/test+1%262/test+3%264/test+5%266");
            }

            @Test
            void testWithLeadingSlashes() {
                testMultiplePaths("///test 1&2", "///test 3&4", "///test 5&6", "/test+1%262/test+3%264/test+5%266");
            }

            @Test
            void testWithTrailingSlashes() {
                testMultiplePaths("test 1&2///", "test 3&4///", "test 5&6///", "/test+1%262/test+3%264/test+5%266/");
            }

            @Test
            void testOnlySlashes() {
                testMultiplePaths("/", "//", "///", "/");
            }

            private void testMultiplePaths(String path1, String path2, String path3, String expectedPath) {
                URI uri = HttpUriBuilder.forHost("localhost")
                        .path(path1, path2, path3)
                        .toURI();

                assertEquals(expectedPath, uri.getRawPath());
            }

            @Nested
            class WithDisallowedSegment {

                @ParameterizedTest
                @ValueSource(strings = { ".", "./test", "..", "../test" })
                void testFirstSegmentFails(String path) {
                    HttpUriBuilder builder = HttpUriBuilder.forHost("localhost");

                    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> builder.path("test1", path, "test2"));

                    assertEquals(Messages.HttpUriBuilder.invalidPathSegment(), exception.getMessage());

                    assertEquals("/test1", builder.toURI().getRawPath());
                }

                @ParameterizedTest
                @ValueSource(strings = { "test/.", "test/./test", "test/..", "test/../test" })
                void testNonFirstSegmentFails(String path) {
                    HttpUriBuilder builder = HttpUriBuilder.forHost("localhost");

                    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> builder.path("test1", path, "test2"));

                    assertEquals(Messages.HttpUriBuilder.invalidPathSegment(), exception.getMessage());

                    assertEquals("/test1/test", builder.toURI().getRawPath());
                }
            }
        }
    }

    @Nested
    class PathSegment {

        @Nested
        class SinglePathSegment {

            @Test
            void testSimplePathSegment() {
                testSinglePathSegment("test 1&2", "/test+1%262");
            }

            @Test
            void testWithLeadingSlashes() {
                testSinglePathSegment("///test 1&2", "/%2F%2F%2Ftest+1%262");
            }

            @Test
            void testWithTrailingSlashes() {
                testSinglePathSegment("test 1&2///", "/test+1%262%2F%2F%2F");
            }

            @Test
            void testOnlySlashes() {
                testSinglePathSegment("/", "/%2F");
                testSinglePathSegment("///", "/%2F%2F%2F");
            }

            @ParameterizedTest
            @ValueSource(strings = { ".", ".." })
            void withDisallowedAndSlash(String disallowed) {
                testSinglePathSegment(disallowed + "/", "/" + disallowed + "%2F");
                testSinglePathSegment("/" + disallowed, "/%2F" + disallowed);
                testSinglePathSegment("/" + disallowed + "/", "/%2F" + disallowed + "%2F");
            }

            private void testSinglePathSegment(String path, String expectedPath) {
                URI uri = HttpUriBuilder.forHost("localhost")
                        .pathSegment(path)
                        .toURI();

                assertEquals(expectedPath, uri.getRawPath());
            }

            @ParameterizedTest
            @ValueSource(strings = { ".", ".." })
            void testWithDisallowedSegment(String path) {
                HttpUriBuilder builder = HttpUriBuilder.forHost("localhost");

                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> builder.pathSegment(path));

                assertEquals(Messages.HttpUriBuilder.invalidPathSegment(), exception.getMessage());

                assertEquals("/", builder.toURI().getRawPath());
            }
        }

        @Nested
        class MultiplePathSegments {

            @Test
            void testSimplePaths() {
                testMultiplePathSegments("test 1&2", "test 3&4", "test 5&6", "/test+1%262/test+3%264/test+5%266");
            }

            @Test
            void testWithLeadingSlashes() {
                testMultiplePathSegments("///test 1&2", "///test 3&4", "///test 5&6", "/%2F%2F%2Ftest+1%262/%2F%2F%2Ftest+3%264/%2F%2F%2Ftest+5%266");
            }

            @Test
            void testWithTrailingSlashes() {
                testMultiplePathSegments("test 1&2///", "test 3&4///", "test 5&6///", "/test+1%262%2F%2F%2F/test+3%264%2F%2F%2F/test+5%266%2F%2F%2F");
            }

            @Test
            void testOnlySlashes() {
                testMultiplePathSegments("/", "//", "///", "/%2F/%2F%2F/%2F%2F%2F");
            }

            @ParameterizedTest
            @ValueSource(strings = { ".", ".." })
            void withDisallowedAndSlash(String disallowed) {
                testMultiplePathSegments("test1", disallowed + "/", "test2", "/test1/" + disallowed + "%2F/test2");
                testMultiplePathSegments("test1", "/" + disallowed, "test2", "/test1/%2F" + disallowed + "/test2");
                testMultiplePathSegments("test1", "/" + disallowed + "/", "test2", "/test1/%2F" + disallowed + "%2F/test2");
            }

            private void testMultiplePathSegments(String pathSegment1, String pathSegment2, String pathSegment3, String expectedPath) {
                URI uri = HttpUriBuilder.forHost("localhost")
                        .pathSegment(pathSegment1, pathSegment2, pathSegment3)
                        .toURI();

                assertEquals(expectedPath, uri.getRawPath());
            }

            @ParameterizedTest
            @ValueSource(strings = { ".", ".." })
            void testWithDisallowedSegment(String pathSegment) {
                HttpUriBuilder builder = HttpUriBuilder.forHost("localhost");

                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                        () -> builder.pathSegment("test1", pathSegment, "test2"));

                assertEquals(Messages.HttpUriBuilder.invalidPathSegment(), exception.getMessage());

                assertEquals("/test1", builder.toURI().getRawPath());
            }
        }
    }

    @Test
    void testClearPath() {
        HttpUriBuilder builder = HttpUriBuilder.forHost("localhost")
                .path("some/path")
                .pathSegment("with", "segment");

        assertNotEquals("/", builder.toURI().getRawPath());

        builder.clearPath();

        assertEquals("/", builder.toURI().getRawPath());
    }

    @Test
    void testQueryParameter() {
        URI uri = HttpUriBuilder.forHost("localhost")
                .queryParameter("bool", true)
                .queryParameter("int", 13)
                .queryParameter("encoded parameter", "with + and %")
                .toURI();

        assertEquals("bool=true&int=13&encoded+parameter=with+%2B+and+%25", uri.getRawQuery());
        assertEquals("bool=true&int=13&encoded+parameter=with+++and+%", uri.getQuery());
    }

    @Test
    void testQueryParameters() {
        URI uri = HttpUriBuilder.forHost("localhost")
                .queryParameters("bool", true, false)
                .queryParameters("int", 13, 14)
                .queryParameters("encoded parameter", "with + and %", "and more")
                .toURI();

        assertEquals("bool=true&bool=false&int=13&int=14&encoded+parameter=with+%2B+and+%25&encoded+parameter=and+more", uri.getRawQuery());
        assertEquals("bool=true&bool=false&int=13&int=14&encoded+parameter=with+++and+%&encoded+parameter=and+more", uri.getQuery());
    }

    @Test
    void testFragment() {
        URI uri = HttpUriBuilder.forHost("localhost")
                .fragment("fragment with + and %")
                .toURI();

        assertEquals("fragment%20with%20+%20and%20%25", uri.getRawFragment());
        assertEquals("fragment with + and %", uri.getFragment());
    }

    @Nested
    class ToURI {

        @Test
        void testMinimal() {
            URI uri = HttpUriBuilder.forHost("localhost")
                    .toURI();

            assertEquals(URI.create("https://localhost/"), uri);
        }

        @Test
        void testWithPathOnly() {
            URI uri = HttpUriBuilder.forHost("localhost")
                    .path("test1", "test2", "test3/test4", "")
                    .toURI();

            assertEquals(URI.create("https://localhost/test1/test2/test3/test4/"), uri);
        }

        @Test
        void testComplete() {
            HttpUriBuilder builder = HttpUriBuilder.forHost("localhost")
                    .scheme("http")
                    .userInfo("user", "pass")
                    .port(80)
                    .path("test1", "test2", "tést3//test4", "")
                    .queryParameter("param", "value")
                    .fragment("fragment");

            URI uri = builder.toURI();

            assertEquals(URI.create("http://user:pass@localhost:80/test1/test2/t%C3%A9st3/test4/?param=value#fragment"), uri);

            uri = builder.charset(StandardCharsets.US_ASCII).toURI();

            assertEquals(URI.create("http://user:pass@localhost:80/test1/test2/t%3Fst3/test4/?param=value#fragment"), uri);
        }

        @Nested
        class WithPathParamReplacer {

            @Test
            void testMinimal() {
                Map<String, String> params = Map.of("id", "123");

                URI uri = HttpUriBuilder.forHost("localhost")
                        .toURI(params::get);

                assertEquals(URI.create("https://localhost/"), uri);
            }

            @Test
            void testWithPathOnly() {
                Map<String, String> params = Map.of("id", "id/123");

                URI uri = HttpUriBuilder.forHost("localhost")
                        .path("test1", "{id}", "test3/test4", "")
                        .toURI(params::get);

                assertEquals(URI.create("https://localhost/test1/id%2F123/test3/test4/"), uri);
            }

            @Test
            void testComplete() {
                Map<String, String> params = Map.of("id", "id/123/é", "empty", "");

                HttpUriBuilder builder = HttpUriBuilder.forHost("localhost")
                        .scheme("http")
                        .userInfo("user", "pass")
                        .port(80)
                        .path("test1", "{id}", "test3/{empty}/test4", "")
                        .queryParameter("param", "value")
                        .fragment("fragment");

                URI uri = builder.toURI(params::get);

                assertEquals(URI.create("http://user:pass@localhost:80/test1/id%2F123%2F%C3%A9/test3//test4/?param=value#fragment"), uri);

                uri = builder.charset(StandardCharsets.US_ASCII).toURI(params::get);

                assertEquals(URI.create("http://user:pass@localhost:80/test1/id%2F123%2F%3F/test3//test4/?param=value#fragment"), uri);
            }
        }
    }
}
