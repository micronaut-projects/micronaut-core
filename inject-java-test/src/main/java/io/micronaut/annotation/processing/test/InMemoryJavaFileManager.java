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


import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.*;
import javax.tools.JavaFileObject.Kind;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A file manager implementation that stores all output in memory.
 *
 * NOTE: Forked from Google Compile Testing Project
 *
 * @author Gregory Kick
 */
@SuppressWarnings("all")
final class InMemoryJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> implements Filer {
    private final Map<URI, JavaFileObject> inMemoryFileObjects = new LinkedHashMap<>();

    InMemoryJavaFileManager(JavaFileManager fileManager) {
        super(fileManager);
    }

    private static URI uriForFileObject(Location location, String packageName, String relativeName) {
        StringBuilder uri = new StringBuilder("mem:///").append(location.getName()).append('/');
        if (!packageName.isEmpty()) {
            uri.append(packageName.replace('.', '/')).append('/');
        }
        uri.append(relativeName);
        return URI.create(uri.toString());
    }

    private static URI uriForJavaFileObject(Location location, String className, Kind kind) {
        return URI.create(
                "mem:///" + location.getName() + '/' + className.replace('.', '/') + kind.extension);
    }

    /**
     * Obtain the path for a file.
     * @param name The name
     * @return The path
     */
    public String getMetaInfPath(String name) {
        return uriForFileObject(StandardLocation.CLASS_OUTPUT, "META-INF", name).getPath();
    }

    @Override
    public boolean isSameFile(FileObject a, FileObject b) {
        /* This check is less strict than what is typically done by the normal compiler file managers
         * (e.g. JavacFileManager), but is actually the moral equivalent of what most of the
         * implementations do anyway. We use this check rather than just delegating to the compiler's
         * file manager because file objects for tests generally cause IllegalArgumentExceptions. */
        return a.toUri().equals(b.toUri());
    }

    @Override
    public FileObject getFileForInput(Location location, String packageName,
                                      String relativeName) throws IOException {
        if (location.isOutputLocation()) {
            final URI uri = uriForFileObject(location, packageName, relativeName);
            return inMemoryFileObjects.computeIfPresent(uri, new BiFunction<URI, JavaFileObject, JavaFileObject>() {
                @Override
                public JavaFileObject apply(URI uri, JavaFileObject javaFileObject) {
                    return new InMemoryJavaFileManager.InMemoryJavaFileObject(uri);
                }
            });
        } else {
            return super.getFileForInput(location, packageName, relativeName);
        }
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location, String className, Kind kind)
            throws IOException {
        if (location.isOutputLocation()) {
            final URI uri = uriForJavaFileObject(location, className, kind);
            return inMemoryFileObjects.computeIfPresent(uri, new BiFunction<URI, JavaFileObject, JavaFileObject>() {
                @Override
                public JavaFileObject apply(URI uri, JavaFileObject javaFileObject) {
                    return new InMemoryJavaFileManager.InMemoryJavaFileObject(uri);
                }
            });
        } else {
            return super.getJavaFileForInput(location, className, kind);
        }
    }

    @Override
    public FileObject getFileForOutput(Location location, String packageName,
                                       String relativeName, FileObject sibling) throws IOException {
        URI uri = uriForFileObject(location, packageName, relativeName);
        return inMemoryFileObjects.computeIfAbsent(uri, new Function<URI, JavaFileObject>() {
            @Override
            public JavaFileObject apply(URI uri) {
                return new InMemoryJavaFileManager.InMemoryJavaFileObject(uri);
            }
        });
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, final Kind kind,
                                               FileObject sibling) throws IOException {
        URI uri = uriForJavaFileObject(location, className, kind);
        return inMemoryFileObjects.computeIfAbsent(uri, new Function<URI, JavaFileObject>() {
            @Override
            public JavaFileObject apply(URI uri) {
                return new InMemoryJavaFileManager.InMemoryJavaFileObject(uri);
            }
        });
    }

    Iterable<JavaFileObject> getOutputFiles() {
        return Collections.unmodifiableCollection(inMemoryFileObjects.values());
    }

    @Override
    public JavaFileObject createSourceFile(CharSequence name, Element... originatingElements) throws IOException {
        return getJavaFileForOutput(StandardLocation.SOURCE_OUTPUT, name.toString(), Kind.SOURCE, null);
    }

    @Override
    public JavaFileObject createClassFile(CharSequence name, Element... originatingElements) throws IOException {
        return getJavaFileForOutput(StandardLocation.CLASS_OUTPUT, name.toString(), Kind.SOURCE, null);
    }

    @Override
    public FileObject createResource(Location location, CharSequence pkg, CharSequence relativeName, Element... originatingElements) throws IOException {
        return getFileForOutput(StandardLocation.CLASS_OUTPUT, pkg.toString(), relativeName.toString(), null);
    }

    @Override
    public FileObject getResource(Location location, CharSequence pkg, CharSequence relativeName) throws IOException {
        return getFileForInput(StandardLocation.SOURCE_PATH, pkg.toString(), relativeName.toString());
    }

    private static final class InMemoryJavaFileObject extends SimpleJavaFileObject
            implements JavaFileObject {
        private long lastModified = 0L;
        private Optional<byte[]> data = Optional.empty();

        InMemoryJavaFileObject(URI uri) {
            super(uri, deduceKind(uri));
        }

        @Override
        public InputStream openInputStream() throws IOException {
            if (data.isPresent()) {
                return new ByteArrayInputStream(data.get());
            } else {
                throw new FileNotFoundException();
            }
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            return new ByteArrayOutputStream() {
                @Override
                public void close() throws IOException {
                    final byte[] bytes = toByteArray();
                    data = Optional.of(bytes);
                    super.close();
                }
            };
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
            return new InputStreamReader(openInputStream(), StandardCharsets.UTF_8);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors)
                throws IOException {
            if (data.isPresent()) {
                return new String(data.get(), StandardCharsets.UTF_8);
            } else {
                throw new FileNotFoundException();
            }
        }

        @Override
        public Writer openWriter() throws IOException {
            return new StringWriter() {
                @Override
                public void close() throws IOException {
                    super.close();
                    final byte[] bytes = toString().getBytes(StandardCharsets.UTF_8);
                    data = Optional.of(bytes);
                    lastModified = System.currentTimeMillis();
                }
            };
        }

        @Override
        public long getLastModified() {
            return lastModified;
        }

        @Override
        public boolean delete() {
            this.data = Optional.empty();
            this.lastModified = 0L;
            return true;
        }

        @Override
        public String toString() {
            return "InMemoryJavaFileObject{" +
                    "uri=" + uri +
                    ", kind=" + kind +
                    '}';
        }
    }


    static Kind deduceKind(URI uri) {
        String path = uri.getPath();
        for (Kind kind : Kind.values()) {
            if (path.endsWith(kind.extension)) {
                return kind;
            }
        }
        return Kind.OTHER;
    }
}
