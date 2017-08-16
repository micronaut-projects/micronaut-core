package org.particleframework.annotation.processing;

import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Options;
import org.particleframework.core.io.service.ServiceDescriptorGenerator;
import org.particleframework.inject.BeanConfiguration;
import org.particleframework.inject.writer.BeanConfigurationWriter;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor8;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;

@SupportedAnnotationTypes({
    "org.particleframework.context.annotation.Configuration"
})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class PackageConfigurationAnnotationProcessor extends AbstractProcessor {

    private Messager messager;
    private Filer filer;
    private Elements elementUtils;
    private AnnotationUtils annotationUtils;
    private ServiceDescriptorGenerator serviceDescriptorGenerator;
    private File targetDirectory;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
        this.elementUtils = processingEnv.getElementUtils();
        this.annotationUtils = new AnnotationUtils(elementUtils);
        Options javacOptions = Options.instance(((JavacProcessingEnvironment)processingEnv).getContext());
        this.targetDirectory = new File(javacOptions.get(Option.D));
        serviceDescriptorGenerator = new ServiceDescriptorGenerator();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        AnnotationElementScanner scanner = new AnnotationElementScanner();
        Set<? extends Element> elements = roundEnv.getRootElements();
        ElementFilter.packagesIn(elements).forEach(element -> element.accept(scanner, element));
        return true;
    }

    private void note(Element e, String msg, Object... args) {
        messager.printMessage(Diagnostic.Kind.NOTE, String.format(msg, args), e);
    }

    private void note(String msg, Object... args) {
        messager.printMessage(Diagnostic.Kind.NOTE, String.format(msg, args));
    }

    private void error(Element e, String msg, Object... args) {
        messager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args), e);
    }
    private void error(String msg, Object... args) {
        messager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args));
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
