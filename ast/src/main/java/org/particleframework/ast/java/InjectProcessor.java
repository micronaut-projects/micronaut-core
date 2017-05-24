package org.particleframework.ast.java;

import com.google.auto.service.AutoService;
import org.particleframework.context.annotation.Configuration;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import java.util.Set;

/**
 * Created by maqsoodi on 5/24/2017.
 */
@SupportedAnnotationTypes({"org.particleframework.context.annotation.Configuration",
        "org.particleframework.context.annotation.Requires"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class InjectProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        for (final Element element : roundEnv.getElementsAnnotatedWith(Configuration.class)) {

            if (element instanceof TypeElement) {
                final TypeElement typeElement = (TypeElement) element;

                for (final Element enclosedElement : typeElement.getEnclosedElements()) {
                    if (enclosedElement instanceof VariableElement) {
                        final VariableElement variableElement = (VariableElement) enclosedElement;
                        if (!variableElement.getModifiers().contains(Modifier.FINAL)) {
                            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                    String.format(
                                            "Class '%s' is annotated as @Immutable, but field '%s' is not declared as final",
                                            typeElement.getSimpleName(), variableElement.getSimpleName()
                                    )
                            );
                        }
                    }
                }
            }
        }

        // Claiming that annotations have been processed by this processor
        return true;
    }
}
