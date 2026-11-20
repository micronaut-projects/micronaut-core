/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.python.compiler;

import io.micronaut.python.processing.staticcompile.StaticLineMap;
import org.jspecify.annotations.Nullable;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileObject;
import javax.tools.JavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Rewrites the class files javac writes for a stub with compiled bodies to the Python positions
 * of the compiled statements (see {@link StaticLineMap}), so that stack traces and debuggers show
 * the Python file and line. The positions are read from the source javac passes as the sibling of
 * a class file; a class file whose source has none, or that cannot be rewritten, is written as
 * javac wrote it.
 */
final class PythonLineMapping {
    private final Map<URI, Optional<StaticLineMap>> maps = new HashMap<>();

    /**
     * @param output  The file javac writes
     * @param kind    The kind of the file
     * @param sibling The file it is written for: the source of a class file
     * @return The file, rewriting a class file of a stub with compiled bodies when it is closed
     */
    JavaFileObject output(JavaFileObject output, JavaFileObject.Kind kind, @Nullable FileObject sibling) {
        if (kind != JavaFileObject.Kind.CLASS || !(sibling instanceof JavaFileObject source) || source.getKind() != JavaFileObject.Kind.SOURCE) {
            return output;
        }
        StaticLineMap map = map(source);
        if (map == null) {
            return output;
        }
        return new ForwardingJavaFileObject<>(output) {
            @Override
            public OutputStream openOutputStream() {
                return new ByteArrayOutputStream() {
                    private boolean closed;

                    @Override
                    public void close() throws IOException {
                        if (closed) {
                            return;
                        }
                        closed = true;
                        try (OutputStream target = fileObject.openOutputStream()) {
                            target.write(map.apply(toByteArray(), fileName(source)));
                        }
                    }
                };
            }
        };
    }

    private static String fileName(JavaFileObject source) {
        String path = source.toUri().getPath();
        if (path == null) {
            path = source.getName();
        }
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private synchronized @Nullable StaticLineMap map(JavaFileObject source) {
        return maps.computeIfAbsent(source.toUri(), ignored -> {
            try {
                return Optional.ofNullable(StaticLineMap.of(source.getCharContent(true)));
            } catch (IOException | RuntimeException e) {
                return Optional.empty();
            }
        }).orElse(null);
    }
}
