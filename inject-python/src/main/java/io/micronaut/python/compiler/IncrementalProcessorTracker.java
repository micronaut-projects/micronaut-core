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

import io.micronaut.annotation.processing.AggregatingPackageElementVisitorProcessor;
import io.micronaut.annotation.processing.AggregatingTypeElementVisitorProcessor;
import io.micronaut.annotation.processing.BeanDefinitionInjectProcessor;
import io.micronaut.annotation.processing.MixinVisitorProcessor;
import io.micronaut.annotation.processing.PackageElementVisitorProcessor;
import io.micronaut.annotation.processing.TypeElementVisitorProcessor;
import io.micronaut.python.processing.PythonAnnotationProcessor;

import javax.annotation.processing.Completion;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decorates annotation processors so their Filer outputs can be classified without Gradle.
 */
final class IncrementalProcessorTracker {
    private static final String ISOLATING_MARKER = "org.gradle.annotation.processing.isolating";
    private static final String AGGREGATING_MARKER = "org.gradle.annotation.processing.aggregating";

    private final JavaCompilationTracker compilationTracker;
    private final Path targetDirectory;
    private final Map<String, Set<String>> isolatingOutputs = new LinkedHashMap<>();
    private final Set<String> aggregatingOutputs = new LinkedHashSet<>();
    private final Set<String> pythonProcessorOutputs = new LinkedHashSet<>();
    private final Set<String> contractViolatingOutputs = new LinkedHashSet<>();
    private boolean processorCompatible = true;

    IncrementalProcessorTracker(JavaCompilationTracker compilationTracker, Path targetDirectory) {
        this.compilationTracker = compilationTracker;
        this.targetDirectory = normalizeDirectory(targetDirectory);
    }

    List<Processor> wrap(List<Processor> processors) {
        List<Processor> wrapped = new ArrayList<>(processors.size());
        for (Processor processor : processors) {
            ProcessorKind kind = processorKind(processor);
            if (kind == ProcessorKind.UNKNOWN) {
                processorCompatible = false;
            }
            wrapped.add(new TrackingProcessor(
                processor,
                kind,
                processor instanceof PythonAnnotationProcessor
            ));
        }
        return wrapped;
    }

    Map<String, Set<String>> isolatingOutputs() {
        return isolatingOutputs;
    }

    Set<String> aggregatingOutputs() {
        return aggregatingOutputs;
    }

    Set<String> pythonProcessorOutputs() {
        return pythonProcessorOutputs;
    }

    Set<String> contractViolatingOutputs() {
        return contractViolatingOutputs;
    }

    boolean processorCompatible() {
        return processorCompatible;
    }

    private static ProcessorKind processorKind(Processor processor) {
        Set<String> options = processor.getSupportedOptions();
        boolean isolating = options.contains(ISOLATING_MARKER);
        boolean aggregating = options.contains(AGGREGATING_MARKER);
        if (isolating != aggregating) {
            return isolating ? ProcessorKind.ISOLATING : ProcessorKind.AGGREGATING;
        }
        if (processor instanceof AggregatingTypeElementVisitorProcessor
            || processor instanceof AggregatingPackageElementVisitorProcessor) {
            return ProcessorKind.AGGREGATING;
        }
        if (processor instanceof MixinVisitorProcessor
            || processor instanceof PackageElementVisitorProcessor
            || processor instanceof TypeElementVisitorProcessor
            || processor instanceof BeanDefinitionInjectProcessor
            || processor instanceof PythonAnnotationProcessor) {
            return ProcessorKind.ISOLATING;
        }
        return ProcessorKind.UNKNOWN;
    }

    private static Path normalizeDirectory(Path directory) {
        try {
            return directory.toRealPath().normalize();
        } catch (IOException e) {
            return directory.toAbsolutePath().normalize();
        }
    }

    private final class TrackingProcessor implements Processor {
        private final Processor delegate;
        private final ProcessorKind kind;
        private final boolean pythonProcessor;

        private TrackingProcessor(Processor delegate,
                                  ProcessorKind kind,
                                  boolean pythonProcessor) {
            this.delegate = delegate;
            this.kind = kind;
            this.pythonProcessor = pythonProcessor;
        }

        @Override
        public Set<String> getSupportedOptions() {
            return delegate.getSupportedOptions();
        }

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return delegate.getSupportedAnnotationTypes();
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return delegate.getSupportedSourceVersion();
        }

        @Override
        public void init(ProcessingEnvironment processingEnvironment) {
            delegate.init(new TrackingProcessingEnvironment(
                processingEnvironment,
                new TrackingFiler(processingEnvironment.getFiler(), kind, pythonProcessor)
            ));
        }

