/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.annotation.processing;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.annotation.processing.visitor.*;
import io.micronaut.aop.Adapter;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.Introduction;
import io.micronaut.aop.writer.AopProxyWriter;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.configuration.ConfigurationMetadata;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.configuration.PropertyMetadata;
import io.micronaut.inject.processing.JavaModelUtils;
import io.micronaut.inject.processing.ProcessedTypes;
import io.micronaut.inject.writer.BeanDefinitionReferenceWriter;
import io.micronaut.inject.writer.BeanDefinitionVisitor;
import io.micronaut.inject.writer.BeanDefinitionWriter;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.inject.*;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.ElementScanner8;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static javax.lang.model.element.ElementKind.*;

/**
 * <p>The core annotation processor used to generate bean definitions and power AOP for Micronaut.</p>
 *
 * <p>Each dependency injection candidate is visited and {@link BeanDefinitionWriter} is used to produce byte code via ASM.
 * Each bean results in a instanceof {@link io.micronaut.inject.BeanDefinition}</p>
 *
 * @author Graeme Rocher
 * @author Dean Wette
 * @since 1.0
 */
@Internal
@SupportedOptions({AbstractInjectAnnotationProcessor.MICRONAUT_PROCESSING_INCREMENTAL, AbstractInjectAnnotationProcessor.MICRONAUT_PROCESSING_ANNOTATIONS})
public class BeanDefinitionInjectProcessor extends AbstractInjectAnnotationProcessor {

    private static final String AROUND_TYPE = "io.micronaut.aop.Around";
    private static final String INTRODUCTION_TYPE = "io.micronaut.aop.Introduction";
    private static final String[] ANNOTATION_STEREOTYPES = new String[]{
            ProcessedTypes.POST_CONSTRUCT,
            ProcessedTypes.PRE_DESTROY,
            "javax.inject.Inject",
            "javax.inject.Qualifier",
            "javax.inject.Singleton",
            "io.micronaut.context.annotation.Bean",
            "io.micronaut.context.annotation.Replaces",
            "io.micronaut.context.annotation.Value",
            "io.micronaut.context.annotation.Property",
            "io.micronaut.context.annotation.Executable",
            AROUND_TYPE,
            INTRODUCTION_TYPE
    };
    private static final String ANN_CONSTRAINT = "javax.validation.Constraint";
    private static final String ANN_VALID = "javax.validation.Valid";
    private static final Predicate<AnnotationMetadata> IS_CONSTRAINT = am ->
            am.hasStereotype(ANN_CONSTRAINT) || am.hasStereotype(ANN_VALID);
    private static final String ANN_CONFIGURATION_ADVICE = "io.micronaut.runtime.context.env.ConfigurationAdvice";
    private static final String ANN_VALIDATED = "io.micronaut.validation.Validated";

    private JavaConfigurationMetadataBuilder metadataBuilder;
    private Set<String> beanDefinitions;
    private Set<String> processed = new HashSet<>();
    private boolean processingOver;

