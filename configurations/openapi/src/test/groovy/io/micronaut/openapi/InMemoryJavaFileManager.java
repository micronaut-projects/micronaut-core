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
package io.micronaut.openapi;

import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Map.Entry;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;

/**
 * A file manager implementation that stores all output in memory.
 *
 * @author Gregory Kick
 */
final class InMemoryJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    private final LoadingCache<URI, JavaFileObject> inMemoryFileObjects =
            CacheBuilder.newBuilder().build(new CacheLoader<URI, JavaFileObject>() {
                @Override
                public JavaFileObject load(URI key) {
                    return new InMemoryJavaFileManager.InMemoryJavaFileObject(key);
                }
            });

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
            return inMemoryFileObjects.getIfPresent(
                    uriForFileObject(location, packageName, relativeName));
        } else {
            return super.getFileForInput(location, packageName, relativeName);
        }
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location, String className, Kind kind)
            throws IOException {
        if (location.isOutputLocation()) {
            return inMemoryFileObjects.getIfPresent(uriForJavaFileObject(location, className, kind));
        } else {
            return super.getJavaFileForInput(location, className, kind);
        }
    }

    @Override
    public FileObject getFileForOutput(Location location, String packageName,
                                       String relativeName, FileObject sibling) throws IOException {
        URI uri = uriForFileObject(location, packageName, relativeName);
        return inMemoryFileObjects.getUnchecked(uri);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, final Kind kind,
                                               FileObject sibling) throws IOException {
        URI uri = uriForJavaFileObject(location, className, kind);
        return inMemoryFileObjects.getUnchecked(uri);
    }

    ImmutableList<JavaFileObject> getGeneratedSources() {
        ImmutableList.Builder<JavaFileObject> result = ImmutableList.builder();
        for (Entry<URI, JavaFileObject> entry : inMemoryFileObjects.asMap().entrySet()) {
            if (entry.getKey().getPath().startsWith("/" + StandardLocation.SOURCE_OUTPUT.name())
                    && (entry.getValue().getKind() == Kind.SOURCE)) {
                result.add(entry.getValue());
            }
        }
        return result.build();
    }

    ImmutableList<JavaFileObject> getOutputFiles() {
        return ImmutableList.copyOf(inMemoryFileObjects.asMap().values());
    }

    private static final class InMemoryJavaFileObject extends SimpleJavaFileObject
            implements JavaFileObject {
        private long lastModified = 0L;
        private Optional<ByteSource> data = Optional.absent();

        InMemoryJavaFileObject(URI uri) {
            super(uri, deduceKind(uri));
        }

        @Override
        public InputStream openInputStream() throws IOException {
            if (data.isPresent()) {
                return data.get().openStream();
            } else {
                throw new FileNotFoundException();
            }
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            return new ByteArrayOutputStream() {
                @Override
                public void close() throws IOException {
                    super.close();
                    data = Optional.of(ByteSource.wrap(toByteArray()));
                    lastModified = System.currentTimeMillis();
                }
            };
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
            if (data.isPresent()) {
                return data.get().asCharSource(Charset.defaultCharset()).openStream();
            } else {
                throw new FileNotFoundException();
            }
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors)
                throws IOException {
            if (data.isPresent()) {
                return data.get().asCharSource(Charset.defaultCharset()).read();
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
                    data =
                            Optional.of(ByteSource.wrap(toString().getBytes(Charset.defaultCharset())));
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
            this.data = Optional.absent();
            this.lastModified = 0L;
            return true;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("uri", toUri())
                    .add("kind", kind)
                    .toString();
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
