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

import io.micronaut.annotation.processing.visitor.ElementProvider;
import io.micronaut.annotation.processing.visitor.JavaNativeElement;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.writer.AbstractClassWriterOutputVisitor;
import io.micronaut.inject.writer.ClassGenerationException;
import io.micronaut.inject.writer.GeneratedFile;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An implementation of {@link io.micronaut.inject.writer.ClassWriterOutputVisitor} for annotation processing.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class AnnotationProcessingOutputVisitor extends AbstractClassWriterOutputVisitor {

    private static final Element[] ELEMENTS_EMPTY_ARRAY = new Element[0];

    private final Filer filer;
    private final Map<String, Optional<GeneratedFile>> metaInfFiles = new LinkedHashMap<>();
    private final Map<String, FileObject> openedFiles = new LinkedHashMap<>();
    private final Map<String, Optional<GeneratedFile>> generatedFiles = new LinkedHashMap<>();
    private final boolean isGradleFiler;
    private boolean finalRound;
    private boolean everyOriginatingElement;

    /**
     * @param filer The {@link Filer} for creating new files
     */
    public AnnotationProcessingOutputVisitor(Filer filer) {
        super(isEclipseFiler(filer));
        this.filer = filer;
        if (filer != null) {
            final String filerName = filer.getClass().getName();
            this.isGradleFiler = filerName.startsWith("org.gradle.api") || filerName.startsWith("org.jetbrains.kotlin.kapt3");
        } else {
            this.isGradleFiler = false;
        }
    }

    /**
     * Marks the final annotation processing round: the classes visited from now on are written as class output
     * resources. javac warns about every class file created with {@link Filer#createClassFile} in the final round,
     * since it is not processed anymore, which a written bean definition does not need to be.
     *
     * @since 5.3.0
     */
    @Internal
    public void finalRound() {
        this.finalRound = true;
    }

    /**
     * Sets whether the files visited from now on are written with every originating element, as the outputs of
     * everything the compilation contained, instead of the first originating element only for the filer of Gradle.
     *
     * <p>The filer of an isolating Gradle processor tracks a file with exactly one originating type, and recompiles
     * every source in the next incremental compilation when a file has several. A file written once every round is
     * over, for what several types contained, has to be recompiled with them, so it is written with all of them.</p>
     *
     * @param everyOriginatingElement Whether every originating element is passed to the filer
     * @since 5.3.0
     */
    @Internal
    public void setEveryOriginatingElement(boolean everyOriginatingElement) {
        this.everyOriginatingElement = everyOriginatingElement;
    }

    private static boolean isEclipseFiler(Filer filer) {
        return filer != null && filer.getClass().getTypeName().startsWith("org.eclipse.jdt");
    }

    @Override
    public OutputStream visitClass(String classname, io.micronaut.inject.ast.@Nullable Element originatingElement) throws IOException {
        return visitClass(classname, new io.micronaut.inject.ast.Element[] {originatingElement});
    }

    @Override
    public OutputStream visitClass(String classname, io.micronaut.inject.ast.Element... originatingElements) throws IOException {
        Element[] nativeOriginatingElements;
        if (isGradleFiler && !everyOriginatingElement && ArrayUtils.isNotEmpty(originatingElements)) {
            // gradle filer only support single originating element for isolating processors
            nativeOriginatingElements = toJavaElements(originatingElements[0]);
        } else {
            // other compilers like the IntelliJ compiler support multiple
            nativeOriginatingElements = toJavaElements(originatingElements);
        }
        if (finalRound) {
            int lastDot = classname.lastIndexOf('.');
            String packageName = lastDot == -1 ? "" : classname.substring(0, lastDot);
            String fileName = classname.substring(lastDot + 1) + JavaFileObject.Kind.CLASS.extension;
            return filer.createResource(StandardLocation.CLASS_OUTPUT, packageName, fileName, nativeOriginatingElements).openOutputStream();
        }
        return filer.createClassFile(classname, nativeOriginatingElements).openOutputStream();
    }

    private static Element[] toJavaElements(io.micronaut.inject.ast.@Nullable Element... originatingElements) {
        if (ArrayUtils.isEmpty(originatingElements)) {
            return ELEMENTS_EMPTY_ARRAY;
        }
        var list = new ArrayList<Element>(originatingElements.length);
        for (io.micronaut.inject.ast.Element originatingElement : originatingElements) {
            if (originatingElement != null && originatingElement.getNativeType() instanceof JavaNativeElement javaNativeElement) {
                list.add(javaNativeElement.element());
            }
        }
        return list.toArray(ELEMENTS_EMPTY_ARRAY);
    }

    @Override
    public void visitServiceDescriptor(String type, String classname, io.micronaut.inject.ast.Element originatingElement) {
        Element element = originatingElement instanceof ElementProvider jne ? jne.element() : null;
        writeServiceDescriptor(type, classname, element);
    }

    /**
     * Writes the service entry of a generated class, with the first of its originating elements, or with every one
     * of them once {@link #setEveryOriginatingElement(boolean)} is set, like the class itself.
     *
     * @param type                The fully qualified service name
     * @param classname           The fully qualified class name
     * @param originatingElements The originating elements of the class
     * @since 5.3.0
     */
    @Internal
    public void visitServiceEntry(String type, String classname, io.micronaut.inject.ast.Element... originatingElements) {
        if (everyOriginatingElement || ArrayUtils.isEmpty(originatingElements)) {
            writeServiceDescriptor(type, classname, toJavaElements(originatingElements));
        } else {
            visitServiceDescriptor(type, classname, originatingElements[0]);
        }
    }

    @SuppressWarnings("java:S1075")
    private void writeServiceDescriptor(String type, String classname, Element... originatingElements) {
        final String path = "META-INF/micronaut/" + type + "/" + classname;
        try {
            final FileObject fileObject = filer.createResource(
                StandardLocation.CLASS_OUTPUT,
                "",
                path,
                originatingElements
            );
            try (Writer w = fileObject.openWriter()) {
                w.write("");
            }
        } catch (IOException e) {
            throw new ClassGenerationException("Unable to generate Bean entry at path: " + path, e);
        }
    }

    @Override
    public Optional<GeneratedFile> visitMetaInfFile(String path, io.micronaut.inject.ast.Element... originatingElements) {
        return metaInfFiles.computeIfAbsent(path, s -> {
            String finalPath = "META-INF/" + path;
            Element[] nativeOriginatingElements = toNativeOriginatingElements(originatingElements);
            return Optional.of(new GeneratedFileObject(finalPath, nativeOriginatingElements));
        });
    }

    private static Element[] toNativeOriginatingElements(io.micronaut.inject.ast.Element[] originatingElements) {
        return Arrays.stream(originatingElements)
            .map(e -> ((ElementProvider) e).element()).toArray(Element[]::new);
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedFile(String path) {
        return generatedFiles.computeIfAbsent(path, s -> Optional.of(new GeneratedFileObject(path, StandardLocation.SOURCE_OUTPUT)));
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedFile(String path, io.micronaut.inject.ast.Element... originatingElements) {
        Element[] nativeOriginatingElements = toNativeOriginatingElements(originatingElements);
        return generatedFiles.computeIfAbsent(path, s -> Optional.of(new GeneratedFileObject(path, StandardLocation.SOURCE_OUTPUT, nativeOriginatingElements)));
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedSourceFile(String packageName, String fileNameWithoutExtension, io.micronaut.inject.ast.Element... originatingElements) {
        Element[] nativeOriginatingElements = toNativeOriginatingElements(originatingElements);
        var path = packageName.replace('.', File.separatorChar) + File.separator + fileNameWithoutExtension + ".java";
        return generatedFiles.computeIfAbsent(path, s -> Optional.of(new GeneratedFileObject(packageName, fileNameWithoutExtension, nativeOriginatingElements)));
    }

    /**
     * Class to handle generated files by the annotation processor.
     */
    class GeneratedFileObject implements GeneratedFile {

        private final String packageName;
        private final String fileName;
        private final String path;
        private final StandardLocation classOutput;
        private final Element[] originatingElements;
        private FileObject inputObject;
        private FileObject outputObject;

        /**
         * @param packageName The package of the generated source file
         * @param fileName The name of the source file, without extension
         * @param originatingElements the originating elements
         */
        GeneratedFileObject(String packageName, String fileName, Element... originatingElements) {
            this.path = packageName.replace('.', File.separatorChar);
            this.packageName = packageName;
            this.fileName = fileName;
            this.classOutput = StandardLocation.SOURCE_OUTPUT;
            this.originatingElements = originatingElements;
        }

        /**
         * @param path The path for the generated file
         * @param originatingElements the originating elements
         */
        GeneratedFileObject(String path, Element... originatingElements) {
            this.path = path;
            this.classOutput = StandardLocation.CLASS_OUTPUT;
            this.originatingElements = originatingElements;
            this.packageName = null;
            this.fileName = null;
        }

        /**
         * @param path The path for the generated file
         * @param location The location
         * @param originatingElements The originating elements
         */
        GeneratedFileObject(String path, StandardLocation location, Element... originatingElements) {
            this.path = path;
            this.classOutput = location;
            this.originatingElements = originatingElements;
            this.packageName = null;
            this.fileName = null;
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
                if (packageName != null && fileName != null) {
                    outputObject = filer.createSourceFile(
                        packageName + "." + fileName,
                        originatingElements
                    );
                } else {
                    outputObject = filer.createResource(classOutput, "", path, originatingElements);
                }
            }
            return outputObject;
        }
    }

}
