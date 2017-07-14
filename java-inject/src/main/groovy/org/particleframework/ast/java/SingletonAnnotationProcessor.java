package org.particleframework.ast.java;

import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Options;
import org.particleframework.core.io.service.ServiceDescriptorGenerator;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.BeanDefinitionClass;
import org.particleframework.inject.writer.BeanDefinitionClassWriter;
import org.particleframework.inject.writer.BeanDefinitionWriter;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

@SupportedAnnotationTypes({
    "javax.inject.Singleton",
    "javax.inject.Inject"
})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class SingletonAnnotationProcessor extends AbstractProcessor {

    private Messager messager;
    private Filer filer;
    private Elements elementUtils;
    private Types typeUtils;

    private Map<String, BeanDefinitionWriterElementWrapper> beanDefinitionwriters = new LinkedHashMap<>();

    private String buildPath;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            generateClassesAndServiceDescriptors();
        } else {
            annotations.stream().forEach(annotation -> {
                note("starting annotation processing for @%s", annotation.getQualifiedName());
                Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(annotation);
                elements.stream().forEach(element -> {
                    ElementKind elementKind = element.getKind();
                    if ("Singleton".equals(annotation.getQualifiedName()) && elementKind != ElementKind.CLASS) {
                        error(element, "@Singleton is only applicable to class, but found it applied to @%s",
                            elementKind);
                    } else {
                            note(element, "Found @%s for class in %s", annotation.getSimpleName(), element);
                            TypeElement typeElement = typeElementFor(element);
                            String fullyQualifiedBeanClassName = typeElement.getQualifiedName().toString();

                            BeanDefinitionWriterElementWrapper wrapper = beanDefinitionwriters.get(fullyQualifiedBeanClassName);
                            if (wrapper == null) {
                                wrapper = new BeanDefinitionWriterElementWrapper();
                                wrapper.beanDefinitionWriter = createBeanDefinitionWriterFor(annotation, element);
                                wrapper.annotationElements = new LinkedHashSet<>();
                                beanDefinitionwriters.put(fullyQualifiedBeanClassName, wrapper);
                            }
                            wrapper.annotationElements.add(element);
                    }});
                }
            );
        }
        return true;
    }

    private void generateClassesAndServiceDescriptors() {
        ServiceDescriptorGenerator generator = new ServiceDescriptorGenerator();
        File targetDirectory = new File(buildPath);
        this.beanDefinitionwriters.values().forEach(wrapper -> {
            BeanDefinitionWriter writer = wrapper.beanDefinitionWriter;
            // TODO determine the ctor stuff
            writer.visitBeanDefinitionConstructor();
            wrapper.annotationElements.forEach(element -> {
                try {
                    switch (element.getKind()) {
                        case CLASS:
                                generateSingletonBeanClass(wrapper.beanDefinitionWriter, element);
                            break;
                        case METHOD:
                            generateMethodInjectionBeanClass(wrapper.beanDefinitionWriter, element);
                            break;
                        case FIELD:
                            generateFieldInjectionBeanClass(wrapper.beanDefinitionWriter, element);
                            break;
                    }
                } catch (IOException e) {
                    error("Unexpected error: %s", e.getMessage());
                    // FIXME something is wrong, probably want to fail fast
                    e.printStackTrace();
                }
            });

            try {
                writer.visitBeanDefinitionEnd();
                JavaFileObject javaFileObject = filer.createClassFile(writer.getBeanDefinitionName(),
                    wrapper.annotationElements.toArray(new Element[wrapper.annotationElements.size()]));
                try (OutputStream outputStream = javaFileObject.openOutputStream()) {
                    writer.writeTo(outputStream);
                }

                String beanDefinitionName = wrapper.beanDefinitionWriter.getBeanDefinitionName();
                String beanTypeName = wrapper.beanDefinitionWriter.getBeanTypeName();
                BeanDefinitionClassWriter beanDefinitionClassWriter = generateBeanDefinitionClass(
                    wrapper.annotationElements, wrapper.beanDefinitionWriter.getBeanDefinitionName(), beanDefinitionName);

                generator.generate(targetDirectory, wrapper.beanDefinitionWriter.getBeanDefinitionName(),BeanDefinition.class);
                generator.generate(targetDirectory, beanDefinitionClassWriter.getBeanDefinitionClassName(),BeanDefinitionClass.class);
            } catch (IOException ioe) {
                error("Unexpected error: %s", ioe.getMessage());
                // FIXME something is wrong, probably want to fail fast
                ioe.printStackTrace();

            }

        });
    }

    private BeanDefinitionWriter createBeanDefinitionWriterFor(TypeElement annotation, Element element) {
        TypeElement typeElement = typeElementFor(element);
        PackageElement packageElement = elementUtils.getPackageOf(element);
        String beanClassName = classNameFor(packageElement, typeElement);
        String packageName = packageElement.getQualifiedName().toString();
        boolean isSingleton = "Singleton".equals(annotation.getSimpleName().toString()) && element.getKind() == ElementKind.CLASS;
        String scope = null; // TODO determine scope?
        return new BeanDefinitionWriter(packageName, beanClassName, scope, isSingleton);
    }

    private TypeElement typeElementFor(Element element) {
        while (ElementKind.CLASS != element.getKind() ) {
            element = element.getEnclosingElement();
        }
        return (TypeElement) element;
    }

    // if it's an inner class org.oci.A.B, we want A.B
    // FIXME deal with static nested classes
    private String classNameFor(PackageElement packageElement, TypeElement typeElement) {
        String qualifiedName = elementUtils.getBinaryName(typeElement).toString();
        return qualifiedName.replaceFirst(packageElement.getQualifiedName().toString() + "\\.","");
    }

    private void generateSingletonBeanClass(BeanDefinitionWriter beanDefinitionWriter, Element element) throws IOException {

    }

    private void generateMethodInjectionBeanClass(BeanDefinitionWriter beanDefinitionWriter, Element element) throws IOException {
        assert (ElementKind.METHOD == element.getKind()) : "element kind must be METHOD";

        Name methodName = element.getSimpleName();
        ExecutableType execType = (ExecutableType) element.asType();
        Map<String,Object> methodArgs = new LinkedHashMap<>();

        List<? extends TypeMirror> parameterTypes = execType.getParameterTypes();
        for (int i = 0; i < parameterTypes.size(); i++) {
            TypeMirror typeMirror = parameterTypes.get(i);
            if (typeMirror.getKind() == TypeKind.ARRAY) {
                // TODO handle array injection
                Map<String,List<Object>> genericTypes = new LinkedHashMap<>();
                ArrayType arrayType = (ArrayType)typeMirror; // FIXME is there an API way of getting this without a cast?
                TypeMirror componentType = arrayType.getComponentType();
                String argName = argNameFromType(componentType.toString(), i);
                methodArgs.put(argName, arrayType.toString());
                genericTypes.put(argName, Collections.singletonList(componentType.toString()));
                beanDefinitionWriter.visitMethodInjectionPoint(false, Void.TYPE, methodName.toString(), methodArgs, null, genericTypes);
            } else {
                TypeElement typeElement = elementUtils.getTypeElement(typeMirror.toString());
                assert (typeElement != null) : "typeElement cannot be null";
                String argName = argNameFromType(typeElement.getSimpleName().toString(), i);
                methodArgs.put(argName, typeElement.toString());
                beanDefinitionWriter.visitMethodInjectionPoint(false, Void.TYPE, methodName.toString(), methodArgs);
            }
        }
    }

    private String argNameFromType(String type, int suffix) {
        String classname = type.replaceFirst("((.*)\\.)?([^\\.]*)", "$3");
        String argName = classname.substring(0,1).toLowerCase();
        if (classname.length() > 1) {
            argName += classname.substring(1);
        }
        return argName + suffix;
    }

    private void generateFieldInjectionBeanClass(BeanDefinitionWriter beanDefinitionWriter, Element element) throws IOException {
        assert (ElementKind.FIELD == element.getKind()) : "element kind must be FIELD";

        Name fieldName = element.getSimpleName();
        TypeMirror fieldType = element.asType();

        beanDefinitionWriter.visitFieldInjectionPoint(true, fieldType.toString(), fieldName.toString());
    }

    private BeanDefinitionClassWriter generateBeanDefinitionClass(
        Set<Element> elements,
        String fullyQualifiedBeanClassName,
        String beanDefinitionName) throws IOException
    {
        BeanDefinitionClassWriter beanClassWriter = new BeanDefinitionClassWriter(fullyQualifiedBeanClassName, beanDefinitionName);
        String classFileName = beanClassWriter.getBeanDefinitionQualifiedClassName();
        note("CREATING NEW CLASS FILE %s for @Singleton", classFileName);
        JavaFileObject javaFileObject = filer.createClassFile(classFileName, elements.toArray(new Element[elements.size()]));
        try (OutputStream out = javaFileObject.openOutputStream()) {
            beanClassWriter.writeTo(out);
        }
        return beanClassWriter;
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();
        Options javacOptions = Options.instance(((JavacProcessingEnvironment)processingEnv).getContext());
        this.buildPath = javacOptions.get(Option.D);
        Map<String, String> options = processingEnv.getOptions();
        note("Options passed to annotation processor are %s", options);
    }

    private void error(Element e, String msg, Object... args) {
        messager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args), e);
    }
    private void error(String msg, Object... args) {
        messager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args));
    }

    private void note(Element e, String msg, Object... args) {
        messager.printMessage(Diagnostic.Kind.NOTE, String.format(msg, args), e);
    }

    private void note(String msg, Object... args) {
        messager.printMessage(Diagnostic.Kind.NOTE, String.format(msg, args));
    }
}

class BeanDefinitionWriterElementWrapper {
    Set<Element> annotationElements;
    BeanDefinitionWriter beanDefinitionWriter;
}