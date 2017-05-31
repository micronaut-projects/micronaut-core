package org.particleframework.ast.java;

import org.particleframework.inject.writer.BeanConfigurationWriter;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.io.File;
import java.util.Set;

/**
 * Created by maqsoodi on 5/24/2017.
 */
@SupportedAnnotationTypes({"org.particleframework.context.annotation.Configuration"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class InjectProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            for (final Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (element.getSimpleName().contentEquals("package-info")) {
                    try {
                        BeanConfigurationWriter writer = new BeanConfigurationWriter(annotation.getQualifiedName().toString());
                        writer.writeTo(new File("/abc/"));
                    } catch (Throwable e) {
                        new Exception("Error generating bean configuration for package-info class [${element.simplename}]: $e.message");
                    }
                }
            }
        }
        // return true once annotations have been processed by this processor
        return true;
    }

}
