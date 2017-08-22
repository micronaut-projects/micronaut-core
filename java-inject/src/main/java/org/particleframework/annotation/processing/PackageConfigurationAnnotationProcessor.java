package org.particleframework.annotation.processing;

import org.particleframework.core.io.service.ServiceDescriptorGenerator;
import org.particleframework.inject.BeanConfiguration;
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
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;

@SupportedAnnotationTypes({
    "org.particleframework.context.annotation.Configuration"
})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class PackageConfigurationAnnotationProcessor extends AbstractInjectAnnotationProcessor {

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
            if (annotationUtils.hasStereotype(packageElement, org.particleframework.context.annotation.Configuration.class)) {
                String packageName = packageElement.getQualifiedName().toString();
                BeanConfigurationWriter writer = new BeanConfigurationWriter(packageName);
                String configurationClassName = writer.getConfigurationClassName();
                note("CREATING NEW CLASS FILE %s for @Configuration in package-info", configurationClassName);
                try {
                    JavaFileObject javaFileObject =
                        filer.createClassFile(configurationClassName, packageElement);
                    try (OutputStream out = javaFileObject.openOutputStream()) {
                        writer.writeTo(out);
                    }
                    if (configurationClassName != null) {
                        serviceDescriptorGenerator.generate(targetDirectory, configurationClassName, BeanConfiguration.class);
                    }
                } catch (IOException e) {
                    error("Unexpected error: %s", e.getMessage());
                    // FIXME something is wrong, probably want to fail fast
                    e.printStackTrace();
                }
            }
            return aPackage;
        }
    }
}
