package io.micronaut.dev.compile.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Set;

/**
 * Writes, for the classes annotated with {@link Uncredited} a round found, the resource {@code META-INF/uncredited/index}
 * listing them, naming no originating element, as a processor that aggregates may.
 */
@SupportedAnnotationTypes("io.micronaut.dev.compile.processor.Uncredited")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class UncreditedProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        StringBuilder names = new StringBuilder();
        roundEnv.getElementsAnnotatedWith(Uncredited.class).forEach(element -> names.append(element.getSimpleName()).append('\n'));
        if (names.isEmpty()) {
            return false;
        }
        try {
            FileObject resource = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/uncredited/index");
            try (Writer writer = resource.openWriter()) {
                writer.write(names.toString());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return false;
    }
}
