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
    private final Map<String, GeneratedFileObject> metaInfFiles = new LinkedHashMap<>();
    private final Map<String, FileObject> openedFiles = new LinkedHashMap<>();
    private final Map<String, GeneratedFileObject> generatedFiles = new LinkedHashMap<>();
    private final boolean isGradleFiler;
    private boolean finalRound;
    private boolean wholeCompilation;

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
     * Sets whether the files visited from now on follow from the whole compilation, as the files written once every
     * round is over do, instead of from their originating elements alone.
     *
     * <p>Whether a file follows from the whole compilation is recorded when it is visited and when its file is
     * created: a {@link GeneratedFile} obtained now keeps it, even if its file is created later, and one obtained
     * earlier but not created yet gets it, whether it is visited again or written directly now.</p>
     *
     * <p>Such a file is written with every originating element, except to the filer of Gradle. Gradle tracks a file
     * of an isolating processor by the top level types of its originating elements: a file with exactly one is
     * deleted and written again only when that type is compiled again, and a file with none or several disables
     * incremental annotation processing, so that the subsequent affected compilations compile all sources. A file that follows from the whole compilation has to
     * be written again when any source is added, changed or removed, so it is passed to the filer of Gradle without
     * originating element, whatever the number of types its originating elements belong to.</p>
     *
     * @param wholeCompilation Whether the files follow from the whole compilation
     * @since 5.3.0
     */
    @Internal
    public void setWholeCompilation(boolean wholeCompilation) {
        this.wholeCompilation = wholeCompilation;
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
        Element[] nativeOriginatingElements = filerOriginatingElements(originatingElements);
        if (finalRound) {
            int lastDot = classname.lastIndexOf('.');
            String packageName = lastDot == -1 ? "" : classname.substring(0, lastDot);
            String fileName = classname.substring(lastDot + 1) + JavaFileObject.Kind.CLASS.extension;
            return filer.createResource(StandardLocation.CLASS_OUTPUT, packageName, fileName, nativeOriginatingElements).openOutputStream();
        }
        return filer.createClassFile(classname, nativeOriginatingElements).openOutputStream();
    }

    private Element[] filerOriginatingElements(io.micronaut.inject.ast.@Nullable Element... originatingElements) {
        if (!isGradleFiler) {
            // other compilers like the IntelliJ compiler support multiple
            return toJavaElements(originatingElements);
        }
        if (wholeCompilation || ArrayUtils.isEmpty(originatingElements)) {
            // without originating element, Gradle disables incremental annotation processing
            return ELEMENTS_EMPTY_ARRAY;
        }
        // gradle filer only support single originating element for isolating processors
        return toJavaElements(originatingElements[0]);
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
        if (wholeCompilation && isGradleFiler) {
            // without originating element, Gradle disables incremental annotation processing
            writeServiceDescriptor(type, classname);
        } else {
            Element element = originatingElement instanceof ElementProvider jne ? jne.element() : null;
            writeServiceDescriptor(type, classname, element);
        }
    }

    /**
     * Writes the service entry of a generated class, with the first of its originating elements, or, once
     * {@link #setWholeCompilation(boolean)} is set, with the originating elements the class itself is written
     * with.
     *
     * @param type                The fully qualified service name
     * @param classname           The fully qualified class name
     * @param originatingElements The originating elements of the class
     * @since 5.3.0
     */
    @Internal
    public void visitServiceEntry(String type, String classname, io.micronaut.inject.ast.Element... originatingElements) {
        if (wholeCompilation || ArrayUtils.isEmpty(originatingElements)) {
            writeServiceDescriptor(type, classname, filerOriginatingElements(originatingElements));
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
        return inPhase(metaInfFiles.computeIfAbsent(path, s -> {
            String finalPath = "META-INF/" + path;
            Element[] nativeOriginatingElements = toNativeOriginatingElements(originatingElements);
            return new GeneratedFileObject(finalPath, nativeOriginatingElements);
        }));
    }

    /**
     * Records on a visited file that it follows from the whole compilation, if it is visited once every round is
     * over, see {@link #setWholeCompilation(boolean)}.
     *
     * @param generatedFile The visited file
     * @return The file
     */
    private Optional<GeneratedFile> inPhase(GeneratedFileObject generatedFile) {
        if (wholeCompilation) {
            generatedFile.wholeCompilation = true;
        }
        return Optional.of(generatedFile);
    }

    private static Element[] toNativeOriginatingElements(io.micronaut.inject.ast.Element[] originatingElements) {
        return Arrays.stream(originatingElements)
            .map(e -> ((ElementProvider) e).element()).toArray(Element[]::new);
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedFile(String path) {
        return inPhase(generatedFiles.computeIfAbsent(path, s -> new GeneratedFileObject(path, StandardLocation.SOURCE_OUTPUT)));
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedFile(String path, io.micronaut.inject.ast.Element... originatingElements) {
        Element[] nativeOriginatingElements = toNativeOriginatingElements(originatingElements);
        return inPhase(generatedFiles.computeIfAbsent(path, s -> new GeneratedFileObject(path, StandardLocation.SOURCE_OUTPUT, nativeOriginatingElements)));
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedSourceFile(String packageName, String fileNameWithoutExtension, io.micronaut.inject.ast.Element... originatingElements) {
        Element[] nativeOriginatingElements = toNativeOriginatingElements(originatingElements);
        var path = packageName.replace('.', File.separatorChar) + File.separator + fileNameWithoutExtension + ".java";
        return inPhase(generatedFiles.computeIfAbsent(path, s -> new GeneratedFileObject(packageName, fileNameWithoutExtension, nativeOriginatingElements)));
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
        /**
         * Whether the file was visited once every round is over, see {@link #setWholeCompilation(boolean)}.
         */
        private boolean wholeCompilation;
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
                // the file follows from the whole compilation if it was visited, or its file is created, once every
                // round is over: without originating element, Gradle disables incremental annotation processing
                boolean followsFromCompilation = wholeCompilation || AnnotationProcessingOutputVisitor.this.wholeCompilation;
                Element[] elements = followsFromCompilation && isGradleFiler ? ELEMENTS_EMPTY_ARRAY : originatingElements;
                if (packageName != null && fileName != null) {
                    outputObject = filer.createSourceFile(
                        packageName + "." + fileName,
                        elements
                    );
                } else {
                    outputObject = filer.createResource(classOutput, "", path, elements);
                }
            }
            return outputObject;
        }
    }

}
