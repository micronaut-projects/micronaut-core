package org.particleframework.annotation.processing;

import org.particleframework.context.annotation.Configuration;
import org.particleframework.core.io.service.ServiceDescriptorGenerator;
import org.particleframework.inject.BeanConfiguration;
import org.particleframework.inject.annotation.AnnotationMetadataWriter;
import org.particleframework.inject.writer.BeanConfigurationWriter;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.SimpleElementVisitor8;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;
import java.util.Set;

@SupportedAnnotationTypes({
    "org.particleframework.context.annotation.Configuration"
})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class PackageConfigurationInjectProcessor extends AbstractInjectAnnotationProcessor {

    private ServiceDescriptorGenerator serviceDescriptorGenerator;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        serviceDescriptorGenerator = new ServiceDescriptorGenerator();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        AnnotationElementScanner scanner = new AnnotationElementScanner();
        Set<? extends Element> elements = roundEnv.getRootElements();
        ElementFilter.packagesIn(elements).forEach(element -> element.accept(scanner, element));
        return true;
    }

    class AnnotationElementScanner extends SimpleElementVisitor8<Object, Object> {
        @Override
        public Object visitPackage(PackageElement packageElement, Object p) {
            Object aPackage = super.visitPackage(packageElement, p);
            if (annotationUtils.hasStereotype(packageElement, Configuration.class)) {
                String packageName = packageElement.getQualifiedName().toString();
                BeanConfigurationWriter writer = new BeanConfigurationWriter(packageName, annotationUtils.getAnnotationMetadata(packageElement));
                String configurationClassName = writer.getConfigurationClassName();
                note("Creating class file %s for @Configuration in package-info", configurationClassName);
                try {
                    JavaFileObject javaFileObject =
                        filer.createClassFile(configurationClassName, packageElement);

                    AnnotationMetadataWriter annotationMetadataWriter = writer.getAnnotationMetadataWriter();
                    if(annotationMetadataWriter != null) {
                        JavaFileObject annotationMetadataFile = filer.createClassFile(annotationMetadataWriter.getClassName(), packageElement);
                        try (OutputStream out = annotationMetadataFile.openOutputStream()) {
                            annotationMetadataWriter.writeTo(out);
                        }
                    }
                    try (OutputStream out = javaFileObject.openOutputStream()) {
                        writer.writeTo(out);
                    }
                    if (configurationClassName != null) {
                        Optional<File> targetDirectory = getTargetDirectory();
                        if(targetDirectory.isPresent()) {
                            serviceDescriptorGenerator.generate(targetDirectory.get(), configurationClassName, BeanConfiguration.class);
                        }
                    }
                } catch (IOException e) {
                    error("Unexpected error: %s", e.getMessage());
                }
            }
            return aPackage;
        }
    }
}
