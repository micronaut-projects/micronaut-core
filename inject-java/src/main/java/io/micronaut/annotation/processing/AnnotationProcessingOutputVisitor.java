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
package io.micronaut.annotation.processing;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.inject.writer.AbstractClassWriterOutputVisitor;
import io.micronaut.inject.writer.ClassGenerationException;
import io.micronaut.inject.writer.GeneratedFile;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An implementation of {@link io.micronaut.inject.writer.ClassWriterOutputVisitor} for annotation processing.
 *
 * @author Graeme Rocher
 * @since 1.
 */
public class AnnotationProcessingOutputVisitor extends AbstractClassWriterOutputVisitor {

    private final Filer filer;
    private final Map<String, Optional<GeneratedFile>> metaInfFiles = new HashMap<>();
    private final Map<String, FileObject> openedFiles = new HashMap<>();
    private final Map<String, Optional<GeneratedFile>> generatedFiles = new HashMap<>();

    /**
     * @param filer The {@link Filer} for creating new files
     */
    public AnnotationProcessingOutputVisitor(Filer filer) {
        this.filer = filer;
    }

    @Override
    public OutputStream visitClass(String classname, @Nullable io.micronaut.inject.ast.Element originatingElement) throws IOException {
        JavaFileObject javaFileObject;
        if (originatingElement != null) {
            Object nativeType = originatingElement.getNativeType();
            if (nativeType instanceof Element) {
                javaFileObject = filer.createClassFile(classname, (Element) nativeType);
            } else {
                javaFileObject = filer.createClassFile(classname);
            }
        } else {
            javaFileObject = filer.createClassFile(classname);
        }
        return javaFileObject.openOutputStream();

    }

    @Override
    public Optional<GeneratedFile> visitMetaInfFile(String path) {
        return metaInfFiles.computeIfAbsent(path, s -> {
            String finalPath = "META-INF/" + path;
            return Optional.of(new GeneratedFileObject(finalPath));
        });
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedFile(String path) {
        return generatedFiles.computeIfAbsent(path, s -> Optional.of(new GeneratedFileObject(path, StandardLocation.SOURCE_OUTPUT)));
    }

    /**
     * Class to handle generated files by the annotation processor.
     */
    class GeneratedFileObject implements GeneratedFile {

        private final String path;
        private final StandardLocation classOutput;
        private FileObject inputObject;
        private FileObject outputObject;

        /**
         * @param path The path for the generated file
         */
        GeneratedFileObject(String path) {
            this.path = path;
            classOutput = StandardLocation.CLASS_OUTPUT;
        }

        /**
         * @param path The path for the generated file
         * @param location The location
         */
        GeneratedFileObject(String path, StandardLocation location) {
            this.path = path;
            this.classOutput = location;
        }

        @Override
        public URI toURI() {
            try {
                return getOutputObject().toUri();
            } catch (IOException e) {
                throw new ClassGenerationException("Unable to return URI for file object: " + path);
            }
        }

        @Override
        public String getName() {
            return path;
        }

        @Override
        public Writer openWriter() throws IOException {
            return getOutputObject().openWriter();
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            return getOutputObject().openOutputStream();
        }

        @Override
        public InputStream openInputStream() throws IOException {
            if (inputObject == null) {
                inputObject = openFileForReading(path);
            }
            return inputObject.openInputStream();
        }

        @Override
        public Reader openReader() throws IOException {
            if (inputObject == null) {
                inputObject = openFileForReading(path);
            }
            return inputObject.openReader(true);
        }

        @Override
        public CharSequence getTextContent() throws IOException {
            try {
                if (inputObject == null) {
                    inputObject = openFileForReading(path);
                }
                return inputObject.getCharContent(true);
            } catch (FileNotFoundException e) {
                return null;
            }
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

        private FileObject getOutputObject() throws IOException {
            if (outputObject == null) {
                outputObject = filer.createResource(classOutput, "", path);
            }
            return outputObject;
        }
    }

}
