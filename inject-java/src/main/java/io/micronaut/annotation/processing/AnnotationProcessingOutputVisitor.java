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
package io.micronaut.annotation.processing;

import io.micronaut.inject.writer.AbstractClassWriterOutputVisitor;
import io.micronaut.inject.writer.ClassGenerationException;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.GeneratedFile;

import javax.annotation.processing.Filer;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An implementation of {@link ClassWriterOutputVisitor} for annotation processing
 *
 * @author Graeme Rocher
 * @since 1.
 */
public class AnnotationProcessingOutputVisitor extends AbstractClassWriterOutputVisitor {

    private final Filer filer;
    private final Map<String, Optional<GeneratedFile>> metaInfFiles = new HashMap<>();
    private final Map<String, FileObject> openedFiles = new HashMap<>();

    AnnotationProcessingOutputVisitor(Filer filer) {
        this.filer = filer;
    }

    @Override
    public OutputStream visitClass(String classname) throws IOException {
        JavaFileObject javaFileObject = filer.createClassFile(classname);
        return javaFileObject.openOutputStream();
    }

    @Override
    public Optional<GeneratedFile> visitMetaInfFile(String path) {
        return metaInfFiles.computeIfAbsent(path, s -> {
            String finalPath = "META-INF/" + path;
            return Optional.of(
                    new GeneratedFileObject(
                            finalPath
                    )
            );
        });
    }

    private FileObject openFileForReading(String path) {
        return openedFiles.computeIfAbsent(path, s -> {
            try {
                return filer.getResource(StandardLocation.CLASS_OUTPUT, "", path);
            } catch (IOException e) {
                throw new ClassGenerationException("Unable to open file for path: " + path, e);
            }
        });
    }

    class GeneratedFileObject implements GeneratedFile  {

        private final String path;

        private FileObject inputObject;

        GeneratedFileObject(String path) {
            this.path = path;
        }

        @Override
        public String getName() {
            return path;
        }

        @Override
        public Writer openWriter() throws IOException {
            return filer.createResource(
                    StandardLocation.CLASS_OUTPUT,
                    "",
                    path
            ).openWriter();
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            return filer.createResource(
                    StandardLocation.CLASS_OUTPUT,
                    "",
                    path
            ).openOutputStream();
        }

        @Override
        public InputStream openInputStream() throws IOException {
            if(inputObject == null) {
                inputObject = openFileForReading(path);
            }
            return inputObject.openInputStream();
        }

        @Override
        public Reader openReader() throws IOException {
            if(inputObject == null) {
                inputObject = openFileForReading(path);
            }
            return inputObject.openReader(true);
        }

        @Override
        public CharSequence getTextContent() throws IOException {
            try {
                if(inputObject == null) {
                    inputObject = openFileForReading(path);
                }
                return inputObject.getCharContent(true);
            } catch (FileNotFoundException e) {
                return null;
            }
        }
    }

}
