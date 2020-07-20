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
package io.micronaut.core.io;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArgumentUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;

/**
 * Implementation of {@link Readable} for files.
 *
 * @author graemerocher
 * @since 1.1.0
 */
@Internal
class FileReadable implements Readable {

    private final File file;

    /**
     * Default constructor.
     * @param file The file
     */
    FileReadable(@NonNull File file) {
        ArgumentUtils.requireNonNull("file", file);
        this.file = file;
    }

    @NonNull
    @Override
    public InputStream asInputStream() throws IOException {
        return Files.newInputStream(file.toPath());
    }

    @Override
    public Reader asReader() throws IOException {
        return Files.newBufferedReader(file.toPath());
    }

    @Override
    public Reader asReader(Charset charset) throws IOException {
        return Files.newBufferedReader(file.toPath(), charset);
    }

    @Override
    public boolean exists() {
        return file.exists();
    }

    @Override
    public String getName() {
        return file.getName();
    }
}
