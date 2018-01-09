/*
 * Copyright 2017 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.particleframework.annotation.processing;

import org.particleframework.aop.Interceptor;
import org.particleframework.aop.Introduction;
import org.particleframework.aop.writer.AopProxyWriter;
import org.particleframework.context.annotation.*;
import org.particleframework.inject.annotation.AnnotationMetadataReference;
import org.particleframework.core.annotation.Internal;
import org.particleframework.core.util.StringUtils;
import org.particleframework.core.value.OptionalValues;
import org.particleframework.core.naming.NameUtils;
import org.particleframework.core.util.ArrayUtils;
import org.particleframework.context.annotation.Executable;
import org.particleframework.core.annotation.AnnotationMetadata;
import org.particleframework.inject.annotation.JavaAnnotationMetadataBuilder;
import org.particleframework.inject.writer.*;

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
import javax.lang.model.type.*;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.ElementScanner8;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static javax.lang.model.element.ElementKind.*;
import static javax.lang.model.type.TypeKind.ARRAY;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class BeanDefinitionInjectProcessor extends AbstractInjectAnnotationProcessor {

    private static final String[] ANNOTATION_STEREOTYPES = new String[]{
            "javax.annotation.PostConstruct",
            "javax.annotation.PreDestroy",
            "javax.inject.Inject",
            "javax.inject.Qualifier",
            "javax.inject.Singleton",
            "org.particleframework.context.annotation.Bean",
            "org.particleframework.context.annotation.Replaces",
            "org.particleframework.context.annotation.Value",
            "org.particleframework.context.annotation.Executable"

    };
    private static final String AROUND_TYPE = "org.particleframework.aop.Around";
    private static final String INTRODUCTION_TYPE = "org.particleframework.aop.Introduction";

    private Map<String, AnnBeanElementVisitor> beanDefinitionWriters;
    private ClassWriterOutputVisitor classWriterOutputVisitor;
    private Set<String> processed = new HashSet<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.beanDefinitionWriters = new LinkedHashMap<>();
        this.classWriterOutputVisitor = new BeanDefinitionWriterVisitor(filer, getTargetDirectory().orElse(null));
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        if (annotations.isEmpty()) {
            return false;
        }
        annotations = annotations.stream().filter(ann -> annotationUtils.hasStereotype(ann, ANNOTATION_STEREOTYPES)).collect(Collectors.toSet());
        if (!annotations.isEmpty()) {

            // accumulate all the class elements for all annotated elements
            annotations.forEach(annotation -> roundEnv.getElementsAnnotatedWith(annotation)
                    .stream()
                    // filtering annotation definitions, which are not processed
                    .filter(element -> element.getKind() != ANNOTATION_TYPE)
                    .forEach(element -> {
                        TypeElement typeElement = modelUtils.classElementFor(element);
                        String name = typeElement.getQualifiedName().toString();
                        if (!beanDefinitionWriters.containsKey(name)) {
                            if (!modelUtils.isAbstract(typeElement)) {
                                if (!processed.contains(name) && !name.endsWith(BeanDefinitionVisitor.PROXY_SUFFIX)) {
                                    AnnBeanElementVisitor visitor = new AnnBeanElementVisitor(typeElement);
                                    beanDefinitionWriters.put(name, visitor);
                                }
                            } else {
                                if (annotationUtils.hasStereotype(typeElement, INTRODUCTION_TYPE)) {
                                    AnnBeanElementVisitor visitor = new AnnBeanElementVisitor(typeElement);
                                    beanDefinitionWriters.put(name, visitor);
                                }
                            }
                        }
                    }));

            // remove already processed the annotations
            for (String name : processed) {
                beanDefinitionWriters.remove(name);
            }

            // process remaining

            int count = beanDefinitionWriters.size();
            if (count > 0) {

                note("Creating bean classes for %s type elements", count);
                beanDefinitionWriters.forEach((key, visitor) -> {
                    TypeElement classElement = visitor.getConcreteClass();
                    String className = classElement.getQualifiedName().toString();
                    classElement.accept(visitor, className);
                    visitor.getBeanDefinitionWriters().forEach((name, writer) -> {
                        String beanDefinitionName = writer.getBeanDefinitionName();
                        if (!processed.contains(beanDefinitionName)) {
                            processed.add(beanDefinitionName);
                            processBeanDefinitions(classElement, writer);
                        }
                    });

                });
                return true;
            }
        }
        return false;
    }

    private void processBeanDefinitions(TypeElement beanClassElement, BeanDefinitionVisitor beanDefinitionWriter) {
        try {
            beanDefinitionWriter.visitBeanDefinitionEnd();
            beanDefinitionWriter.accept(classWriterOutputVisitor);

            String beanDefinitionName = beanDefinitionWriter.getBeanDefinitionName();
            String beanTypeName = beanDefinitionWriter.getBeanTypeName();

            AnnotationMetadata annotationMetadata = beanDefinitionWriter.getAnnotationMetadata();
            BeanDefinitionReferenceWriter beanDefinitionReferenceWriter =
                    new BeanDefinitionReferenceWriter(beanTypeName, beanDefinitionName, annotationMetadata);
            String className = beanDefinitionReferenceWriter.getBeanDefinitionQualifiedClassName();
            processed.add(className);
            beanDefinitionReferenceWriter.setContextScope(
                    annotationUtils.hasStereotype(beanClassElement, Context.class));

            Optional<String> replacesType = annotationUtils.getAnnotationMetadata(beanClassElement).getValue(Replaces.class, String.class);
            replacesType.ifPresent(beanDefinitionReferenceWriter::setReplaceBeanName);
            beanDefinitionReferenceWriter.accept(classWriterOutputVisitor);
        } catch (IOException e) {
            // raise a compile error
            error("Unexpected error: %s", e.getMessage());
        }
    }

    class AnnBeanElementVisitor extends ElementScanner8<Object, Object> {
        private final TypeElement concreteClass;
        private final Map<Name, BeanDefinitionVisitor> beanDefinitionWriters;
        private final boolean isConfigurationPropertiesType;
        private final boolean isFactoryType;
        private final boolean isExecutableType;
        private final boolean isAopProxyType;
        private final OptionalValues<Boolean> aopSettings;
        private ExecutableElementParamInfo constructorParamterInfo;

        AnnBeanElementVisitor(TypeElement concreteClass) {
            this.concreteClass = concreteClass;
            beanDefinitionWriters = new LinkedHashMap<>();
            this.isFactoryType = annotationUtils.hasStereotype(concreteClass, Factory.class);
            this.isConfigurationPropertiesType = isConfigurationProperties(concreteClass);

            this.isAopProxyType = annotationUtils.hasStereotype(concreteClass, AROUND_TYPE);
            this.aopSettings = isAopProxyType ? annotationUtils.getAnnotationMetadata(concreteClass).getValues(AROUND_TYPE, Boolean.class) : OptionalValues.empty();
            this.isExecutableType = isAopProxyType || annotationUtils.hasStereotype(concreteClass, Executable.class);
        }

        TypeElement getConcreteClass() {
            return concreteClass;
        }

        Map<Name, BeanDefinitionVisitor> getBeanDefinitionWriters() {
            return beanDefinitionWriters;
        }

        @Override
        public Object visitType(TypeElement classElement, Object o) {

            if (annotationUtils.hasStereotype(classElement, INTRODUCTION_TYPE) && modelUtils.isAbstract(classElement)) {

                AnnotationMetadata typeAnnotationMetadata = annotationUtils.getAnnotationMetadata(classElement);
                AopProxyWriter aopProxyWriter = createAopWriterFor(classElement);
                ExecutableElement constructor = classElement.getKind() == ElementKind.CLASS ? modelUtils.concreteConstructorFor(classElement) : null;
                ExecutableElementParamInfo constructorData = constructor != null ? populateParameterData(constructor) : null;
                if (constructorData != null) {
                    aopProxyWriter.visitBeanDefinitionConstructor(
                            constructorData.getParameters(),
                            constructorData.getQualifierTypes(),
                            constructorData.getGenericTypes()
                    );
                } else {
                    aopProxyWriter.visitBeanDefinitionConstructor();
                }
                beanDefinitionWriters.put(classElement.getQualifiedName(), aopProxyWriter);
                classElement.asType().accept(new PublicMethodVisitor<Object, AopProxyWriter>() {

                    @Override
                    protected void accept(ExecutableElement method, AopProxyWriter aopProxyWriter) {
                        ExecutableElementParamInfo params = populateParameterData(method);
                        Object owningType = modelUtils.resolveTypeReference(method.getEnclosingElement());
                        if (owningType == null) {
                            throw new IllegalStateException("Owning type cannot be null");
                        }
                        Object resolvedReturnType = modelUtils.resolveTypeReference(method.getReturnType());
                        Map<String, Object> returnTypeGenerics = genericUtils.resolveGenericTypes(method.getReturnType());
                        String methodName = method.getSimpleName().toString();
                        Map<String, Object> methodParameters = params.getParameters();
                        Map<String, Object> methodQualifier = params.getQualifierTypes();
                        Map<String, Map<String, Object>> methodGenericTypes = params.getGenericTypes();
                        AnnotationMetadata annotationMetadata;
                        if( annotationUtils.isAnnotated(method) ) {
                            annotationMetadata = annotationUtils.getAnnotationMetadata(classElement, method);
                        }
                        else {
                            annotationMetadata = new AnnotationMetadataReference(
                                    aopProxyWriter.getBeanDefinitionName() + BeanDefinitionReferenceWriter.REF_SUFFIX,
                                    typeAnnotationMetadata
                            );
                        }


                        aopProxyWriter.visitAroundMethod(
                                owningType,
                                resolvedReturnType,
                                returnTypeGenerics,
                                methodName,
                                methodParameters,
                                methodQualifier,
                                methodGenericTypes,
                                annotationMetadata

                        );
                    }

                    @Override
                    protected boolean isAcceptable(ExecutableElement executableElement) {
                        Set<Modifier> modifiers = executableElement.getModifiers();
                        return modelUtils.isAbstract(executableElement) && !modifiers.contains(Modifier.FINAL) && !modifiers.contains(Modifier.STATIC);
                    }
                }, aopProxyWriter);
                return null;
            } else {
                assert (classElement.getKind() == CLASS) : "classElement must be a class";

                Element enclosingElement = classElement.getEnclosingElement();
                // don't process inner class unless this is the visitor for it
                if (!enclosingElement.getKind().isClass() ||
                        concreteClass.getQualifiedName().equals(classElement.getQualifiedName())) {

                    if (concreteClass.getQualifiedName().equals(classElement.getQualifiedName())) {
                        // we know this class has supported annotations so we need a beandef writer for it
                        BeanDefinitionWriter beanDefinitionWriter = createBeanDefinitionWriterFor(classElement);
                        beanDefinitionWriters.put(concreteClass.getQualifiedName(), beanDefinitionWriter);

                        ExecutableElement constructor = modelUtils.concreteConstructorFor(classElement);
                        this.constructorParamterInfo = populateParameterData(constructor);
                        Name proxyKey = createProxyKey(beanDefinitionWriter.getBeanDefinitionName());
                        BeanDefinitionVisitor proxyWriter = beanDefinitionWriters.get(proxyKey);
                        if (proxyWriter != null) {
                            proxyWriter.visitBeanDefinitionConstructor(
                                    constructorParamterInfo.getParameters(),
                                    constructorParamterInfo.getQualifierTypes(),
                                    constructorParamterInfo.getGenericTypes());

                        }
                        beanDefinitionWriter.visitBeanDefinitionConstructor(
                                constructorParamterInfo.getParameters(),
                                constructorParamterInfo.getQualifierTypes(),
                                constructorParamterInfo.getGenericTypes());

                        if (isAopProxyType) {
                            Object[] interceptorTypes = annotationUtils.getAnnotationMetadata(concreteClass)
                                    .getAnnotationNamesByStereotype(AROUND_TYPE)
                                    .toArray();
                            resolveAopProxyWriter(
                                    beanDefinitionWriter,
                                    aopSettings,
                                    false,
                                    this.constructorParamterInfo,
                                    interceptorTypes);
                        }

                    }

                    List<? extends Element> elements = classElement.getEnclosedElements().stream()
                            // already handled the public ctor
                            .filter(element -> element.getKind() != CONSTRUCTOR)
                            .collect(Collectors.toList());

                    if (isConfigurationPropertiesType) {
                        // handle non @Inject, @Value fields as config properties
                        List<? extends Element> members = elementUtils.getAllMembers(classElement);
                        ElementFilter.fieldsIn(members).forEach(
                                field -> {
                                    if (!modelUtils.isStatic(field) && !modelUtils.isFinal(field)) {
                                        visitConfigurationProperty(field);
                                    }
                                }
                        );
                        ElementFilter.methodsIn(members).forEach(method -> {
                            boolean isCandidateMethod = !modelUtils.isStatic(method) &&
                                                            !modelUtils.isPrivate(method) &&
                                                            !modelUtils.isAbstract(method) &&
                                                            NameUtils.isSetterName(method.getSimpleName().toString());
                            if(isCandidateMethod) {
                                Element e = method.getEnclosingElement();
                                if(e instanceof TypeElement && !e.equals(classElement)) {
                                    if(!annotationUtils.hasStereotype(e, ConfigurationProperties.class)) {
                                        visitConfigurationPropertySetter(method);
                                    }
                                }
                            }
                        });
                    } else {
                        TypeElement superClass = modelUtils.superClassFor(classElement);
                        if (superClass != null && !modelUtils.isObjectClass(superClass)) {
                            superClass.accept(this, o);
                        }
                    }

                    return scan(elements, o);
                } else {
                    return null;
                }
            }
        }

        @Override
        public Object visitExecutable(ExecutableElement method, Object o) {
            if (method.getKind() == ElementKind.CONSTRUCTOR) {
                // ctor is handled by visitType
                error("Unexpected call to visitExecutable for ctor %s of %s",
                        method.getSimpleName(), o);
                return null;
            }

            AnnotationMetadata methodAnnotationMetadata = annotationUtils.getAnnotationMetadata(method);
            // handle @Bean annotation for @Factory class
            if (isFactoryType && methodAnnotationMetadata.hasDeclaredStereotype(Bean.class, Scope.class) && method.getReturnType().getKind() == TypeKind.DECLARED) {
                visitBeanFactoryMethod(method);
                return null;
            }

            if (modelUtils.isStatic(method) || modelUtils.isAbstract(method)) {
                return null;
            }

            boolean injected = methodAnnotationMetadata.hasStereotype(Inject.class);
            boolean postConstruct = methodAnnotationMetadata.hasStereotype(PostConstruct.class);
            boolean preDestroy = methodAnnotationMetadata.hasStereotype(PreDestroy.class);
            if (injected || postConstruct || preDestroy) {

                visitAnnotatedMethod(method, o);
                return null;
            }

            TypeElement declaringClassElement = modelUtils.classElementFor(method);
            if (modelUtils.isObjectClass(declaringClassElement)) {
                return null;
            }

            Set<Modifier> modifiers = method.getModifiers();
            boolean hasInvalidModifiers = modelUtils.isAbstract(method) || modifiers.contains(Modifier.STATIC) || methodAnnotationMetadata.hasAnnotation(Internal.class) || modelUtils.isPrivate(method);
            boolean isPublic = modifiers.contains(Modifier.PUBLIC) && !hasInvalidModifiers;
            boolean isExecutable = ((isExecutableType && isPublic) || methodAnnotationMetadata.hasStereotype(Executable.class)) && !hasInvalidModifiers;
            if (isExecutable) {
                visitExecutableMethod(method, methodAnnotationMetadata);
                return null;
            }
            else if(isConfigurationPropertiesType && modelUtils.isPublic(method) && NameUtils.isSetterName(method.getSimpleName().toString()) && method.getParameters().size() == 1) {
                visitConfigurationPropertySetter(method);
            }

            return null;
        }

        private void visitConfigurationPropertySetter(ExecutableElement method) {
            BeanDefinitionVisitor writer = beanDefinitionWriters.get(this.concreteClass.getQualifiedName());
            if (!writer.isValidated() && annotationUtils.hasStereotype(method, "javax.validation.Constraint")) {
                writer.setValidated(true);
            }
            String qualifierRef = annotationUtils.resolveQualifier(method);
            TypeMirror valueType = method.getParameters().get(0).asType();
            Object fieldType = modelUtils.resolveTypeReference(valueType);
            Map<String, Object> genericTypes = Collections.emptyMap();
            TypeKind typeKind = valueType.getKind();
            if (!(typeKind.isPrimitive() || typeKind == ARRAY)) {
                genericTypes = genericUtils.resolveGenericTypes(valueType);
            }

            TypeElement declaringClass = modelUtils.classElementFor(method);

            writer.visitSetterValue(
                    modelUtils.resolveTypeReference(declaringClass),
                    qualifierRef,
                    modelUtils.requiresReflection(method),
                    fieldType,
                    method.getSimpleName().toString(),
                    genericTypes,
                    true);
        }

        void visitBeanFactoryMethod(ExecutableElement beanMethod) {
            TypeMirror returnType = beanMethod.getReturnType();
            ExecutableElementParamInfo beanMethodParams = populateParameterData(beanMethod);

            BeanDefinitionWriter beanMethodWriter = createFactoryBeanMethodWriterFor(beanMethod, returnType);
            beanDefinitionWriters.put(beanMethod.getSimpleName(), beanMethodWriter);

            final String beanMethodName = beanMethod.getSimpleName().toString();
            final Map<String, Object> beanMethodParameters = beanMethodParams.getParameters();
            final Object beanMethodDeclaringType = modelUtils.resolveTypeReference(beanMethod.getEnclosingElement());
            beanMethodWriter.visitBeanFactoryMethod(
                    beanMethodDeclaringType,
                    beanMethodName,
                    beanMethodParameters,
                    beanMethodParams.getQualifierTypes(),
                    beanMethodParams.getGenericTypes()
            );

            AnnotationMetadata methodAnnotationMetadata = new JavaAnnotationMetadataBuilder(elementUtils).buildForMethod(beanMethod);
            if (methodAnnotationMetadata.hasStereotype(AROUND_TYPE)) {
                Object[] interceptorTypes = methodAnnotationMetadata
                        .getAnnotationNamesByStereotype(AROUND_TYPE)
                        .toArray();
                TypeElement returnTypeElement = (TypeElement) ((DeclaredType) beanMethod.getReturnType()).asElement();
                ExecutableElement constructor = returnTypeElement.getKind() == ElementKind.CLASS ? modelUtils.concreteConstructorFor(returnTypeElement) : null;
                ExecutableElementParamInfo constructorData = constructor != null ? populateParameterData(constructor) : null;
                OptionalValues<Boolean> aopSettings = methodAnnotationMetadata.getValues(AROUND_TYPE, Boolean.class);
                Map<CharSequence, Boolean> finalSettings = new LinkedHashMap<>();
                for (CharSequence setting : aopSettings) {
                    Optional<Boolean> entry = aopSettings.get(setting);
                    entry.ifPresent(val ->
                            finalSettings.put(setting, val)
                    );
                }
                finalSettings.put(Interceptor.PROXY_TARGET, true);
                AopProxyWriter proxyWriter = resolveAopProxyWriter(
                        beanMethodWriter,
                        OptionalValues.of(Boolean.class, finalSettings),
                        true,
                        constructorData,
                        interceptorTypes);

                returnType.accept(new PublicMethodVisitor<Object, AopProxyWriter>() {
                    @Override
                    protected void accept(ExecutableElement method, AopProxyWriter aopProxyWriter) {
                        ExecutableElementParamInfo params = populateParameterData(method);
                        Object owningType = modelUtils.resolveTypeReference(method.getEnclosingElement());
                        if (owningType == null) {
                            throw new IllegalStateException("Owning type cannot be null");
                        }
                        Object resolvedReturnType = modelUtils.resolveTypeReference(method.getReturnType());
                        Map<String, Object> returnTypeGenerics = genericUtils.resolveGenericTypes(method.getReturnType());
                        String methodName = method.getSimpleName().toString();
                        Map<String, Object> methodParameters = params.getParameters();
                        Map<String, Object> methodQualifier = params.getQualifierTypes();
                        Map<String, Map<String, Object>> methodGenericTypes = params.getGenericTypes();

                        AnnotationMetadata annotationMetadata;
                        boolean isAnnotationReference = false;
                        if( annotationUtils.isAnnotated(method) ) {
                            annotationMetadata = annotationUtils.getAnnotationMetadata(beanMethod, method);
                        }
                        else {
                            isAnnotationReference = true;
                            annotationMetadata = new AnnotationMetadataReference(
                                    beanMethodWriter.getBeanDefinitionName() + BeanDefinitionReferenceWriter.REF_SUFFIX,
                                    methodAnnotationMetadata
                            );
                        }

                        ExecutableMethodWriter executableMethodWriter = beanMethodWriter.visitExecutableMethod(
                                owningType,
                                resolvedReturnType,
                                returnTypeGenerics,
                                methodName,
                                methodParameters,
                                methodQualifier,
                                methodGenericTypes,
                                annotationMetadata
                        );


                        aopProxyWriter.visitAroundMethod(
                                owningType,
                                resolvedReturnType,
                                returnTypeGenerics,
                                methodName,
                                methodParameters,
                                methodQualifier,
                                methodGenericTypes,
                                !isAnnotationReference ? new AnnotationMetadataReference(executableMethodWriter.getClassName(),annotationMetadata): annotationMetadata

                        );
                    }
                }, proxyWriter);
            }

            if (methodAnnotationMetadata.isPresent(Bean.class, "preDestroy")) {

                Optional<String> preDestroyMethod = methodAnnotationMetadata
                        .getValue(Bean.class, "preDestroy", String.class);
                preDestroyMethod
                        .ifPresent(destroyMethodName -> {
                            if (StringUtils.isNotEmpty(destroyMethodName)) {
                                TypeElement destroyMethodDeclaringClass = (TypeElement) typeUtils.asElement(returnType);
                                beanMethodWriter.visitPreDestroyMethod(
                                        destroyMethodDeclaringClass.getQualifiedName().toString(),
                                        destroyMethodName
                                );
                            }
                        });
            }
        }

        void visitExecutableMethod(ExecutableElement method, AnnotationMetadata methodAnnotationMetadata) {
            TypeMirror returnType = method.getReturnType();
            Map<String, Object> returnTypeGenerics = genericUtils.resolveGenericTypes(returnType);
            ExecutableElementParamInfo params = populateParameterData(method);

            BeanDefinitionVisitor beanWriter = beanDefinitionWriters.get(this.concreteClass.getQualifiedName());


            Object typeRef = modelUtils.resolveTypeReference(method.getEnclosingElement());
            if (typeRef == null) typeRef = modelUtils.resolveTypeReference(concreteClass);

            ExecutableMethodWriter executableMethodWriter = beanWriter.visitExecutableMethod(
                    typeRef,
                    modelUtils.resolveTypeReference(returnType),
                    returnTypeGenerics,
                    method.getSimpleName().toString(),
                    params.getParameters(),
                    params.getQualifierTypes(),
                    params.getGenericTypes(), methodAnnotationMetadata);

            boolean hasExplicitAround = methodAnnotationMetadata.hasStereotype(AROUND_TYPE);
            if (isAopProxyType || hasExplicitAround) {
                if (isAopProxyType && !hasExplicitAround && !method.getModifiers().contains(Modifier.PUBLIC)) {
                    // ignore methods that are not public and have no explicit advise
                    return;
                }

                Object[] interceptorTypes = methodAnnotationMetadata
                        .getAnnotationNamesByStereotype(AROUND_TYPE)
                        .toArray();

                OptionalValues<Boolean> settings = methodAnnotationMetadata.getValues(AROUND_TYPE, Boolean.class);
                AopProxyWriter aopProxyWriter = resolveAopProxyWriter(
                        beanWriter,
                        settings,
                        false,
                        this.constructorParamterInfo,
                        interceptorTypes
                );

                aopProxyWriter.visitInterceptorTypes(interceptorTypes);

                boolean isAnnotationReference = methodAnnotationMetadata instanceof AnnotationMetadataReference;

                aopProxyWriter.visitAroundMethod(
                        typeRef,
                        modelUtils.resolveTypeReference(returnType),
                        returnTypeGenerics,
                        method.getSimpleName().toString(),
                        params.getParameters(),
                        params.getQualifierTypes(),
                        params.getGenericTypes(), !isAnnotationReference ? new AnnotationMetadataReference(executableMethodWriter.getClassName(),methodAnnotationMetadata): methodAnnotationMetadata);


            }
        }

        private AopProxyWriter resolveAopProxyWriter(BeanDefinitionVisitor beanWriter,
                                                     OptionalValues<Boolean> aopSettings,
                                                     boolean isFactoryType,
                                                     ExecutableElementParamInfo constructorParamterInfo,
                                                     Object... interceptorTypes) {
            String beanName = beanWriter.getBeanDefinitionName();
            Name proxyKey = createProxyKey(beanName);
            BeanDefinitionVisitor aopWriter = beanDefinitionWriters.get(proxyKey);

            AopProxyWriter aopProxyWriter;
            if (aopWriter == null) {
                aopProxyWriter
                        = new AopProxyWriter(
                        (BeanDefinitionWriter) beanWriter,
                        aopSettings,
                        interceptorTypes

                );


                if (constructorParamterInfo != null) {
                    aopProxyWriter.visitBeanDefinitionConstructor(
                            constructorParamterInfo.getParameters(),
                            constructorParamterInfo.getQualifierTypes(),
                            constructorParamterInfo.getGenericTypes()
                    );
                } else {
                    aopProxyWriter.visitBeanDefinitionConstructor();
                }

                if (isFactoryType) {
                    aopProxyWriter
                            .visitSuperFactoryType(beanName);
                } else {
                    aopProxyWriter
                            .visitSuperType(beanName);
                }
                aopWriter = aopProxyWriter;
                beanDefinitionWriters.put(
                        proxyKey,
                        aopWriter
                );
            } else {
                aopProxyWriter = (AopProxyWriter) aopWriter;
            }
            return aopProxyWriter;
        }

        void visitAnnotatedMethod(ExecutableElement method, Object o) {
            ExecutableElementParamInfo params = populateParameterData(method);
            BeanDefinitionVisitor writer = beanDefinitionWriters.get(this.concreteClass.getQualifiedName());
            TypeMirror returnType = method.getReturnType();
            TypeElement declaringClass = modelUtils.classElementFor(method);

            boolean isParent = !declaringClass.getQualifiedName().equals(this.concreteClass.getQualifiedName());
            ExecutableElement overridingMethod = modelUtils.overridingOrHidingMethod(method, this.concreteClass).orElse(method);
            TypeElement overridingClass = modelUtils.classElementFor(overridingMethod);
            boolean overridden = isParent &&
                    !overridingClass.getQualifiedName().equals(declaringClass.getQualifiedName());

            boolean isPackagePrivate = modelUtils.isPackagePrivate(method);
            boolean isPrivate = modelUtils.isPrivate(method);
            if (overridden && !(isPrivate || isPackagePrivate)) {
                // bail out if the method has been overridden, since it will have already been handled
                return;
            }

            PackageElement packageOfOverridingClass = elementUtils.getPackageOf(overridingMethod);
            PackageElement packageOfDeclaringClass = elementUtils.getPackageOf(declaringClass);
            boolean isPackagePrivateAndPackagesDiffer = overridden && isPackagePrivate &&
                    !packageOfOverridingClass.getQualifiedName().equals(packageOfDeclaringClass.getQualifiedName());
            boolean requiresReflection = isPrivate || isPackagePrivateAndPackagesDiffer;
            boolean overriddenInjected = overridden && annotationUtils.hasStereotype(overridingMethod, Inject.class);

            if (isParent && isPackagePrivate && !isPackagePrivateAndPackagesDiffer && overriddenInjected) {
                // bail out if the method has been overridden by another method annotated with @Inject
                return;
            }
            if (isParent && overridden && !overriddenInjected && !isPackagePrivateAndPackagesDiffer && !isPrivate) {
                // bail out if the overridden method is package private and in the same package
                // and is not annotated with @Inject
                return;
            }
            if (!requiresReflection && modelUtils.isInheritedAndNotPublic(this.concreteClass, declaringClass, method)) {
                requiresReflection = true;
            }

            if (annotationUtils.hasStereotype(method, PostConstruct.class)) {
                writer.visitPostConstructMethod(
                        modelUtils.resolveTypeReference(declaringClass),
                        requiresReflection,
                        modelUtils.resolveTypeReference(returnType),
                        method.getSimpleName().toString(),
                        params.getParameters(),
                        params.getQualifierTypes(),
                        params.getGenericTypes()
                );
            } else if (annotationUtils.hasStereotype(method, PreDestroy.class)) {
                writer.visitPreDestroyMethod(
                        modelUtils.resolveTypeReference(declaringClass),
                        requiresReflection,
                        modelUtils.resolveTypeReference(returnType),
                        method.getSimpleName().toString(),
                        params.getParameters(),
                        params.getQualifierTypes(),
                        params.getGenericTypes()
                );
            } else if (annotationUtils.hasStereotype(method, Inject.class)) {
                writer.visitMethodInjectionPoint(
                        modelUtils.resolveTypeReference(declaringClass),
                        requiresReflection,
                        modelUtils.resolveTypeReference(returnType),
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
        public Object visitVariable(VariableElement variable, Object o) {
            if (modelUtils.isStatic(variable) || modelUtils.isFinal(variable)) {
                return null;
            }

            // assuming just fields, visitExecutable should be handling params for method calls
            if (variable.getKind() != FIELD) return null;

            boolean isInjected = annotationUtils.hasStereotype(variable, Inject.class);
            boolean isValue = !isInjected &&
                    (annotationUtils.hasStereotype(variable, Value.class)); // || isConfigurationPropertiesType);

            if (isInjected || isValue) {
                BeanDefinitionVisitor writer = beanDefinitionWriters.get(this.concreteClass.getQualifiedName());

                TypeElement declaringClass = modelUtils.classElementFor(variable);
                String qualifierRef = annotationUtils.resolveQualifier(variable);

                boolean isPrivate = modelUtils.isPrivate(variable);
                boolean requiresReflection = isPrivate
                        || modelUtils.isInheritedAndNotPublic(this.concreteClass, declaringClass, variable);

                if (!writer.isValidated()
                        && annotationUtils.hasStereotype(variable, "javax.validation.Constraint")) {
                    writer.setValidated(true);
                }

                Name fieldName = variable.getSimpleName();
                TypeMirror type = variable.asType();
                Object fieldType = modelUtils.resolveTypeReference(type);

                if (isValue) {
                    writer.visitFieldValue(
                            modelUtils.resolveTypeReference(declaringClass),
                            qualifierRef,
                            requiresReflection,
                            fieldType,
                            fieldName.toString(),
                            isConfigurationPropertiesType //isConfigurationPropertiesType
                    );
                } else {
                    writer.visitFieldInjectionPoint(
                            modelUtils.resolveTypeReference(declaringClass),
                            qualifierRef,
                            requiresReflection,
                            fieldType,
                            fieldName.toString()
                    );
                }
            }
            return null;
        }

        public Object visitConfigurationProperty(VariableElement field) {
            Optional<ExecutableElement> setterMethod = modelUtils.findSetterMethodFor(field);
            boolean isInjected = annotationUtils.hasStereotype(field, Inject.class);
            boolean isValue = annotationUtils.hasStereotype(field, Value.class);

            boolean isMethodInjected = isInjected || (setterMethod.isPresent() && annotationUtils.hasStereotype(setterMethod.get(), Inject.class));
            if (!(isMethodInjected || isValue)) {
                // visitVariable didn't handle it
                BeanDefinitionVisitor writer = beanDefinitionWriters.get(this.concreteClass.getQualifiedName());
                if (!writer.isValidated() && annotationUtils.hasStereotype(field, "javax.validation.Constraint")) {
                    writer.setValidated(true);
                }
                String qualifierRef = annotationUtils.resolveQualifier(field);
                TypeMirror fieldTypeMirror = field.asType();
                Object fieldType = modelUtils.resolveTypeReference(fieldTypeMirror);
                Map<String, Object> genericTypes = Collections.emptyMap();
                TypeKind typeKind = fieldTypeMirror.getKind();

                if (!(typeKind.isPrimitive() || typeKind == ARRAY)) {
                    genericTypes = genericUtils.resolveGenericTypes(fieldTypeMirror);
                }

                TypeElement declaringClass = modelUtils.classElementFor(field);


                if(annotationUtils.hasStereotype(field, ConfigurationBuilder.class)) {
                    String fieldName = field.getSimpleName().toString();
                    ConfigBuilder configBuilder = new ConfigBuilder(fieldType).forField(fieldName);
                    writer.visitConfigBuilderStart(configBuilder);

                    try {
                        visitConfigurationBuilder(field, fieldTypeMirror, writer);
                    } finally {
                        writer.visitConfigBuilderEnd();
                    }
                }
                else {

                    if (setterMethod.isPresent()) {
                        ExecutableElement method = setterMethod.get();
                        writer.visitSetterValue(
                                modelUtils.resolveTypeReference(declaringClass),
                                qualifierRef,
                                modelUtils.requiresReflection(method),
                                fieldType,
                                field.getSimpleName().toString(),
                                method.getSimpleName().toString(),
                                genericTypes,
                                isConfigurationPropertiesType);
                    } else {
                        boolean isPrivate = modelUtils.isPrivate(field);
                        boolean requiresReflection = isInheritedAndNotPublic(modelUtils.classElementFor(field), field.getModifiers());

                        if(!isPrivate) {
                            Object declaringType = modelUtils.resolveTypeReference(declaringClass);
                            String fieldName = field.getSimpleName().toString();
                            writer.visitFieldValue(
                                    declaringType,
                                    qualifierRef,
                                    requiresReflection,
                                    fieldType,
                                    fieldName,
                                    isConfigurationPropertiesType);

                        }
                    }
                }

            }

            return null;
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

        protected boolean isInheritedAndNotPublic(TypeElement declaringClass, Set<Modifier> modifiers) {
            PackageElement declaringPackage = elementUtils.getPackageOf(declaringClass);
            PackageElement concretePackage = elementUtils.getPackageOf(concreteClass);
            return !declaringClass.equals(concreteClass) &&
                    !declaringPackage.equals(concretePackage) &&
                    !(modifiers.contains(Modifier.PUBLIC));
        }

        private void visitConfigurationBuilder(Element builderElement, TypeMirror builderType, BeanDefinitionVisitor writer) {
            AnnotationMetadata annotationMetadata = annotationUtils.getAnnotationMetadata(builderElement);
            Boolean allowZeroArgs = annotationMetadata.getValue(ConfigurationBuilder.class, "allowZeroArgs", Boolean.class).orElse(false);
            List<String> prefixes = Arrays.asList(annotationMetadata.getValue(ConfigurationBuilder.class, "prefixes", String[].class).orElse(new String[]{"set"}));
            String configurationPrefix = annotationMetadata.getValue(ConfigurationBuilder.class, "configurationPrefix", String.class).orElse("");
            PublicMethodVisitor visitor = new PublicMethodVisitor() {
                @Override
                protected void accept(ExecutableElement method, Object o) {
                    List<? extends VariableElement> params = method.getParameters();
                    String methodName = method.getSimpleName().toString();
                    String prefix = getMethodPrefix(prefixes, methodName);
                    VariableElement paramType = params.size() == 1 ? params.get(0) : null;
                    Object expectedType = paramType != null ? modelUtils.resolveTypeReference(paramType.asType()) : null;
                    writer.visitConfigBuilderMethod(
                            prefix,
                            configurationPrefix,
                            modelUtils.resolveTypeReference(method.getReturnType()),
                            methodName,
                            expectedType,
                            paramType != null ? genericUtils.resolveGenericTypes(paramType.asType()) : null
                    );
                }

                @Override
                protected boolean isAcceptable(ExecutableElement executableElement) {
                    int paramCount = executableElement.getParameters().size();
                    return (paramCount == 1 || allowZeroArgs && paramCount == 0) && super.isAcceptable(executableElement) && isPrefixedWith(executableElement, prefixes);
                }

                private boolean isPrefixedWith(Element enclosedElement, List<String> prefixes) {
                    String name = enclosedElement.getSimpleName().toString();
                    for (String prefix : prefixes) {
                        if (name.startsWith(prefix)) return true;
                    }
                    return false;
                }

                private String getMethodPrefix(List<String> prefixes, String methodName) {
                    for (String prefix : prefixes) {
                        if(methodName.startsWith(prefix)) {
                            return prefix;
                        }
                    }
                    return methodName;
                }
            };

            builderType.accept(visitor, null);
        }

        private BeanDefinitionWriter createBeanDefinitionWriterFor(TypeElement typeElement) {
            TypeMirror providerTypeParam =
                    genericUtils.interfaceGenericTypeFor(typeElement, Provider.class);
            AnnotationMetadata annotationMetadata = annotationUtils.getAnnotationMetadata(typeElement);

            PackageElement packageElement = elementUtils.getPackageOf(typeElement);
            String beanClassName = modelUtils.simpleBinaryNameFor(typeElement);

            boolean isSingleton = annotationMetadata.hasDeclaredStereotype(Singleton.class);
            boolean isInterface = typeElement.getKind() == ElementKind.INTERFACE;
            BeanDefinitionWriter beanDefinitionWriter = new BeanDefinitionWriter(
                    packageElement.getQualifiedName().toString(),
                    beanClassName,
                    providerTypeParam == null
                            ? elementUtils.getBinaryName(typeElement).toString()
                            : providerTypeParam.toString(),
                    isInterface,
                    isSingleton, annotationMetadata);
            return beanDefinitionWriter;
        }

        private boolean isConfigurationProperties(TypeElement concreteClass) {
            AnnotationMetadata annotationMetadata = annotationUtils.getAnnotationMetadata(concreteClass);
            return annotationMetadata.hasDeclaredStereotype(ConfigurationReader.class) || annotationMetadata.hasDeclaredStereotype(EachProperty.class);
        }

        private DynamicName createProxyKey(String beanName) {
            return new DynamicName(beanName + "$Proxy");
        }

        private AopProxyWriter createAopWriterFor(TypeElement typeElement) {
            AnnotationMetadata annotationMetadata = annotationUtils.getAnnotationMetadata(typeElement);

            PackageElement packageElement = elementUtils.getPackageOf(typeElement);
            String beanClassName = modelUtils.simpleBinaryNameFor(typeElement);
            Object[] aroundInterceptors = annotationUtils.getAnnotationMetadata(typeElement)
                    .getAnnotationNamesByStereotype(AROUND_TYPE)
                    .toArray();
            Object[] introductionInterceptors = annotationUtils.getAnnotationMetadata(typeElement)
                    .getAnnotationNamesByStereotype(Introduction.class)
                    .toArray();

            Object[] interceptorTypes = ArrayUtils.concat(aroundInterceptors, introductionInterceptors);
            boolean isSingleton = annotationMetadata.hasDeclaredStereotype(Singleton.class);
            boolean isInterface = typeElement.getKind() == ElementKind.INTERFACE;
            AopProxyWriter aopProxyWriter = new AopProxyWriter(
                    packageElement.getQualifiedName().toString(),
                    beanClassName,
                    isInterface,
                    isSingleton,
                    annotationMetadata,
                    interceptorTypes);
            return aopProxyWriter;
        }

        private BeanDefinitionWriter createFactoryBeanMethodWriterFor(ExecutableElement method, TypeMirror producedType) {
            AnnotationMetadata annotationMetadata = annotationUtils.getAnnotationMetadata(method);
            Optional<String> scopeAnn =
                    annotationMetadata.getAnnotationNameByStereotype(Scope.class);

            Element element = typeUtils.asElement(producedType);
            TypeElement producedElement = modelUtils.classElementFor(element);

            PackageElement producedPackageElement = elementUtils.getPackageOf(producedElement);
            PackageElement definingPackageElement = elementUtils.getPackageOf(concreteClass);

            boolean isInterface = producedElement.getKind() == ElementKind.INTERFACE;
            String packageName = producedPackageElement.getQualifiedName().toString();
            String beanDefinitionPackage = definingPackageElement.getQualifiedName().toString();
            String shortClassName = modelUtils.simpleBinaryNameFor(producedElement);
            String upperCaseMethodName = NameUtils.capitalize(method.getSimpleName().toString());
            String factoryMethodBeanDefinitionName = beanDefinitionPackage + ".$" + concreteClass.getSimpleName().toString() + "$" + upperCaseMethodName + "Definition";
            BeanDefinitionWriter beanDefinitionWriter = new BeanDefinitionWriter(
                    packageName,
                    shortClassName,
                    factoryMethodBeanDefinitionName,
                    modelUtils.resolveTypeReference(producedElement).toString(),
                    isInterface,
                    annotationMetadata.hasDeclaredStereotype(Singleton.class),
                    annotationMetadata);
            return beanDefinitionWriter;
        }


        private ExecutableElementParamInfo populateParameterData(ExecutableElement element) {
            ExecutableElementParamInfo params = new ExecutableElementParamInfo();
            if (element == null) {
                return params;
            }
            element.getParameters().forEach(paramElement -> {

                String argName = paramElement.getSimpleName().toString();
                TypeMirror typeMirror = paramElement.asType();
                TypeKind kind = typeMirror.getKind();
                String qualifier = annotationUtils.resolveQualifier(paramElement);
                if (qualifier != null) {
                    params.addQualifierType(argName, qualifier);
                }

                switch (kind) {
                    case ARRAY:
                        ArrayType arrayType = (ArrayType) typeMirror;
                        TypeMirror componentType = arrayType.getComponentType();
                        params.addParameter(argName, modelUtils.resolveTypeReference(arrayType));
                        params.addGenericTypes(argName, Collections.singletonMap("E", modelUtils.resolveTypeReference(componentType)));

                        break;
                    case TYPEVAR:
                        TypeVariable typeVariable = (TypeVariable) typeMirror;

                        DeclaredType parameterType = genericUtils.resolveTypeVariable(paramElement, typeVariable);
                        if (parameterType != null) {

                            params.addParameter(argName, modelUtils.resolveTypeReference(parameterType));
                            params.addGenericTypes(argName, Collections.singletonMap(typeVariable.toString(), modelUtils.resolveTypeReference(parameterType)));
                        } else {
                            error(element, "Unprocessable generic type %s for param %s of element %s", typeVariable, paramElement, element);
                        }

                        break;
                    case DECLARED:
                        DeclaredType declaredType = (DeclaredType) typeMirror;


                        TypeElement typeElement = elementUtils.getTypeElement(typeUtils.erasure(declaredType).toString());
                        if (typeElement == null) {
                            typeElement = (TypeElement) declaredType.asElement();
                        }

                        params.addParameter(argName, modelUtils.resolveTypeReference(typeElement));

                        Map<String, Object> resolvedParameters = genericUtils.resolveGenericTypes(declaredType, typeElement);
                        if (!resolvedParameters.isEmpty()) {
                            params.addGenericTypes(argName, resolvedParameters);
                        }
                        break;
                    default:
                        if (kind.isPrimitive()) {
                            String typeName = typeMirror.toString();
                            Object argType = modelUtils.classOfPrimitiveFor(typeName);
                            params.addParameter(argName, argType);
                        } else {
                            error(element, "Unexpected kind %s for param %s of element %s", kind, typeMirror, element);
                        }
                }
            });

            return params;
        }

    }

    class DynamicName implements Name {
        private final CharSequence name;

        public DynamicName(CharSequence name) {
            this.name = name;
        }

        @Override
        public boolean contentEquals(CharSequence cs) {
            return name.equals(cs);
        }

        @Override
        public int length() {
            return name.length();
        }

        @Override
        public char charAt(int index) {
            return name.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return name.subSequence(start, end);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DynamicName that = (DynamicName) o;

            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }
}



