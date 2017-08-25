package org.particleframework.annotation.processing;

import org.particleframework.config.ConfigurationProperties;
import org.particleframework.context.annotation.Bean;
import org.particleframework.context.annotation.Context;
import org.particleframework.context.annotation.Factory;
import org.particleframework.core.io.service.ServiceDescriptorGenerator;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.BeanDefinitionClass;
import org.particleframework.inject.annotation.Executable;
import org.particleframework.inject.writer.BeanDefinitionClassWriter;
import org.particleframework.inject.writer.BeanDefinitionWriter;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Scope;
import javax.inject.Singleton;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementScanner8;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Collectors;

import static javax.lang.model.element.ElementKind.ANNOTATION_TYPE;
import static javax.lang.model.element.ElementKind.CLASS;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.Modifier.*;
import static javax.lang.model.type.TypeKind.*;

@SupportedAnnotationTypes({
    "javax.annotation.PostConstruct",
    "javax.annotation.PreDestroy",
    "javax.inject.Inject",
    "javax.inject.Qualifier",
    "javax.inject.Singleton",
    "org.particleframework.config.ConfigurationProperties",
    "org.particleframework.context.annotation.Bean",
    "org.particleframework.context.annotation.Context",
    "org.particleframework.context.annotation.Factory",
    "org.particleframework.context.annotation.Replaces",
    "org.particleframework.context.annotation.Value",
    "org.particleframework.inject.qualifiers.primary.Primary"
})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class BeanDefinitionInjectProcessor extends AbstractInjectAnnotationProcessor {

    private Map<String, AnnBeanElementVisitor> beanDefinitionWriters;
    private ServiceDescriptorGenerator serviceDescriptorGenerator;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.serviceDescriptorGenerator = new ServiceDescriptorGenerator();
        this.beanDefinitionWriters = new LinkedHashMap<>();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        annotations.forEach(ann -> {
            roundEnv.getElementsAnnotatedWith(ann)
                .stream()
                // filtering Qualifier annotation definitions, which are not processed
                .filter(e -> e.getKind() != ANNOTATION_TYPE)
                .forEach(e -> {
                    TypeElement typeElement = modelUtils.classElementFor(e);
                    AnnBeanElementVisitor visitor = new AnnBeanElementVisitor(typeElement);
                    beanDefinitionWriters.put(typeElement.getQualifiedName().toString(), visitor);
                });
        });

        if (roundEnv.processingOver()) {
            note("Creating bean classes for %s type elements", beanDefinitionWriters.size());
            beanDefinitionWriters.values().forEach(visitor -> {
                TypeElement classElement = visitor.getConcreteClass();
                classElement.accept(visitor, classElement.getQualifiedName().toString());
                visitor.getBeanDefinitionWriters().values().forEach(beanDefinitionWriter -> {
                    try {
                        beanDefinitionWriter.visitBeanDefinitionEnd();
                        JavaFileObject javaFileObject = filer.createClassFile(beanDefinitionWriter.getBeanDefinitionName());
                        try (OutputStream outputStream = javaFileObject.openOutputStream()) {
                            beanDefinitionWriter.writeTo(outputStream);
                        }

                        String beanDefinitionName = beanDefinitionWriter.getBeanDefinitionName();
                        String beanTypeName = beanDefinitionWriter.getBeanTypeName();

                        BeanDefinitionClassWriter beanDefinitionClassWriter =
                            new BeanDefinitionClassWriter(beanTypeName, beanDefinitionName);
                        String className = beanDefinitionClassWriter.getBeanDefinitionQualifiedClassName();
                        Element beanClassElement = elementUtils.getTypeElement(beanTypeName);
                        beanDefinitionClassWriter.setContextScope(annotationUtils.hasStereotype(beanClassElement, Context.class));
                        JavaFileObject beanDefClassFileObject = filer.createClassFile(className);
                        try (OutputStream out = beanDefClassFileObject.openOutputStream()) {
                            beanDefinitionClassWriter.writeTo(out);
                        }
                        serviceDescriptorGenerator.generate(
                            targetDirectory,
                            beanDefinitionWriter.getBeanDefinitionName(),
                            BeanDefinition.class);
                        serviceDescriptorGenerator.generate(
                            targetDirectory,
                            beanDefinitionClassWriter.getBeanDefinitionClassName(),
                            BeanDefinitionClass.class);
                    } catch (IOException e) {
                        error("Unexpected error: %s", e.getMessage());
                        // FIXME something is wrong, probably want to fail fast
                        e.printStackTrace();
                    }
                });
            });
        }
        return true;
    }

    private BeanDefinitionClassWriter createBeanDefinitionClassWriterFor(
        String fullyQualifiedBeanClassName,
        String beanDefinitionName) throws IOException
    {
        BeanDefinitionClassWriter beanClassWriter = new BeanDefinitionClassWriter(fullyQualifiedBeanClassName, beanDefinitionName);
        String className = beanClassWriter.getBeanDefinitionQualifiedClassName();
        Element classElement = elementUtils.getTypeElement(fullyQualifiedBeanClassName);
        beanClassWriter.setContextScope(annotationUtils.hasStereotype(classElement, Context.class));
        note("CREATING NEW CLASS FILE %s for @Singleton", className);
        JavaFileObject javaFileObject = filer.createClassFile(className);
        try (OutputStream out = javaFileObject.openOutputStream()) {
            beanClassWriter.writeTo(out);
        }
        return beanClassWriter;
    }


    class AnnBeanElementVisitor extends ElementScanner8<Object,Object> {
        private final TypeElement concreteClass;
        private final Map<Element, BeanDefinitionWriter> beanDefinitionWriters;
        private final boolean isConfigurationPropertiesType;
        private final boolean isFactoryType;
        private final boolean isExecutableType;

        AnnBeanElementVisitor(TypeElement concreteClass) {
            this.concreteClass = concreteClass;
            beanDefinitionWriters = new LinkedHashMap<>();
            this.isFactoryType = annotationUtils.hasStereotype(concreteClass, Factory.class);
            this.isExecutableType = annotationUtils.hasStereotype(concreteClass, Executable.class);
            this.isConfigurationPropertiesType = annotationUtils.hasStereotype(concreteClass, ConfigurationProperties.class);
        }

        public TypeElement getConcreteClass() {
            return concreteClass;
        }

        public Map<Element, BeanDefinitionWriter> getBeanDefinitionWriters() {
            return beanDefinitionWriters;
        }

        @Override
        public Object scan(Element e, Object o) {
            note("Scan %s for %s", e.getSimpleName(), o);
            return super.scan(e, o);
        }

        @Override
        public Object visitType(TypeElement classElement, Object o) {
            note("Visit type %s for %s", classElement.getSimpleName(), o);
            assert (classElement.getKind() == CLASS) : "classElement must be a class";

            Element enclosingElement = classElement.getEnclosingElement();
            if (enclosingElement.getKind() != CLASS) {
                // it's not an inner class
                // we know this class has supported annotations so we need a beandef writer for it
                BeanDefinitionWriter beanDefinitionWriter = createBeanDefinitionWriterFor(classElement);
                beanDefinitionWriters.put(this.concreteClass, beanDefinitionWriter);
                if (isConfigurationPropertiesType) {

                }
                // TODO handle configuration properties class

                ExecutableElement ctor = publicConstructorFor(classElement);
                ExecutableElementParamInfo paramInfo = populateParameterData(ctor);
                beanDefinitionWriter.visitBeanDefinitionConstructor(
                    paramInfo.getParameters(),
                    paramInfo.getQualifierTypes(),
                    paramInfo.getGenericTypes());
            } else {
                TypeElement outer = (TypeElement)enclosingElement;
                // handle inner classes
            }

//
            // hmmm...I think I'll somehow use this to handle superclasses with annotations
            // where classElement is the defining class
//            List<TypeElement> superClasses = modelUtils.superClassesFor(classElement);
//            superClasses.forEach(element -> element.accept(this, o));

            //

            // we just handled the public ctor
            List<? extends Element> elements = classElement.getEnclosedElements().stream()
                .filter(element -> element.getKind() != CONSTRUCTOR)
                .collect(Collectors.toList());
            return scan(elements, o);
        }

        @Override
        public Object visitExecutable(ExecutableElement method, Object o) {
            note("Visit executable %s for %s", method.getSimpleName(), o);
            if (method.getKind() == ElementKind.CONSTRUCTOR) {
                // ctor is handled by visitType
                error("Unexpected call to visitExecutable for ctor %s of %s",
                    method.getSimpleName(), o);
                return null;
            }

            boolean definesBean = annotationUtils.hasStereotype(method, Bean.class);
            // handle @Bean annotation for @Factory class
            if (isFactoryType && definesBean) {
                visitBeanFactoryMethod(method, o);
                return null;
            }
            boolean isStaticMethod = method.getModifiers().contains(STATIC);
            boolean isAbstractMethod = method.getModifiers().contains(ABSTRACT);
            if (isStaticMethod || isAbstractMethod) {
                return null;
            }

            boolean isPublicMethod = method.getModifiers().contains(PUBLIC);
            boolean isExecutableMethod = annotationUtils.hasStereotype(method, Executable.class);
            if (isExecutableMethod
                || (isExecutableType && isPublicMethod)) {
                // InjectTransform 299 has test for Object.class
                visitExecutableMethod(method, o);
                return null;
            }

            boolean injected = annotationUtils.hasStereotype(method, Inject.class);
            boolean postConstruct = annotationUtils.hasStereotype(method, PostConstruct.class);
            boolean preDestroy = annotationUtils.hasStereotype(method, PreDestroy.class);
            if (injected || postConstruct || preDestroy) {

                // TODO figure out how to replicate all this logic.
                /*
                boolean isParent = methodNode.declaringClass != concreteClass
                MethodNode overriddenMethod = isParent ? concreteClass.getMethod(methodNode.name, methodNode.parameters) : methodNode
                boolean overridden = isParent && overriddenMethod.declaringClass != methodNode.declaringClass

                boolean isPackagePrivate = isPackagePrivate(methodNode, methodNode.modifiers)
                boolean isPrivate = methodNode.isPrivate()
                if (isParent && !isPrivate && !isPackagePrivate) {
                    if (overridden) {
                        // bail out if the method has been overridden, since it will have already been handled
                        return
                    }
                }
                boolean isPackagePrivateAndPackagesDiffer = overridden && (overriddenMethod.declaringClass.packageName != methodNode.declaringClass.packageName) && isPackagePrivate
                boolean requiresReflection = isPrivate || isPackagePrivateAndPackagesDiffer
                boolean overriddenInjected = overridden && stereoTypeFinder.hasStereoType(overriddenMethod, Inject)

                if (isParent && isPackagePrivate && !isPackagePrivateAndPackagesDiffer && !overriddenInjected) {
                    // bail out if the overridden method is package private and in the same package
                    // and is not annotated with @Inject
                    return
                }
                if (!requiresReflection && isInheritedAndNotPublic(methodNode, methodNode.declaringClass, methodNode.modifiers)) {
                    requiresReflection = true
                }
                 */

                // for example
                // concrete class = org.particleframework.inject.inheritance.AbstractInheritanceSpec$B
                // method = void setAnother(A a)
                // method declaring class = org.particleframework.inject.inheritance.AbstractInheritanceSpec$AbstractB
                // isParent = true
                // overriden method = void setAnother(A a)
                // overriden method declaring class = org.particleframework.inject.inheritance.AbstractInheritanceSpec$AbstractB
                // overriden is false
                // isPackagePrivate and isPrivate = false
                // isPackagePrivateAndPackagesDiffer = false
                // requiresReflection = false
                // overriddenInjected = false
                // isInheritedAndNotPublic = false

                boolean isPackagePrivate = modelUtils.isPackagePrivate(method);
                boolean isPrivate = modelUtils.isPrivate(method);
                boolean isParent = method.getEnclosingElement() != this.concreteClass;
                boolean requiresReflection = isPrivate;
                // FIXME

                visitAnnotatedMethod(method, requiresReflection);
                return null;
            }
            return null;
        }

        void visitBeanFactoryMethod(ExecutableElement method, Object o) {
            AnnotationMirror beanAnnotation = annotationUtils.findAnnotationWithStereotype(method, Bean.class);
            assert (beanAnnotation != null) : "bean annotation cannot be null";
            ExecutableElementParamInfo params = populateParameterData(method);

            TypeMirror producedType = method.getReturnType();

            BeanDefinitionWriter beanMethodWriter = createFactoryBeanMethodWriterFor(method, producedType);
            beanDefinitionWriters.put(method, beanMethodWriter);

            beanMethodWriter.visitBeanFactoryMethod(
                modelUtils.resolveTypeReference(this.concreteClass),
                method.getSimpleName().toString(),
                params.getParameters(),
                params.getQualifierTypes(),
                params.getGenericTypes()
            );

            // this needs to be tested. I only translated this from
            // InjectTransform.visitConstructorOrMethod(line 194ff)
            // but none of the Spock specs reach this code
            annotationUtils.getAnnotationElementValue("preDestroy", beanAnnotation)
                .ifPresent(destroyMethodName -> {
                    // this has to be an object type, right?
                    TypeElement element = (TypeElement)typeUtils.asElement(producedType);
                    beanMethodWriter.visitPreDestroyMethod(element.getQualifiedName(), destroyMethodName);
                });
        }

        void visitExecutableMethod(ExecutableElement method, Object o) {
            if (this.concreteClass.getSuperclass().getKind() != NONE) {
                TypeMirror producedType = method.getReturnType();
                Object returnType = modelUtils.resolveTypeReference(producedType);
                List<Object> genericTypes = genericUtils.resolveGenericTypes(producedType);
                ExecutableElementParamInfo params = populateParameterData(method);

                BeanDefinitionWriter writer = beanDefinitionWriters.get(this.concreteClass);

                writer.visitExecutableMethod(
                    modelUtils.resolveTypeReference(this.concreteClass),
                    returnType,
                    genericTypes,
                    method.getSimpleName().toString(),
                    params.getParameters(),
                    params.getQualifierTypes(),
                    params.getGenericTypes());
            }
        }

        void visitAnnotatedMethod(ExecutableElement method, boolean requiresReflection) {
            ExecutableElementParamInfo params = populateParameterData(method);
            TypeElement declaringClass = modelUtils.classElementFor(method);
            BeanDefinitionWriter writer = beanDefinitionWriters.get(this.concreteClass);
            TypeMirror producedType = method.getReturnType();
            Object returnType = modelUtils.resolveTypeReference(producedType);

            if (annotationUtils.hasStereotype(method, PostConstruct.class)) {
                writer.visitPostConstructMethod(
                    declaringClass.getQualifiedName().toString(),
                    requiresReflection,
                    returnType,
                    method.getSimpleName().toString(),
                    params.getParameters(),
                    params.getQualifierTypes(),
                    params.getGenericTypes()
                );
            } else if (annotationUtils.hasStereotype(method, PreDestroy.class)) {
                writer.visitPreDestroyMethod(
                    declaringClass.getQualifiedName().toString(),
                    requiresReflection,
                    returnType,
                    method.getSimpleName().toString(),
                    params.getParameters(),
                    params.getQualifierTypes(),
                    params.getGenericTypes()
                );
            } else if (annotationUtils.hasStereotype(method, Inject.class)) {
                writer.visitMethodInjectionPoint(
                    declaringClass.getQualifiedName().toString(),
                    requiresReflection,
                    returnType,
                    method.getSimpleName().toString(),
                    params.getParameters(),
                    params.getQualifierTypes(),
                    params.getGenericTypes()
                );
            } else {
                error("Unexpected call to visitAnnotatedMethod(%s)", method);
            }
        }

        @Override
        public Object visitVariable(VariableElement e, Object o) {
            note("Visit variable %s for %s", e.getSimpleName(), o);
            return super.visitVariable(e, o);
        }

        @Override
        public Object visitTypeParameter(TypeParameterElement e, Object o) {
            note("Visit param %s for %s", e.getSimpleName(), o);
            return super.visitTypeParameter(e, o);
        }

        @Override
        public Object visitUnknown(Element e, Object o) {
            note("Visit unknown %s for %s", e.getSimpleName(), o);
            return super.visitUnknown(e, o);
        }

        private BeanDefinitionWriter createBeanDefinitionWriterFor(TypeElement typeElement) {
            TypeMirror providerTypeParam =
                genericUtils.interfaceGenericTypeFor(typeElement, Provider.class);
            AnnotationMirror scopeAnn =
                annotationUtils.findAnnotationWithStereotype(typeElement, Scope.class);
            AnnotationMirror singletonAnn =
                annotationUtils.findAnnotationWithStereotype(typeElement, Singleton.class);

            PackageElement packageElement = elementUtils.getPackageOf(typeElement);
            String beanClassName = typeElement.getSimpleName().toString();
            String packageName = packageElement.getQualifiedName().toString();

            return new BeanDefinitionWriter(
                packageName,
                beanClassName,
                providerTypeParam == null
                    ? typeElement.getQualifiedName().toString()
                    : providerTypeParam.toString(),
                scopeAnn == null
                    ? null
                    : scopeAnn.getAnnotationType().toString(),
                singletonAnn != null);
        }

        private BeanDefinitionWriter createFactoryBeanMethodWriterFor(ExecutableElement method, TypeMirror producedType) {
            AnnotationMirror scopeAnn =
                annotationUtils.findAnnotationWithStereotype(method, Scope.class);
            AnnotationMirror singletonAnn =
                annotationUtils.findAnnotationWithStereotype(method, Singleton.class);

            TypeElement producedElement = modelUtils.classElementFor(typeUtils.asElement(producedType));
            PackageElement producedPackageElement = elementUtils.getPackageOf(producedElement);
            String producedPackageName = producedPackageElement.getQualifiedName().toString();

            PackageElement definingPackageElement = elementUtils.getPackageOf(concreteClass);
            String definingPackageName = definingPackageElement.getQualifiedName().toString();

            String producedClassName = producedElement.getSimpleName().toString();

            return new BeanDefinitionWriter(
                producedPackageName,
                producedClassName,
                scopeAnn == null
                    ? null
                    : scopeAnn.getAnnotationType().toString(),
                singletonAnn != null,
                definingPackageName
            );
        }

        ExecutableElement publicConstructorFor(TypeElement classElement) {
            // following logic of InjectTransform.groovy
            List<ExecutableElement> constructors = modelUtils.findPublicConstructors(classElement);
            if (constructors.isEmpty()) {
                return null;
            }
            if (constructors.size() == 1) {
                return constructors.get(0);
            }
            Optional<ExecutableElement> element = constructors.stream().filter(ctor -> {
                List<? extends VariableElement> parameters = ctor.getParameters();
                return !parameters.stream()
                    .filter(param -> Objects.nonNull(param.getAnnotation(Inject.class)))
                    .collect(Collectors.toList())
                    .isEmpty();
            }).findFirst();
            return element.isPresent()? element.get() : null;
        }


        private ExecutableElementParamInfo populateParameterData(ExecutableElement element) {
            ExecutableElementParamInfo params = new ExecutableElementParamInfo();
            if (element == null) {
                return params;
            }
            element.getParameters().forEach(varElem -> {

                TypeMirror typeMirror = varElem.asType();
                TypeKind kind = typeMirror.getKind();
                String argName = varElem.getSimpleName().toString();
                Object qualifier = annotationUtils.resolveQualifier(varElem);
                if (qualifier != null) {
                    params.addQualifierType(argName, qualifier);
                }

                if (kind == ARRAY) {
                    ArrayType arrayType = (ArrayType) typeMirror; // FIXME is there an API way of getting this without a cast?
                    TypeMirror componentType = arrayType.getComponentType();
                    params.addParameter(argName, arrayType.toString());
                    params.addGenericTypes(argName, Collections.singletonList(componentType.toString()));
                } else if (kind == DECLARED) {
                    DeclaredType declaredType = (DeclaredType) typeMirror;

                    TypeElement typeElement = elementUtils.getTypeElement(typeUtils.erasure(declaredType).toString());
                    assert (typeElement != null) : "typeElement cannot be null";

                    params.addParameter(argName, typeElement.toString());
                    List<Object> typeParams = declaredType.getTypeArguments().stream()
                        .map(TypeMirror::toString)
                        .collect(Collectors.toList());
                    if (!typeParams.isEmpty()) {
                        params.addGenericTypes(argName, typeParams);
                    }
                } else if (kind.isPrimitive()) {
                    String typeName = typeMirror.toString();
                    Object argType = modelUtils.classOfPrimitiveFor(typeName);
                    params.addParameter(argName, argType);
                } else {
                    error(element, "Unexpected kind %s for param %s of element %s", kind, typeMirror, element);
                }
            });

            return params;
        }
    }

    static class ExecutableElementParamInfo {
        Map<String, Object> parameters = new LinkedHashMap<>();
        Map<String, Object> qualifierTypes = new LinkedHashMap<>();
        Map<String, List<Object>> genericTypes = new LinkedHashMap<>();

        public void addParameter(String paramName, Object type) {
            parameters.put(paramName, type);
        }

        public void addQualifierType(String paramName, Object qualifier) {
            qualifierTypes.put(paramName, qualifier);
        }

        public void addGenericTypes(String paramName, List<Object> generics) {
            genericTypes.put(paramName, generics);
        }

        public Map<String, Object> getParameters() {
            return Collections.unmodifiableMap(parameters);
        }

        public Map<String, Object> getQualifierTypes() {
            return Collections.unmodifiableMap(qualifierTypes);
        }

        public Map<String, List<Object>> getGenericTypes() {
            return Collections.unmodifiableMap(genericTypes);
        }
    }
}



