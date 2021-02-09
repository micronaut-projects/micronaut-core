/*
 * Copyright (C) 2013 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.annotation.processing.test;


import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
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
public final class JavaFileObjects {
    private JavaFileObjects() { }

    /**
     * Creates a {@link JavaFileObject} with a path corresponding to the {@code fullyQualifiedName}
     * containing the give {@code source}. The returned object will always be read-only and have the
     * {@link Kind#SOURCE} {@linkplain JavaFileObject#getKind() kind}.
     *
     * <p>Note that this method makes no attempt to verify that the name matches the contents of the
     * source and compilation errors may result if they do not match.
     */
    public static JavaFileObject forSourceString(String fullyQualifiedName, String source) {
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
     */
    public static JavaFileObject forSourceLines(String fullyQualifiedName, String... lines) {
        return forSourceLines(fullyQualifiedName, Arrays.asList(lines));
    }

    /** An overload of {@code #forSourceLines} that takes an {@code Iterable<String>}. */
    public static JavaFileObject forSourceLines(String fullyQualifiedName, Iterable<String> lines) {
        return forSourceString(fullyQualifiedName, String.join("\n", lines));
    }

    private static final class StringSourceJavaFileObject extends SimpleJavaFileObject {
        final String source;
        final long lastModified;

        StringSourceJavaFileObject(String fullyQualifiedName, String source) {
            super(createUri(fullyQualifiedName), SOURCE);
            // TODO(gak): check that fullyQualifiedName looks like a fully qualified class name
            this.source = source;
            this.lastModified = System.currentTimeMillis();
        }

        static URI createUri(String fullyQualifiedClassName) {
            return URI.create(fullyQualifiedClassName.replace('.','/')
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
