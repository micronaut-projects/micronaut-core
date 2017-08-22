package org.particleframework.annotation.processing;

import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Options;
import org.particleframework.config.ConfigurationProperties;
import org.particleframework.context.annotation.Context;
import org.particleframework.context.annotation.Value;
import org.particleframework.core.io.service.ServiceDescriptorGenerator;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.BeanDefinitionClass;
import org.particleframework.inject.writer.BeanDefinitionClassWriter;
import org.particleframework.inject.writer.BeanDefinitionWriter;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.processing.*;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Scope;
import javax.inject.Singleton;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Collectors;

import static javax.lang.model.element.ElementKind.*;
import static javax.lang.model.type.TypeKind.ARRAY;
import static javax.lang.model.type.TypeKind.DECLARED;

@SupportedAnnotationTypes({
    "org.particleframework.config.ConfigurationProperties",
    "org.particleframework.context.annotation.Context",
    "javax.inject.Inject",
    "javax.annotation.PostConstruct",
    "javax.annotation.PreDestroy",
    "javax.inject.Singleton",
    "org.particleframework.context.annotation.Value"
})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class DependencyInjectionAnnotationProcessor extends AbstractProcessor {

    private Messager messager;
    private Filer filer;
    private Elements elementUtils;
    private Types typeUtils;
    private AnnotationUtils annotationUtils;
    private GenericUtils genericUtils;
    private ModelUtils modelUtils;

    private final Map<String, BeanDefinitionWriterElementWrapper> beanDefinitionWriters = new TreeMap<>();
    private String buildPath;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            generateClassesAndServiceDescriptors();
        } else {
            annotations.forEach(annotation -> {
                note("starting annotation processing for @%s", annotation.getQualifiedName());
                Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(annotation);
                elements.forEach(element -> {
                    ElementKind elementKind = element.getKind();
                    if ("Singleton".equals(annotation.getQualifiedName().toString()) && elementKind != CLASS) {
                        error(element, "@Singleton is only applicable to class, but found it applied to @%s",
                            elementKind);
                    } else {
//                            note(element, "Found @%s for class in %s", annotation.getSimpleName(), element);
                            TypeElement classTypeElement = modelUtils.classElementFor(element);
                            String fullyQualifiedBeanClassName = classTypeElement.getQualifiedName().toString();

                            BeanDefinitionWriterElementWrapper wrapper = beanDefinitionWriters.get(fullyQualifiedBeanClassName);
                            if (wrapper == null) {
                                wrapper = new BeanDefinitionWriterElementWrapper();
                                wrapper.fullyQualifiedBeanClassName = fullyQualifiedBeanClassName;
                                wrapper.beanDefinitionWriter = createBeanDefinitionWriterFor(element);
                                wrapper.annotationElements = new LinkedHashSet<>();
                                wrapper.configurationFieldElements = new LinkedHashSet<>();
                                beanDefinitionWriters.put(fullyQualifiedBeanClassName, wrapper);
                            }
                            wrapper.annotationElements.add(element);
                            if (element.getKind() == CONSTRUCTOR) {
                                wrapper.hasDefinedCtor = true;
                            }
                    }});
                }
            );
        }
        return true;
    }

    private void generateClassesAndServiceDescriptors() {
        ServiceDescriptorGenerator generator = new ServiceDescriptorGenerator();
        File targetDirectory = new File(buildPath);
        this.beanDefinitionWriters.values().forEach(wrapper -> {
            BeanDefinitionWriter writer = wrapper.beanDefinitionWriter;
            String className = writer.getBeanTypeName();
            if (!wrapper.hasDefinedCtor) {
                List<? extends Element> publicConstructors = modelUtils.findPublicConstructors(className);

                Element ctorElement;
                if (publicConstructors.size() == 0) {
                    error("Class %s must have at least one public constructor in order to be a candidate for dependency injection", className);
                } else {
                    if (publicConstructors.size() > 1) {
                        // FIXME: see InjectTransform 503
                        // constructorNode = publicConstructors.find() { it.getAnnotations(makeCached(Inject)) }
                        // need to do this as well?

                        // I think I can use elementUtils.getAllAnnotationMirrors
                        // and find the ctor that matches one
                        ctorElement = publicConstructors.get(0);
                    } else {
                        ctorElement = publicConstructors.get(0);
                    }
                    wrapper.annotationElements.add(ctorElement);
                }
            }

            // add non-annotated fields for class annotated with ConfigurationProperties
            // FIXME add check for static as per InjectTransform
            TypeElement typeElement = elementUtils.getTypeElement(className);
            if (isConfigurationProperties(typeElement)) {
                List<? extends Element> members = elementUtils.getAllMembers(typeElement);
                List<VariableElement> fieldElements = ElementFilter.fieldsIn(members)
                    .stream()
                    .filter(e -> !(isValue(e) && isInject(e)))
                    .collect(Collectors.toList());

                wrapper.configurationFieldElements.addAll(fieldElements);
            }

            // see BeanDefinitionWriter:1087 and InjectTransform.defineBeanDefinition
            // there needs to be a constructorVisitor before field injection occurs
            ElementFilter.constructorsIn(wrapper.annotationElements)
                .forEach(constructor -> {
                    try {
                        visitConstructorInjectionFor(writer, constructor);
                    } catch (IOException e) {
                        error("Unexpected error: %s", e.getMessage());
                        // FIXME something is wrong, probably want to fail fast
                        e.printStackTrace();
                    }
            });

            // everything else
            // maybe split this up and do classes, fields, methods, etc in order like
            // above with ElementFilter
            wrapper.annotationElements.stream()
                .filter(element -> element.getKind() != CONSTRUCTOR)
                .forEach(element -> {
                try {
                    switch (element.getKind()) {
                        case CLASS:
                            visitSingletonFor(writer, element);
                            break;
                        case METHOD:
                            visitMethodInjectionFor(writer, element);
                            break;
                        case FIELD:
                            visitFieldInjectionFor(writer, element);
                            break;
                    }
                } catch (IOException e) {
                    error("Unexpected error: %s", e.getMessage());
                    // FIXME something is wrong, probably want to fail fast
                    e.printStackTrace();
                }
            });

            wrapper.configurationFieldElements.forEach(configField -> {
                modelUtils.findSetterMethodFor(configField)
                    .ifPresent(method -> visitSetterInjectionFor(writer, configField, method));
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
                BeanDefinitionClassWriter beanDefinitionClassWriter = createBeanDefinitionClassWriterFor(
                    wrapper.annotationElements, beanTypeName, beanDefinitionName);

                generator.generate(targetDirectory, wrapper.beanDefinitionWriter.getBeanDefinitionName(),BeanDefinition.class);
                generator.generate(targetDirectory, beanDefinitionClassWriter.getBeanDefinitionClassName(),BeanDefinitionClass.class);
            } catch (IOException ioe) {
                error("Unexpected error: %s", ioe.getMessage());
                // FIXME something is wrong, probably want to fail fast
                ioe.printStackTrace();
            }
        });
    }

    private BeanDefinitionWriter createBeanDefinitionWriterFor(Element element) {
        TypeElement typeElement = modelUtils.classElementFor(element);
        PackageElement packageElement = elementUtils.getPackageOf(element);
        String beanClassName = unqualifiedClassnameFor(typeElement,packageElement);
        String packageName = packageElement.getQualifiedName().toString();
        boolean isSingleton = annotationUtils.hasStereotype(
            typeElement, Collections.singletonList(Singleton.class.getName()));
        AnnotationMirror scopeAnn = annotationUtils.findAnnotationWithStereotype(
            typeElement, Scope.class.getName());
        String scope = null;
        if (scopeAnn != null) {
            scope = scopeAnn.getAnnotationType().toString();
        }
        TypeMirror providerTypeParam = genericUtils.interfaceGenericTypeFor(
            typeElement, Provider.class.getName());
        if (providerTypeParam != null) {
            return new BeanDefinitionWriter(
                packageName,
                beanClassName,
                providerTypeParam.toString(),
                scope,
                isSingleton);
        } else {
            return new BeanDefinitionWriter(
                packageName,
                beanClassName,
                scope,
                isSingleton);
        }
    }

    // if it's an inner class org.oci.A.B, we want A.B
    // FIXME deal with static nested classes
    private String unqualifiedClassnameFor(TypeElement typeElement, PackageElement packageElement) {
        String qualifiedName = elementUtils.getBinaryName(typeElement).toString();
        return qualifiedName.replaceFirst(packageElement.getQualifiedName().toString() + "\\.","");
    }

    private void visitSingletonFor(BeanDefinitionWriter beanDefinitionWriter, Element
        element) {

    }

    private void visitFieldInjectionFor(BeanDefinitionWriter beanDefinitionWriter, Element element) {
        assert (FIELD == element.getKind()) : "element kind must be FIELD";

        if (!beanDefinitionWriter.isValidated() && annotationUtils.hasStereotype(element, "javax.validation.Constraint")) {
            beanDefinitionWriter.setValidated(true);
        }

        Name fieldName = element.getSimpleName();
        TypeMirror type = element.asType();

        // Is this a SMELL??? is it the right way to do it? It works so far
        // probably since arrays do not use type erasure...
        TypeKind kind = type.getKind();
        Object fieldType = null;
        if (kind == ARRAY) {
            fieldType = type.toString();
        } else if (kind == DECLARED){
            TypeElement fieldElement = elementUtils.getTypeElement(typeUtils.erasure(type).toString());
            fieldType = fieldElement.getQualifiedName().toString();
        } else if (kind.isPrimitive()) {
            fieldType = modelUtils.classOfPrimitiveFor(type.toString());
        }
        assert (fieldType != null) : "fieldTYpe cannot be null";

        boolean requiresReflection = modelUtils.requiresReflection(element);
        boolean isValue = isValue(element);

        // FIXME handle Qualifier resolution
        // FIXME handle declaringClass.isResolved() from InjectTransofrm logic

        TypeElement classElement = modelUtils.classElementFor(element);

        if (isValue) {
            beanDefinitionWriter.visitFieldValue(
                classElement.getQualifiedName().toString(),
                null,
                requiresReflection,
                fieldType,
                fieldName.toString(),
                isConfigurationProperties(element)
                );
        } else {
            beanDefinitionWriter.visitFieldInjectionPoint(
                classElement.getQualifiedName().toString(),
                null,
                requiresReflection,
                fieldType,
                fieldName.toString());
        }
    }

    public void visitSetterInjectionFor(BeanDefinitionWriter beanDefinitionWriter, Element field, Element method) {
        ElementKind fieldKind = field.getKind();
        ElementKind methodKind = method.getKind();
        assert (FIELD == fieldKind) : "field kind must be FIELD";
        assert (METHOD == methodKind) : "method kind must be METHOD";
        TypeElement classElement = modelUtils.classElementFor(method);
        Object qualifier = null;
        Object fieldType;
        List<Object> genericTypes = null;
        TypeKind fieldTypeKind = field.asType().getKind();
        if (fieldTypeKind == ARRAY || fieldTypeKind.isPrimitive()) {
            fieldType = field.asType().toString();
        } else {
            TypeElement fieldElement = elementUtils.getTypeElement(typeUtils.erasure(field.asType()).toString());
            fieldType = fieldElement.getQualifiedName().toString();
            genericTypes = ((DeclaredType)field.asType()).getTypeArguments().stream()
                .map(TypeMirror::toString)
                .collect(Collectors.toList());
        }

        if (field.asType().getKind().isPrimitive()) {
            fieldType = modelUtils.classOfPrimitiveFor(fieldType.toString());
        }

        if (!beanDefinitionWriter.isValidated() && annotationUtils.hasStereotype(field, "javax.validation.Constraint")) {
            beanDefinitionWriter.setValidated(true);
        }

        beanDefinitionWriter.visitSetterValue(
            classElement.getQualifiedName().toString(),
            qualifier, // null
            modelUtils.requiresReflection(method),
            fieldType, // int
            field.getSimpleName().toString(), //defaultPort
            method.getSimpleName().toString(),
            genericTypes,
            true);
    }

    private void visitConstructorInjectionFor(BeanDefinitionWriter beanDefinitionWriter, Element element) throws IOException {
        ElementKind elementKind = element.getKind();
        assert (CONSTRUCTOR == elementKind) : "element kind must be CONSTRUCTOR";
        Map<String,Object> methodArgs = new LinkedHashMap<>();
        Map<String,List<Object>> genericTypes = new LinkedHashMap<>();

        visitParametersFor(element, methodArgs, genericTypes);
        beanDefinitionWriter.visitBeanDefinitionConstructor(methodArgs, null, genericTypes);
    }

    private void visitMethodInjectionFor(BeanDefinitionWriter beanDefinitionWriter, Element method) throws IOException {
        ElementKind elementKind = method.getKind();
        assert (METHOD == elementKind) : "element kind must be METHOD";

        TypeElement classElement = modelUtils.classElementFor(method);

        Map<String,Object> methodArgs = new LinkedHashMap<>();
        Map<String,List<Object>> genericTypes = new LinkedHashMap<>();

        visitParametersFor(method, methodArgs, genericTypes);
        // FIXME test for requires reflection
        boolean requiresReflection = modelUtils.requiresReflection(method);

        // FIXME resolve return type (might not be Void.TYPE)
        if (annotationUtils.hasStereotype(method, PostConstruct.class)) {
            beanDefinitionWriter.visitPostConstructMethod(
                classElement.getQualifiedName().toString(),
                requiresReflection,
                Void.TYPE,
                method.getSimpleName().toString(),
                methodArgs,
                null,
                genericTypes);

        } else if (annotationUtils.hasStereotype(method, PreDestroy.class)) {
            beanDefinitionWriter.visitPreDestroyMethod(
                classElement.getQualifiedName().toString(),
                requiresReflection,
                Void.TYPE,
                method.getSimpleName().toString(),
                methodArgs,
                null,
                genericTypes);

        } else {
            beanDefinitionWriter.visitMethodInjectionPoint(
                classElement.getQualifiedName().toString(),
                requiresReflection,
                Void.TYPE,
                method.getSimpleName().toString(),
                methodArgs,
                null,
                genericTypes);
        }
    }

    private void visitParametersFor(Element element,
                                    Map<String,Object> methodArgs, Map<String,List<Object>> genericTypes) {

        ExecutableType execType = (ExecutableType) element.asType();

        List<? extends TypeMirror> parameterTypes = execType.getParameterTypes();
        for (int i = 0; i < parameterTypes.size(); i++) {

            TypeMirror typeMirror = parameterTypes.get(i);
            TypeKind kind = typeMirror.getKind();

            if (kind == ARRAY) {
                ArrayType arrayType = (ArrayType) typeMirror; // FIXME is there an API way of getting this without a cast?
                TypeMirror componentType = arrayType.getComponentType();
                String argName = argNameFrom(componentType.toString(), i);
                methodArgs.put(argName, arrayType.toString());
                genericTypes.put(argName, Collections.singletonList(componentType.toString()));
            } else if (kind == DECLARED) {
                DeclaredType declaredType = (DeclaredType) typeMirror;

                TypeElement typeElement = elementUtils.getTypeElement(typeUtils.erasure(declaredType).toString());
                assert (typeElement != null) : "typeElement cannot be null";
                String argName = argNameFrom(typeElement.getSimpleName().toString(), i);

                methodArgs.put(argName, typeElement.toString());
                List<Object> params = declaredType.getTypeArguments().stream()
                    .map(TypeMirror::toString)
                    .collect(Collectors.toList());
                if (!params.isEmpty()) {
                    genericTypes.put(argName, params);
                }
            } else if (kind.isPrimitive()) {
                String typeName = typeMirror.toString();
                Object argType = modelUtils.classOfPrimitiveFor(typeName);
                String argName = argNameFrom(typeName, i);
                methodArgs.put(argName, argType);
            } else {
                error(element, "ERROR: Unexpected element kind %s for %s", kind, element);
            }
        }
    }

    private boolean isValue(Element element) {
        boolean isValue = annotationUtils.hasStereotype(element, Value.class);
        return !isInject(element) && isValue || isConfigurationProperties(element);
    }

    private boolean isInject(Element element) {
        return annotationUtils.hasStereotype(element, Inject.class);
    }

    private boolean isConfigurationProperties(Element element) {
        TypeElement typeElement = modelUtils.classElementFor(element);
        return annotationUtils.hasStereotype(typeElement, ConfigurationProperties.class);
    }

    private String argNameFrom(String type, int suffix) {
        String className = type.replaceFirst("((.*)\\.)?([^\\.]*)", "$3");
        String argName = className.substring(0,1).toLowerCase();
        if (className.length() > 1) {
            argName += className.substring(1);
        }
        return argName + suffix;
    }

    private BeanDefinitionClassWriter createBeanDefinitionClassWriterFor(
        Set<Element> elements,
        String fullyQualifiedBeanClassName,
        String beanDefinitionName) throws IOException
    {
        BeanDefinitionClassWriter beanClassWriter = new BeanDefinitionClassWriter(fullyQualifiedBeanClassName, beanDefinitionName);
        String classFileName = beanClassWriter.getBeanDefinitionQualifiedClassName();
        Element classElement = elementUtils.getTypeElement(fullyQualifiedBeanClassName);
        beanClassWriter.setContextScope(annotationUtils.hasStereotype(classElement, Context.class));
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
        this.modelUtils = new ModelUtils(elementUtils,typeUtils);
        this.annotationUtils = new AnnotationUtils(elementUtils);
        this.genericUtils = new GenericUtils(elementUtils,typeUtils);

        Options javacOptions = Options.instance(((JavacProcessingEnvironment)processingEnv).getContext());
        this.buildPath = javacOptions.get(Option.D);
        Map<String, String> options = processingEnv.getOptions();
        note("Options passed to annotation processor are %s", options);
    }

    private void error(Element e, String msg, Object... args) {
//        messager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args), e);
    }
    private void error(String msg, Object... args) {
//        messager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args));
    }

    private void note(Element e, String msg, Object... args) {
//        messager.printMessage(Diagnostic.Kind.NOTE, String.format(msg, args), e);
    }

    private void note(String msg, Object... args) {
//        messager.printMessage(Diagnostic.Kind.NOTE, String.format(msg, args));
    }
}

class BeanDefinitionWriterElementWrapper {
    String fullyQualifiedBeanClassName;
    Set<Element> annotationElements;
    Set<Element> configurationFieldElements;
    BeanDefinitionWriter beanDefinitionWriter;
    boolean hasDefinedCtor;
}