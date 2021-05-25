/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.annotation.processing.test;


import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Objects;

import static javax.tools.JavaFileObject.Kind.SOURCE;

/**
 * A utility class for creating {@link JavaFileObject} instances.
 *
 * @author Gregory Kick
 */
final class JavaFileObjects {
    private JavaFileObjects() { }

    /**
     * Creates a {@link JavaFileObject} with a path corresponding to the {@code fullyQualifiedName}
     * containing the give {@code source}. The returned object will always be read-only and have the
     * {@link javax.tools.JavaFileObject.Kind#SOURCE} {@linkplain JavaFileObject#getKind() kind}.
     *
     * <p>Note that this method makes no attempt to verify that the name matches the contents of the
     * source and compilation errors may result if they do not match.
     *
     * @param fullyQualifiedName the fully qualified name
     * @param source The source
     * @return the java file object
     */
    static JavaFileObject forSourceString(String fullyQualifiedName, String source) {
        Objects.requireNonNull(fullyQualifiedName);
        if (fullyQualifiedName.startsWith("package ")) {
            throw new IllegalArgumentException(
                    String.format("fullyQualifiedName starts with \"package\" (%s). Did you forget to "
                            + "specify the name and specify just the source text?",  fullyQualifiedName));
        }
        return new StringSourceJavaFileObject(fullyQualifiedName, Objects.requireNonNull(source));
    }

    /**
     * Behaves exactly like {@link #forSourceString}, but joins lines so that multi-line source
     * strings may omit the newline characters.  For example: <pre>   {@code
     *
     *   JavaFileObjects.forSourceLines("example.HelloWorld",
     *       "package example;",
     *       "",
     *       "final class HelloWorld {",
     *       "  void sayHello() {",
     *       "    System.out.println(\"hello!\");",
     *       "  }",
     *       "}");
     *   }</pre>
     *
     * @param fullyQualifiedName the fully qualified name
     * @param lines The source
     * @return The java file object
     */
    static JavaFileObject forSourceLines(String fullyQualifiedName, String... lines) {
        return forSourceLines(fullyQualifiedName, Arrays.asList(lines));
    }

    /**
     * An overload of {@code #forSourceLines} that takes an {@code Iterable<String>}.
     *
     * @param fullyQualifiedName the fully qualified name
     * @param lines The source
     * @return The java file object
     **/
    static JavaFileObject forSourceLines(String fullyQualifiedName, Iterable<String> lines) {
        return forSourceString(fullyQualifiedName, String.join("\n", lines));
    }

    /**
     * in-memory source file object.
     */
    private static final class StringSourceJavaFileObject extends SimpleJavaFileObject {
        final String source;
        final long lastModified;

        /**
         * Default constructor.
         * @param fullyQualifiedName the fully qualified name
         * @param source The source
         */
        StringSourceJavaFileObject(String fullyQualifiedName, String source) {
            super(createUri(fullyQualifiedName), SOURCE);
            this.source = source;
            this.lastModified = System.currentTimeMillis();
        }

        private static URI createUri(String fullyQualifiedClassName) {
            return URI.create(fullyQualifiedClassName.replace('.', '/')
                    + SOURCE.extension);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }

        @Override
        public OutputStream openOutputStream() {
            throw new IllegalStateException();
        }

        @Override
        public InputStream openInputStream() {
            return new ByteArrayInputStream(source.getBytes(Charset.defaultCharset()));
        }

        @Override
        public Writer openWriter() {
            throw new IllegalStateException();
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) {
            return new StringReader(source);
        }

        @Override
        public long getLastModified() {
            return lastModified;
        }
    }

}
