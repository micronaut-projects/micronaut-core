package io.micronaut.dev.compile.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Writes one source {@code generated.AggregateOf} and one resource
 * {@code META-INF/aggregated}, each naming the classes annotated with {@link Aggregated} that it was given and
 * credited to all of them, as a processor that aggregates what a compilation visited does.
 */
@SupportedAnnotationTypes("io.micronaut.dev.compile.processor.Aggregated")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class AggregatedProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Aggregated.class);
        if (elements.isEmpty()) {
            return false;
        }
        Element[] origins = elements.toArray(new Element[0]);
        String names = elements.stream().map(element -> element.getSimpleName().toString()).collect(Collectors.toCollection(TreeSet::new)).toString();
        try {
            JavaFileObject source = processingEnv.getFiler().createSourceFile("generated.AggregateOf", origins);
            try (Writer writer = source.openWriter()) {
                writer.write("package generated; public class AggregateOf { public static String names() { return \"" + names + "\"; } }");
            }
            FileObject resource = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/aggregated", origins);
            try (Writer writer = resource.openWriter()) {
                writer.write(names);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return false;
    }
}