    @Override
    public final synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.metadataBuilder = new JavaConfigurationMetadataBuilder(elementUtils, typeUtils, annotationUtils);
        ConfigurationMetadataBuilder.setConfigurationMetadataBuilder(metadataBuilder);
        this.beanDefinitions = new LinkedHashSet<>();
    }

    @Override
    public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        processingOver = roundEnv.processingOver();

        annotations = annotations
                .stream()
                .filter(ann -> !ann.getQualifiedName().toString().equals(AnnotationUtil.KOTLIN_METADATA))
                .filter(ann ->
                        annotationUtils.hasStereotype(ann, ANNOTATION_STEREOTYPES) || AbstractAnnotationMetadataBuilder.isAnnotationMapped(ann.getQualifiedName().toString()))
                .collect(Collectors.toSet());

        if (!annotations.isEmpty()) {
            TypeElement groovyObjectTypeElement = elementUtils.getTypeElement("groovy.lang.GroovyObject");
            TypeMirror groovyObjectType = groovyObjectTypeElement != null ? groovyObjectTypeElement.asType() : null;
            // accumulate all the class elements for all annotated elements
            annotations.forEach(annotation -> roundEnv.getElementsAnnotatedWith(annotation)
                    .stream()
                    // filtering annotation definitions, which are not processed
                    .filter(element -> element.getKind() != ANNOTATION_TYPE)
                    .forEach(element -> {
                        TypeElement typeElement = modelUtils.classElementFor(element);

                        if (element.getKind() == ENUM) {
                            error(element, "Enum types cannot be defined as beans");
                            return;
                        }
                        // skip Groovy code, handled by InjectTransform. Required for GroovyEclipse compiler
                        if (typeElement == null || (groovyObjectType != null && typeUtils.isAssignable(typeElement.asType(), groovyObjectType))) {
                            return;
                        }

                        String name = typeElement.getQualifiedName().toString();
                        if (!beanDefinitions.contains(name) && !processed.contains(name) && !name.endsWith(BeanDefinitionVisitor.PROXY_SUFFIX)) {
                            boolean isInterface = JavaModelUtils.resolveKind(typeElement, ElementKind.INTERFACE).isPresent();
                            if (!isInterface) {
                                beanDefinitions.add(name);
                            } else {
                                if (annotationUtils.hasStereotype(typeElement, INTRODUCTION_TYPE) || annotationUtils.hasStereotype(typeElement, ConfigurationReader.class)) {
                                    beanDefinitions.add(name);
                                }
                            }
                        }
                    }));
        }

        // remove already processed in previous round
        for (String name : processed) {
            beanDefinitions.remove(name);
        }

        // process remaining
        int count = beanDefinitions.size();
        if (count > 0) {
            note("Creating bean classes for %s type elements", count);
            beanDefinitions.forEach(className -> {
                if (processed.add(className)) {
                    final TypeElement refreshedClassElement = elementUtils.getTypeElement(className);
                    try {
                        final AnnBeanElementVisitor visitor = new AnnBeanElementVisitor(refreshedClassElement);
                        refreshedClassElement.accept(visitor, className);
                        visitor.getBeanDefinitionWriters().forEach((name, writer) -> {
                            String beanDefinitionName = writer.getBeanDefinitionName();
                            if (processed.add(beanDefinitionName)) {
                                processBeanDefinitions(refreshedClassElement, writer);
                            }
                        });
                        AnnotationUtils.invalidateCache();
                        AbstractAnnotationMetadataBuilder.clearMutated();

                    } catch (PostponeToNextRoundException e) {
                        processed.remove(className);
                    }
                }
            });
            AnnotationUtils.invalidateCache();
        }

        /*
        Since the underlying Filer expects us to write only once into a file we need to make sure it happens in the last
        processing round.
        */
        if (processingOver) {
            try {
                writeBeanDefinitionsToMetaInf();
            } finally {
                AnnotationUtils.invalidateCache();
                AbstractAnnotationMetadataBuilder.clearMutated();
            }
        }

        return false;
    }

    /**
     * Writes {@link io.micronaut.inject.BeanDefinitionReference} into /META-INF/services/io.micronaut.inject.BeanDefinitionReference.
     */
    private void writeBeanDefinitionsToMetaInf() {
        try {
            classWriterOutputVisitor.finish();
        } catch (Exception e) {
            String message = e.getMessage();
            error("Error occurred writing META-INF files: %s", message != null ? message : e);
        }
    }

    private void processBeanDefinitions(TypeElement beanClassElement, BeanDefinitionVisitor beanDefinitionWriter) {
        try {
            beanDefinitionWriter.visitBeanDefinitionEnd();
            beanDefinitionWriter.accept(classWriterOutputVisitor);

            String beanTypeName = beanDefinitionWriter.getBeanTypeName();
            List<? extends TypeMirror> interfaces = beanClassElement.getInterfaces();
            for (TypeMirror anInterface : interfaces) {

                if (anInterface instanceof DeclaredType) {
                    DeclaredType declaredType = (DeclaredType) anInterface;
                    Element element = declaredType.asElement();
                    if (element instanceof TypeElement) {
                        TypeElement te = (TypeElement) element;
                        String name = te.getQualifiedName().toString();
                        if (Provider.class.getName().equals(name)) {
                            List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
                            if (!typeArguments.isEmpty()) {
                                beanTypeName = genericUtils.resolveTypeReference(typeArguments.get(0)).toString();
                            }
                        }
                    }
                }
            }

            BeanDefinitionReferenceWriter beanDefinitionReferenceWriter =
                    new BeanDefinitionReferenceWriter(beanTypeName, beanDefinitionWriter);
            beanDefinitionReferenceWriter.setRequiresMethodProcessing(beanDefinitionWriter.requiresMethodProcessing());

            String className = beanDefinitionReferenceWriter.getBeanDefinitionQualifiedClassName();
            processed.add(className);
            beanDefinitionReferenceWriter.setContextScope(
                    annotationUtils.hasStereotype(beanClassElement, Context.class));

            beanDefinitionReferenceWriter.accept(classWriterOutputVisitor);
        } catch (IOException e) {
            // raise a compile error
            String message = e.getMessage();
            error("Unexpected error: %s", message != null ? message : e.getClass().getSimpleName());
        }
    }

    private String getPropertyMetadataTypeReference(TypeMirror valueType) {
        if (modelUtils.isOptional(valueType)) {
            return genericUtils.getFirstTypeArgument(valueType)
                    .map(typeMirror -> modelUtils.resolveTypeName(typeMirror))
                    .orElseGet(() -> modelUtils.resolveTypeName(valueType));
        } else {
            return modelUtils.resolveTypeName(valueType);
        }
    }

    private AnnotationMetadata addPropertyMetadata(io.micronaut.inject.ast.Element astElement, VariableElement element, String propertyName) {
        final PropertyMetadata pm = metadataBuilder.visitProperty(
                getPropertyMetadataTypeReference(element.asType()),
                propertyName, null, null
        );
        return addPropertyMetadata(astElement, pm);
    }

    private AnnotationMetadata addPropertyMetadata(io.micronaut.inject.ast.Element targetElement, PropertyMetadata propertyMetadata) {
        return targetElement.annotate(Property.class, (builder) -> builder.member("name", propertyMetadata.getPath())).getAnnotationMetadata();
    }

    /**
     * Annotation Bean element visitor.
     */
    class AnnBeanElementVisitor extends ElementScanner8<Object, Object> {
        private final TypeElement concreteClass;
        private final AnnotationMetadata concreteClassMetadata;
        private final Map<Name, BeanDefinitionVisitor> beanDefinitionWriters;
        private final boolean isConfigurationPropertiesType;
        private final boolean isFactoryType;
        private final boolean isExecutableType;
        private final boolean isAopProxyType;
        private final OptionalValues<Boolean> aopSettings;
        private final boolean isDeclaredBean;
        private final JavaVisitorContext visitorContext;
        private final JavaClassElement concreteClassElement;
        private ConfigurationMetadata configurationMetadata;
        private final ExecutableElementParamInfo constructorParameterInfo;
        private final AtomicInteger adaptedMethodIndex = new AtomicInteger(0);
        private final AtomicInteger factoryMethodIndex = new AtomicInteger(0);
        private AnnotationMetadata currentClassMetadata;
        private final Set<Name> visitedTypes = new HashSet<>();

        /**
         * @param concreteClass The {@link TypeElement}
         */
        AnnBeanElementVisitor(TypeElement concreteClass) {
            this.concreteClass = concreteClass;
            this.concreteClassMetadata = annotationUtils.getAnnotationMetadata(concreteClass);
            this.currentClassMetadata = concreteClassMetadata;
            visitorContext = new JavaVisitorContext(
                    processingEnv,
                    messager,
                    elementUtils,
                    annotationUtils,
                    typeUtils,
                    modelUtils,
                    genericUtils,
                    filer,
                    visitorAttributes
            );
            this.concreteClassElement = new JavaClassElement(concreteClass, concreteClassMetadata, visitorContext);
            beanDefinitionWriters = new LinkedHashMap<>();
            this.isFactoryType = concreteClassMetadata.hasStereotype(Factory.class);
            this.isConfigurationPropertiesType = concreteClassMetadata.hasDeclaredStereotype(ConfigurationReader.class) || concreteClassMetadata.hasDeclaredStereotype(EachProperty.class);
            this.isAopProxyType = concreteClassMetadata.hasStereotype(AROUND_TYPE) && !modelUtils.isAbstract(concreteClass);
            this.aopSettings = isAopProxyType ? concreteClassMetadata.getValues(AROUND_TYPE, Boolean.class) : OptionalValues.empty();
            ExecutableElement constructor = modelUtils.concreteConstructorFor(concreteClass, annotationUtils);
            this.constructorParameterInfo = populateParameterData(
                    concreteClassElement.getName(),
                    constructor,
                    null);
            this.isExecutableType = isAopProxyType || concreteClassMetadata.hasStereotype(Executable.class);
            boolean hasQualifier = concreteClassMetadata.hasStereotype(Qualifier.class) && !modelUtils.isAbstract(concreteClass);
            this.isDeclaredBean = isExecutableType || concreteClassMetadata.hasStereotype(Scope.class) || concreteClassMetadata.hasStereotype(DefaultScope.class) || constructorParameterInfo.getAnnotationMetadata().hasStereotype(Inject.class) || hasQualifier;
        }

        /**
         * @return The bean definition writers
         */
        Map<Name, BeanDefinitionVisitor> getBeanDefinitionWriters() {
            return beanDefinitionWriters;
        }

        @Override
        public Object visitType(TypeElement classElement, Object o) {
            Name classElementQualifiedName = classElement.getQualifiedName();
            if ("java.lang.Record".equals(classElementQualifiedName.toString())) {
                return o;
            }
            if (visitedTypes.contains(classElementQualifiedName)) {
                // bail out if already visited
                return o;
            }
            boolean isInterface = concreteClassElement.isInterface();
            visitedTypes.add(classElementQualifiedName);
            AnnotationMetadata typeAnnotationMetadata = annotationUtils.getAnnotationMetadata(classElement);
            this.currentClassMetadata = typeAnnotationMetadata;

            if (isConfigurationPropertiesType) {

                // TODO: populate documentation
                this.configurationMetadata = metadataBuilder.visitProperties(
                        concreteClass,
                        null
                );
                if (isInterface) {
                    typeAnnotationMetadata = addAnnotation(
                            typeAnnotationMetadata,
                            ANN_CONFIGURATION_ADVICE
                    );
                }
            }

            if (typeAnnotationMetadata.hasStereotype(INTRODUCTION_TYPE)) {
                AopProxyWriter aopProxyWriter = createIntroductionAdviceWriter(classElement);
                ExecutableElement constructor = JavaModelUtils.resolveKind(classElement, ElementKind.CLASS).isPresent() ? modelUtils.concreteConstructorFor(classElement, annotationUtils) : null;
                ExecutableElementParamInfo constructorData = constructor != null ? populateParameterData(concreteClassElement.getName(), constructor, null) : null;

                if (constructorData != null) {
                    aopProxyWriter.visitBeanDefinitionConstructor(
                            constructorData.getAnnotationMetadata(),
                            constructorData.isRequiresReflection(),
                            constructorData.getParameters(),
                            constructorData.getParameterMetadata(),
                            constructorData.getGenericParameterTypes()
                    );
                } else {
                    aopProxyWriter.visitBeanDefinitionConstructor(
                            AnnotationMetadata.EMPTY_METADATA,
                            false
                    );
                }
                beanDefinitionWriters.put(classElementQualifiedName, aopProxyWriter);
                visitIntroductionAdviceInterface(classElement, typeAnnotationMetadata, aopProxyWriter);

                if (!isInterface) {

                    List<? extends Element> elements = classElement.getEnclosedElements().stream()
                            // already handled the public ctor
                            .filter(element -> element.getKind() != CONSTRUCTOR)
                            .collect(Collectors.toList());
                    return scan(elements, o);
                } else {
                    return null;
                }

            } else {
                Element enclosingElement = classElement.getEnclosingElement();
                // don't process inner class unless this is the visitor for it
                final Name qualifiedName = concreteClass.getQualifiedName();
                if (!JavaModelUtils.isClass(enclosingElement) ||
                        qualifiedName.equals(classElementQualifiedName)) {

                    if (qualifiedName.equals(classElementQualifiedName)) {
                        if (isDeclaredBean) {
                            // we know this class has supported annotations so we need a beandef writer for it
                            PackageElement packageElement = elementUtils.getPackageOf(classElement);
                            if (packageElement.isUnnamed()) {
                                error(classElement, "Micronaut beans cannot be in the default package");
                                return null;
                            }
                            BeanDefinitionVisitor beanDefinitionWriter = getOrCreateBeanDefinitionWriter(classElement, qualifiedName);

                            if (isAopProxyType) {

                                if (modelUtils.isFinal(classElement)) {
                                    error(classElement, "Cannot apply AOP advice to final class. Class must be made non-final to support proxying: " + classElement);
                                    return null;
                                }
                                Object[] interceptorTypes = annotationUtils.getAnnotationMetadata(concreteClass)
                                        .getAnnotationNamesByStereotype(AROUND_TYPE)
                                        .toArray();
                                resolveAopProxyWriter(
                                        beanDefinitionWriter,
                                        aopSettings,
                                        false,
                                        this.constructorParameterInfo,
                                        interceptorTypes);
                            }
                        } else {
                            if (modelUtils.isAbstract(classElement)) {
                                return null;
                            }
                        }
                    }

                    List<? extends Element> elements = classElement
                            .getEnclosedElements()
                            .stream()
                            // already handled the public ctor
                            .filter(element -> element.getKind() != CONSTRUCTOR)
                            .collect(Collectors.toList());

                    if (isConfigurationPropertiesType) {
                        // handle non @Inject, @Value fields as config properties
                        List<? extends Element> members = elementUtils.getAllMembers(classElement);
                        ElementFilter.fieldsIn(members).forEach(
                                field -> {
                                    if (classElement != field.getEnclosingElement()) {

                                        AnnotationMetadata fieldAnnotationMetadata = annotationUtils.getAnnotationMetadata(field);
                                        boolean isConfigBuilder = fieldAnnotationMetadata.hasStereotype(ConfigurationBuilder.class);
                                        if (modelUtils.isStatic(field)) {
                                            return;
                                        }
                                        // its common for builders to be initialized, so allow final
                                        if (!modelUtils.isFinal(field) || isConfigBuilder) {
                                            visitConfigurationProperty(field, fieldAnnotationMetadata);
                                        }
                                    }
                                }
                        );
                        ElementFilter.methodsIn(members).forEach(method -> {
                            boolean isCandidateMethod = !modelUtils.isStatic(method) &&
                                    !modelUtils.isPrivate(method) &&
                                    !modelUtils.isAbstract(method);
                            if (isCandidateMethod) {
                                Element e = method.getEnclosingElement();
                                if (e instanceof TypeElement && !e.equals(classElement)) {
                                    String methodName = method.getSimpleName().toString();
                                    if (method.getParameters().size() == 1 &&
                                            NameUtils.isSetterName(methodName)) {
                                        visitConfigurationPropertySetter(method);
                                    } else if (NameUtils.isGetterName(methodName)) {
                                        BeanDefinitionVisitor writer = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName());
                                        if (!writer.isValidated()) {
                                            writer.setValidated(IS_CONSTRAINT.test(annotationUtils.getAnnotationMetadata(method)));
                                        }
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

        /**
         * Gets or creates a bean definition writer.
         *
         * @param classElement  The class element
         * @param qualifiedName The name
         * @return The writer
         */
        public BeanDefinitionVisitor getOrCreateBeanDefinitionWriter(TypeElement classElement, Name qualifiedName) {
            BeanDefinitionVisitor beanDefinitionWriter = beanDefinitionWriters.get(qualifiedName);
            if (beanDefinitionWriter == null) {

                beanDefinitionWriter = createBeanDefinitionWriterFor(classElement);
                Name proxyKey = createProxyKey(beanDefinitionWriter.getBeanDefinitionName());
                beanDefinitionWriters.put(qualifiedName, beanDefinitionWriter);


                BeanDefinitionVisitor proxyWriter = beanDefinitionWriters.get(proxyKey);
                final AnnotationMetadata annotationMetadata = new AnnotationMetadataHierarchy(
                        concreteClassMetadata,
                        constructorParameterInfo.getAnnotationMetadata()
                );
                if (proxyWriter != null) {
                    proxyWriter.visitBeanDefinitionConstructor(
                            annotationMetadata,
                            constructorParameterInfo.isRequiresReflection(),
                            constructorParameterInfo.getParameters(),
                            constructorParameterInfo.getParameterMetadata(),
                            constructorParameterInfo.getGenericParameterTypes());
                }

                beanDefinitionWriter.visitBeanDefinitionConstructor(
                        annotationMetadata,
                        constructorParameterInfo.isRequiresReflection(),
                        constructorParameterInfo.getParameters(),
                        constructorParameterInfo.getParameterMetadata(),
                        constructorParameterInfo.getGenericParameterTypes());

                if (constructorParameterInfo.isValidated()) {
                    beanDefinitionWriter.setValidated(true);
                }
            }
            return beanDefinitionWriter;
        }

        private AnnotationMetadata addAnnotation(AnnotationMetadata annotationMetadata, String annotation) {
            final JavaAnnotationMetadataBuilder metadataBuilder = javaVisitorContext.getAnnotationUtils().newAnnotationBuilder();
            annotationMetadata = metadataBuilder.annotate(
                    annotationMetadata,
                    io.micronaut.core.annotation.AnnotationValue.builder(annotation).build());
            return annotationMetadata;
        }

        private void visitIntroductionAdviceInterface(TypeElement classElement, AnnotationMetadata typeAnnotationMetadata, AopProxyWriter aopProxyWriter) {
            JavaClassElement introductionType = new JavaClassElement(classElement, typeAnnotationMetadata, javaVisitorContext);
            final boolean isConfigProps = typeAnnotationMetadata.hasAnnotation(ANN_CONFIGURATION_ADVICE);
            if (isConfigProps) {
                metadataBuilder.visitProperties(
                        classElement,
                        null
                );
            }
            classElement.asType().accept(new PublicAbstractMethodVisitor<Object, AopProxyWriter>(classElement, javaVisitorContext) {

                @Override
                protected boolean isAcceptableMethod(ExecutableElement executableElement) {
                    return super.isAcceptableMethod(executableElement)
                            || annotationUtils.getAnnotationMetadata(executableElement).hasDeclaredStereotype(AROUND_TYPE)
                            || annotationUtils.getAnnotationMetadata(classElement).hasDeclaredStereotype(AROUND_TYPE);
                }

                @Override
                protected void accept(DeclaredType type, Element element, AopProxyWriter aopProxyWriter) {
                    ExecutableElement method = (ExecutableElement) element;
                    Element declaredTypeElement = type.asElement();
                    if (declaredTypeElement instanceof TypeElement) {
                        JavaClassElement declaringClassElement = new JavaClassElement(
                                (TypeElement) declaredTypeElement,
                                concreteClassMetadata,
                                visitorContext
                        );
                        if (!classElement.equals(declaredTypeElement)) {
                            aopProxyWriter.addOriginatingElement(
                                    declaringClassElement
                            );
                        }
                        final boolean isAbstract = modelUtils.isAbstract(method);

                        ExecutableElementParamInfo params = populateParameterData(introductionType.getName(), method, null);
                        TypeElement owningType = modelUtils.classElementFor(method);
                        if (owningType == null) {
                            throw new IllegalStateException("Owning type cannot be null");
                        }
                        JavaClassElement owningTypeElement = new JavaClassElement(owningType, typeAnnotationMetadata, visitorContext);

                        String methodName = method.getSimpleName().toString();
                        Map<String, ParameterElement> methodParameters = params.getParameters();
                        Map<String, ClassElement> genericParameters = params.getGenericParameterTypes();
                        Map<String, AnnotationMetadata> parameterAnnotationMetadata = params.getParameterMetadata();
                        AnnotationMetadata annotationMetadata;

                        if (annotationUtils.isAnnotated(introductionType.getName(), method) || JavaAnnotationMetadataBuilder.hasAnnotation(method, Override.class)) {
                            annotationMetadata = annotationUtils.newAnnotationBuilder().buildForParent(introductionType.getName(), classElement, method);
                        } else {
                            annotationMetadata = new AnnotationMetadataReference(
                                    aopProxyWriter.getBeanDefinitionName() + BeanDefinitionReferenceWriter.REF_SUFFIX,
                                    typeAnnotationMetadata
                            );
                        }

                        if (!annotationMetadata.hasStereotype(ANN_VALIDATED) &&
                                isDeclaredBean &&
                                params.getParameterMetadata().values().stream().anyMatch(IS_CONSTRAINT)) {
                            if (annotationMetadata instanceof AnnotationMetadataReference) {
                                annotationMetadata = annotationUtils.newAnnotationBuilder().buildForParent(introductionType.getName(), classElement, method);
                            }
                            annotationMetadata = addAnnotation(annotationMetadata, ANN_VALIDATED);
                        }

                        JavaMethodElement javaMethodElement = new JavaMethodElement(
                                introductionType,
                                method,
                                annotationMetadata,
                                visitorContext
                        );

                        if (isConfigProps) {
                            if (isAbstract) {

                                if (!aopProxyWriter.isValidated()) {
                                    aopProxyWriter.setValidated(IS_CONSTRAINT.test(annotationMetadata));
                                }

                                if (!NameUtils.isGetterName(methodName)) {
                                    error(classElement, "Only getter methods are allowed on @ConfigurationProperties interfaces: " + method);
                                    return;
                                }

                                if (!methodParameters.isEmpty()) {
                                    error(classElement, "Only zero argument getter methods are allowed on @ConfigurationProperties interfaces: " + method);
                                    return;
                                }

                                String docComment = elementUtils.getDocComment(method);
                                final String propertyName = NameUtils.getPropertyNameForGetter(methodName);
                                final String propertyType = javaMethodElement.getReturnType().getName();

                                if ("void".equals(propertyType)) {
                                    error(classElement, "Getter methods must return a value @ConfigurationProperties interfaces: " + method);
                                    return;
                                }
                                final PropertyMetadata propertyMetadata = metadataBuilder.visitProperty(
                                        classElement,
                                        classElement,
                                        propertyType,
                                        propertyName,
                                        docComment,
                                        annotationMetadata.stringValue(Bindable.class, "defaultValue").orElse(null)
                                );
                                addPropertyMetadata(
                                        javaMethodElement,
                                        propertyMetadata
                                );

                                annotationMetadata = javaMethodElement.annotate(ANN_CONFIGURATION_ADVICE, (annBuilder) -> {
                                    if (!javaMethodElement.getReturnType().isPrimitive() && javaMethodElement.getReturnType().hasStereotype(Scope.class)) {
                                        annBuilder.member("bean", true);
                                    }
                                    if (typeAnnotationMetadata.hasStereotype(EachProperty.class)) {
                                        annBuilder.member("iterable", true);
                                    }

                                }).getAnnotationMetadata();
                            }
                        }

                        if (annotationMetadata.hasStereotype(AROUND_TYPE)) {
                            Object[] interceptorTypes = annotationMetadata
                                    .getAnnotationNamesByStereotype(AROUND_TYPE)
                                    .toArray();

                            aopProxyWriter.visitInterceptorTypes(interceptorTypes);
                        }

                        if (isAbstract) {
                            aopProxyWriter.visitIntroductionMethod(
                                    owningTypeElement,
                                    javaMethodElement,
                                    methodParameters,
                                    parameterAnnotationMetadata,
                                    genericParameters,
                                    annotationMetadata
                            );
                        } else {
                            boolean isInterface = declaringClassElement.isInterface();
                            boolean isDefault = method.isDefault();
                            if (isInterface && isDefault) {
                                // Default methods cannot be "super" accessed on the defined type
                                owningTypeElement = introductionType;
                            }

                            // only apply around advise to non-abstract methods of introduction advise
                            aopProxyWriter.visitAroundMethod(
                                    owningTypeElement,
                                    javaMethodElement,
                                    methodParameters,
                                    genericParameters,
                                    parameterAnnotationMetadata,
                                    annotationMetadata,
                                    isInterface,
                                    isDefault
                            );
                        }
                    }


                }
            }, aopProxyWriter);
        }

        @Override
        public Object visitExecutable(ExecutableElement method, Object o) {
            if (method.getKind() == ElementKind.CONSTRUCTOR) {
                // ctor is handled by visitType
                error("Unexpected call to visitExecutable for ctor %s of %s",
                        method.getSimpleName(), o);
                return null;
            }

            if (modelUtils.isStatic(method) || modelUtils.isAbstract(method)) {
                return null;
            }


            final AnnotationMetadata annotationMetadata = annotationUtils.getAnnotationMetadata(method);

            AnnotationMetadata methodAnnotationMetadata;

            if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
                methodAnnotationMetadata = annotationMetadata;
            } else {

                methodAnnotationMetadata = new AnnotationMetadataHierarchy(
                        concreteClassMetadata,
                        annotationMetadata
                );
            }

            TypeKind returnKind = method.getReturnType().getKind();
            if ((returnKind == TypeKind.ERROR) && !processingOver) {
                throw new PostponeToNextRoundException();
            }

            // handle @Bean annotation for @Factory class
            if (isFactoryType && methodAnnotationMetadata.hasDeclaredStereotype(Bean.class, Scope.class) && returnKind == TypeKind.DECLARED) {
                visitBeanFactoryMethod(method);
                return null;
            }


            boolean injected = methodAnnotationMetadata.hasDeclaredStereotype(Inject.class);
            boolean postConstruct = methodAnnotationMetadata.hasDeclaredStereotype(ProcessedTypes.POST_CONSTRUCT);
            boolean preDestroy = methodAnnotationMetadata.hasDeclaredStereotype(ProcessedTypes.PRE_DESTROY);
            if (injected || postConstruct || preDestroy || methodAnnotationMetadata.hasDeclaredStereotype(ConfigurationInject.class)) {
                if (isDeclaredBean) {
                    visitAnnotatedMethod(method, o);
                } else if (injected) {
                    // DEPRECATE: This behaviour should be deprecated in 2.0
                    visitAnnotatedMethod(method, o);
                }
                return null;
            }


            Set<Modifier> modifiers = method.getModifiers();
            boolean hasInvalidModifiers = modelUtils.isAbstract(method) || modifiers.contains(Modifier.STATIC) || methodAnnotationMetadata.hasAnnotation(Internal.class) || modelUtils.isPrivate(method);
            boolean isPublic = modifiers.contains(Modifier.PUBLIC) && !hasInvalidModifiers;
            boolean isExecutable =
                    !hasInvalidModifiers &&
                            (isExecutableThroughType(method.getEnclosingElement(), methodAnnotationMetadata, annotationMetadata, modifiers, isPublic) ||
                                    annotationMetadata.hasStereotype(AROUND_TYPE));

            boolean hasConstraints = false;
            if (isDeclaredBean && !methodAnnotationMetadata.hasStereotype(ANN_VALIDATED) &&
                    method.getParameters()
                            .stream()
                            .anyMatch(p -> annotationUtils.hasStereotype(p, ANN_CONSTRAINT) || annotationUtils.hasStereotype(p, ANN_VALID))) {
                hasConstraints = true;
                methodAnnotationMetadata = javaVisitorContext.getAnnotationUtils().newAnnotationBuilder().annotate(
                        methodAnnotationMetadata,
                        io.micronaut.core.annotation.AnnotationValue.builder(ANN_VALIDATED).build()
                );
            }

            if (isDeclaredBean && isExecutable) {
                visitExecutableMethod(method, methodAnnotationMetadata);
            } else if (isConfigurationPropertiesType && !modelUtils.isPrivate(method) && !modelUtils.isStatic(method)) {
                String methodName = method.getSimpleName().toString();
                if (NameUtils.isSetterName(methodName) && method.getParameters().size() == 1) {
                    visitConfigurationPropertySetter(method);
                } else if (NameUtils.isGetterName(methodName)) {
                    BeanDefinitionVisitor writer = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName());
                    if (!writer.isValidated()) {
                        writer.setValidated(IS_CONSTRAINT.test(methodAnnotationMetadata));
                    }
                }
            } else if (isPublic && hasConstraints) {
                visitExecutableMethod(method, methodAnnotationMetadata);
            }

            return null;
        }

        private boolean isExecutableThroughType(
                Element enclosingElement,
                AnnotationMetadata annotationMetadataHierarchy,
                AnnotationMetadata declaredMetadata, Set<Modifier> modifiers,
                boolean isPublic) {
            return (isExecutableType && (isPublic || (modifiers.isEmpty()) && concreteClass.equals(enclosingElement))) ||
                    annotationMetadataHierarchy.hasDeclaredStereotype(Executable.class) ||
                    declaredMetadata.hasStereotype(Executable.class);
        }

        private void visitConfigurationPropertySetter(ExecutableElement method) {
            BeanDefinitionVisitor writer = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName());
            VariableElement parameter = method.getParameters().get(0);

            TypeElement declaringClass = modelUtils.classElementFor(method);

            if (declaringClass != null) {
                AnnotationMetadata methodAnnotationMetadata = annotationUtils.getAnnotationMetadata(method);
                JavaClassElement javaClassElement = new JavaClassElement(declaringClass, methodAnnotationMetadata, visitorContext);
                JavaMethodElement javaMethodElement = new JavaMethodElement(javaClassElement, method, methodAnnotationMetadata, visitorContext);
                ParameterElement parameterElement = javaMethodElement.getParameters()[0];

                String propertyName = NameUtils.getPropertyNameForSetter(method.getSimpleName().toString());
                boolean isInterface = parameterElement.getType().isInterface();

                if (methodAnnotationMetadata.hasStereotype(ConfigurationBuilder.class)) {
                    writer.visitConfigBuilderMethod(
                            parameterElement.getType(),
                            NameUtils.getterNameFor(propertyName),
                            methodAnnotationMetadata,
                            metadataBuilder,
                            isInterface
                    );
                    try {
                        visitConfigurationBuilder(declaringClass, method, parameter.asType(), writer);
                    } finally {
                        writer.visitConfigBuilderEnd();
                    }
                } else {
                    if (shouldExclude(configurationMetadata, propertyName)) {
                        return;
                    }
                    String docComment = elementUtils.getDocComment(method);
                    String setterName = javaMethodElement.getName();
                    PropertyMetadata propertyMetadata = metadataBuilder.visitProperty(
                            concreteClass,
                            declaringClass,
                            parameterElement.getGenericType().getCanonicalName(),
                            propertyName,
                            docComment,
                            null
                    );

                    AnnotationMetadata annotationMetadata = addPropertyMetadata(javaMethodElement, propertyMetadata);

                    boolean requiresReflection = true;
                    if (javaMethodElement.isPublic()) {
                        requiresReflection = false;
                    } else if (modelUtils.isPackagePrivate(method) || javaMethodElement.isProtected()) {
                        String declaringPackage = javaClassElement.getPackageName();
                        String concretePackage = concreteClassElement.getPackageName();
                        requiresReflection = !declaringPackage.equals(concretePackage);
                    }

                    writer.visitSetterValue(
                            javaClassElement,
                            javaMethodElement.getReturnType(),
                            annotationMetadata,
                            requiresReflection,
                            parameterElement,
                            setterName,
                            parameterElement.getGenericType().getTypeArguments(),
                            parameterElement.getAnnotationMetadata(),
                            true);
                }
            }

        }

        /**
         * @param beanMethod The {@link ExecutableElement}
         */
        void visitBeanFactoryMethod(ExecutableElement beanMethod) {
            if (isFactoryType && annotationUtils.hasStereotype(concreteClass, AROUND_TYPE)) {
                visitExecutableMethod(beanMethod, annotationUtils.getAnnotationMetadata(beanMethod));
            }
            TypeElement producedElement = modelUtils.classElementFor(typeUtils.asElement(beanMethod.getReturnType()));
            TypeElement factoryElement = modelUtils.classElementFor(beanMethod);

            if (producedElement == null || factoryElement == null) {
                return;
            }
            String producedTypeName = producedElement.getQualifiedName().toString();
            ExecutableElementParamInfo beanMethodParams = populateParameterData(producedTypeName, beanMethod, null);

            TypeMirror returnType = beanMethod.getReturnType();
            JavaClassElement declaringClassElement = new JavaClassElement(
                    factoryElement,
                    concreteClassMetadata,
                    visitorContext
            );
            JavaMethodElement javaMethodElement = new JavaMethodElement(
                    declaringClassElement,
                    beanMethod,
                    beanMethodParams.getAnnotationMetadata(),
                    visitorContext
            );

            BeanDefinitionWriter beanMethodWriter = createFactoryBeanMethodWriterFor(beanMethod, producedElement);
            Map<String, Map<String, Object>> beanTypeArguments = null;
            Map<String, Map<String, TypeMirror>> beanTypeArgumentsMirrors = null;
            if (returnType instanceof DeclaredType) {
                DeclaredType dt = (DeclaredType) returnType;
                beanTypeArguments = genericUtils.buildGenericTypeArgumentInfo(dt);
                beanTypeArgumentsMirrors = genericUtils.buildGenericTypeArgumentElementInfo(dt.asElement(), dt);
                Map<String, Map<String, Object>> finalizedArguments = resolveFinalTypeArguments(beanTypeArguments);
                beanMethodWriter.visitTypeArguments(finalizedArguments);
            }

            final String beanMethodName = beanMethod.getSimpleName().toString();
            final Map<String, ParameterElement> beanMethodParameters = beanMethodParams.getParameters();

            StringBuilder methodKey = new StringBuilder(beanMethodName)
                    .append("(")
                    .append(beanMethodParameters.values().stream()
                            .map(Object::toString)
                            .collect(Collectors.joining(",")))
                    .append(")");

            beanDefinitionWriters.put(new DynamicName(methodKey), beanMethodWriter);

            AnnotationMetadata methodAnnotationMetadata = annotationUtils.newAnnotationBuilder().buildForParent(
                    producedElement,
                    beanMethod
            );
            beanMethodWriter.visitBeanFactoryMethod(
                    declaringClassElement,
                    concreteClassElement,
                    javaMethodElement.getReturnType(),
                    beanMethodName,
                    methodAnnotationMetadata,
                    beanMethodParameters,
                    beanMethodParams.getParameterMetadata(),
                    beanMethodParams.getGenericParameterTypes()
            );

            if (methodAnnotationMetadata.hasStereotype(AROUND_TYPE) && !modelUtils.isAbstract(concreteClass)) {
                Object[] interceptorTypes = methodAnnotationMetadata
                        .getAnnotationNamesByStereotype(AROUND_TYPE)
                        .toArray();
                TypeElement returnTypeElement = (TypeElement) ((DeclaredType) beanMethod.getReturnType()).asElement();

                if (modelUtils.isFinal(returnTypeElement)) {
                    error(returnTypeElement, "Cannot apply AOP advice to final class. Class must be made non-final to support proxying: " + returnTypeElement);
                    return;
                }

                ExecutableElement constructor = JavaModelUtils.isClass(returnTypeElement) ? modelUtils.concreteConstructorFor(returnTypeElement, annotationUtils) : null;
                ExecutableElementParamInfo constructorData = constructor != null ? populateParameterData(null, constructor, null) : null;

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
                proxyWriter.visitTypeArguments(beanTypeArguments);

                returnType.accept(new PublicMethodVisitor<Object, AopProxyWriter>(javaVisitorContext) {
                    @Override
                    protected void accept(DeclaredType type, Element element, AopProxyWriter aopProxyWriter) {
                        ExecutableElement method = (ExecutableElement) element;
                        TypeElement owningType = modelUtils.classElementFor(method);
                        JavaClassElement declaringClassElement = new JavaClassElement(
                                owningType,
                                concreteClassMetadata,
                                visitorContext
                        );
                        ExecutableElementParamInfo params = populateParameterData(null, method, null);

                        Map<String, ParameterElement> methodParameters = params.getParameters();
                        Map<String, AnnotationMetadata> methodQualifier = params.getParameterMetadata();
                        Map<String, ClassElement> genericParameters = params.getGenericParameterTypes();

                        AnnotationMetadata annotationMetadata;
                        // if the method is annotated we build metadata for the method
                        if (annotationUtils.isAnnotated(producedTypeName, method)) {
                            annotationMetadata = annotationUtils.getAnnotationMetadata(beanMethod, method);
                        } else {
                            // otherwise we setup a reference to the parent metadata (essentially the annotations declared on the bean factory method)
                            annotationMetadata = new AnnotationMetadataReference(
                                    beanMethodWriter.getBeanDefinitionName() + BeanDefinitionReferenceWriter.REF_SUFFIX,
                                    methodAnnotationMetadata
                            );
                        }

                        JavaMethodElement advisedMethodElement = new JavaMethodElement(
                                declaringClassElement,
                                method,
                                annotationMetadata,
                                visitorContext
                        );

                        aopProxyWriter.visitAroundMethod(
                                declaringClassElement,
                                advisedMethodElement,
                                methodParameters,
                                genericParameters,
                                methodQualifier,
                                annotationMetadata,
                                declaringClassElement.isInterface(),
                                method.isDefault()
                        );
                    }
                }, proxyWriter);
            } else if (methodAnnotationMetadata.hasStereotype(Executable.class)) {

                Map<String, Map<String, TypeMirror>> finalBeanTypeArgumentsMirrors = beanTypeArgumentsMirrors;
                returnType.accept(new PublicMethodVisitor<Object, BeanDefinitionWriter>(javaVisitorContext) {
                    @Override
                    protected void accept(DeclaredType type, Element element, BeanDefinitionWriter beanWriter) {
                        ExecutableElement method = (ExecutableElement) element;
                        TypeElement owningType = modelUtils.classElementFor(method);
                        if (owningType == null) {
                            throw new IllegalStateException("Owning type cannot be null");
                        }
                        String methodName = method.getSimpleName().toString();

                        AnnotationMetadata annotationMetadata = new AnnotationMetadataReference(
                                beanMethodWriter.getBeanDefinitionName() + BeanDefinitionReferenceWriter.REF_SUFFIX,
                                methodAnnotationMetadata
                        );

                        ExecutableElementParamInfo params = populateParameterData(producedTypeName, method, finalBeanTypeArgumentsMirrors);
                        Map<String, ParameterElement> methodParameters = params.getParameters();
                        Map<String, ClassElement> genericParameters = params.getGenericParameterTypes();
                        Map<String, AnnotationMetadata> methodQualifier = params.getParameterMetadata();

                        JavaClassElement declaringClassElement = new JavaClassElement(producedElement, concreteClassMetadata, visitorContext);
                        JavaMethodElement executableMethod = new JavaMethodElement(
                                declaringClassElement,
                                method,
                                annotationMetadata,
                                visitorContext
                        ) {
                            @Override
                            @NonNull
                            public ClassElement getGenericReturnType() {
                                if (finalBeanTypeArgumentsMirrors != null) {
                                    return super.returnType(finalBeanTypeArgumentsMirrors);
                                } else {
                                    return super.getGenericReturnType();
                                }
                            }
                        };

                        beanMethodWriter.visitExecutableMethod(
                                declaringClassElement,
                                executableMethod,
                                methodParameters,
                                genericParameters,
                                methodQualifier,
                                annotationMetadata,
                                declaringClassElement.isInterface(),
                                method.isDefault()
                        );

                    }
                }, beanMethodWriter);
            }

            if (methodAnnotationMetadata.isPresent(Bean.class, "preDestroy")) {
                Optional<String> preDestroyMethod = methodAnnotationMetadata.getValue(Bean.class, "preDestroy", String.class);
                preDestroyMethod
                        .ifPresent(destroyMethodName -> {
                            if (StringUtils.isNotEmpty(destroyMethodName)) {
                                TypeElement destroyMethodDeclaringClass = (TypeElement) typeUtils.asElement(returnType);
                                JavaClassElement destroyMethodDeclaringElement = new JavaClassElement(destroyMethodDeclaringClass, AnnotationMetadata.EMPTY_METADATA, visitorContext);
                                final Optional<ExecutableElement> destroyMethodRef = modelUtils.findAccessibleNoArgumentInstanceMethod(destroyMethodDeclaringClass, destroyMethodName);
                                if (destroyMethodRef.isPresent()) {
                                    ExecutableElement executableElement = destroyMethodRef.get();
                                    JavaMethodElement destroyMethodElement = new JavaMethodElement(
                                            declaringClassElement,
                                            executableElement,
                                            AnnotationMetadata.EMPTY_METADATA,
                                            visitorContext
                                    );
                                    beanMethodWriter.visitPreDestroyMethod(
                                            destroyMethodDeclaringElement,
                                            destroyMethodElement.getReturnType(),
                                            destroyMethodName
                                    );
                                } else {
                                    error(beanMethod, "@Bean method defines a preDestroy method that does not exist or is not public: " + destroyMethodName);
                                }

                            }
                        });
            }
        }

        /**
         * @param method                   The {@link ExecutableElement}
         * @param methodAnnotationMetadata The {@link AnnotationMetadata}
         */
        void visitExecutableMethod(ExecutableElement method, AnnotationMetadata methodAnnotationMetadata) {
            TypeElement declaringClass = modelUtils.classElementFor(method);
            if (declaringClass == null || modelUtils.isObjectClass(declaringClass)) {
                return;
            }

            boolean isOwningClass = declaringClass.getQualifiedName().equals(concreteClass.getQualifiedName());

            if (isOwningClass && modelUtils.isAbstract(concreteClass) && !concreteClassMetadata.hasStereotype(INTRODUCTION_TYPE)) {
                return;
            }

            if (!isOwningClass && modelUtils.overridingOrHidingMethod(method, concreteClass, true).isPresent()) {
                return;
            }

            JavaClassElement declaringClassElement = new JavaClassElement(declaringClass, concreteClassMetadata, visitorContext);
            JavaMethodElement javaMethodElement = new JavaMethodElement(concreteClassElement, method, methodAnnotationMetadata, visitorContext);
            ExecutableElementParamInfo params = populateParameterData(null, method, null);
            BeanDefinitionVisitor beanWriter = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName());

            // This method requires pre-processing. See Executable#processOnStartup()
            boolean preprocess = methodAnnotationMetadata.isTrue(Executable.class, "processOnStartup");
            if (preprocess) {
                beanWriter.setRequiresMethodProcessing(true);
            }

            if (methodAnnotationMetadata.hasStereotype(Adapter.class)) {
                visitAdaptedMethod(method, methodAnnotationMetadata);
            }

            boolean executableMethodVisited = false;

            // shouldn't visit around advice on an introduction advice instance
            if (!(beanWriter instanceof AopProxyWriter)) {
                final boolean isConcrete = !modelUtils.isAbstract(concreteClass);
                final boolean isPublic = method.getModifiers().contains(Modifier.PUBLIC) || modelUtils.isPackagePrivate(method);
                if ((isAopProxyType && isPublic) ||
                        (!isAopProxyType && methodAnnotationMetadata.hasStereotype(AROUND_TYPE)) ||
                        (methodAnnotationMetadata.hasDeclaredStereotype(AROUND_TYPE) && isConcrete)) {

                    Object[] interceptorTypes = methodAnnotationMetadata.getAnnotationNamesByStereotype(AROUND_TYPE)
                            .toArray();

                    OptionalValues<Boolean> settings = methodAnnotationMetadata.getValues(AROUND_TYPE, Boolean.class);
                    AopProxyWriter aopProxyWriter = resolveAopProxyWriter(
                            beanWriter,
                            settings,
                            false,
                            this.constructorParameterInfo,
                            interceptorTypes
                    );

                    aopProxyWriter.visitInterceptorTypes(interceptorTypes);

                    AnnotationMetadata aroundMethodMetadata;
                    if (methodAnnotationMetadata instanceof AnnotationMetadataHierarchy) {
                        aroundMethodMetadata = methodAnnotationMetadata;
                    } else {
                        aroundMethodMetadata = new AnnotationMetadataHierarchy(concreteClassMetadata, methodAnnotationMetadata);
                    }

                    if (modelUtils.isFinal(method)) {
                        if (methodAnnotationMetadata.hasDeclaredStereotype(AROUND_TYPE)) {
                            error(method, "Method defines AOP advice but is declared final. Change the method to be non-final in order for AOP advice to be applied.");
                        } else {
                            if (isAopProxyType && isPublic && !declaringClass.equals(concreteClass)) {
                                addOriginatingElementIfNecessary(beanWriter, declaringClass);
                                beanWriter.visitExecutableMethod(
                                        declaringClassElement,
                                        javaMethodElement,
                                        params.getParameters(),
                                        params.getGenericParameterTypes(),
                                        params.getParameterMetadata(),
                                        aroundMethodMetadata,
                                        declaringClassElement.isInterface(),
                                        method.isDefault()
                                );
                                executableMethodVisited = true;
                            } else {
                                error(method, "Public method inherits AOP advice but is declared final. Either make the method non-public or apply AOP advice only to public methods declared on the class.");
                            }
                        }
                    } else {
                        addOriginatingElementIfNecessary(beanWriter, declaringClass);
                        aopProxyWriter.visitAroundMethod(
                                declaringClassElement,
                                javaMethodElement,
                                params.getParameters(),
                                params.getGenericParameterTypes(),
                                params.getParameterMetadata(),
                                aroundMethodMetadata,
                                declaringClassElement.isInterface(),
                                method.isDefault()
                        );
                        executableMethodVisited = true;
                    }

                }
            }

            if (!executableMethodVisited) {
                addOriginatingElementIfNecessary(beanWriter, declaringClass);
                beanWriter.visitExecutableMethod(
                        declaringClassElement,
                        javaMethodElement,
                        params.getParameters(),
                        params.getGenericParameterTypes(),
                        params.getParameterMetadata(),
                        methodAnnotationMetadata,
                        declaringClassElement.isInterface(),
                        method.isDefault()
                );
            }

        }

        private void visitAdaptedMethod(ExecutableElement sourceMethod, AnnotationMetadata methodAnnotationMetadata) {
            if (methodAnnotationMetadata instanceof AnnotationMetadataHierarchy) {
                methodAnnotationMetadata = ((AnnotationMetadataHierarchy) methodAnnotationMetadata).getDeclaredMetadata();
            }
            Optional<TypeElement> targetType = methodAnnotationMetadata.getValue(Adapter.class, String.class).flatMap(s ->
                    Optional.ofNullable(elementUtils.getTypeElement(s))
            );

            if (targetType.isPresent()) {
                TypeElement typeElement = targetType.get();
                boolean isInterface = JavaModelUtils.isInterface(typeElement);
                if (isInterface) {
                    JavaClassElement typeToImplementElement = new JavaClassElement(
                            typeElement,
                            annotationUtils.getAnnotationMetadata(typeElement),
                            visitorContext
                    );
                    DeclaredType typeToImplement = (DeclaredType) typeElement.asType();
                    String packageName = concreteClassElement.getPackageName();
                    String declaringClassSimpleName = concreteClassElement.getSimpleName();
                    String beanClassName = generateAdaptedMethodClassName(sourceMethod, typeElement, declaringClassSimpleName);
                    JavaMethodElement sourceMethodElement = new JavaMethodElement(
                            concreteClassElement,
                            sourceMethod,
                            methodAnnotationMetadata,
                            visitorContext
                    );
                    AopProxyWriter aopProxyWriter = new AopProxyWriter(
                            packageName,
                            beanClassName,
                            true,
                            false,
                            sourceMethodElement,
                            methodAnnotationMetadata,
                            new Object[]{modelUtils.resolveTypeReference(typeToImplement)},
                            ArrayUtils.EMPTY_OBJECT_ARRAY
                    );

                    aopProxyWriter.visitBeanDefinitionConstructor(methodAnnotationMetadata, false);

                    beanDefinitionWriters.put(elementUtils.getName(packageName + '.' + beanClassName), aopProxyWriter);

                    Map<String, ClassElement> typeVariables = typeToImplementElement.getTypeArguments();

                    AnnotationMetadata finalMethodAnnotationMetadata = methodAnnotationMetadata;
                    typeToImplement.accept(new PublicAbstractMethodVisitor<Object, AopProxyWriter>(typeElement, javaVisitorContext) {
                        boolean first = true;

                        @Override
                        protected void accept(DeclaredType type, Element element, AopProxyWriter aopProxyWriter) {
                            if (!first) {
                                error(sourceMethod, "Interface to adapt [" + typeToImplement + "] is not a SAM type. More than one abstract method declared.");
                                return;
                            }
                            first = false;
                            ExecutableElement targetMethod = (ExecutableElement) element;
                            JavaMethodElement targetMethodElement = new JavaMethodElement(
                                    typeToImplementElement,
                                    targetMethod,
                                    annotationUtils.getAnnotationMetadata(targetMethod),
                                    visitorContext
                            );

                            ParameterElement[] mnSourceParams = sourceMethodElement.getParameters();
                            ParameterElement[] mnTargetParams = targetMethodElement.getParameters();
                            List<? extends VariableElement> targetParameters = targetMethod.getParameters();
                            List<? extends VariableElement> sourceParameters = sourceMethod.getParameters();

                            int paramLen = targetParameters.size();
                            if (paramLen == sourceParameters.size()) {

                                Map<String, Object> genericTypes = new LinkedHashMap<>();
                                for (int i = 0; i < paramLen; i++) {

                                    VariableElement targetElement = targetParameters.get(i);
                                    TypeMirror targetMirror = targetElement.asType();
                                    ClassElement targetType = mnTargetParams[i].getGenericType();
                                    ClassElement sourceType = mnSourceParams[i].getGenericType();

                                    if (targetMirror.getKind() == TypeKind.TYPEVAR) {
                                        TypeVariable tv = (TypeVariable) targetMirror;
                                        String variableName = tv.toString();

                                        if (typeVariables.containsKey(variableName)) {
                                            genericTypes.put(variableName, sourceType);
                                        }
                                    }


                                    if (!sourceType.isAssignable(targetType.getName())) {
                                        error(sourceMethod, "Cannot adapt method [" + sourceMethod + "] to target method [" + targetMethod + "]. Type [" + sourceType.getName() + "] is not a subtype of type [" + targetType.getName() + "] for argument at position " + i);
                                        return;
                                    }
                                }


                                if (!genericTypes.isEmpty()) {
                                    Map<String, Map<String, Object>> typeData = Collections.singletonMap(
                                            modelUtils.resolveTypeReference(typeToImplement).toString(),
                                            genericTypes
                                    );

                                    aopProxyWriter.visitTypeArguments(
                                            resolveFinalTypeArguments(typeData)
                                    );
                                }

                                JavaClassElement declaringClassElement = new JavaClassElement(
                                        typeElement,
                                        finalMethodAnnotationMetadata,
                                        visitorContext
                                );
                                ExecutableElementParamInfo params = populateParameterData(typeElement.getQualifiedName().toString(), targetMethod, null);
                                Map<String, ParameterElement> methodParameters = params.getParameters();
                                Map<String, ClassElement> genericParameters = params.getGenericParameterTypes();
                                Map<String, AnnotationMetadata> methodQualifier = params.getParameterMetadata();

                                AnnotationClassValue<?>[] adaptedArgumentTypes = new AnnotationClassValue[paramLen];
                                for (int i = 0; i < adaptedArgumentTypes.length; i++) {
                                    VariableElement ve = sourceParameters.get(i);
                                    Object r = modelUtils.resolveTypeReference(ve.asType());
                                    if (r instanceof Class) {
                                        adaptedArgumentTypes[i] = new AnnotationClassValue((Class) r);
                                    } else {
                                        adaptedArgumentTypes[i] = new AnnotationClassValue(r.toString());
                                    }
                                }

                                JavaMethodElement javaMethodElement = new JavaMethodElement(
                                        declaringClassElement,
                                        targetMethod,
                                        finalMethodAnnotationMetadata,
                                        visitorContext
                                );

                                AnnotationMetadata annotationMetadata = javaMethodElement.annotate(Adapter.class, (builder) -> {
                                    AnnotationClassValue<Object> acv = new AnnotationClassValue<>(concreteClassElement.getName());
                                    builder.member(Adapter.InternalAttributes.ADAPTED_BEAN, acv);
                                    builder.member(Adapter.InternalAttributes.ADAPTED_METHOD, sourceMethodElement.getName());
                                    builder.member(Adapter.InternalAttributes.ADAPTED_ARGUMENT_TYPES, adaptedArgumentTypes);
                                    String qualifier = concreteClassMetadata.getValue(Named.class, String.class).orElse(null);
                                    if (StringUtils.isNotEmpty(qualifier)) {
                                        builder.member(Adapter.InternalAttributes.ADAPTED_QUALIFIER, qualifier);
                                    }
                                }).getAnnotationMetadata();

                                aopProxyWriter.visitAroundMethod(
                                        declaringClassElement,
                                        javaMethodElement,
                                        methodParameters,
                                        genericParameters,
                                        methodQualifier,
                                        annotationMetadata,
                                        declaringClassElement.isInterface(),
                                        targetMethod.isDefault()
                                );


                            } else {
                                error(sourceMethod, "Cannot adapt method [" + sourceMethod + "] to target method [" + targetMethod + "]. Argument lengths don't match.");
                            }
                        }
                    }, aopProxyWriter);
                }
            }
        }

        private String generateAdaptedMethodClassName(ExecutableElement method, TypeElement typeElement, String declaringClassSimpleName) {
            String rootName = declaringClassSimpleName + '$' + typeElement.getSimpleName().toString() + '$' + method.getSimpleName().toString();
            return rootName + adaptedMethodIndex.incrementAndGet();
        }

        private AopProxyWriter resolveAopProxyWriter(BeanDefinitionVisitor beanWriter,
                                                     OptionalValues<Boolean> aopSettings,
                                                     boolean isFactoryType,
                                                     ExecutableElementParamInfo constructorParameterInfo,
                                                     Object... interceptorTypes) {
            String beanName = beanWriter.getBeanDefinitionName();
            Name proxyKey = createProxyKey(beanName);
            BeanDefinitionVisitor aopWriter = beanWriter instanceof AopProxyWriter ? beanWriter : beanDefinitionWriters.get(proxyKey);

            AopProxyWriter aopProxyWriter;
            if (aopWriter == null) {
                aopProxyWriter
                        = new AopProxyWriter(
                        (BeanDefinitionWriter) beanWriter,
                        aopSettings,
                        interceptorTypes
                );

                if (constructorParameterInfo != null) {
                    aopProxyWriter.visitBeanDefinitionConstructor(
                            constructorParameterInfo.getAnnotationMetadata(),
                            constructorParameterInfo.isRequiresReflection(),
                            constructorParameterInfo.getParameters(),
                            constructorParameterInfo.getParameterMetadata(),
                            constructorParameterInfo.getGenericParameterTypes()
                    );
                } else {
                    aopProxyWriter.visitBeanDefinitionConstructor(
                            AnnotationMetadata.EMPTY_METADATA,
                            false
                    );
                }

                if (isFactoryType) {
                    aopProxyWriter
                            .visitSuperBeanDefinitionFactory(beanName);
                } else {
                    aopProxyWriter
                            .visitSuperBeanDefinition(beanName);
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

        /**
         * @param method The {@link ExecutableElement}
         * @param o      An object
         */
        void visitAnnotatedMethod(ExecutableElement method, Object o) {
            ExecutableElementParamInfo params = populateParameterData(null, method, null);
            TypeElement declaringClass = modelUtils.classElementFor(method);

            if (declaringClass == null) {
                return;
            }

            boolean isParent = !declaringClass.getQualifiedName().equals(this.concreteClass.getQualifiedName());
            ExecutableElement overridingMethod = modelUtils.overridingOrHidingMethod(method, this.concreteClass, false).orElse(method);
            TypeElement overridingClass = modelUtils.classElementFor(overridingMethod);
            boolean overridden = isParent && overridingClass != null && !overridingClass.getQualifiedName().equals(declaringClass.getQualifiedName());

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
            boolean overriddenInjected = overridden && annotationUtils.getAnnotationMetadata(overridingMethod).hasDeclaredStereotype(Inject.class);

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

            AnnotationMetadata annotationMetadata = annotationUtils.getAnnotationMetadata(method);
            JavaClassElement declaringClassElement = new JavaClassElement(
                    declaringClass,
                    concreteClassMetadata,
                    visitorContext
            );
            JavaMethodElement javaMethodElement = new JavaMethodElement(
                    concreteClassElement,
                    method,
                    annotationMetadata,
                    visitorContext
            );

            if (annotationMetadata.hasDeclaredStereotype(ProcessedTypes.POST_CONSTRUCT)) {
                BeanDefinitionVisitor writer = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName());
                final AopProxyWriter aopWriter = resolveAopWriter(writer);
                if (aopWriter != null && !aopWriter.isProxyTarget()) {
                    writer = aopWriter;
                }
                addOriginatingElementIfNecessary(writer, declaringClass);
                writer.visitPostConstructMethod(
                        declaringClassElement,
                        requiresReflection,
                        javaMethodElement.getReturnType(),
                        method.getSimpleName().toString(),
                        params.getParameters(),
                        params.getParameterMetadata(),
                        params.getGenericParameterTypes(),
                        annotationMetadata
                );
            } else if (annotationMetadata.hasDeclaredStereotype(ProcessedTypes.PRE_DESTROY)) {
                BeanDefinitionVisitor writer = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName());
                final AopProxyWriter aopWriter = resolveAopWriter(writer);
                if (aopWriter != null && !aopWriter.isProxyTarget()) {
                    writer = aopWriter;
                }
                addOriginatingElementIfNecessary(writer, declaringClass);
                writer.visitPreDestroyMethod(
                        declaringClassElement,
                        requiresReflection,
                        javaMethodElement.getReturnType(),
                        method.getSimpleName().toString(),
                        params.getParameters(),
                        params.getParameterMetadata(),
                        params.getGenericParameterTypes(),
                        annotationMetadata
                );
            } else if (annotationMetadata.hasDeclaredStereotype(Inject.class) || annotationMetadata.hasDeclaredStereotype(ConfigurationInject.class)) {
                BeanDefinitionVisitor writer = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName());
                addOriginatingElementIfNecessary(writer, declaringClass);
                writer.visitMethodInjectionPoint(
                        declaringClassElement,
                        requiresReflection,
                        javaMethodElement.getReturnType(),
                        method.getSimpleName().toString(),
                        params.getParameters(),
                        params.getParameterMetadata(),
                        params.getGenericParameterTypes(),
                        annotationMetadata
                );
            } else {
                error("Unexpected call to visitAnnotatedMethod(%s)", method);
            }
        }

        private @Nullable
        AopProxyWriter resolveAopWriter(BeanDefinitionVisitor writer) {
            Name proxyKey = createProxyKey(writer.getBeanDefinitionName());
            final BeanDefinitionVisitor aopWriter = beanDefinitionWriters.get(proxyKey);
            if (aopWriter instanceof AopProxyWriter) {
                return (AopProxyWriter) aopWriter;
            } else if (isAopProxyType) {
                Object[] interceptorTypes = annotationUtils.getAnnotationMetadata(concreteClass)
                        .getAnnotationNamesByStereotype(AROUND_TYPE)
                        .toArray();
                return resolveAopProxyWriter(
                        writer,
                        aopSettings,
                        isFactoryType,
                        constructorParameterInfo,
                        interceptorTypes
                );
            }
            return null;
        }

        @Override
        public Object visitVariable(VariableElement variable, Object o) {
            // assuming just fields, visitExecutable should be handling params for method calls
            if (variable.getKind() != FIELD) {
                return null;
            }

            if (modelUtils.isStatic(variable)) {
                return null;
            } else if (modelUtils.isFinal(variable)) {
                AnnotationMetadata fieldAnnotationMetadata = annotationUtils.getAnnotationMetadata(variable);
                boolean isConfigBuilder = fieldAnnotationMetadata.hasStereotype(ConfigurationBuilder.class);
                if (isConfigBuilder) {
                    visitConfigurationProperty(variable, fieldAnnotationMetadata);
                }
                return null;
            }

            AnnotationMetadata fieldAnnotationMetadata = annotationUtils.getAnnotationMetadata(variable);
            boolean isInjected = fieldAnnotationMetadata.hasStereotype(Inject.class);
            boolean isValue = !isInjected &&
                    (fieldAnnotationMetadata.hasStereotype(Value.class) || fieldAnnotationMetadata.hasStereotype(Property.class));

            if (isInjected || isValue) {
                BeanDefinitionVisitor writer = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName());
                TypeElement declaringClass = modelUtils.classElementFor(variable);

                if (declaringClass == null) {
                    return null;
                }

                JavaClassElement declaringClassElement = new JavaClassElement(declaringClass, concreteClassMetadata, visitorContext);
                JavaFieldElement javaFieldElement = new JavaFieldElement(variable, fieldAnnotationMetadata, visitorContext);
                addOriginatingElementIfNecessary(writer, declaringClass);

                boolean isPrivate = javaFieldElement.isPrivate();
                boolean requiresReflection = isPrivate
                        || modelUtils.isInheritedAndNotPublic(this.concreteClass, declaringClass, variable);

                if (!writer.isValidated()) {
                    writer.setValidated(IS_CONSTRAINT.test(fieldAnnotationMetadata));
                }

                TypeMirror type = variable.asType();
                if ((type.getKind() == TypeKind.ERROR) && !processingOver) {
                    throw new PostponeToNextRoundException();
                }

                if (isValue) {
                    writer.visitFieldValue(
                            declaringClassElement,
                            javaFieldElement.getType(),
                            javaFieldElement.getName(),
                            requiresReflection,
                            fieldAnnotationMetadata,
                            javaFieldElement.getGenericType().getTypeArguments(),
                            isConfigurationPropertiesType
                    );
                } else {
                    writer.visitFieldInjectionPoint(
                            declaringClassElement,
                            javaFieldElement.getType(),
                            javaFieldElement.getName(),
                            requiresReflection,
                            fieldAnnotationMetadata,
                            javaFieldElement.getGenericType().getTypeArguments()
                    );
                }
            } else if (isConfigurationPropertiesType) {
                visitConfigurationProperty(variable, fieldAnnotationMetadata);
            }
            return null;
        }

        private void addOriginatingElementIfNecessary(BeanDefinitionVisitor writer, TypeElement declaringClass) {
            if (!concreteClass.equals(declaringClass)) {
                writer.addOriginatingElement(new JavaClassElement(declaringClass, currentClassMetadata, visitorContext));
            }
        }

        /**
         * @param field                   The {@link VariableElement}
         * @param fieldAnnotationMetadata The annotation metadata for the field
         * @return Returns null after visiting the configuration properties
         */
        public Object visitConfigurationProperty(VariableElement field, AnnotationMetadata fieldAnnotationMetadata) {
            Optional<ExecutableElement> setterMethod = modelUtils.findSetterMethodFor(field);
            boolean isInjected = fieldAnnotationMetadata.hasStereotype(Inject.class);
            boolean isValue = fieldAnnotationMetadata.hasStereotype(Value.class) || fieldAnnotationMetadata.hasStereotype(Property.class);

            boolean isMethodInjected = isInjected || (setterMethod.isPresent() && annotationUtils.hasStereotype(setterMethod.get(), Inject.class));
            if (!(isMethodInjected || isValue)) {
                // visitVariable didn't handle it
                BeanDefinitionVisitor writer = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName());
                if (!writer.isValidated()) {
                    writer.setValidated(IS_CONSTRAINT.test(fieldAnnotationMetadata));
                }


                TypeElement declaringClass = modelUtils.classElementFor(field);

                if (declaringClass == null) {
                    return null;
                }
                JavaClassElement declaringClassElement = new JavaClassElement(declaringClass, concreteClassMetadata, visitorContext);
                JavaFieldElement javaFieldElement = new JavaFieldElement(field, fieldAnnotationMetadata, visitorContext);
                String fieldName = javaFieldElement.getName();

                if (fieldAnnotationMetadata.hasStereotype(ConfigurationBuilder.class)) {

                    boolean accessible = false;
                    if (modelUtils.isPublic(field)) {
                        accessible = true;
                    } else if (modelUtils.isPackagePrivate(field) || modelUtils.isProtected(field)) {
                        String declaringPackage = declaringClassElement.getPackageName();
                        String concretePackage = concreteClassElement.getPackageName();
                        accessible = declaringPackage.equals(concretePackage);
                    }

                    boolean isInterface = javaFieldElement.getType().isInterface();
                    if (accessible) {
                        writer.visitConfigBuilderField(javaFieldElement.getType(), fieldName, fieldAnnotationMetadata, metadataBuilder, isInterface);
                    } else {
                        // Using the field would throw a IllegalAccessError, use the method instead
                        Optional<ExecutableElement> getterMethod = modelUtils.findGetterMethodFor(field);
                        if (getterMethod.isPresent()) {
                            writer.visitConfigBuilderMethod(javaFieldElement.getType(), getterMethod.get().getSimpleName().toString(), fieldAnnotationMetadata, metadataBuilder, isInterface);
                        } else {
                            error(field, "ConfigurationBuilder applied to a non accessible (private or package-private/protected in a different package) field must have a corresponding non-private getter method.");
                        }
                    }
                    try {
                        visitConfigurationBuilder(declaringClass, field, field.asType(), writer);
                    } finally {
                        writer.visitConfigBuilderEnd();
                    }
                } else {
                    if (shouldExclude(configurationMetadata, fieldName)) {
                        return null;
                    }
                    if (setterMethod.isPresent()) {
                        ExecutableElement method = setterMethod.get();
                        // Just visit the field metadata, the setter will be processed
                        String docComment = elementUtils.getDocComment(method);
                        metadataBuilder.visitProperty(
                                concreteClass,
                                declaringClass,
                                getPropertyMetadataTypeReference(field.asType()),
                                fieldName,
                                docComment,
                                null
                        );
                    } else {
                        boolean isPrivate = javaFieldElement.isPrivate();
                        boolean requiresReflection = isInheritedAndNotPublic(modelUtils.classElementFor(field), field.getModifiers());

                        if (!isPrivate) {
                            String docComment = elementUtils.getDocComment(field);
                            PropertyMetadata propertyMetadata = metadataBuilder.visitProperty(
                                    concreteClass,
                                    declaringClass,
                                    getPropertyMetadataTypeReference(field.asType()),
                                    fieldName,
                                    docComment,
                                    null
                            );

                            fieldAnnotationMetadata = addPropertyMetadata(javaFieldElement, propertyMetadata);
                            writer.visitFieldValue(
                                    declaringClassElement,
                                    javaFieldElement.getType(),
                                    fieldName,
                                    requiresReflection,
                                    fieldAnnotationMetadata,
                                    javaFieldElement.getGenericType().getTypeArguments(),
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
            if (!JavaModelUtils.isRecordOrRecordComponent(e)) {
                note("Visit unknown %s for %s", e.getSimpleName(), o);
                return super.visitUnknown(e, o);
            }
            return o;
        }

        /**
         * @param declaringClass The {@link TypeElement}
         * @param modifiers      The {@link Modifier}
         * @return Whether is inherited and not public
         */
        protected boolean isInheritedAndNotPublic(TypeElement declaringClass, Set<Modifier> modifiers) {
            PackageElement declaringPackage = elementUtils.getPackageOf(declaringClass);
            PackageElement concretePackage = elementUtils.getPackageOf(concreteClass);
            return !declaringClass.equals(concreteClass) &&
                    !declaringPackage.equals(concretePackage) &&
                    !(modifiers.contains(Modifier.PUBLIC));
        }

        private void visitConfigurationBuilder(TypeElement declaringClass, Element builderElement, TypeMirror builderType, BeanDefinitionVisitor writer) {
            AnnotationMetadata annotationMetadata = annotationUtils.getAnnotationMetadata(builderElement);
            Boolean allowZeroArgs = annotationMetadata.getValue(ConfigurationBuilder.class, "allowZeroArgs", Boolean.class).orElse(false);
            List<String> prefixes = Arrays.asList(annotationMetadata.getValue(ConfigurationBuilder.class, "prefixes", String[].class).orElse(new String[]{"set"}));
            String configurationPrefix = annotationMetadata.getValue(ConfigurationBuilder.class, String.class)
                    .map(v -> v + ".").orElse("");
            Set<String> includes = annotationMetadata.getValue(ConfigurationBuilder.class, "includes", Set.class).orElse(Collections.emptySet());
            Set<String> excludes = annotationMetadata.getValue(ConfigurationBuilder.class, "excludes", Set.class).orElse(Collections.emptySet());

            PublicMethodVisitor visitor = new PublicMethodVisitor(javaVisitorContext) {
                @Override
                protected void accept(DeclaredType type, Element element, Object o) {
                    ExecutableElement method = (ExecutableElement) element;
                    List<? extends VariableElement> params = method.getParameters();
                    String methodName = method.getSimpleName().toString();
                    String prefix = getMethodPrefix(prefixes, methodName);
                    String propertyName = NameUtils.decapitalize(methodName.substring(prefix.length()));
                    if (shouldExclude(includes, excludes, propertyName)) {
                        return;
                    }

                    int paramCount = params.size();
                    JavaMethodElement javaMethodElement = new JavaMethodElement(
                            concreteClassElement,
                            method,
                            AnnotationMetadata.EMPTY_METADATA,
                            visitorContext
                    );
                    if (paramCount < 2) {
                        VariableElement paramType = paramCount == 1 ? params.get(0) : null;

                        ClassElement parameterElement = null;
                        if (paramType != null) {
                            parameterElement = new JavaParameterElement(concreteClassElement, paramType, AnnotationMetadata.EMPTY_METADATA, visitorContext).getGenericType();
                        }
                        PropertyMetadata metadata = metadataBuilder.visitProperty(
                                concreteClass,
                                declaringClass,
                                parameterElement != null ? parameterElement.getName() : null,
                                configurationPrefix + propertyName,
                                null,
                                null
                        );


                        writer.visitConfigBuilderMethod(
                                prefix,
                                javaMethodElement.getReturnType(),
                                methodName,
                                parameterElement,
                                parameterElement != null ? parameterElement.getTypeArguments() : null,
                                metadata.getPath()
                        );
                    } else if (paramCount == 2) {
                        // check the params are a long and a TimeUnit
                        VariableElement first = params.get(0);
                        VariableElement second = params.get(1);
                        TypeMirror tu = elementUtils.getTypeElement(TimeUnit.class.getName()).asType();
                        TypeMirror typeMirror = first.asType();
                        if (typeMirror.toString().equals("long") && typeUtils.isAssignable(second.asType(), tu)) {

                            PropertyMetadata metadata = metadataBuilder.visitProperty(
                                    concreteClass,
                                    declaringClass,
                                    Duration.class.getName(),
                                    configurationPrefix + propertyName,
                                    null,
                                    null
                            );

                            writer.visitConfigBuilderDurationMethod(
                                    prefix,
                                    javaMethodElement.getReturnType(),
                                    methodName,
                                    metadata.getPath()
                            );
                        }
                    }
                }

                @SuppressWarnings("MagicNumber")
                @Override
                protected boolean isAcceptable(Element element) {
                    // ignore deprecated methods
                    if (annotationUtils.hasStereotype(element, Deprecated.class)) {
                        return false;
                    }
                    Set<Modifier> modifiers = element.getModifiers();
                    if (element.getKind() == ElementKind.METHOD) {
                        ExecutableElement method = (ExecutableElement) element;
                        int paramCount = method.getParameters().size();
                        return modifiers.contains(Modifier.PUBLIC) && ((paramCount > 0 && paramCount < 3) || allowZeroArgs && paramCount == 0) && isPrefixedWith(method, prefixes);
                    } else {
                        return false;
                    }
                }

                private boolean isPrefixedWith(Element enclosedElement, List<String> prefixes) {
                    String name = enclosedElement.getSimpleName().toString();
                    for (String prefix : prefixes) {
                        if (name.startsWith(prefix)) {
                            return true;
                        }
                    }
                    return false;
                }

                private String getMethodPrefix(List<String> prefixes, String methodName) {
                    for (String prefix : prefixes) {
                        if (methodName.startsWith(prefix)) {
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

            JavaClassElement classElement;
            AnnotationMetadata annotationMetadata;
            if (typeElement == concreteClass) {
                classElement = concreteClassElement;
                annotationMetadata = classElement.getAnnotationMetadata();
            } else {
                annotationMetadata = annotationUtils.getAnnotationMetadata(typeElement);
                classElement = new JavaClassElement(typeElement, annotationMetadata, visitorContext);
            }
            String packageName = classElement.getPackageName();
            boolean isInterface = classElement.isInterface();
            String beanClassName = modelUtils.simpleBinaryNameFor(typeElement);

            if (configurationMetadata != null) {
                // unfortunate we have to do this
                String existingPrefix = annotationMetadata.getValue(
                        ConfigurationReader.class,
                        "prefix", String.class)
                        .orElse("");

                String computedPrefix = StringUtils.isNotEmpty(existingPrefix) ? existingPrefix + "." + configurationMetadata.getName() : configurationMetadata.getName();
                annotationMetadata = classElement.annotate(ConfigurationReader.class, (builder) ->
                        builder.member("prefix", computedPrefix)
                ).getAnnotationMetadata();
            }


            BeanDefinitionWriter beanDefinitionWriter = new BeanDefinitionWriter(
                    packageName,
                    beanClassName,
                    providerTypeParam == null
                            ? elementUtils.getBinaryName(typeElement).toString()
                            : providerTypeParam.toString(),
                    isInterface,
                    classElement,
                    annotationMetadata
            );

            visitTypeArguments(typeElement, beanDefinitionWriter);

            return beanDefinitionWriter;
        }

        private void visitTypeArguments(TypeElement typeElement, BeanDefinitionWriter beanDefinitionWriter) {
            Map<String, Map<String, Object>> typeArguments = genericUtils.buildGenericTypeArgumentInfo(typeElement);
            if (CollectionUtils.isNotEmpty(typeArguments)) {
                Map<String, Map<String, Object>> finalizedArguments = resolveFinalTypeArguments(typeArguments);
                beanDefinitionWriter.visitTypeArguments(
                        finalizedArguments
                );
            }
        }

        private DynamicName createProxyKey(String beanName) {
            return new DynamicName(beanName + "$Proxy");
        }

        @SuppressWarnings("MagicNumber")
        private AopProxyWriter createIntroductionAdviceWriter(TypeElement typeElement) {
            AnnotationMetadata annotationMetadata = annotationUtils.getAnnotationMetadata(typeElement);

            PackageElement packageElement = elementUtils.getPackageOf(typeElement);
            String beanClassName = modelUtils.simpleBinaryNameFor(typeElement);
            Object[] aroundInterceptors = annotationMetadata
                    .getAnnotationNamesByStereotype(AROUND_TYPE)
                    .toArray();
            Object[] introductionInterceptors = annotationMetadata
                    .getAnnotationNamesByStereotype(Introduction.class)
                    .toArray();

            String[] interfaceTypes = annotationMetadata.getValue(Introduction.class, "interfaces", String[].class).orElse(new String[0]);

            Object[] interceptorTypes = ArrayUtils.concat(aroundInterceptors, introductionInterceptors);
            boolean isInterface = JavaModelUtils.isInterface(typeElement);
            AopProxyWriter aopProxyWriter = new AopProxyWriter(
                    packageElement.getQualifiedName().toString(),
                    beanClassName,
                    isInterface,
                    new JavaClassElement(typeElement, annotationMetadata, visitorContext),
                    annotationMetadata,
                    interfaceTypes,
                    interceptorTypes
            );

            Set<TypeElement> additionalInterfaces = Arrays.stream(interfaceTypes)
                    .map(elementUtils::getTypeElement)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            if (ArrayUtils.isNotEmpty(interfaceTypes)) {
                List<? extends AnnotationMirror> annotationMirrors = typeElement.getAnnotationMirrors();
                populateIntroductionInterfaces(annotationMirrors, additionalInterfaces);
                if (!additionalInterfaces.isEmpty()) {
                    for (TypeElement additionalInterface : additionalInterfaces) {
                        visitIntroductionAdviceInterface(additionalInterface, annotationMetadata, aopProxyWriter);
                    }
                }
            }
            return aopProxyWriter;
        }

        private void populateIntroductionInterfaces(List<? extends AnnotationMirror> annotationMirrors, Set<TypeElement> additionalInterfaces) {
            for (AnnotationMirror annotationMirror : annotationMirrors) {
                DeclaredType annotationType = annotationMirror.getAnnotationType();
                if (annotationType.toString().equals(Introduction.class.getName())) {
                    Map<? extends ExecutableElement, ? extends AnnotationValue> values = annotationMirror.getElementValues();
                    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : values.entrySet()) {
                        ExecutableElement key = entry.getKey();
                        if (key.toString().equalsIgnoreCase("interfaces()")) {
                            Object value = entry.getValue().getValue();
                            if (value instanceof List) {
                                for (Object v : ((List) value)) {
                                    if (v instanceof AnnotationValue) {
                                        tryAddAnnotationValue(additionalInterfaces, (AnnotationValue) v);
                                    }
                                }
                            } else if (value instanceof AnnotationValue) {
                                tryAddAnnotationValue(additionalInterfaces, (AnnotationValue) value);
                            }
                        }
                    }
                } else {
                    Element element = annotationType.asElement();
                    if (annotationUtils.hasStereotype(element, Introduction.class)) {
                        populateIntroductionInterfaces(element.getAnnotationMirrors(), additionalInterfaces);
                    }
                }
            }
        }

        private void tryAddAnnotationValue(Set<TypeElement> additionalInterfaces, AnnotationValue v) {
            Object v2 = v.getValue();
            if (v2 instanceof TypeMirror) {
                TypeMirror tm = (TypeMirror) v2;
                if (tm.getKind() == TypeKind.DECLARED) {
                    DeclaredType dt = (DeclaredType) tm;
                    additionalInterfaces.add((TypeElement) dt.asElement());
                }
            }
        }

        private BeanDefinitionWriter createFactoryBeanMethodWriterFor(ExecutableElement method, TypeElement producedElement) {
            AnnotationMetadata annotationMetadata = annotationUtils.newAnnotationBuilder().buildForParent(producedElement, method, true);
            PackageElement producedPackageElement = elementUtils.getPackageOf(producedElement);
            PackageElement definingPackageElement = elementUtils.getPackageOf(concreteClass);

            boolean isInterface = JavaModelUtils.isInterface(producedElement);
            String packageName = producedPackageElement.getQualifiedName().toString();
            String beanDefinitionPackage = definingPackageElement.getQualifiedName().toString();
            String shortClassName = modelUtils.simpleBinaryNameFor(producedElement);
            String upperCaseMethodName = NameUtils.capitalize(method.getSimpleName().toString());
            String factoryMethodBeanDefinitionName = beanDefinitionPackage + ".$" + concreteClass.getSimpleName().toString() + "$" + upperCaseMethodName + factoryMethodIndex.getAndIncrement() + "Definition";
            return new BeanDefinitionWriter(
                    packageName,
                    shortClassName,
                    factoryMethodBeanDefinitionName,
                    modelUtils.resolveTypeReference(producedElement).toString(),
                    isInterface,
                    new JavaMethodElement(
                            new JavaClassElement(concreteClass, concreteClassMetadata, visitorContext),
                            method,
                            annotationMetadata,
                            visitorContext
                    ),
                    annotationMetadata
            );
        }

        private ExecutableElementParamInfo populateParameterData(
                @Nullable String declaringTypeName,
                ExecutableElement element,
                Map<String, Map<String, TypeMirror>> typeArgumentMirrors) {
            if (element == null) {
                return new ExecutableElementParamInfo(false, null);
            }
            AnnotationMetadata elementMetadata;

            if (declaringTypeName == null) {
                elementMetadata = annotationUtils.getAnnotationMetadata(
                        element
                );
            } else {
                elementMetadata = annotationUtils.newAnnotationBuilder().build(
                        declaringTypeName,
                        element
                );
            }
            ExecutableElementParamInfo params = new ExecutableElementParamInfo(
                    modelUtils.isPrivate(element),
                    elementMetadata
            );
            final boolean isConstructBinding = elementMetadata.hasDeclaredStereotype(ConfigurationInject.class) ||
                    isConfigurationPropertiesType && JavaModelUtils.isRecord(concreteClass);
            if (isConstructBinding) {
                this.configurationMetadata = metadataBuilder.visitProperties(
                        concreteClass,
                        null);

            }

            element.getParameters().forEach(paramElement -> {

                String argName = paramElement.getSimpleName().toString();

                AnnotationMetadata annotationMetadata = annotationUtils.getAnnotationMetadata(paramElement);
                ParameterElement parameterElement = new JavaParameterElement(
                        concreteClassElement,
                        paramElement,
                        annotationMetadata,
                        visitorContext
                ) {
                    @Override
                    public ClassElement getGenericType() {
                        if (typeArgumentMirrors != null) {
                            return parameterizedClassElement(getNativeType().asType(), visitorContext, typeArgumentMirrors);
                        } else {
                            return super.getGenericType();
                        }
                    }
                };

                if (annotationMetadata.hasStereotype(ANN_CONSTRAINT)) {
                    params.setValidated(true);
                }

                TypeMirror typeMirror = paramElement.asType();
                if ((typeMirror.getKind() == TypeKind.ERROR) && !processingOver) {
                    throw new PostponeToNextRoundException();
                }
                if (isConstructBinding && Stream.of(Property.class, Value.class, Parameter.class, Qualifier.class).noneMatch(annotationMetadata::hasStereotype)) {
                    final Element e = typeUtils.asElement(typeMirror);
                    final AnnotationMetadata parameterTypeMetadata = e != null ? annotationUtils.getAnnotationMetadata(e) : AnnotationMetadata.EMPTY_METADATA;
                    if (!parameterTypeMetadata.hasStereotype(Scope.class)) {
                        addPropertyMetadata(parameterElement, paramElement, argName);
                    }
                }

                params.addParameter(parameterElement.getName(), parameterElement);
            });

            return params;
        }

        private boolean shouldExclude(Set<String> includes, Set<String> excludes, String propertyName) {
            if (!includes.isEmpty() && !includes.contains(propertyName)) {
                return true;
            }
            return !excludes.isEmpty() && excludes.contains(propertyName);
        }

        private boolean shouldExclude(ConfigurationMetadata configurationMetadata, String propertyName) {
            return shouldExclude(configurationMetadata.getIncludes(), configurationMetadata.getExcludes(), propertyName);
        }
    }

    private Map<String, Map<String, Object>> resolveFinalTypeArguments(Map<String, Map<String, Object>> typeArguments) {
        Map<String, Map<String, Object>> finalizedArguments = new LinkedHashMap<>(typeArguments);
        typeArguments.forEach((key, value) -> {
            Map<String, Object> newValue = new LinkedHashMap<>(value.size());
            value.forEach((t, tm) -> {
                if (tm instanceof TypeMirror) {
                    newValue.put(t, genericUtils.resolveTypeReference((TypeMirror) tm));
                } else {
                    newValue.put(t, tm);
                }
            });
            finalizedArguments.put(key, newValue);
        });
        return finalizedArguments;
    }

    /**
     * A dynamic name.
     */
    static class DynamicName implements Name {
        private final CharSequence name;

        /**
         * @param name The name
         */
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
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            DynamicName that = (DynamicName) o;

            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

}
