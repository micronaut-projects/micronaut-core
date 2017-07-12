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

    public static final String META_INF_SERVICES_DIR = "META-INF/services/";
    private Messager messager;
    private Filer filer;
    private Elements elementUtils;
    private Types typeUtils;

    private Map<Class, Set<String>> serviceProviders = new HashMap<>();
    private Set<String> beanDefinitionClassNames = new HashSet<>();

    private String buildPath;


    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            generateBeanDefinitionServiceDescriptors();
        } else {
            annotations.stream().forEach(annotation -> {
                note("starting annotation processing for @%s", annotation.getQualifiedName());
                roundEnv.getElementsAnnotatedWith(annotation).stream().forEach(element -> {
                    ElementKind elementKind = element.getKind();
                    if ("Singleton".equals(annotation.getQualifiedName()) && elementKind != ElementKind.CLASS) {
                        error(element, "@Singleton is only applicable to class, but found it applied to @%s",
                            elementKind);
                    } else {
                        try {
                            note(element, "Found @%s for class in %s", annotation.getSimpleName(), element);
                            TypeElement typeElement = typeElementFor(element);
                            PackageElement packageElement = elementUtils.getPackageOf(element);

                            String beanClassName = classNameFor(packageElement, typeElement);
                            String packageName = packageElement.getQualifiedName().toString();
                            String fullyQualifiedBeanClassName = typeElement.getQualifiedName().toString();
                            String generatedDefinitionClassName = packageName + ".$" + beanClassName + "Definition";
                            addServiceProvider(element, BeanDefinition.class, generatedDefinitionClassName);

                            if (!beanDefinitionClassNames.contains(fullyQualifiedBeanClassName)) {
                                beanDefinitionClassNames.add(fullyQualifiedBeanClassName);
                                BeanDefinitionWriter beanDefinitionWriter = null;
                                switch (elementKind) {
                                    case CLASS:
                                        beanDefinitionWriter = generateSingletonBeanClass(element, packageName, beanClassName);
                                        break;
                                    case METHOD:
                                        beanDefinitionWriter = generateMethodInjectionBeanClass(element, packageName, beanClassName);
                                        break;
                                    case FIELD:
                                        beanDefinitionWriter = generateFieldInjectionBeanClass(element, packageName, beanClassName);
                                        break;
                                }
                                if (beanDefinitionWriter != null) {
                                    String beanDefinitionName = beanDefinitionWriter.getBeanDefinitionName();
                                    BeanDefinitionClassWriter beanDefinitionClassWriter = generateBeanDefinitionClass(element, fullyQualifiedBeanClassName, beanDefinitionName);
                                    addServiceProvider(element, BeanDefinitionClass.class, beanDefinitionClassWriter.getBeanDefinitionClassName());
                                }
                            }
                        } catch (IOException ioe) {
                            error("Unexpected error: %s", ioe.getMessage());
                            // FIXME something is wrong, probably want to fail fast
                            ioe.printStackTrace();
                        }
                    }});
                }
            );
        }

        return true;
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

    // really want a multimap for this...Guava?
    private void addServiceProvider(Element element, Class descriptor, String generatedDefinitionClassName) {
        Set<String> serviceProviderClassNames = serviceProviders.get(descriptor);
        note(element, "Adding service provider %s to %s", generatedDefinitionClassName, descriptor.getName());
        if (serviceProviderClassNames == null) {
            serviceProviderClassNames = new HashSet<>();
            serviceProviders.put(descriptor, serviceProviderClassNames);
        }
        serviceProviderClassNames.add(generatedDefinitionClassName);
    }

    private BeanDefinitionWriter generateSingletonBeanClass(Element element, String packageName, String beanClassName) throws IOException {
        BeanDefinitionWriter beanDefinitionWriter = new BeanDefinitionWriter(packageName, beanClassName, null, true);
        beanDefinitionWriter.visitBeanDefinitionConstructor();

        beanDefinitionWriter.visitBeanDefinitionEnd();

        JavaFileObject javaFileObject = filer.createClassFile(beanDefinitionWriter.getBeanDefinitionName(), element);
        try (OutputStream outputStream = javaFileObject.openOutputStream()) {
            beanDefinitionWriter.writeTo(outputStream);
        }

        return beanDefinitionWriter;
    }

    private BeanDefinitionWriter generateMethodInjectionBeanClass(Element element, String packageName, String beanClassName) throws IOException {
        assert (ElementKind.METHOD == element.getKind()) : "element kind must be METHOD";

        BeanDefinitionWriter beanDefinitionWriter = new BeanDefinitionWriter(packageName, beanClassName, null, true);
        beanDefinitionWriter.visitBeanDefinitionConstructor();

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
                genericTypes.put(argName, Arrays.asList(componentType.toString()));
                beanDefinitionWriter.visitMethodInjectionPoint(false, Void.TYPE, methodName.toString(), methodArgs, null, genericTypes);
            } else {
                TypeElement typeElement = elementUtils.getTypeElement(typeMirror.toString());
                assert (typeElement != null) : "typeElement cannot be null";
                String argName = argNameFromType(typeElement.getSimpleName().toString(), i);
                methodArgs.put(argName, typeElement.toString());
                beanDefinitionWriter.visitMethodInjectionPoint(false, Void.TYPE, methodName.toString(), methodArgs);
            }
        }
        beanDefinitionWriter.visitBeanDefinitionEnd();

        String classFileName = beanDefinitionWriter.getBeanDefinitionName();
        JavaFileObject javaFileObject = filer.createClassFile(classFileName, element);
        note("CREATING NEW CLASS FILE %s for @Inject", classFileName);
        try (OutputStream outputStream = javaFileObject.openOutputStream()) {
            beanDefinitionWriter.writeTo(outputStream);
        }

        return beanDefinitionWriter;
    }

    private String argNameFromType(String type, int suffix) {
        String classname = type.replaceFirst("((.*)\\.)?([^\\.]*)", "$3");
        String argName = classname.substring(0,1).toLowerCase();
        if (classname.length() > 1) {
            argName += classname.substring(1);
        }
        return argName + suffix;
    }

    private BeanDefinitionWriter generateFieldInjectionBeanClass(Element element, String packageName, String beanClassName) throws IOException {
        assert (ElementKind.FIELD == element.getKind()) : "element kind must be FIELD";

        BeanDefinitionWriter beanDefinitionWriter = new BeanDefinitionWriter(packageName, beanClassName, null, true);
        beanDefinitionWriter.visitBeanDefinitionConstructor();

        Name fieldName = element.getSimpleName();
        TypeMirror fieldType = element.asType();

        beanDefinitionWriter.visitFieldInjectionPoint(true, fieldType.toString(), fieldName.toString());
        beanDefinitionWriter.visitBeanDefinitionEnd();

        String classFileName = beanDefinitionWriter.getBeanDefinitionName();
        JavaFileObject javaFileObject = filer.createClassFile(classFileName, element);
        note("CREATING NEW CLASS FILE %s for @Inject", classFileName);
        try (OutputStream outputStream = javaFileObject.openOutputStream()) {
            beanDefinitionWriter.writeTo(outputStream);
        }

        return beanDefinitionWriter;
    }

    private BeanDefinitionClassWriter generateBeanDefinitionClass(
        Element element,
        String fullyQualifiedBeanClassName,
        String beanDefinitionName) throws IOException
    {
        BeanDefinitionClassWriter beanClassWriter = new BeanDefinitionClassWriter(fullyQualifiedBeanClassName, beanDefinitionName);
        String classFileName = beanClassWriter.getBeanDefinitionQualifiedClassName();
        note("CREATING NEW CLASS FILE %s for @Singleton", classFileName);
        JavaFileObject javaFileObject = filer.createClassFile(classFileName, element);
        try (OutputStream out = javaFileObject.openOutputStream()) {
            beanClassWriter.writeTo(out);
        }
        return beanClassWriter;
    }

    private void generateBeanDefinitionServiceDescriptors() {
        ServiceDescriptorGenerator generator = new ServiceDescriptorGenerator();
        File targetDirectory = new File(buildPath);
        serviceProviders.forEach((serviceDescriptor, providers) -> {
            providers.forEach(provider -> {
                try {
                    generator.generate(targetDirectory, provider, serviceDescriptor);
                } catch (IOException e) {
                    error("Failed to write provide %s to service descriptor file %s", provider, serviceDescriptor);
                    e.printStackTrace();
                }
            });
        });
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
