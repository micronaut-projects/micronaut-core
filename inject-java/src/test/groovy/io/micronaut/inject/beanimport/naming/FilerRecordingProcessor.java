package io.micronaut.inject.beanimport.naming;

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
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Runs a processor with a {@link Filer} that records the originating elements of every generated file, the way
 * Gradle wraps the filer of an incremental annotation processor.
 */
public final class FilerRecordingProcessor implements Processor {

    private final Processor delegate;
    private final Map<String, List<String>> originatingTypes;

    /**
     * @param delegate         The processor
     * @param originatingTypes Receives, for the path of each generated file, the top level types of its originating elements
     */
    public FilerRecordingProcessor(Processor delegate, Map<String, List<String>> originatingTypes) {
        this.delegate = delegate;
        this.originatingTypes = originatingTypes;
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
    public void init(ProcessingEnvironment processingEnv) {
        delegate.init(new RecordingEnvironment(processingEnv, new RecordingFiler(processingEnv.getFiler())));
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return delegate.process(annotations, roundEnv);
    }

    @Override
    public Iterable<? extends Completion> getCompletions(Element element, AnnotationMirror annotation, ExecutableElement member, String userText) {
        return delegate.getCompletions(element, annotation, member, userText);
    }

    private void record(String path, Element... originatingElements) {
        List<String> types = new ArrayList<>();
        if (originatingElements != null) {
            for (Element originatingElement : originatingElements) {
                if (originatingElement != null) {
                    types.add(topLevelType(originatingElement));
                }
            }
        }
        originatingTypes.put(path, types);
    }

    private static String topLevelType(Element element) {
        Element topLevel = element;
        while (topLevel.getEnclosingElement() instanceof Element enclosing && !(enclosing instanceof PackageElement) && !(enclosing instanceof ModuleElement)) {
            topLevel = enclosing;
        }
        return topLevel instanceof QualifiedNameable qualifiedNameable ? qualifiedNameable.getQualifiedName().toString() : topLevel.toString();
    }

    private final class RecordingFiler implements Filer {
        private final Filer filer;

        private RecordingFiler(Filer filer) {
            this.filer = filer;
        }

        @Override
        public JavaFileObject createSourceFile(CharSequence name, Element... originatingElements) throws IOException {
            record(name.toString().replace('.', '/') + ".java", originatingElements);
            return filer.createSourceFile(name, originatingElements);
        }

        @Override
        public JavaFileObject createClassFile(CharSequence name, Element... originatingElements) throws IOException {
            record(name.toString().replace('.', '/') + ".class", originatingElements);
            return filer.createClassFile(name, originatingElements);
        }

        @Override
        public FileObject createResource(JavaFileManager.Location location, CharSequence moduleAndPkg, CharSequence relativeName, Element... originatingElements) throws IOException {
            String pkg = moduleAndPkg.toString().replace('.', '/');
            record(pkg.isEmpty() ? relativeName.toString() : pkg + "/" + relativeName, originatingElements);
            return filer.createResource(location, moduleAndPkg, relativeName, originatingElements);
        }

        @Override
        public FileObject getResource(JavaFileManager.Location location, CharSequence moduleAndPkg, CharSequence relativeName) throws IOException {
            return filer.getResource(location, moduleAndPkg, relativeName);
        }
    }

    private record RecordingEnvironment(ProcessingEnvironment environment, Filer filer) implements ProcessingEnvironment {

        @Override
        public Map<String, String> getOptions() {
            return environment.getOptions();
        }

        @Override
        public Messager getMessager() {
            return environment.getMessager();
        }

        @Override
        public Filer getFiler() {
            return filer;
        }

        @Override
        public Elements getElementUtils() {
            return environment.getElementUtils();
        }

        @Override
        public Types getTypeUtils() {
            return environment.getTypeUtils();
        }

        @Override
        public SourceVersion getSourceVersion() {
            return environment.getSourceVersion();
        }

        @Override
        public Locale getLocale() {
            return environment.getLocale();
        }

        @Override
        public boolean isPreviewEnabled() {
            return environment.isPreviewEnabled();
        }
    }
}
