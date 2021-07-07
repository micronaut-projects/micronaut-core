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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.writer.AbstractClassWriterOutputVisitor;
import io.micronaut.inject.writer.ClassGenerationException;
import io.micronaut.inject.writer.GeneratedFile;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.FileNotFoundException;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.*;

/**
 * An implementation of {@link io.micronaut.inject.writer.ClassWriterOutputVisitor} for annotation processing.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class AnnotationProcessingOutputVisitor extends AbstractClassWriterOutputVisitor {

    private static final Field FILTER_OUTPUT_STREAM_OUT = ReflectionUtils.findField(FilterOutputStream.class, "out")
        .map(field -> {
            try {
                addOpenJavaModules(FilterOutputStream.class, AnnotationProcessingOutputVisitor.class);
                field.setAccessible(true);
                return field;
            } catch (Exception e) {
                return null;
            }
        })
        .orElse(null);

    private final Filer filer;
    private final Map<String, Optional<GeneratedFile>> metaInfFiles = new LinkedHashMap<>();
    private final Map<String, FileObject> openedFiles = new LinkedHashMap<>();
    private final Map<String, Optional<GeneratedFile>> generatedFiles = new LinkedHashMap<>();
    private final boolean isGradleFiler;

    /**
     * @param filer The {@link Filer} for creating new files
     */
    public AnnotationProcessingOutputVisitor(Filer filer) {
        super(isEclipseFiler(filer));
        this.filer = filer;
        final String filerName = filer.getClass().getName();
        this.isGradleFiler = filerName.startsWith("org.gradle.api") || filerName.startsWith("org.jetbrains.kotlin.kapt3");
    }

    //--add-opens=java.base/$hostPackageName=ALL-UNNAMED
    private static void addOpenJavaModules(Class<?> hostClass, Class<?> targetClass) {
        // For Java 9 and above
        try {
            Method getModule = Class.class.getMethod("getModule");
            Class<?> module = getModule.getReturnType();
            Method getPackageName = Class.class.getMethod("getPackageName");
            Method addOpens = module.getMethod("addOpens", String.class, module);
            Object hostModule = getModule.invoke(hostClass);
            String hostPackageName = (String) getPackageName.invoke(hostClass);
            Object actionModule = getModule.invoke(targetClass);
            addOpens.invoke(hostModule, hostPackageName, actionModule);
        } catch (Exception e) {
            // Ignore
        }
    }

    private static boolean isEclipseFiler(Filer filer) {
        return filer != null && filer.getClass().getTypeName().startsWith("org.eclipse.jdt");
    }

    @Override
    public OutputStream visitClass(String classname, @Nullable io.micronaut.inject.ast.Element originatingElement) throws IOException {
        return visitClass(classname, new io.micronaut.inject.ast.Element[]{originatingElement});
    }

    @Override
    public OutputStream visitClass(String classname, io.micronaut.inject.ast.Element... originatingElements) throws IOException {
        JavaFileObject javaFileObject;
        Element[] nativeOriginatingElements;
        if (ArrayUtils.isNotEmpty(originatingElements)) {
            if (isGradleFiler) {
                // gradle filer only support single originating element for isolating processors
                final io.micronaut.inject.ast.Element e = originatingElements[0];
                final Object nativeType = e.getNativeType();
                if (nativeType instanceof Element) {
                    nativeOriginatingElements = new Element[] { (Element) nativeType };
                } else {
                    nativeOriginatingElements = new Element[0];
                }
            } else {
                // other compilers like the IntelliJ compiler support multiple
                List<Element> list = new ArrayList<>(originatingElements.length);
                for (io.micronaut.inject.ast.Element originatingElement : originatingElements) {
                    Object nativeType = originatingElement.getNativeType();
                    if (nativeType instanceof Element) {
                        list.add((Element) nativeType);
                    }
                }
                nativeOriginatingElements = list.toArray(new Element[0]);
            }
        } else {
            nativeOriginatingElements = new Element[0];
        }
        javaFileObject = filer.createClassFile(classname, nativeOriginatingElements);
        OutputStream os = javaFileObject.openOutputStream();
        return unwrapFilterOutputStream(os);
    }

    private OutputStream unwrapFilterOutputStream(OutputStream os) {
        // https://bugs.openjdk.java.net/browse/JDK-8255729
        // FilterOutputStream and JavacFiler$FilerOutputStream is always using write(int) and killing performance, unwrap if possible
        if (FILTER_OUTPUT_STREAM_OUT != null && os instanceof FilterOutputStream) {
            try {
                OutputStream osToWrite = (OutputStream) FILTER_OUTPUT_STREAM_OUT.get(os);
                if (osToWrite == null) {
                    return os;
                }
                return new OutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        osToWrite.write(b);
                    }

                    @Override
                    public void write(byte[] b) throws IOException {
                        osToWrite.write(b);
                    }

                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        osToWrite.write(b, off, len);
                    }

                    @Override
                    public void flush() throws IOException {
                        osToWrite.flush();
                    }

                    @Override
                    public void close() throws IOException {
                        // Close original output stream
                        os.close();
                    }
                };
            } catch (Exception e) {
                // Use original output stream if we cannot unwrap it
                return os;
            }
        }
        return os;
    }

    @Override
    @Deprecated
    public Optional<GeneratedFile> visitMetaInfFile(String path) {
        return visitMetaInfFile(path, io.micronaut.inject.ast.Element.EMPTY_ELEMENT_ARRAY);
    }

    @Override
    public Optional<GeneratedFile> visitMetaInfFile(String path, io.micronaut.inject.ast.Element... originatingElements) {
        return metaInfFiles.computeIfAbsent(path, s -> {
            String finalPath = "META-INF/" + path;
            Element[] nativeOriginatingElements = Arrays.stream(originatingElements)
                    .map(e -> (Element) e.getNativeType()).toArray(Element[]::new);
            return Optional.of(new GeneratedFileObject(finalPath, nativeOriginatingElements));
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
        private final Element[] originatingElements;
        private FileObject inputObject;
        private FileObject outputObject;

        /**
         * @param path                The path for the generated file
         * @param originatingElements the originating elements
         */
        GeneratedFileObject(String path, Element... originatingElements) {
            this.path = path;
            this.classOutput = StandardLocation.CLASS_OUTPUT;
            this.originatingElements = originatingElements;
        }

        /**
         * @param path                The path for the generated file
         * @param location            The location
         * @param originatingElements The originating elements
         */
        GeneratedFileObject(String path, StandardLocation location, Element... originatingElements) {
            this.path = path;
            this.classOutput = location;
            this.originatingElements = originatingElements;
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
                outputObject = filer.createResource(classOutput, "", path, originatingElements);
            }
            return outputObject;
        }
    }

}
