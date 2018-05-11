/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.inject.writer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;

/**
 * A {@link GeneratedFile} backed by a {@link File}.
 *
 * @author graemerocher
 * @since 1.0
 */
class FileBackedGeneratedFile implements GeneratedFile {
    private final File file;

    /**
     * @param file The file
     */
    FileBackedGeneratedFile(File file) {
        this.file = file;
    }

    @Override
    public String getName() {
        return file.getName();
    }

    @Override
    public InputStream openInputStream() throws IOException {
        file.getParentFile().mkdirs();
        return new FileInputStream(file);
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        file.getParentFile().mkdirs();
        return new FileOutputStream(file);
    }

    @Override
    public Reader openReader() throws IOException {
        file.getParentFile().mkdirs();
        return new FileReader(file);
    }

    @Override
    public CharSequence getTextContent() throws IOException {
        if (file.exists()) {
            return new String(Files.readAllBytes(file.toPath()));
        }
        return null;
    }

    @Override
    public Writer openWriter() throws IOException {
        file.getParentFile().mkdirs();
        return new FileWriter(file);
    }
}
