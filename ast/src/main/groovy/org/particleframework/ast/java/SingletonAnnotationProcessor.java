package org.particleframework.ast.java;

import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.BeanDefinitionClass;
import org.particleframework.inject.writer.BeanDefinitionClassWriter;
import org.particleframework.inject.writer.BeanDefinitionWriter;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SupportedAnnotationTypes({
    "javax.inject.Singleton",
    "javax.inject.Inject"
})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class SingletonAnnotationProcessor extends AbstractProcessor {

    public static final String META_INF_SERVICES_DIR = "META-INF/services/";
    private Messager messager;
    private Filer filer;

    private Map<String, Set<String>> serviceProviders = new HashMap<>();
    private Set<String> beanDefinitionClassNames = new HashSet<>();

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
                            String generatedDefinitionClassName = null;
                            String fullyQualifiedBeanClassName = null;
                            String beanClassName = null;
                            String packageName = null;
                            switch (elementKind) {
                                case CLASS:
                                    generatedDefinitionClassName = "org.particleframework.inject.field.$JavaSingletonDefinition";
                                    beanClassName = "JavaSingleton";
                                    fullyQualifiedBeanClassName = "org.particleframework.inject.field.JavaSingleton";
                                    packageName = "org.particleframework.inject.field";

                                    addServiceProvider(element, BeanDefinition.class.getName(), generatedDefinitionClassName);
                                    // SMELL hack for now, see what's needed later
                                    if (!beanDefinitionClassNames.contains(fullyQualifiedBeanClassName)) {
                                        beanDefinitionClassNames.add(fullyQualifiedBeanClassName);
                                        String beanDefinitionName = generateSingletonBeanClass(element, packageName, beanClassName);
                                        BeanDefinitionClassWriter beanClassWriter = generateBeanDefinitionClass(element, fullyQualifiedBeanClassName, beanDefinitionName);
                                        addServiceProvider(element, BeanDefinitionClass.class.getName(), beanClassWriter.getBeanDefinitionClassName());
                                    }
                                    break;
                                case METHOD:
                                    generatedDefinitionClassName = "org.particleframework.inject.field.$JavaClassDefinition";
                                    beanClassName = "JavaClass";
                                    fullyQualifiedBeanClassName = "org.particleframework.inject.field.JavaClass";
                                    packageName = "org.particleframework.inject.field";

                                    addServiceProvider(element, BeanDefinition.class.getName(), generatedDefinitionClassName);
                                    // SMELL hack for now, see what's needed later
                                    if (!beanDefinitionClassNames.contains(fullyQualifiedBeanClassName)) {
                                        beanDefinitionClassNames.add(fullyQualifiedBeanClassName);
                                        String beanDefinitionName = generateInjectBeanClass(element, packageName, beanClassName);
                                        BeanDefinitionClassWriter beanClassWriter = generateBeanDefinitionClass(element, fullyQualifiedBeanClassName, beanDefinitionName);
                                        addServiceProvider(element, BeanDefinitionClass.class.getName(), beanClassWriter.getBeanDefinitionClassName());
                                    }
                                    break;
                            }
                        } catch (IOException ioe) {
                            error("Unexpected error: %s", ioe.getMessage());
                            ioe.printStackTrace();
                        }
                    }});
                }
            );
        }

        return true;
    }

    // really want a multimap for this...Guava?
    private void addServiceProvider(Element element, String name, String generatedDefinitionClassName) {
        Set<String> serviceProviderClassNames = serviceProviders.get(name);
        note(element, "Adding service provider %s to %s", generatedDefinitionClassName, name);
        if (serviceProviderClassNames == null) {
            serviceProviderClassNames = new HashSet<>();
            serviceProviders.put(name, serviceProviderClassNames);
        }
        serviceProviderClassNames.add(generatedDefinitionClassName);
    }

    private String generateSingletonBeanClass(Element element, String packageName, String beanClassName) throws IOException {
        BeanDefinitionWriter beanDefinitionWriter = new BeanDefinitionWriter(packageName, beanClassName, null, true);
        beanDefinitionWriter.visitBeanDefinitionConstructor();

        beanDefinitionWriter.visitBeanDefinitionEnd();

        JavaFileObject javaFileObject = filer.createClassFile(beanDefinitionWriter.getBeanDefinitionName(), element);
        try (OutputStream outputStream = javaFileObject.openOutputStream()) {
            beanDefinitionWriter.writeTo(outputStream);
        }

        return beanDefinitionWriter.getBeanDefinitionName();
    }

    private String generateInjectBeanClass(Element element, String packageName, String beanClassName) throws IOException {
        BeanDefinitionWriter beanDefinitionWriter = new BeanDefinitionWriter(packageName, beanClassName, null, true);
        beanDefinitionWriter.visitBeanDefinitionConstructor();

        ExecutableType typeMirror = (ExecutableType) element.asType();
        List<? extends TypeMirror> parameterTypes = typeMirror.getParameterTypes();
        Map m = new LinkedHashMap();
        TypeMirror tm = parameterTypes.get(0);
        TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(tm.toString());
        m.put("ji", typeElement.toString());
        beanDefinitionWriter.visitMethodInjectionPoint(false, Void.TYPE, "setJavaInterface", m);
        beanDefinitionWriter.visitBeanDefinitionEnd();

        note("CREATING NEW CLASS FILE %s", packageName + "." + beanClassName);
        JavaFileObject javaFileObject = filer.createClassFile(beanDefinitionWriter.getBeanDefinitionName(), element);
        try (OutputStream outputStream = javaFileObject.openOutputStream()) {
            beanDefinitionWriter.writeTo(outputStream);
        }

        return beanDefinitionWriter.getBeanDefinitionName();
    }

    private BeanDefinitionClassWriter generateBeanDefinitionClass(
        Element element,
        String fullyQualifiedBeanClassName,
        String beanDefinitionName) throws IOException
    {
        BeanDefinitionClassWriter beanClassWriter = new BeanDefinitionClassWriter(fullyQualifiedBeanClassName, beanDefinitionName);
        String classFileName = beanClassWriter.getBeanDefinitionQualifiedClassName();
        JavaFileObject javaFileObject = filer.createClassFile(classFileName, element);
        try (OutputStream out = javaFileObject.openOutputStream()) {
            beanClassWriter.writeTo(out);
        }
        return beanClassWriter;
    }

    private void generateBeanDefinitionServiceDescriptors() {
        serviceProviders.forEach((serviceDescriptor, providers) -> {
            try {
                providers.addAll(existingProvidersFor(serviceDescriptor));
                FileObject resourceFileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, "",
                    "META-INF/services/" + serviceDescriptor);
                try (BufferedWriter bw = new BufferedWriter(resourceFileObject.openWriter())) {
                    bw.write(providers.stream().collect(Collectors.joining(System.getProperty("line.separator"))));
                }
            } catch (IOException e) {
                error("Unexpected error: %s", e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private List<String> existingProvidersFor(String serviceDescriptor) throws IOException {
        FileObject resourceFileObject = filer.getResource(StandardLocation.CLASS_OUTPUT, "", META_INF_SERVICES_DIR + serviceDescriptor);
        if (resourceFileObject.getLastModified() != 0) {
            // the file exists, preserve existing service providers
            note("Service descriptor exists for %s. Reading entries", serviceDescriptor);
            CharSequence content = resourceFileObject.getCharContent(true);
            if (content != null) {
                Arrays.asList(Pattern.compile("\\n").split(content));
            }
            return content != null ? Arrays.asList(Pattern.compile("\\n").split(content)) : Collections.emptyList();
        }
        return Collections.emptyList();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
        note("Options passed to annotaion processor are %s", processingEnv.getOptions());
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