        @Override
        @SuppressWarnings("DoNotClaimAnnotations")
        public boolean process(Set<? extends TypeElement> annotations,
                               RoundEnvironment roundEnvironment) {
            return delegate.process(annotations, roundEnvironment);
        }

        @Override
        public Iterable<? extends Completion> getCompletions(Element element,
                                                             AnnotationMirror annotation,
                                                             ExecutableElement member,
                                                             String userText) {
            return delegate.getCompletions(element, annotation, member, userText);
        }
    }

    private final class TrackingFiler implements Filer {
        private final Filer delegate;
        private final ProcessorKind kind;
        private final boolean pythonProcessor;

        private TrackingFiler(Filer delegate,
                              ProcessorKind kind,
                              boolean pythonProcessor) {
            this.delegate = delegate;
            this.kind = kind;
            this.pythonProcessor = pythonProcessor;
        }

        private void trackOutput(FileObject output, Element... originatingElements) {
            String relativeOutput = relativeOutput(output);
            if (relativeOutput == null) {
                processorCompatible = false;
                return;
            }
            if (pythonProcessor) {
                pythonProcessorOutputs.add(relativeOutput);
            }
            if (kind != ProcessorKind.ISOLATING) {
                aggregatingOutputs.add(relativeOutput);
                return;
            }
            if (originatingElements.length != 1) {
                if (pythonProcessor) {
                    // Python processing produces shared VFS indexes, package initializers, and bridge
                    // modules. They are tracked separately and replaced whenever Python is processed.
                    return;
                }
                contractViolatingOutputs.add(relativeOutput);
                return;
            }
            String source = compilationTracker.sourceKey(originatingElements[0]);
            if (source != null) {
                isolatingOutputs.computeIfAbsent(source, ignored -> new LinkedHashSet<>())
                    .add(relativeOutput);
            }
        }

        private String relativeOutput(FileObject output) {
            try {
                Path outputPath = Path.of(output.toUri()).toAbsolutePath().normalize();
                if (!outputPath.startsWith(targetDirectory)) {
                    return null;
                }
                return targetDirectory.relativize(outputPath).toString()
                    .replace(outputPath.getFileSystem().getSeparator(), "/");
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public JavaFileObject createSourceFile(CharSequence name,
                                               Element... originatingElements) throws IOException {
            JavaFileObject output = delegate.createSourceFile(name, originatingElements);
            if (kind == ProcessorKind.ISOLATING) {
                compilationTracker.recordGeneratedSource(
                    output,
                    pythonProcessor,
                    originatingElements
                );
            }
            trackOutput(output, originatingElements);
            return output;
        }

        @Override
        public JavaFileObject createClassFile(CharSequence name,
                                              Element... originatingElements) throws IOException {
            JavaFileObject output = delegate.createClassFile(name, originatingElements);
            trackOutput(output, originatingElements);
            return output;
        }

        @Override
        public FileObject createResource(JavaFileManager.Location location,
                                         CharSequence moduleAndPkg,
                                         CharSequence relativeName,
                                         Element... originatingElements) throws IOException {
            FileObject output = delegate.createResource(location, moduleAndPkg, relativeName, originatingElements);
            trackOutput(output, originatingElements);
            return output;
        }

        @Override
        public FileObject getResource(JavaFileManager.Location location,
                                      CharSequence moduleAndPkg,
                                      CharSequence relativeName) throws IOException {
            return delegate.getResource(location, moduleAndPkg, relativeName);
        }
    }

    private record TrackingProcessingEnvironment(ProcessingEnvironment delegate,
                                                 Filer filer) implements ProcessingEnvironment {
        @Override
        public Map<String, String> getOptions() {
            return delegate.getOptions();
        }

        @Override
        public Messager getMessager() {
            return delegate.getMessager();
        }

        @Override
        public Filer getFiler() {
            return filer;
        }

        @Override
        public Elements getElementUtils() {
            return delegate.getElementUtils();
        }

        @Override
        public Types getTypeUtils() {
            return delegate.getTypeUtils();
        }

        @Override
        public SourceVersion getSourceVersion() {
            return delegate.getSourceVersion();
        }

        @Override
        public Locale getLocale() {
            return delegate.getLocale();
        }

        @Override
        public boolean isPreviewEnabled() {
            return delegate.isPreviewEnabled();
        }
    }

    private enum ProcessorKind {
        ISOLATING,
        AGGREGATING,
        UNKNOWN
    }
}
