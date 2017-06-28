package org.particleframework.ast.java;

import org.particleframework.ast.groovy.descriptor.ServiceDescriptorGenerator;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.BeanDefinitionClass;
import org.particleframework.inject.writer.BeanDefinitionClassWriter;
import org.particleframework.inject.writer.BeanDefinitionWriter;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SupportedAnnotationTypes({"javax.inject.Inject", "javax.inject.Singleton"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class SpikeAnnotationProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {

            File targetDirectory = new File("./ast/build/classes/test/");

            for (TypeElement annotation : annotations) {
                for (final Element element : roundEnv.getElementsAnnotatedWith(annotation)) {

                    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                        String.format("found @%s for %s definition at %s",
                            annotation.getSimpleName(), element.getKind(), element));


                    if (element.getKind() == ElementKind.METHOD) {
                        String generatedDefinitionClassName = "org.particleframework.inject.field.$JavaClassDefinition";
                        generateBeanDefinitionServiceDescriptor(targetDirectory, generatedDefinitionClassName);

                        BeanDefinitionWriter beanDefinitionWriter = new BeanDefinitionWriter("org.particleframework.inject.field", "JavaClass", null, true);
                        beanDefinitionWriter.visitBeanDefinitionConstructor();

                        ExecutableType typeMirror = (ExecutableType) element.asType();
                        List<? extends TypeMirror> parameterTypes = typeMirror.getParameterTypes();
                        Map m = new LinkedHashMap();
                        TypeMirror tm = parameterTypes.get(0);
                        TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(tm.toString());
                        m.put("ji", typeElement.toString());
                        beanDefinitionWriter.visitMethodInjectionPoint(false, Void.TYPE, "setJavaInterface", m);
                        beanDefinitionWriter.visitBeanDefinitionEnd();
                        beanDefinitionWriter.writeTo(targetDirectory);


                        String beanDefinitionName = beanDefinitionWriter.getBeanDefinitionName();
                        BeanDefinitionClassWriter beanClassWriter = new BeanDefinitionClassWriter("org.particleframework.inject.field.JavaClass", beanDefinitionName);
                        beanClassWriter.writeTo(targetDirectory);
                        generateBeanDefinitionClassServiceDescriptor(targetDirectory, beanClassWriter);

                    } else if(element.getKind() == ElementKind.CLASS){
                        String generatedDefinitionClassName = "org.particleframework.inject.field.$JavaSingletonDefinition";
                        String beanClassName = "JavaSingleton";
                        String fullyQualifiedBeanClassName = "org.particleframework.inject.field.JavaSingleton";
                        String packageName = "org.particleframework.inject.field";

                        generateSupportFiles(targetDirectory, generatedDefinitionClassName, beanClassName, fullyQualifiedBeanClassName, packageName, targetDirectory);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    /*
        String generatedDefinitionClassName = "org.particleframework.inject.field.$JavaSingletonDefinition";
        String beanClassName = "JavaSingleton";
        String fullyQualifiedBeanClassName = "org.particleframework.inject.field.JavaSingleton";
        String packageName = "org.particleframework.inject.field";

     */
    protected void generateSupportFiles(File targetDirectory, String generatedDefinitionClassName, String beanClassName, String fullyQualifiedBeanClassName, String packageName, File compilationDir) throws IOException {
        generateBeanDefinitionServiceDescriptor(targetDirectory, generatedDefinitionClassName);

        String beanDefinitionName = generateBeanDefinitionClass(beanClassName, packageName, compilationDir);
        BeanDefinitionClassWriter beanClassWriter = generateBeanDefinitionClass(fullyQualifiedBeanClassName, compilationDir, beanDefinitionName);

        generateBeanDefinitionClassServiceDescriptor(targetDirectory, beanClassWriter);
    }

    private BeanDefinitionClassWriter generateBeanDefinitionClass(String fullyQualifiedBeanClassName, File compilationDir, String beanDefinitionName) {
        BeanDefinitionClassWriter beanClassWriter = new BeanDefinitionClassWriter(fullyQualifiedBeanClassName, beanDefinitionName);
        beanClassWriter.writeTo(compilationDir);
        return beanClassWriter;
    }

    private void generateBeanDefinitionClassServiceDescriptor(File targetDirectory, BeanDefinitionClassWriter beanClassWriter) {
        ServiceDescriptorGenerator generator = new ServiceDescriptorGenerator();
        if (targetDirectory != null) {
            generator.generate(targetDirectory, beanClassWriter.getBeanDefinitionClassName(), BeanDefinitionClass.class);
        }
    }

    private String generateBeanDefinitionClass(String beanClassName, String packageName, File compilationDir) throws IOException {
        BeanDefinitionWriter beanDefinitionWriter = new BeanDefinitionWriter(packageName, beanClassName, null, true);
        beanDefinitionWriter.visitBeanDefinitionConstructor();

        beanDefinitionWriter.visitBeanDefinitionEnd();
        beanDefinitionWriter.writeTo(compilationDir);

        return beanDefinitionWriter.getBeanDefinitionName();
    }

    private void generateBeanDefinitionServiceDescriptor(File targetDirectory, String generatedDefinitionClassName) {
        new ServiceDescriptorGenerator().generate(targetDirectory, generatedDefinitionClassName, BeanDefinition.class);
    }

}
