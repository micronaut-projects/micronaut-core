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

/**
 * Generates, for every class annotated with {@link Marked}, a source {@code <Name>Generated} and a
 * resource {@code META-INF/marked/<Name>}, both credited to the annotated class's source as javac's
 * filer does.
 */
@SupportedAnnotationTypes("io.micronaut.dev.compile.processor.Marked")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class MarkedProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Marked.class)) {
            TypeElement type = (TypeElement) element;
            String packageName = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
            String simpleName = type.getSimpleName().toString();
            try {
                JavaFileObject source = processingEnv.getFiler().createSourceFile(packageName + "." + simpleName + "Generated", type);
                try (Writer writer = source.openWriter()) {
                    writer.write("package " + packageName + "; public class " + simpleName + "Generated { }");
                }
                FileObject resource = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/marked/" + simpleName, type);
                try (Writer writer = resource.openWriter()) {
                    writer.write(simpleName);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return false;
    }
}
