package io.micronaut.dev.compile.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Set;

/**
 * Writes, for every class annotated with {@link Counted}, a resource {@code META-INF/counted/<Name>} holding how many
 * times this processor's class was initialized for a compilation: a static field, which a class loader kept between
 * compilations keeps too.
 */
@SupportedAnnotationTypes("io.micronaut.dev.compile.processor.Counted")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class CountedProcessor extends AbstractProcessor {

    private static int initializations;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        initializations++;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Counted.class)) {
            if (element.getAnnotation(Counted.class).fail()) {
                throw new IllegalStateException("A processor that fails");
            }
            String simpleName = element.getSimpleName().toString();
            try {
                FileObject resource = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/counted/" + simpleName, element);
                try (Writer writer = resource.openWriter()) {
                    writer.write(Integer.toString(initializations));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return false;
    }
}
