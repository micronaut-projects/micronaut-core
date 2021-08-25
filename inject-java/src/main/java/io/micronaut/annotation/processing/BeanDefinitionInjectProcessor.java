/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.aop.*;
import io.micronaut.aop.internal.intercepted.InterceptedMethodUtil;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.annotation.processing.visitor.JavaElementFactory;
import io.micronaut.annotation.processing.visitor.JavaMethodElement;
import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
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
import io.micronaut.inject.ast.*;
import io.micronaut.inject.configuration.ConfigurationMetadata;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.configuration.PropertyMetadata;
import io.micronaut.inject.processing.JavaModelUtils;
import io.micronaut.inject.visitor.BeanElementVisitor;
import io.micronaut.inject.visitor.VisitorConfiguration;
import io.micronaut.inject.writer.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.*;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.type.*;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.ElementScanner8;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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

    private static final String AROUND_TYPE = AnnotationUtil.ANN_AROUND;
    private static final String INTRODUCTION_TYPE = AnnotationUtil.ANN_INTRODUCTION;
    private static final String[] ANNOTATION_STEREOTYPES = new String[]{
            AnnotationUtil.POST_CONSTRUCT,
            AnnotationUtil.PRE_DESTROY,
            "jakarta.annotation.PreDestroy",
            "jakarta.annotation.PostConstruct",
            "javax.inject.Inject",
            "javax.inject.Qualifier",
            "javax.inject.Singleton",
            "jakarta.inject.Inject",
            "jakarta.inject.Qualifier",
            "jakarta.inject.Singleton",
            "io.micronaut.context.annotation.Bean",
            "io.micronaut.context.annotation.Replaces",
            "io.micronaut.context.annotation.Value",
            "io.micronaut.context.annotation.Property",
            "io.micronaut.context.annotation.Executable",
            AROUND_TYPE,
            AnnotationUtil.ANN_INTERCEPTOR_BINDINGS,
            AnnotationUtil.ANN_INTERCEPTOR_BINDING,
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
    private boolean processingOver;
    private final Set<String> processed = new HashSet<>();

    @Override
    public final synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.metadataBuilder = new JavaConfigurationMetadataBuilder(elementUtils, typeUtils, annotationUtils);
        ConfigurationMetadataBuilder.setConfigurationMetadataBuilder(metadataBuilder);
        this.beanDefinitions = new LinkedHashSet<>();

        for (BeanElementVisitor<?> visitor : BeanElementVisitor.VISITORS) {
            if (visitor.isEnabled()) {
                try {
                    visitor.start(javaVisitorContext);
                } catch (Exception e) {
                    javaVisitorContext.fail("Error initializing bean element visitor [" + visitor.getClass().getName() + "]: " + e.getMessage(), null);
                }
            }
        }
    }

    @NonNull
    @Override
    protected JavaVisitorContext newVisitorContext(@NonNull ProcessingEnvironment processingEnv) {
        return new JavaVisitorContext(
                processingEnv,
                messager,
                elementUtils,
                annotationUtils,
                typeUtils,
                modelUtils,
                genericUtils,
                filer,
                visitorAttributes,
                getVisitorKind()
        ) {
            @NonNull
            @Override
            public VisitorConfiguration getConfiguration() {
                return new VisitorConfiguration() {
                    @Override
                    public boolean includeTypeLevelAnnotationsInGenericArguments() {
                        return false;
                    }
                };
            }
        };
    }

    @Override
    public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        processingOver = roundEnv.processingOver();

        annotations = annotations
                .stream()
                .filter(ann -> {
                    final String name = ann.getQualifiedName().toString();
                    String packageName = NameUtils.getPackageName(name);
                    return !name.equals(AnnotationUtil.KOTLIN_METADATA) && !AnnotationUtil.STEREOTYPE_EXCLUDES.contains(packageName);
                })
                .filter(ann -> annotationUtils.hasStereotype(ann, ANNOTATION_STEREOTYPES) || isProcessedAnnotation(ann.getQualifiedName().toString()))
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
                        if (typeElement == null) {
                            return;
                        }
                        if (element.getKind() == ENUM) {
                            final AnnotationMetadata am = annotationUtils.getAnnotationMetadata(element);
                            if (isDeclaredBeanInMetadata(am)) {
                                error(element, "Enum types cannot be defined as beans");
                            }
                            return;
                        }
                        // skip Groovy code, handled by InjectTransform. Required for GroovyEclipse compiler
                        if ((groovyObjectType != null && typeUtils.isAssignable(typeElement.asType(), groovyObjectType))) {
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
                                processBeanDefinitions(writer);
                            }
                        });
                        AnnotationUtils.invalidateMetadata(refreshedClassElement);
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
                for (BeanElementVisitor<?> visitor : BeanElementVisitor.VISITORS) {
                    if (visitor.isEnabled()) {
                        try {
                            visitor.finish(javaVisitorContext);
                        } catch (Exception e) {
                            javaVisitorContext.fail("Error finalizing bean element visitor [" + visitor.getClass().getName() + "]: " + e.getMessage(), null);
                        }
                    }
                }
                final List<AbstractBeanDefinitionBuilder> beanElementBuilders = javaVisitorContext.getBeanElementBuilders();
                if (CollectionUtils.isNotEmpty(beanElementBuilders)) {
                    try {
                        AbstractBeanDefinitionBuilder.writeBeanDefinitionBuilders(classWriterOutputVisitor, beanElementBuilders);
                    } catch (IOException e) {
                        // raise a compile error
                        String message = e.getMessage();
                        error("Unexpected error: %s", message != null ? message : e.getClass().getSimpleName());
                    }
                }
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

    private void processBeanDefinitions(BeanDefinitionVisitor beanDefinitionWriter) {
        try {
            beanDefinitionWriter.visitBeanDefinitionEnd();
            if (beanDefinitionWriter.isEnabled()) {
                beanDefinitionWriter.accept(classWriterOutputVisitor);
                String beanTypeName = beanDefinitionWriter.getBeanTypeName();
                BeanDefinitionReferenceWriter beanDefinitionReferenceWriter =
                        new BeanDefinitionReferenceWriter(beanTypeName, beanDefinitionWriter);
                beanDefinitionReferenceWriter.setRequiresMethodProcessing(beanDefinitionWriter.requiresMethodProcessing());

                String className = beanDefinitionReferenceWriter.getBeanDefinitionQualifiedClassName();
                processed.add(className);
                beanDefinitionReferenceWriter.setContextScope(
                        beanDefinitionWriter.getAnnotationMetadata().hasDeclaredAnnotation(Context.class));

                beanDefinitionReferenceWriter.accept(classWriterOutputVisitor);
            }
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

    private AnnotationMetadata addPropertyMetadata(io.micronaut.inject.ast.Element targetElement, PropertyMetadata propertyMetadata) {
        return targetElement.annotate(Property.class, (builder) -> builder.member("name", propertyMetadata.getPath())).getAnnotationMetadata();
    }

    private boolean isDeclaredBeanInMetadata(AnnotationMetadata concreteClassMetadata) {
        return concreteClassMetadata.hasDeclaredStereotype(Bean.class) ||
                concreteClassMetadata.hasStereotype(AnnotationUtil.SCOPE) ||
                concreteClassMetadata.hasStereotype(DefaultScope.class);
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
        private final JavaElementFactory elementFactory;
        private final ClassElement concreteClassElement;
        private final MethodElement constructorElement;
        private final AnnotationMetadata constructorAnnotationMetadata;
        private ConfigurationMetadata configurationMetadata;
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
            this.elementFactory = javaVisitorContext.getElementFactory();
            this.concreteClassElement = elementFactory.newClassElement(concreteClass, concreteClassMetadata);
            this.beanDefinitionWriters = new LinkedHashMap<>();
            this.isFactoryType = concreteClassMetadata.hasStereotype(Factory.class);
            this.isConfigurationPropertiesType = concreteClassMetadata.hasDeclaredStereotype(ConfigurationReader.class) || concreteClassMetadata.hasDeclaredStereotype(EachProperty.class);
            this.isAopProxyType = !concreteClassElement.isAbstract() && !concreteClassElement.isAssignable(Interceptor.class) && hasAroundStereotype(concreteClassMetadata);
            this.aopSettings = isAopProxyType ? concreteClassMetadata.getValues(AROUND_TYPE, Boolean.class) : OptionalValues.empty();
            this.constructorElement = concreteClassElement.getPrimaryConstructor().orElse(null);
            MethodElement constructorElement = this.constructorElement;
            if (constructorElement != null) {
                postponeIfParametersContainErrors((ExecutableElement) constructorElement.getNativeType());
            }
            this.constructorAnnotationMetadata = constructorElement != null ? constructorElement.getAnnotationMetadata() : AnnotationMetadata.EMPTY_METADATA;
            this.isExecutableType = isAopProxyType || concreteClassMetadata.hasStereotype(Executable.class);
            boolean hasQualifier = concreteClassMetadata.hasStereotype(AnnotationUtil.QUALIFIER) && !concreteClassElement.isAbstract();
            this.isDeclaredBean = isDeclaredBean(constructorElement, hasQualifier);
        }

        private void postponeIfParametersContainErrors(ExecutableElement executableElement) {
            if (executableElement != null && !processingOver) {
                List<? extends VariableElement> parameters = executableElement.getParameters();
                for (VariableElement parameter : parameters) {
                    TypeMirror typeMirror = parameter.asType();
                    if ((typeMirror.getKind() == TypeKind.ERROR)) {
                        throw new PostponeToNextRoundException();
                    }
                }
            }
        }

        private boolean isDeclaredBean(@Nullable MethodElement constructor, boolean hasQualifier) {
            final AnnotationMetadata concreteClassMetadata = this.concreteClassMetadata;
            return isExecutableType ||
                    isDeclaredBeanInMetadata(concreteClassMetadata) ||
                    (constructor != null && constructor.hasStereotype(AnnotationUtil.INJECT)) || hasQualifier;
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
                this.configurationMetadata = metadataBuilder.visitProperties(
                        concreteClass,
                        concreteClassElement.getDocumentation().orElse(null)
                );
                if (isInterface) {
                    typeAnnotationMetadata = concreteClassElement.annotate(
                            ANN_CONFIGURATION_ADVICE
                    ).getAnnotationMetadata();
                    this.currentClassMetadata = typeAnnotationMetadata;
                }
            }
            // don't process inner class unless this is the visitor for it
            final Name qualifiedName = concreteClass.getQualifiedName();
            boolean isTypeConcreteClass = qualifiedName.equals(classElementQualifiedName);

            if (typeAnnotationMetadata.hasStereotype(INTRODUCTION_TYPE) && isTypeConcreteClass) {
                AopProxyWriter aopProxyWriter = createIntroductionAdviceWriter(concreteClassElement);

                if (constructorElement != null) {
                    aopProxyWriter.visitBeanDefinitionConstructor(
                            constructorElement,
                            concreteClassElement.isPrivate(),
                            javaVisitorContext
                    );
                } else {
                    aopProxyWriter.visitDefaultConstructor(concreteClassMetadata, javaVisitorContext);
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
                if (!JavaModelUtils.isClass(enclosingElement) || isTypeConcreteClass) {
                    if (isTypeConcreteClass) {
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
                                io.micronaut.core.annotation.AnnotationValue<?>[] interceptorTypes =
                                        InterceptedMethodUtil.resolveInterceptorBinding(concreteClassMetadata, InterceptorKind.AROUND);
                                resolveAopProxyWriter(
                                        beanDefinitionWriter,
                                        aopSettings,
                                        false,
                                        this.constructorElement,
                                        interceptorTypes
                                );
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

                                        AnnotationMetadata fieldAnnotationMetadata = annotationUtils.getDeclaredAnnotationMetadata(field);
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
                        constructorAnnotationMetadata
                );
                if (proxyWriter != null) {
                    if (constructorElement != null) {
                        proxyWriter.visitBeanDefinitionConstructor(
                                constructorElement,
                                constructorElement.isPrivate(),
                                javaVisitorContext
                        );
                    } else {
                        proxyWriter.visitDefaultConstructor(
                                annotationMetadata,
                                javaVisitorContext
                        );
                    }
                }

                if (constructorElement != null) {
                    beanDefinitionWriter.visitBeanDefinitionConstructor(
                            constructorElement,
                            constructorElement.isPrivate(),
                            javaVisitorContext
                    );
                } else {
                    beanDefinitionWriter.visitDefaultConstructor(annotationMetadata, javaVisitorContext);
                }
            }
            return beanDefinitionWriter;
        }

        private void visitIntroductionAdviceInterface(TypeElement classElement, AnnotationMetadata typeAnnotationMetadata, AopProxyWriter aopProxyWriter) {
            ClassElement introductionType = elementFactory.newClassElement(classElement, typeAnnotationMetadata);
            final AnnotationMetadata resolvedTypeMetadata = annotationUtils.getAnnotationMetadata(classElement);
            final boolean isAopProxyType = hasDeclaredAroundAdvice(resolvedTypeMetadata);
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
                            || isAopProxyType
                            || hasMethodLevelAdvice(executableElement);
                }

                private boolean hasMethodLevelAdvice(ExecutableElement executableElement) {
                    final AnnotationMetadata annotationMetadata = annotationUtils.getAnnotationMetadata(executableElement);
                    return hasDeclaredAroundAdvice(annotationMetadata);
                }

                @Override
                protected void accept(DeclaredType type, Element element, AopProxyWriter aopProxyWriter) {
                    ExecutableElement method = (ExecutableElement) element;
                    Element declaredTypeElement = type.asElement();
                    if (declaredTypeElement instanceof TypeElement) {
                        ClassElement declaringClassElement = elementFactory.newClassElement(
                                (TypeElement) declaredTypeElement,
                                concreteClassMetadata
                        );
                        if (!classElement.equals(declaredTypeElement)) {
                            aopProxyWriter.addOriginatingElement(
                                    declaringClassElement
                            );
                        }

                        TypeElement owningType = modelUtils.classElementFor(method);
                        if (owningType == null) {
                            throw new IllegalStateException("Owning type cannot be null");
                        }
                        ClassElement owningTypeElement = elementFactory.newClassElement(owningType, typeAnnotationMetadata);

                        AnnotationMetadata annotationMetadata;

                        if (annotationUtils.isAnnotated(introductionType.getName(), method) || JavaAnnotationMetadataBuilder.hasAnnotation(method, Override.class)) {
                            annotationMetadata = annotationUtils.newAnnotationBuilder().buildForParent(introductionType.getName(), classElement, method);
                            annotationMetadata = new AnnotationMetadataHierarchy(typeAnnotationMetadata, annotationMetadata);
                        } else {
                            annotationMetadata = new AnnotationMetadataReference(
                                    aopProxyWriter.getBeanDefinitionName() + BeanDefinitionReferenceWriter.REF_SUFFIX,
                                    typeAnnotationMetadata
                            );
                        }

                        MethodElement javaMethodElement = elementFactory.newMethodElement(
                                introductionType,
                                method,
                                annotationMetadata
                        );
                        String methodName = javaMethodElement.getName();

                        if (!annotationMetadata.hasStereotype(ANN_VALIDATED) &&
                                isDeclaredBean &&
                                Arrays.stream(javaMethodElement.getParameters()).anyMatch(IS_CONSTRAINT)) {
                            annotationMetadata = javaMethodElement.annotate(ANN_VALIDATED).getAnnotationMetadata();
                        }

                        if (isConfigProps) {
                            if (javaMethodElement.isAbstract()) {

                                if (!aopProxyWriter.isValidated()) {
                                    aopProxyWriter.setValidated(IS_CONSTRAINT.test(annotationMetadata));
                                }

                                if (!NameUtils.isGetterName(methodName)) {
                                    error(classElement, "Only getter methods are allowed on @ConfigurationProperties interfaces: " + method);
                                    return;
                                }

                                if (javaMethodElement.hasParameters()) {
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
                                    if (!javaMethodElement.getReturnType().isPrimitive() && javaMethodElement.getReturnType().hasStereotype(AnnotationUtil.SCOPE)) {
                                        annBuilder.member("bean", true);
                                    }
                                    if (typeAnnotationMetadata.hasStereotype(EachProperty.class)) {
                                        annBuilder.member("iterable", true);
                                    }

                                }).getAnnotationMetadata();
                            }
                        }

                        if (annotationMetadata.hasStereotype(AROUND_TYPE) || annotationMetadata.hasStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS)) {
                            io.micronaut.core.annotation.AnnotationValue<?>[] interceptorTypes = InterceptedMethodUtil.resolveInterceptorBinding(annotationMetadata, InterceptorKind.AROUND);
                            aopProxyWriter.visitInterceptorBinding(interceptorTypes);
                        }

                        if (javaMethodElement.isAbstract()) {
                            aopProxyWriter.visitIntroductionMethod(
                                    owningTypeElement,
                                    javaMethodElement
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
                                    javaMethodElement
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

            postponeIfParametersContainErrors(method);

            final AnnotationMetadata annotationMetadata = annotationUtils.getAnnotationMetadata(method);

            AnnotationMetadata methodAnnotationMetadata = getMetadataHierarchy(annotationMetadata);

            TypeKind returnKind = method.getReturnType().getKind();
            if ((returnKind == TypeKind.ERROR) && !processingOver) {
                throw new PostponeToNextRoundException();
            }

            // handle @Bean annotation for @Factory class
            JavaMethodElement javaMethodElement = elementFactory.newMethodElement(concreteClassElement, method, methodAnnotationMetadata);
            if (isFactoryType && javaMethodElement.hasDeclaredStereotype(Bean.class.getName(), AnnotationUtil.SCOPE) && !javaMethodElement.getReturnType().isPrimitive()) {
                if (!modelUtils.overridingOrHidingMethod(method, concreteClass, true).isPresent()) {
                    visitBeanFactoryElement(method);
                }
                return null;
            }


            boolean injected = methodAnnotationMetadata.hasDeclaredStereotype(AnnotationUtil.INJECT);
            boolean postConstruct = methodAnnotationMetadata.hasDeclaredAnnotation(AnnotationUtil.POST_CONSTRUCT);
            boolean preDestroy = methodAnnotationMetadata.hasDeclaredAnnotation(AnnotationUtil.PRE_DESTROY);
            if (injected || postConstruct || preDestroy || methodAnnotationMetadata.hasDeclaredStereotype(ConfigurationInject.class)) {
                if (isDeclaredBean) {
                    visitAnnotatedMethod(javaMethodElement, method, o);
                } else if (injected) {
                    // DEPRECATE: This behaviour should be deprecated in 2.0
                    visitAnnotatedMethod(javaMethodElement, method, o);
                }
                return null;
            }

            final boolean isAbstract = javaMethodElement.isAbstract();
            final boolean isPrivate = javaMethodElement.isPrivate();
            final boolean isStatic = javaMethodElement.isStatic();
            final boolean isPublic = javaMethodElement.isPublic();
            final boolean isInternal = methodAnnotationMetadata.hasAnnotation(Internal.class);

            boolean hasInvalidModifiers = isAbstract || isStatic || isPrivate || isInternal;

            Set<Modifier> modifiers = method.getModifiers();
            boolean isExecutable =
                            isExecutableThroughType(method.getEnclosingElement(), methodAnnotationMetadata, annotationMetadata, modifiers, isPublic) ||
                            hasAroundStereotype(annotationMetadata);

            boolean hasConstraints = false;
            if (isDeclaredBean && !methodAnnotationMetadata.hasStereotype(ANN_VALIDATED) &&
                    Arrays.stream(javaMethodElement.getParameters())
                            .anyMatch(p -> p.hasStereotype(ANN_CONSTRAINT) || p.hasStereotype(ANN_VALID))) {
                hasConstraints = true;
                methodAnnotationMetadata = javaMethodElement.annotate(ANN_VALIDATED);
            }

            if (isDeclaredBean && isExecutable) {
                if (hasInvalidModifiers) {
                    if (isPrivate) {
                        error(method, "Method annotated as executable but is declared private. Change the method to be non-private in order for AOP advice to be applied.");
                    }
                } else {
                    visitExecutableMethod(javaMethodElement, method, methodAnnotationMetadata);
                }
            } else if (isConfigurationPropertiesType && !modelUtils.isPrivate(method) && !modelUtils.isStatic(method)) {
                String methodName = javaMethodElement.getSimpleName();
                if (NameUtils.isSetterName(methodName) && javaMethodElement.getParameters().length == 1) {
                    visitConfigurationPropertySetter(method);
                } else if (NameUtils.isGetterName(methodName)) {
                    BeanDefinitionVisitor writer = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName());
                    if (!writer.isValidated()) {
                        writer.setValidated(IS_CONSTRAINT.test(methodAnnotationMetadata));
                    }
                }
            } else if (hasConstraints) {
                if (hasInvalidModifiers) {
                    if (isPrivate) {
                        error(method, "Method annotated with constraints but is declared private. Change the method to be non-private in order for AOP advice to be applied.");
                    }
                } else if (isPublic) {
                    visitExecutableMethod(javaMethodElement, method, methodAnnotationMetadata);
                }
            }

            return null;
        }

        @NonNull
        private AnnotationMetadata getMetadataHierarchy(AnnotationMetadata annotationMetadata) {
            AnnotationMetadata methodAnnotationMetadata;

            if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
                methodAnnotationMetadata = annotationMetadata;
            } else {

                methodAnnotationMetadata = new AnnotationMetadataHierarchy(
                        concreteClassMetadata,
                        annotationMetadata
                );
            }
            return methodAnnotationMetadata;
        }

        private boolean hasAroundStereotype(AnnotationMetadata annotationMetadata) {
            if (annotationMetadata.hasStereotype(AROUND_TYPE)) {
                return true;
            } else if (annotationMetadata.hasStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS)) {
                return annotationMetadata.getAnnotationValuesByType(InterceptorBinding.class)
                        .stream().anyMatch(av ->
                                av.enumValue("kind", InterceptorKind.class).orElse(InterceptorKind.AROUND) == InterceptorKind.AROUND
                        );
            }

            return false;
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
                AnnotationMetadata methodAnnotationMetadata = annotationUtils.getDeclaredAnnotationMetadata(method);
                ClassElement javaClassElement = elementFactory.newClassElement(declaringClass, methodAnnotationMetadata);
                MethodElement javaMethodElement = elementFactory.newMethodElement(javaClassElement, method, methodAnnotationMetadata);
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
                    PropertyMetadata propertyMetadata = metadataBuilder.visitProperty(
                            concreteClass,
                            declaringClass,
                            parameterElement.getGenericType().getCanonicalName(),
                            propertyName,
                            docComment,
                            null
                    );

                    addPropertyMetadata(javaMethodElement, propertyMetadata);

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
                            javaMethodElement,
                            requiresReflection,
                            true
                    );
                }
            }

        }

        /**
         * @param element The element
         */
        void visitBeanFactoryElement(Element element) {
            final TypeMirror producedType;
            if (element instanceof ExecutableElement) {
                producedType = ((ExecutableElement) element).getReturnType();
            } else {
                producedType = element.asType();
            }

            TypeElement producedTypeElement = modelUtils.classElementFor(typeUtils.asElement(producedType));
            TypeElement factoryTypeElement = modelUtils.classElementFor(element);

            if (producedType.getKind().isPrimitive()) {
                error(element, "Produced type from a bean factory cannot be primitive");
                return;
            }
            if (producedTypeElement == null || factoryTypeElement == null) {
                return;
            }
            String producedTypeName = producedTypeElement.getQualifiedName().toString();

            ClassElement declaringClassElement = elementFactory.newClassElement(
                    factoryTypeElement,
                    concreteClassMetadata
            );

            AnnotationMetadata methodAnnotationMetadata = annotationUtils.newAnnotationBuilder().buildForParent(
                    producedTypeElement,
                    element
            );

            io.micronaut.inject.ast.Element beanProducingElement;
            ClassElement producedClassElement;
            if (element instanceof ExecutableElement) {
                final ExecutableElement executableElement = (ExecutableElement) element;
                final JavaMethodElement methodElement = elementFactory.newMethodElement(
                        declaringClassElement,
                        executableElement,
                        methodAnnotationMetadata
                );
                if (isFactoryType && annotationUtils.hasStereotype(concreteClass, AROUND_TYPE)) {
                    final JavaMethodElement aopMethod = elementFactory.newMethodElement(
                            declaringClassElement,
                            executableElement,
                            getMetadataHierarchy(methodAnnotationMetadata)
                    );
                    visitExecutableMethod(aopMethod, executableElement, methodAnnotationMetadata);
                }

                beanProducingElement = methodElement;
                producedClassElement = methodElement.getGenericReturnType();
            } else {
                final FieldElement fieldElement = elementFactory.newFieldElement(
                        declaringClassElement,
                        (VariableElement) element,
                        methodAnnotationMetadata
                );
                beanProducingElement = fieldElement;
                producedClassElement = fieldElement.getGenericField();
            }


            BeanDefinitionWriter beanMethodWriter = createFactoryBeanMethodWriterFor(element, producedTypeElement);
            Map<String, Map<String, ClassElement>> allTypeArguments = producedClassElement.getAllTypeArguments();
            beanMethodWriter.visitTypeArguments(allTypeArguments);

            beanDefinitionWriters.put(new DynamicName(beanProducingElement.getDescription(false)), beanMethodWriter);
            if (beanProducingElement instanceof MethodElement) {
                beanMethodWriter.visitBeanFactoryMethod(
                        concreteClassElement,
                        (MethodElement) beanProducingElement
                );
            } else {
                beanMethodWriter.visitBeanFactoryField(
                        concreteClassElement,
                        (FieldElement) beanProducingElement
                );
            }


            if (methodAnnotationMetadata.hasStereotype(AROUND_TYPE) && !modelUtils.isAbstract(concreteClass)) {
                io.micronaut.core.annotation.AnnotationValue<?>[] interceptorTypes = InterceptedMethodUtil.resolveInterceptorBinding(methodAnnotationMetadata, InterceptorKind.AROUND);

                if (producedClassElement.isFinal()) {
                    final Element nativeElement = (Element) producedClassElement.getNativeType();
                    error(nativeElement, "Cannot apply AOP advice to final class. Class must be made non-final to support proxying: " + nativeElement);
                    return;
                }

                MethodElement constructor = producedClassElement.getPrimaryConstructor().orElse(null);
                if (!producedClassElement.isInterface() && constructor != null && constructor.getParameters().length > 0) {
                    final Element nativeElement = (Element) constructor.getNativeType();
                    final String proxyTargetMode = methodAnnotationMetadata.stringValue(AROUND_TYPE, "proxyTargetMode")
                            .orElseGet(() -> {
                                // temporary workaround until micronaut-test can be upgraded to 3.0
                                if (methodAnnotationMetadata.hasAnnotation("io.micronaut.test.annotation.MockBean")) {
                                    return "WARN";
                                } else {
                                    return "ERROR";
                                }
                            });
                    switch (proxyTargetMode) {
                        case "ALLOW":
                            allowProxyConstruction(constructor);
                            break;
                        case "WARN":
                            allowProxyConstruction(constructor);
                            warning(element, "The produced type of a @Factory method has constructor arguments and is proxied. This can lead to unexpected behaviour. See the javadoc for Around.ProxyTargetConstructorMode for more information: " + element);
                            break;
                        case "ERROR":
                        default:
                            error(element, "The produced type from a factory which has AOP proxy advice specified must define an accessible no arguments constructor. Proxying types with constructor arguments can lead to unexpected behaviour. See the javadoc for for Around.ProxyTargetConstructorMode for more information and possible solutions: " + element);
                        return;
                    }

                }
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
                        constructor,
                        interceptorTypes
                );
                proxyWriter.visitTypeArguments(allTypeArguments);

                producedType.accept(new PublicMethodVisitor<Object, AopProxyWriter>(javaVisitorContext) {
                    @Override
                    protected void accept(DeclaredType type, Element element, AopProxyWriter aopProxyWriter) {
                        ExecutableElement method = (ExecutableElement) element;
                        TypeElement owningType = modelUtils.classElementFor(method);
                        ClassElement declaringClassElement = elementFactory.newClassElement(
                                owningType,
                                concreteClassMetadata
                        );
                        AnnotationMetadata annotationMetadata;
                        // if the method is annotated we build metadata for the method
                        if (annotationUtils.isAnnotated(producedTypeName, method)) {
                            annotationMetadata = annotationUtils.getAnnotationMetadata(element, method);
                        } else {
                            // otherwise we setup a reference to the parent metadata (essentially the annotations declared on the bean factory method)
                            annotationMetadata = new AnnotationMetadataReference(
                                    beanMethodWriter.getBeanDefinitionName() + BeanDefinitionReferenceWriter.REF_SUFFIX,
                                    methodAnnotationMetadata
                            );
                        }

                        MethodElement advisedMethodElement = elementFactory.newMethodElement(
                                declaringClassElement,
                                method,
                                annotationMetadata
                        );

                        aopProxyWriter.visitAroundMethod(
                                declaringClassElement,
                                advisedMethodElement
                        );
                    }
                }, proxyWriter);
            } else if (methodAnnotationMetadata.hasStereotype(Executable.class)) {
                DeclaredType dt = (DeclaredType) producedType;
                Map<String, Map<String, TypeMirror>> finalBeanTypeArgumentsMirrors = genericUtils.buildGenericTypeArgumentElementInfo(dt.asElement(), dt, Collections.emptyMap());
                producedType.accept(new PublicMethodVisitor<Object, BeanDefinitionWriter>(javaVisitorContext) {
                    @Override
                    protected void accept(DeclaredType type, Element element, BeanDefinitionWriter beanWriter) {
                        ExecutableElement method = (ExecutableElement) element;
                        TypeElement owningType = modelUtils.classElementFor(method);
                        if (owningType == null) {
                            throw new IllegalStateException("Owning type cannot be null");
                        }
                        AnnotationMetadata annotationMetadata = new AnnotationMetadataReference(
                                beanMethodWriter.getBeanDefinitionName() + BeanDefinitionReferenceWriter.REF_SUFFIX,
                                methodAnnotationMetadata
                        );

                        ClassElement declaringClassElement = elementFactory.newClassElement(producedTypeElement, concreteClassMetadata);
                        MethodElement executableMethod = elementFactory.newMethodElement(
                                declaringClassElement,
                                method,
                                annotationMetadata,
                                finalBeanTypeArgumentsMirrors
                        );

                        beanMethodWriter.visitExecutableMethod(
                                declaringClassElement,
                                executableMethod,
                                javaVisitorContext
                        );

                    }
                }, beanMethodWriter);
            }

            if (methodAnnotationMetadata.isPresent(Bean.class, "preDestroy")) {
                Optional<String> preDestroyMethod = methodAnnotationMetadata.getValue(Bean.class, "preDestroy", String.class);
                preDestroyMethod
                        .ifPresent(destroyMethodName -> {
                            if (StringUtils.isNotEmpty(destroyMethodName)) {
                                TypeElement destroyMethodDeclaringClass = (TypeElement) typeUtils.asElement(producedType);
                                ClassElement destroyMethodDeclaringElement = elementFactory.newClassElement(destroyMethodDeclaringClass, AnnotationMetadata.EMPTY_METADATA);
                                final Optional<MethodElement> destroyMethod = destroyMethodDeclaringElement.getEnclosedElement(
                                        ElementQuery.ALL_METHODS
                                                .onlyAccessible(concreteClassElement)
                                                .onlyInstance()
                                                .named((name) -> name.equals(destroyMethodName))
                                                .filter((e) -> !e.hasParameters())
                                );
                                if (destroyMethod.isPresent()) {
                                    MethodElement destroyMethodElement = destroyMethod.get();
                                    beanMethodWriter.visitPreDestroyMethod(
                                            destroyMethodDeclaringElement,
                                            destroyMethodElement,
                                            false,
                                            javaVisitorContext
                                    );
                                } else {
                                    error(element, "@Bean method defines a preDestroy method that does not exist or is not public: " + destroyMethodName);
                                }

                            }
                        });
            }
        }

        private void allowProxyConstruction(MethodElement constructor) {
            final ParameterElement[] parameters = constructor.getParameters();
            for (ParameterElement parameter : parameters) {
                if (parameter.isPrimitive() && !parameter.isArray()) {
                    final String name = parameter.getType().getName();
                    if ("boolean".equals(name)) {
                        parameter.annotate(Value.class, (builder) -> builder.value(false));
                    } else {
                        parameter.annotate(Value.class, (builder) -> builder.value(0));
                    }
                } else {
                    // allow null
                    parameter.annotate(AnnotationUtil.NULLABLE);
                    parameter.removeAnnotation(AnnotationUtil.NON_NULL);
                }
            }
        }

        /**
         * @param javaMethodElement        The method element
         * @param method                   The {@link ExecutableElement}
         * @param methodAnnotationMetadata The {@link AnnotationMetadata}
         */
        void visitExecutableMethod(MethodElement javaMethodElement, ExecutableElement method, AnnotationMetadata methodAnnotationMetadata) {
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

            ClassElement declaringClassElement = elementFactory.newClassElement(declaringClass, concreteClassMetadata);
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
                final boolean isConcrete = !concreteClassElement.isAbstract();
                final boolean isPublic = javaMethodElement.isPublic() || javaMethodElement.isPackagePrivate();
                if ((isAopProxyType && isPublic) ||
                        (!isAopProxyType && methodAnnotationMetadata.hasStereotype(AROUND_TYPE)) ||
                        (hasDeclaredAroundAdvice(methodAnnotationMetadata) && isConcrete)) {

                    io.micronaut.core.annotation.AnnotationValue<?>[] interceptorTypes = InterceptedMethodUtil.resolveInterceptorBinding(methodAnnotationMetadata, InterceptorKind.AROUND);

                    OptionalValues<Boolean> settings = methodAnnotationMetadata.getValues(AROUND_TYPE, Boolean.class);
                    AopProxyWriter aopProxyWriter = resolveAopProxyWriter(
                            beanWriter,
                            settings,
                            false,
                            this.constructorElement,
                            interceptorTypes
                    );

                    aopProxyWriter.visitInterceptorBinding(interceptorTypes);

                    if (javaMethodElement.isFinal()) {
                        if (hasDeclaredAroundAdvice(methodAnnotationMetadata)) {
                            error(method, "Method defines AOP advice but is declared final. Change the method to be non-final in order for AOP advice to be applied.");
                        } else {
                            if (isAopProxyType && isPublic && !declaringClass.equals(concreteClass)) {
                                addOriginatingElementIfNecessary(beanWriter, declaringClass);
                                beanWriter.visitExecutableMethod(
                                        declaringClassElement,
                                        javaMethodElement,
                                        javaVisitorContext
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
                                javaMethodElement
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
                        javaVisitorContext
                );
            }

        }

        private boolean hasDeclaredAroundAdvice(AnnotationMetadata annotationMetadata) {
            if (annotationMetadata.hasDeclaredStereotype(AROUND_TYPE)) {
                return true;
            } else if (annotationMetadata.hasDeclaredStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS)) {
                return annotationMetadata.getDeclaredAnnotationValuesByType(InterceptorBinding.class)
                        .stream().anyMatch(av ->
                                av.enumValue("kind", InterceptorKind.class).orElse(InterceptorKind.AROUND) == InterceptorKind.AROUND
                        );
            }

            return false;
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
                    ClassElement typeToImplementElement = elementFactory.newClassElement(
                            typeElement,
                            annotationUtils.getAnnotationMetadata(typeElement)
                    );
                    DeclaredType typeToImplement = (DeclaredType) typeElement.asType();
                    String packageName = concreteClassElement.getPackageName();
                    String declaringClassSimpleName = concreteClassElement.getSimpleName();
                    String beanClassName = generateAdaptedMethodClassName(sourceMethod, typeElement, declaringClassSimpleName);

                    MethodElement sourceMethodElement = elementFactory.newMethodElement(
                            concreteClassElement,
                            sourceMethod,
                            methodAnnotationMetadata
                    );

                    AopProxyWriter aopProxyWriter = new AopProxyWriter(
                            packageName,
                            beanClassName,
                            true,
                            false,
                            sourceMethodElement,
                            new AnnotationMetadataHierarchy(concreteClassMetadata, methodAnnotationMetadata),
                            new ClassElement[]{typeToImplementElement},
                            javaVisitorContext,
                            metadataBuilder,
                            null
                    );

                    aopProxyWriter.visitDefaultConstructor(methodAnnotationMetadata, javaVisitorContext);
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
                            MethodElement targetMethodElement = elementFactory.newMethodElement(
                                    typeToImplementElement,
                                    targetMethod,
                                    annotationUtils.getAnnotationMetadata(targetMethod)
                            );

                            ParameterElement[] sourceParams = sourceMethodElement.getParameters();
                            ParameterElement[] targetParams = targetMethodElement.getParameters();
                            List<? extends VariableElement> targetParameters = targetMethod.getParameters();

                            int paramLen = targetParameters.size();
                            if (paramLen == sourceParams.length) {

                                if (sourceMethodElement.isSuspend()) {
                                    error(sourceMethod, "Cannot adapt method [" + sourceMethod + "] to target method [" + targetMethod + "]. Kotlin suspend method not supported here.");
                                    return;
                                }

                                Map<String, ClassElement> genericTypes = new LinkedHashMap<>(paramLen);
                                for (int i = 0; i < paramLen; i++) {
                                    TypeMirror targetMirror = targetParameters.get(i).asType();
                                    ClassElement targetType = targetParams[i].getGenericType();
                                    ClassElement sourceType = sourceParams[i].getGenericType();

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
                                    Map<String, Map<String, ClassElement>> typeData = Collections.singletonMap(
                                            typeToImplementElement.getName(),
                                            genericTypes
                                    );

                                    aopProxyWriter.visitTypeArguments(
                                            typeData
                                    );
                                }

                                ClassElement declaringClassElement = elementFactory.newClassElement(
                                        typeElement,
                                        finalMethodAnnotationMetadata
                                );

                                AnnotationClassValue<?>[] adaptedArgumentTypes = new AnnotationClassValue[paramLen];
                                for (int i = 0; i < adaptedArgumentTypes.length; i++) {
                                    ParameterElement parameterElement = sourceParams[i];
                                    final ClassElement genericType = parameterElement.getGenericType();
                                    adaptedArgumentTypes[i] = new AnnotationClassValue<>(JavaModelUtils.getClassname(genericType));
                                }

                                MethodElement javaMethodElement = elementFactory.newMethodElement(
                                        concreteClassElement,
                                        targetMethod,
                                        finalMethodAnnotationMetadata
                                );

                                javaMethodElement.annotate(Adapter.class, (builder) -> {
                                    AnnotationClassValue<Object> acv = new AnnotationClassValue<>(concreteClassElement.getName());
                                    builder.member(Adapter.InternalAttributes.ADAPTED_BEAN, acv);
                                    builder.member(Adapter.InternalAttributes.ADAPTED_METHOD, sourceMethodElement.getName());
                                    builder.member(Adapter.InternalAttributes.ADAPTED_ARGUMENT_TYPES, adaptedArgumentTypes);
                                    String qualifier = concreteClassMetadata.stringValue(AnnotationUtil.NAMED).orElse(null);
                                    if (StringUtils.isNotEmpty(qualifier)) {
                                        builder.member(Adapter.InternalAttributes.ADAPTED_QUALIFIER, qualifier);
                                    }
                                });

                                aopProxyWriter.visitAroundMethod(
                                        declaringClassElement,
                                        javaMethodElement
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
                                                     MethodElement constructorElement,
                                                     io.micronaut.core.annotation.AnnotationValue<?>... interceptorBinding) {
            String beanName = beanWriter.getBeanDefinitionName();
            Name proxyKey = createProxyKey(beanName);
            BeanDefinitionVisitor aopWriter = beanWriter instanceof AopProxyWriter ? beanWriter : beanDefinitionWriters.get(proxyKey);

            AopProxyWriter aopProxyWriter;
            if (aopWriter == null) {
                aopProxyWriter
                        = new AopProxyWriter(
                        (BeanDefinitionWriter) beanWriter,
                        aopSettings,
                        metadataBuilder,
                        javaVisitorContext,
                        interceptorBinding);

                if (constructorElement != null) {
                    aopProxyWriter.visitBeanDefinitionConstructor(
                            constructorElement,
                            constructorElement.isPrivate(),
                            javaVisitorContext
                    );
                } else {
                    aopProxyWriter.visitDefaultConstructor(AnnotationMetadata.EMPTY_METADATA, javaVisitorContext);
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
         * @param javaMethodElement The java method element
         * @param method            The {@link ExecutableElement}
         * @param o                 An object
         */
        void visitAnnotatedMethod(MethodElement javaMethodElement, ExecutableElement method, Object o) {
            ClassElement declaringClass = javaMethodElement.getDeclaringType();
            boolean isParent = !declaringClass.getName().equals(this.concreteClassElement.getName());
            ExecutableElement overridingMethod = modelUtils.overridingOrHidingMethod(method, this.concreteClass, false).orElse(method);
            TypeElement overridingClass = modelUtils.classElementFor(overridingMethod);
            boolean overridden = isParent && overridingClass != null && !overridingClass.getQualifiedName().toString().equals(declaringClass.getName());

            boolean isPackagePrivate = javaMethodElement.isPackagePrivate();
            boolean isPrivate = javaMethodElement.isPrivate();
            if (overridden && !(isPrivate || isPackagePrivate)) {
                // bail out if the method has been overridden, since it will have already been handled
                return;
            }

            String packageOfOverridingClass = elementUtils.getPackageOf(overridingMethod).getQualifiedName().toString();
            String packageOfDeclaringClass = declaringClass.getPackageName();
            boolean isPackagePrivateAndPackagesDiffer = overridden && isPackagePrivate &&
                    !packageOfOverridingClass.equals(packageOfDeclaringClass);
            boolean requiresReflection = isPrivate || isPackagePrivateAndPackagesDiffer;
            boolean overriddenInjected = overridden && annotationUtils.getAnnotationMetadata(overridingMethod).hasDeclaredStereotype(AnnotationUtil.INJECT);

            if (isParent && isPackagePrivate && !isPackagePrivateAndPackagesDiffer && overriddenInjected) {
                // bail out if the method has been overridden by another method annotated with @Inject
                return;
            }
            if (isParent && overridden && !overriddenInjected && !isPackagePrivateAndPackagesDiffer && !isPrivate) {
                // bail out if the overridden method is package private and in the same package
                // and is not annotated with @Inject
                return;
            }
            if (!requiresReflection && modelUtils.isInheritedAndNotPublic(this.concreteClassElement, declaringClass, javaMethodElement)) {
                requiresReflection = true;
            }

            if (javaMethodElement.hasDeclaredAnnotation(AnnotationUtil.POST_CONSTRUCT)) {
                BeanDefinitionVisitor writer = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName());
                addOriginatingElementIfNecessary(writer, declaringClass);
                writer.visitPostConstructMethod(
                        declaringClass,
                        javaMethodElement,
                        requiresReflection,
                        javaVisitorContext
                );
            } else if (javaMethodElement.hasDeclaredAnnotation(AnnotationUtil.PRE_DESTROY)) {
                BeanDefinitionVisitor writer = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName());
                addOriginatingElementIfNecessary(writer, declaringClass);
                writer.visitPreDestroyMethod(
                        declaringClass,
                        javaMethodElement,
                        requiresReflection,
                        javaVisitorContext
                );
            } else if (javaMethodElement.hasDeclaredStereotype(AnnotationUtil.INJECT) ||
                    javaMethodElement.hasDeclaredStereotype(ConfigurationInject.class)) {
                BeanDefinitionVisitor writer = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName());
                addOriginatingElementIfNecessary(writer, declaringClass);
                writer.visitMethodInjectionPoint(
                        declaringClass,
                        javaMethodElement,
                        requiresReflection,
                        javaVisitorContext
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
                io.micronaut.core.annotation.AnnotationValue<?>[] interceptorTypes =
                        InterceptedMethodUtil.resolveInterceptorBinding(concreteClassMetadata, InterceptorKind.AROUND);
                return resolveAopProxyWriter(
                        writer,
                        aopSettings,
                        isFactoryType,
                        constructorElement,
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
                AnnotationMetadata fieldAnnotationMetadata = annotationUtils.getAnnotationMetadata(variable);
                if (isFactoryType && fieldAnnotationMetadata.hasDeclaredStereotype(Bean.class)) {
                    error(variable, "Beans produced from fields cannot be static");
                }
                return null;
            } else if (modelUtils.isFinal(variable)) {
                AnnotationMetadata fieldAnnotationMetadata = annotationUtils.getAnnotationMetadata(variable);
                if (isFactoryType && fieldAnnotationMetadata.hasDeclaredStereotype(Bean.class)) {
                    // field factory for bean
                    if (modelUtils.isPrivate(variable) || modelUtils.isProtected(variable)) {
                        error(variable, "Beans produced from fields cannot be private or protected");
                    } else {
                        visitBeanFactoryElement(variable);
                    }
                } else {
                    boolean isConfigBuilder = fieldAnnotationMetadata.hasStereotype(ConfigurationBuilder.class);
                    if (isConfigBuilder) {
                        visitConfigurationProperty(variable, fieldAnnotationMetadata);
                    }
                }
                return null;
            }

            AnnotationMetadata fieldAnnotationMetadata = annotationUtils.getAnnotationMetadata(variable);
            boolean isInjected = fieldAnnotationMetadata.hasStereotype(AnnotationUtil.INJECT) || (fieldAnnotationMetadata.hasDeclaredStereotype(AnnotationUtil.QUALIFIER) && !fieldAnnotationMetadata.hasDeclaredAnnotation(Bean.class));
            boolean isValue = (fieldAnnotationMetadata.hasStereotype(Value.class) || fieldAnnotationMetadata.hasStereotype(Property.class));

            if (isInjected || isValue) {
                BeanDefinitionVisitor writer = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName());
                TypeElement declaringClass = modelUtils.classElementFor(variable);

                if (declaringClass == null) {
                    return null;
                }

                ClassElement declaringClassElement = elementFactory.newClassElement(declaringClass, concreteClassMetadata);
                FieldElement javaFieldElement = elementFactory.newFieldElement(concreteClassElement, variable, fieldAnnotationMetadata);
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
                            javaFieldElement,
                            requiresReflection,
                            isConfigurationPropertiesType
                    );
                } else {
                    writer.visitFieldInjectionPoint(
                            declaringClassElement,
                            javaFieldElement,
                            requiresReflection
                    );
                }
            } else if (isConfigurationPropertiesType) {
                visitConfigurationProperty(variable, fieldAnnotationMetadata);
            } else if (isFactoryType && fieldAnnotationMetadata.hasDeclaredStereotype(Bean.class)) {
                // field factory for bean
                if (modelUtils.isPrivate(variable) || modelUtils.isProtected(variable)) {
                    error(variable, "Beans produced from fields cannot be private or protected");
                } else {
                    visitBeanFactoryElement(variable);
                }
            }
            return null;
        }

        private void addOriginatingElementIfNecessary(BeanDefinitionVisitor writer, TypeElement declaringClass) {
            if (!concreteClass.equals(declaringClass)) {
                writer.addOriginatingElement(elementFactory.newClassElement(declaringClass, currentClassMetadata));
            }
        }

        private void addOriginatingElementIfNecessary(BeanDefinitionVisitor writer, ClassElement declaringClass) {
            if (!concreteClassElement.getName().equals(declaringClass.getName())) {
                writer.addOriginatingElement(declaringClass);
            }
        }

        /**
         * @param field                   The {@link VariableElement}
         * @param fieldAnnotationMetadata The annotation metadata for the field
         * @return Returns null after visiting the configuration properties
         */
        public Object visitConfigurationProperty(VariableElement field, AnnotationMetadata fieldAnnotationMetadata) {
            Optional<ExecutableElement> setterMethod = modelUtils.findSetterMethodFor(field);
            boolean isInjected = fieldAnnotationMetadata.hasStereotype(AnnotationUtil.INJECT);
            boolean isValue = fieldAnnotationMetadata.hasStereotype(Value.class) || fieldAnnotationMetadata.hasStereotype(Property.class);

            boolean isMethodInjected = isInjected || (setterMethod.isPresent() && annotationUtils.hasStereotype(setterMethod.get(), AnnotationUtil.INJECT));
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
                ClassElement declaringClassElement = elementFactory.newClassElement(declaringClass, concreteClassMetadata);
                FieldElement javaFieldElement = elementFactory.newFieldElement(declaringClassElement, field, fieldAnnotationMetadata);
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

                            addPropertyMetadata(javaFieldElement, propertyMetadata);
                            writer.visitFieldValue(
                                    declaringClassElement,
                                    javaFieldElement,
                                    requiresReflection,
                                    isConfigurationPropertiesType
                            );
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
                    MethodElement javaMethodElement = elementFactory.newMethodElement(
                            concreteClassElement,
                            method,
                            AnnotationMetadata.EMPTY_METADATA
                    );
                    if (paramCount < 2) {
                        VariableElement paramType = paramCount == 1 ? params.get(0) : null;

                        ClassElement parameterElement = null;
                        if (paramType != null) {
                            parameterElement = ((TypedElement) elementFactory.newParameterElement(concreteClassElement, paramType, AnnotationMetadata.EMPTY_METADATA)).getGenericType();
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
            ClassElement classElement;
            AnnotationMetadata annotationMetadata;
            if (typeElement == concreteClass) {
                classElement = concreteClassElement;
                annotationMetadata = classElement.getAnnotationMetadata();
            } else {
                annotationMetadata = annotationUtils.getAnnotationMetadata(typeElement);
                classElement = elementFactory.newClassElement(typeElement, annotationMetadata);
            }
            if (configurationMetadata != null) {
                // unfortunate we have to do this
                String existingPrefix = annotationMetadata.stringValue(
                        ConfigurationReader.class,
                        "prefix")
                        .orElse("");

                String computedPrefix = StringUtils.isNotEmpty(existingPrefix) ? existingPrefix + "." + configurationMetadata.getName() : configurationMetadata.getName();
                classElement.annotate(ConfigurationReader.class, (builder) ->
                        builder.member("prefix", computedPrefix)
                );
            }


            BeanDefinitionWriter beanDefinitionWriter = new BeanDefinitionWriter(classElement, metadataBuilder, javaVisitorContext);
            beanDefinitionWriter.visitTypeArguments(classElement.getAllTypeArguments());
            return beanDefinitionWriter;
        }

        private DynamicName createProxyKey(String beanName) {
            return new DynamicName(beanName + "$Proxy");
        }

        @SuppressWarnings("MagicNumber")
        private AopProxyWriter createIntroductionAdviceWriter(ClassElement typeElement) {
            AnnotationMetadata annotationMetadata = typeElement.getAnnotationMetadata();

            String packageName = typeElement.getPackageName();
            String beanClassName = typeElement.getSimpleName();
            io.micronaut.core.annotation.AnnotationValue<?>[] aroundInterceptors =
                    InterceptedMethodUtil.resolveInterceptorBinding(annotationMetadata, InterceptorKind.AROUND);
            io.micronaut.core.annotation.AnnotationValue<?>[] introductionInterceptors = InterceptedMethodUtil.resolveInterceptorBinding(annotationMetadata, InterceptorKind.INTRODUCTION);

            ClassElement[] interfaceTypes = Arrays.stream(annotationMetadata.getValue(Introduction.class, "interfaces", String[].class).orElse(new String[0]))
                    .map(ClassElement::of).toArray(ClassElement[]::new);

            io.micronaut.core.annotation.AnnotationValue<?>[] interceptorTypes = ArrayUtils.concat(aroundInterceptors, introductionInterceptors);
            boolean isInterface = typeElement.isInterface();
            AopProxyWriter aopProxyWriter = new AopProxyWriter(
                    packageName,
                    beanClassName,
                    isInterface,
                    typeElement,
                    annotationMetadata,
                    interfaceTypes,
                    javaVisitorContext,
                    metadataBuilder,
                    configurationMetadata,
                    interceptorTypes);

            aopProxyWriter.visitTypeArguments(typeElement.getAllTypeArguments());

            Set<TypeElement> additionalInterfaces = Arrays.stream(interfaceTypes)
                    .map(ce -> elementUtils.getTypeElement(ce.getName()))
                    .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));

            if (ArrayUtils.isNotEmpty(interfaceTypes)) {
                TypeElement te = (TypeElement) typeElement.getNativeType();
                List<? extends AnnotationMirror> annotationMirrors = te.getAnnotationMirrors();
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

        private BeanDefinitionWriter createFactoryBeanMethodWriterFor(Element method, TypeElement producedElement) {
            AnnotationMetadata annotationMetadata = annotationUtils.newAnnotationBuilder().buildForParent(producedElement, method, false);

            annotationMetadata = new AnnotationMetadataHierarchy(
                    concreteClassMetadata,
                    annotationMetadata
            );
            io.micronaut.inject.ast.Element factoryElement;
            if (method instanceof ExecutableElement) {
                factoryElement = elementFactory.newMethodElement(
                        concreteClassElement,
                        (ExecutableElement) method,
                        annotationMetadata
                );

            } else {
                factoryElement = elementFactory.newFieldElement(
                        concreteClassElement,
                        (VariableElement) method,
                        annotationMetadata
                );
            }
            return new BeanDefinitionWriter(
                    factoryElement,
                    OriginatingElements.of(factoryElement),
                    metadataBuilder,
                    javaVisitorContext,
                    factoryMethodIndex.getAndIncrement()
            );
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
