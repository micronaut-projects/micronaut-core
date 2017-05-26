package org.particleframework.ast.java;

import com.google.auto.service.AutoService;
import org.particleframework.ast.groovy.descriptor.ServiceDescriptorGenerator;
import org.particleframework.context.annotation.Configuration;
import org.particleframework.inject.writer.BeanConfigurationWriter;

import javax.annotation.processing.*;
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
@AutoService(Processor.class)
public class InjectProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        for (final Element element : roundEnv.getElementsAnnotatedWith(Configuration.class)) {
            if (element.getSimpleName().contentEquals("package-info")) {
                    try {
                        BeanConfigurationWriter writer = new BeanConfigurationWriter(element.getEnclosedElements().toString());
                        writer.writeTo(new File(element.getEnclosingElement().toString()));
                        ServiceDescriptorGenerator generator = new ServiceDescriptorGenerator();
                        File targetDirectory = new File(element.getEnclosingElement().toString());
                        if (targetDirectory != null) {
                            // looks like this does not required after making writeTo void
                            //generator.generate(targetDirectory, configurationName, BeanConfiguration.class);
                        }
                    } catch (Throwable e) {
                        new Exception("Error generating bean configuration for package-info class [${element.simplename}]: $e.message");
                    }
            }
        }

        // return true once annotations have been processed by this processor
        return true;
    }

}
