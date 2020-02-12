/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.annotation.processing;

import io.micronaut.aop.Adapter;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.Introduction;
import io.micronaut.aop.writer.AopProxyWriter;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.*;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.inject.configuration.ConfigurationMetadata;
import io.micronaut.inject.configuration.ConfigurationMetadataWriter;
import io.micronaut.inject.configuration.PropertyMetadata;
import io.micronaut.inject.processing.JavaModelUtils;
import io.micronaut.inject.processing.ProcessedTypes;
import io.micronaut.inject.writer.BeanDefinitionReferenceWriter;
import io.micronaut.inject.writer.BeanDefinitionVisitor;
import io.micronaut.inject.writer.BeanDefinitionWriter;
import io.micronaut.inject.writer.ExecutableMethodWriter;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Scope;
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
import static javax.lang.model.type.TypeKind.ARRAY;

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
            "io.micronaut.context.annotation.Executable"
    };
    private static final String AROUND_TYPE = "io.micronaut.aop.Around";
    private static final String INTRODUCTION_TYPE = "io.micronaut.aop.Introduction";
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
        this.beanDefinitions = new LinkedHashSet<>();
    }

    @Override
    public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        processingOver = roundEnv.processingOver();
            
        annotations = annotations
                .stream()
                .filter(ann -> !ann.getQualifiedName().toString().equals(AnnotationUtil.KOTLIN_METADATA))
                .filter(ann -> annotationUtils.hasStereotype(ann, ANNOTATION_STEREOTYPES) || AbstractAnnotationMetadataBuilder.isAnnotationMapped(ann.getQualifiedName().toString()))
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
                        if (!beanDefinitions.contains(name)) {
                            if (!processed.contains(name) && !name.endsWith(BeanDefinitionVisitor.PROXY_SUFFIX)) {
                                boolean isInterface = JavaModelUtils.resolveKind(typeElement, ElementKind.INTERFACE).isPresent();
                                if (!isInterface) {
                                    beanDefinitions.add(name);
                                } else {
                                    if (annotationUtils.hasStereotype(typeElement, INTRODUCTION_TYPE) || annotationUtils.hasStereotype(typeElement, ConfigurationReader.class)) {
                                        beanDefinitions.add(name);
                                    }
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
                writeConfigurationMetadata();
                writeBeanDefinitionsToMetaInf();
            } finally {
                AnnotationUtils.invalidateCache();
                AbstractAnnotationMetadataBuilder.clearMutated();
            }
        }

        return false;
    }

    private void writeConfigurationMetadata() {
        if (metadataBuilder.hasMetadata()) {
            ServiceLoader<ConfigurationMetadataWriter> writers = ServiceLoader.load(ConfigurationMetadataWriter.class, getClass().getClassLoader());

            try {
                for (ConfigurationMetadataWriter writer : writers) {
                    try {
                        writer.write(metadataBuilder, classWriterOutputVisitor);
                    } catch (IOException e) {
                        error("Error occurred writing configuration metadata: %s", e.getMessage());
                    }
                }
            } catch (ServiceConfigurationError e) {
                warning("Unable to load ConfigurationMetadataWriter due to : %s", e.getMessage());
            }
        }
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

            String beanDefinitionName = beanDefinitionWriter.getBeanDefinitionName();
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

            AnnotationMetadata annotationMetadata = beanDefinitionWriter.getAnnotationMetadata();
            BeanDefinitionReferenceWriter beanDefinitionReferenceWriter =
                    new BeanDefinitionReferenceWriter(beanTypeName, beanDefinitionName, annotationMetadata);
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

    private AnnotationMetadata addPropertyMetadata(AnnotationMetadata annotationMetadata, VariableElement element, String propertyName) {
        final PropertyMetadata pm = metadataBuilder.visitProperty(
                getPropertyMetadataTypeReference(element.asType()),
                propertyName, null, null
        );
        return addPropertyMetadata(annotationMetadata, pm);
    }

    private AnnotationMetadata addPropertyMetadata(AnnotationMetadata annotationMetadata, PropertyMetadata propertyMetadata) {
        return DefaultAnnotationMetadata.mutateMember(
                annotationMetadata,
                PropertySource.class.getName(),
                AnnotationMetadata.VALUE_MEMBER,
                Collections.singletonList(
                        new io.micronaut.core.annotation.AnnotationValue(
                                Property.class.getName(),
                                Collections.singletonMap(
                                        "name",
                                        propertyMetadata.getPath()
                                )
                        )
                )
        );
    }

    private AnnotationMetadata addAnnotation(AnnotationMetadata annotationMetadata, String annotation) {
        final JavaAnnotationMetadataBuilder metadataBuilder = javaVisitorContext.getAnnotationUtils().newAnnotationBuilder();
        annotationMetadata = metadataBuilder.annotate(
                annotationMetadata,
                io.micronaut.core.annotation.AnnotationValue.builder(annotation).build());
        return annotationMetadata;
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
        private ConfigurationMetadata configurationMetadata;
        private ExecutableElementParamInfo constructorParameterInfo;
        private AtomicInteger adaptedMethodIndex = new AtomicInteger(0);
        private AtomicInteger factoryMethodIndex = new AtomicInteger(0);
        private Set<Name> visitedTypes = new HashSet<>();

        /**
         * @param concreteClass The {@link TypeElement}
         */
        AnnBeanElementVisitor(TypeElement concreteClass) {
            this.concreteClass = concreteClass;
            this.concreteClassMetadata = annotationUtils.getAnnotationMetadata(concreteClass);
            beanDefinitionWriters = new LinkedHashMap<>();
            this.isFactoryType = concreteClassMetadata.hasStereotype(Factory.class);
            this.isConfigurationPropertiesType = concreteClassMetadata.hasDeclaredStereotype(ConfigurationReader.class) || concreteClassMetadata.hasDeclaredStereotype(EachProperty.class);
            this.isAopProxyType = concreteClassMetadata.hasStereotype(AROUND_TYPE) && !modelUtils.isAbstract(concreteClass);
            this.aopSettings = isAopProxyType ? concreteClassMetadata.getValues(AROUND_TYPE, Boolean.class) : OptionalValues.empty();
            ExecutableElement constructor = modelUtils.concreteConstructorFor(concreteClass, annotationUtils);
            this.constructorParameterInfo = populateParameterData(null, constructor, Collections.emptyMap());
            this.isExecutableType = isAopProxyType || concreteClassMetadata.hasStereotype(Executable.class);
            this.isDeclaredBean = isExecutableType || isConfigurationPropertiesType || isFactoryType || concreteClassMetadata.hasStereotype(Scope.class) || concreteClassMetadata.hasStereotype(DefaultScope.class) || constructorParameterInfo.getAnnotationMetadata().hasStereotype(Inject.class);
        }

        /**
         * @return The {@link TypeElement}
         */
        TypeElement getConcreteClass() {
            return concreteClass;
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
            if (visitedTypes.contains(classElementQualifiedName)) {
                // bail out if already visited
                return o;
            }
            boolean isInterface = JavaModelUtils.isInterface(classElement);
            visitedTypes.add(classElementQualifiedName);
            AnnotationMetadata typeAnnotationMetadata = annotationUtils.getAnnotationMetadata(classElement);
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
                ExecutableElementParamInfo constructorData = constructor != null ? populateParameterData(null, constructor, Collections.emptyMap()) : null;

                if (constructorData != null) {
                    aopProxyWriter.visitBeanDefinitionConstructor(
                            constructorData.getAnnotationMetadata(),
                            constructorData.isRequiresReflection(),
                            constructorData.getParameters(),
                            constructorData.getParameterMetadata(),
                            constructorData.getGenericTypes()
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
                        final boolean isBean = isAopProxyType ||
                                isConfigurationPropertiesType ||
                                typeAnnotationMetadata.hasStereotype(ANNOTATION_STEREOTYPES) ||
                                (constructorParameterInfo.getAnnotationMetadata().hasStereotype(Inject.class));

                        if (isBean) {
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
                                        if (!writer.isValidated() && annotationUtils.hasStereotype(method, "javax.validation.Constraint")) {
                                            writer.setValidated(true);
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
                            constructorParameterInfo.getGenericTypes());
                }

                beanDefinitionWriter.visitBeanDefinitionConstructor(
                        annotationMetadata,
                        constructorParameterInfo.isRequiresReflection(),
                        constructorParameterInfo.getParameters(),
                        constructorParameterInfo.getParameterMetadata(),
                        constructorParameterInfo.getGenericTypes());

                if (constructorParameterInfo.isValidated()) {
                    beanDefinitionWriter.setValidated(true);
                }
            }
            return beanDefinitionWriter;
        }

        private void visitIntroductionAdviceInterface(TypeElement classElement, AnnotationMetadata typeAnnotationMetadata, AopProxyWriter aopProxyWriter) {
            String introductionTypeName = classElement.getQualifiedName().toString();
            final boolean isConfigProps = typeAnnotationMetadata.hasAnnotation(ANN_CONFIGURATION_ADVICE);
            if (isConfigProps) {
                metadataBuilder.visitProperties(
                        classElement,
                        null
                );
            }
            classElement.asType().accept(new PublicAbstractMethodVisitor<Object, AopProxyWriter>(classElement, modelUtils, elementUtils) {

                @Override
                protected boolean isAcceptableMethod(ExecutableElement executableElement) {
                    return super.isAcceptableMethod(executableElement) || annotationUtils.getAnnotationMetadata(executableElement).hasDeclaredStereotype(AROUND_TYPE);
                }

                @Override
                protected void accept(DeclaredType type, Element element, AopProxyWriter aopProxyWriter) {
                    ExecutableElement method = (ExecutableElement) element;
                    final boolean isAbstract = modelUtils.isAbstract(method);

                    Map<String, Object> boundTypes = genericUtils.resolveBoundTypes(type);
                    ExecutableElementParamInfo params = populateParameterData(introductionTypeName, method, boundTypes);
                    Object owningType = modelUtils.resolveTypeReference(method.getEnclosingElement());
                    if (owningType == null) {
                        throw new IllegalStateException("Owning type cannot be null");
                    }
                    TypeMirror returnTypeMirror = method.getReturnType();
                    Object resolvedReturnType = genericUtils.resolveTypeReference(returnTypeMirror, boundTypes);
                    Map<String, Object> returnTypeGenerics = genericUtils.resolveGenericTypes(returnTypeMirror, boundTypes);

                    String methodName = method.getSimpleName().toString();
                    Map<String, Object> methodParameters = params.getParameters();
                    Map<String, Object> genericParameters = params.getGenericParameters();
                    Map<String, AnnotationMetadata> methodQualifier = params.getParameterMetadata();
                    Map<String, Map<String, Object>> methodGenericTypes = params.getGenericTypes();
                    AnnotationMetadata annotationMetadata;

                    if (annotationUtils.isAnnotated(introductionTypeName, method) || JavaAnnotationMetadataBuilder.hasAnnotation(method, Override.class)) {
                        annotationMetadata = annotationUtils.newAnnotationBuilder().buildForParent(introductionTypeName, classElement, method);
                    } else {
                        annotationMetadata = new AnnotationMetadataReference(
                                aopProxyWriter.getBeanDefinitionName() + BeanDefinitionReferenceWriter.REF_SUFFIX,
                                typeAnnotationMetadata
                        );
                    }

                    if (!annotationMetadata.hasStereotype(ANN_VALIDATED) &&
                            isDeclaredBean &&
                            params.getParameterMetadata().values().stream().anyMatch(IS_CONSTRAINT)) {
                        annotationMetadata = addAnnotation(annotationMetadata, ANN_VALIDATED);
                    }

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
                            final String propertyType = getPropertyMetadataTypeReference(returnTypeMirror);

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
                            annotationMetadata = addPropertyMetadata(
                                    annotationMetadata,
                                    propertyMetadata
                            );

                            final TypeElement typeElement = !ClassUtils.isJavaBasicType(propertyType) ? resolveTypeElement(returnTypeMirror) : null;
                            final AnnotationValueBuilder<Annotation> builder = io.micronaut.core.annotation.AnnotationValue.builder(ANN_CONFIGURATION_ADVICE);
                            if (typeElement != null && annotationUtils.hasStereotype(typeElement, Scope.class)) {
                                builder.member("bean", true);
                            }
                            if (typeAnnotationMetadata.hasStereotype(EachProperty.class)) {
                                builder.member("iterable", true);
                            }

                            final JavaAnnotationMetadataBuilder metadataBuilder = javaVisitorContext.getAnnotationUtils().newAnnotationBuilder();
                            annotationMetadata = metadataBuilder.annotate(
                                    annotationMetadata,
                                    builder.build());
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
                                owningType,
                                modelUtils.resolveTypeReference(returnTypeMirror),
                                resolvedReturnType,
                                returnTypeGenerics,
                                methodName,
                                methodParameters,
                                genericParameters,
                                methodQualifier,
                                methodGenericTypes,
                                annotationMetadata
                        );
                    } else {
                        // only apply around advise to non-abstract methods of introduction advise
                        aopProxyWriter.visitAroundMethod(
                                owningType,
                                modelUtils.resolveTypeReference(returnTypeMirror),
                                resolvedReturnType,
                                returnTypeGenerics,
                                methodName,
                                methodParameters,
                                genericParameters,
                                methodQualifier,
                                methodGenericTypes,
                                annotationMetadata,
                                JavaModelUtils.isInterface(method.getEnclosingElement())
                        );
                    }


                }

                private @Nullable TypeElement resolveTypeElement(TypeMirror typeMirror) {
                    if (typeMirror instanceof DeclaredType) {
                        final Element element = ((DeclaredType) typeMirror).asElement();
                        if (element instanceof TypeElement) {
                            return (TypeElement) element;
                        }
                    }
                    return null;
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
            if (isDeclaredBean &&
                    !methodAnnotationMetadata.hasStereotype(ANN_VALIDATED) &&
                    method.getParameters()
                            .stream()
                            .anyMatch((p) -> annotationUtils.hasStereotype(p, ANN_CONSTRAINT) || annotationUtils.hasStereotype(p, ANN_VALID))) {
                hasConstraints = true;
                methodAnnotationMetadata = javaVisitorContext.getAnnotationUtils().newAnnotationBuilder().annotate(
                        methodAnnotationMetadata,
                        io.micronaut.core.annotation.AnnotationValue.builder(ANN_VALIDATED).build()
                );
            }

            if (isExecutable) {
                visitExecutableMethod(method, methodAnnotationMetadata);
                return null;
            } else if (isConfigurationPropertiesType && !modelUtils.isPrivate(method) && !modelUtils.isStatic(method)) {
                String methodName = method.getSimpleName().toString();
                if (NameUtils.isSetterName(methodName) && method.getParameters().size() == 1) {
                    visitConfigurationPropertySetter(method);
                } else if (NameUtils.isGetterName(methodName)) {
                    BeanDefinitionVisitor writer = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName());
                    if (!writer.isValidated() && annotationUtils.hasStereotype(method, ANN_CONSTRAINT)) {
                        writer.setValidated(true);
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
                    declaredMetadata.hasAnnotation(Executable.class);
        }

        private void visitConfigurationPropertySetter(ExecutableElement method) {
            BeanDefinitionVisitor writer = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName());
            VariableElement parameter = method.getParameters().get(0);
            TypeMirror valueType = parameter.asType();
            Object fieldType = modelUtils.resolveTypeReference(valueType);
            Map<String, Object> genericTypes = Collections.emptyMap();
            TypeKind typeKind = valueType.getKind();
            if (!(typeKind.isPrimitive() || typeKind == ARRAY)) {
                genericTypes = genericUtils.resolveGenericTypes(valueType, Collections.emptyMap());
            }

            TypeElement declaringClass = modelUtils.classElementFor(method);

            if (declaringClass != null) {

                AnnotationMetadata methodAnnotationMetadata = annotationUtils.getAnnotationMetadata(method);

                String propertyName = NameUtils.getPropertyNameForSetter(method.getSimpleName().toString());
                if (methodAnnotationMetadata.hasStereotype(ConfigurationBuilder.class)) {
                    writer.visitConfigBuilderMethod(
                            fieldType,
                            NameUtils.getterNameFor(propertyName),
                            methodAnnotationMetadata,
                            metadataBuilder);
                    try {
                        visitConfigurationBuilder(declaringClass, method, valueType, writer);
                    } finally {
                        writer.visitConfigBuilderEnd();
                    }
                } else {
                    if (shouldExclude(configurationMetadata, propertyName)) {
                        return;
                    }
                    String docComment = elementUtils.getDocComment(method);
                    String setterName = method.getSimpleName().toString();
                    PropertyMetadata propertyMetadata = metadataBuilder.visitProperty(
                            concreteClass,
                            declaringClass,
                            getPropertyMetadataTypeReference(valueType),
                            propertyName,
                            docComment,
                            null
                    );

                    AnnotationMetadata annotationMetadata = addPropertyMetadata(AnnotationMetadata.EMPTY_METADATA, propertyMetadata);

                    boolean requiresReflection = true;
                    if (modelUtils.isPublic(method)) {
                        requiresReflection = false;
                    } else if (modelUtils.isPackagePrivate(method) || modelUtils.isProtected(method)) {
                        PackageElement declaringPackage = elementUtils.getPackageOf(declaringClass);
                        PackageElement concretePackage = elementUtils.getPackageOf(this.concreteClass);
                        requiresReflection = !declaringPackage.getQualifiedName().equals(concretePackage.getQualifiedName());
                    }

                    writer.visitSetterValue(
                            modelUtils.resolveTypeReference(declaringClass),
                            modelUtils.resolveTypeReference(method.getReturnType()),
                            annotationMetadata,
                            requiresReflection,
                            fieldType,
                            setterName,
                            genericTypes,
                            annotationUtils.getAnnotationMetadata(method.getParameters().get(0)),
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

            TypeMirror returnType = beanMethod.getReturnType();

            TypeElement producedElement = modelUtils.classElementFor(typeUtils.asElement(returnType));

            if (producedElement == null) {
                return;
            }
            String producedTypeName = producedElement.getQualifiedName().toString();
            ExecutableElementParamInfo beanMethodParams = populateParameterData(producedTypeName, beanMethod, Collections.emptyMap());

            BeanDefinitionWriter beanMethodWriter = createFactoryBeanMethodWriterFor(beanMethod, producedElement);

            if (returnType instanceof DeclaredType) {
                DeclaredType dt = (DeclaredType) returnType;
                Map<String, Map<String, Object>> beanTypeArguments = genericUtils.buildGenericTypeArgumentInfo(dt);
                beanMethodWriter.visitTypeArguments(beanTypeArguments);
            }

            final String beanMethodName = beanMethod.getSimpleName().toString();
            final Map<String, Object> beanMethodParameters = beanMethodParams.getParameters();

            StringBuilder methodKey = new StringBuilder(beanMethodName)
                    .append("(")
                    .append(beanMethodParameters.values().stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(",")))
                    .append(")");

            beanDefinitionWriters.put(new DynamicName(methodKey), beanMethodWriter);

            final Object beanMethodDeclaringType = modelUtils.resolveTypeReference(beanMethod.getEnclosingElement());
            AnnotationMetadata methodAnnotationMetadata = annotationUtils.newAnnotationBuilder().buildForParent(
                    producedElement,
                    beanMethod
            );
            beanMethodWriter.visitBeanFactoryMethod(

                    beanMethodDeclaringType,
                    modelUtils.resolveTypeReference(returnType),
                    beanMethodName,
                    methodAnnotationMetadata,
                    beanMethodParameters,
                    beanMethodParams.getParameterMetadata(),
                    beanMethodParams.getGenericTypes()
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
                ExecutableElementParamInfo constructorData = constructor != null ? populateParameterData(null, constructor, Collections.emptyMap()) : null;

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

                returnType.accept(new PublicMethodVisitor<Object, AopProxyWriter>(typeUtils) {
                    @Override
                    protected void accept(DeclaredType type, Element element, AopProxyWriter aopProxyWriter) {
                        ExecutableElement method = (ExecutableElement) element;
                        Object owningType = modelUtils.resolveTypeReference(method.getEnclosingElement());
                        if (owningType == null) {
                            throw new IllegalStateException("Owning type cannot be null");
                        }
                        TypeMirror returnTypeMirror = method.getReturnType();

                        TypeMirror returnType = method.getReturnType();

                        Map<String, Object> returnTypeGenerics = new HashMap<>();
                        genericUtils.resolveBoundGenerics((TypeElement) method.getEnclosingElement(),
                                returnType,
                                genericUtils.buildGenericTypeArgumentElementInfo(concreteClass))
                                .forEach((key, value) ->
                                        returnTypeGenerics.put(key, modelUtils.resolveTypeReference(value)));

                        //Object resolvedReturnType = genericUtils.resolveTypeReference(returnType, returnTypeGenerics);
                        Object resolvedReturnType = modelUtils.resolveTypeReference(returnType);

                        TypeElement enclosingElement = (TypeElement) method.getEnclosingElement();

                        Map<String, Object> boundTypes = genericUtils.buildGenericTypeArgumentInfo(concreteClass).get(enclosingElement.getQualifiedName().toString());
                        if (boundTypes == null) {
                            boundTypes = Collections.emptyMap();
                        }

                        ExecutableElementParamInfo params = populateParameterData(null, method, boundTypes);

                        String methodName = method.getSimpleName().toString();
                        Map<String, Object> methodParameters = params.getParameters();
                        Map<String, AnnotationMetadata> methodQualifier = params.getParameterMetadata();
                        Map<String, Map<String, Object>> methodGenericTypes = params.getGenericTypes();
                        Map<String, Object> genericParameters = params.getGenericParameters();

                        AnnotationMetadata annotationMetadata;
                        boolean isAnnotationReference = false;
                        // if the method is annotated we build metadata for the method
                        if (annotationUtils.isAnnotated(producedTypeName, method)) {
                            annotationMetadata = annotationUtils.getAnnotationMetadata(beanMethod, method);
                        } else {
                            // otherwise we setup a reference to the parent metadata (essentially the annotations declared on the bean factory method)
                            isAnnotationReference = true;
                            annotationMetadata = new AnnotationMetadataReference(
                                    beanMethodWriter.getBeanDefinitionName() + BeanDefinitionReferenceWriter.REF_SUFFIX,
                                    methodAnnotationMetadata
                            );
                        }

                        ExecutableMethodWriter executableMethodWriter = beanMethodWriter.visitExecutableMethod(
                                owningType,
                                modelUtils.resolveTypeReference(returnTypeMirror),
                                resolvedReturnType,
                                returnTypeGenerics,
                                methodName,
                                methodParameters,
                                genericParameters,
                                methodQualifier,
                                methodGenericTypes,
                                annotationMetadata,
                                JavaModelUtils.isInterface(method.getEnclosingElement())
                        );

                        aopProxyWriter.visitAroundMethod(
                                owningType,
                                resolvedReturnType,
                                resolvedReturnType,
                                returnTypeGenerics,
                                methodName,
                                methodParameters,
                                genericParameters,
                                methodQualifier,
                                methodGenericTypes,
                                !isAnnotationReference ? new AnnotationMetadataReference(executableMethodWriter.getClassName(), annotationMetadata) : annotationMetadata,
                                JavaModelUtils.isInterface(method.getEnclosingElement())

                        );
                    }
                }, proxyWriter);
            } else if (methodAnnotationMetadata.hasStereotype(Executable.class)) {

                returnType.accept(new PublicMethodVisitor<Object, BeanDefinitionWriter>(typeUtils) {
                    @Override
                    protected void accept(DeclaredType type, Element element, BeanDefinitionWriter beanWriter) {
                        ExecutableElement method = (ExecutableElement) element;
                        Object owningType = modelUtils.resolveTypeReference(method.getEnclosingElement());
                        if (owningType == null) {
                            throw new IllegalStateException("Owning type cannot be null");
                        }
                        TypeMirror returnTypeMirror = method.getReturnType();
                        String methodName = method.getSimpleName().toString();

                        AnnotationMetadata annotationMetadata = new AnnotationMetadataReference(
                                beanMethodWriter.getBeanDefinitionName() + BeanDefinitionReferenceWriter.REF_SUFFIX,
                                methodAnnotationMetadata
                        );

                        Map<String, Object> returnTypeGenerics = new HashMap<>();
                        genericUtils.resolveBoundGenerics((TypeElement) method.getEnclosingElement(),
                                returnType,
                                genericUtils.buildGenericTypeArgumentElementInfo(type.asElement()))
                                .forEach((key, value) ->
                                        returnTypeGenerics.put(key, modelUtils.resolveTypeReference(value)));

                        TypeElement enclosingElement = (TypeElement) method.getEnclosingElement();

                        Map<String, Object> boundTypes = genericUtils.buildGenericTypeArgumentInfo(type).get(enclosingElement.getQualifiedName().toString());
                        if (boundTypes == null) {
                            boundTypes = Collections.emptyMap();
                        }

                        Object resolvedReturnType = genericUtils.resolveTypeReference(returnTypeMirror, boundTypes);

                        ExecutableElementParamInfo params = populateParameterData(producedTypeName, method, boundTypes);

                        Map<String, Object> methodParameters = params.getParameters();
                        Map<String, Object> genericParameters = params.getGenericParameters();
                        Map<String, AnnotationMetadata> methodQualifier = params.getParameterMetadata();
                        Map<String, Map<String, Object>> methodGenericTypes = params.getGenericTypes();

                        beanMethodWriter.visitExecutableMethod(
                                owningType,
                                modelUtils.resolveTypeReference(returnTypeMirror),
                                resolvedReturnType,
                                returnTypeGenerics,
                                methodName,
                                methodParameters,
                                genericParameters,
                                methodQualifier,
                                methodGenericTypes,
                                annotationMetadata,
                                JavaModelUtils.isInterface(enclosingElement)
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
                                final Optional<ExecutableElement> destroyMethodRef = modelUtils.findAccessibleNoArgumentInstanceMethod(destroyMethodDeclaringClass, destroyMethodName);
                                if (destroyMethodRef.isPresent()) {
                                    beanMethodWriter.visitPreDestroyMethod(
                                            destroyMethodDeclaringClass.getQualifiedName().toString(),
                                            genericUtils.resolveTypeReference(destroyMethodRef.get().getReturnType()),
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
            TypeMirror returnType = method.getReturnType();

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

            Map<String, Object> returnTypeGenerics = new HashMap<>();
            genericUtils.resolveBoundGenerics((TypeElement) method.getEnclosingElement(),
                    returnType,
                    genericUtils.buildGenericTypeArgumentElementInfo(concreteClass))
                    .forEach((key, value) ->
                            returnTypeGenerics.put(key, modelUtils.resolveTypeReference(value)));

            Object resolvedReturnType = modelUtils.resolveTypeReference(returnType);

            TypeElement enclosingElement = (TypeElement) method.getEnclosingElement();

            Map<String, Object> boundTypes = genericUtils.buildGenericTypeArgumentInfo(concreteClass).get(enclosingElement.getQualifiedName().toString());
            if (boundTypes == null) {
                boundTypes = Collections.emptyMap();
            }

            ExecutableElementParamInfo params = populateParameterData(null, method, boundTypes);

            BeanDefinitionVisitor beanWriter = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName());

            // This method requires pre-processing. See Executable#processOnStartup()
            boolean preprocess = methodAnnotationMetadata.isTrue(Executable.class, "processOnStartup");
            if (preprocess) {
                beanWriter.setRequiresMethodProcessing(true);
            }

            Object typeRef = modelUtils.resolveTypeReference(method.getEnclosingElement());
            if (typeRef == null) {
                typeRef = modelUtils.resolveTypeReference(concreteClass);
            }

            final AopProxyWriter proxyWriter = resolveAopWriter(beanWriter);
            ExecutableMethodWriter executableMethodWriter = null;
            if (proxyWriter == null || proxyWriter.isProxyTarget()) {
                executableMethodWriter = beanWriter.visitExecutableMethod(
                        typeRef,
                        resolvedReturnType,
                        resolvedReturnType,
                        returnTypeGenerics,
                        method.getSimpleName().toString(),
                        params.getParameters(),
                        params.getGenericParameters(),
                        params.getParameterMetadata(),
                        params.getGenericTypes(),
                        methodAnnotationMetadata,
                        JavaModelUtils.isInterface(enclosingElement));
            }


            if (methodAnnotationMetadata.hasStereotype(Adapter.class)) {
                visitAdaptedMethod(method, methodAnnotationMetadata);
            }


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

                    boolean isAnnotationReference = methodAnnotationMetadata instanceof AnnotationMetadataReference;

                    AnnotationMetadata aroundMethodMetadata;

                    if (!isAnnotationReference && executableMethodWriter != null) {
                        aroundMethodMetadata = new AnnotationMetadataReference(executableMethodWriter.getClassName(), methodAnnotationMetadata);
                    } else {
                        if (methodAnnotationMetadata instanceof AnnotationMetadataHierarchy) {
                            aroundMethodMetadata = methodAnnotationMetadata;
                        } else {
                            aroundMethodMetadata = new AnnotationMetadataHierarchy(concreteClassMetadata, methodAnnotationMetadata);
                        }
                    }

                    if (modelUtils.isFinal(method)) {
                        if (methodAnnotationMetadata.hasDeclaredStereotype(AROUND_TYPE)) {
                            error(method, "Method defines AOP advice but is declared final. Change the method to be non-final in order for AOP advice to be applied.");
                        } else {
                            if (isAopProxyType && isPublic && !declaringClass.equals(concreteClass)) {
                                if (executableMethodWriter == null) {
                                    beanWriter.visitExecutableMethod(
                                            typeRef,
                                            resolvedReturnType,
                                            resolvedReturnType,
                                            returnTypeGenerics,
                                            method.getSimpleName().toString(),
                                            params.getParameters(),
                                            params.getGenericParameters(),
                                            params.getParameterMetadata(),
                                            params.getGenericTypes(),
                                            methodAnnotationMetadata,
                                            JavaModelUtils.isInterface(enclosingElement));
                                }
                            } else {
                                error(method, "Public method inherits AOP advice but is declared final. Either make the method non-public or apply AOP advice only to public methods declared on the class.");
                            }
                        }
                    } else {
                        aopProxyWriter.visitAroundMethod(
                                typeRef,
                                resolvedReturnType,
                                resolvedReturnType,
                                returnTypeGenerics,
                                method.getSimpleName().toString(),
                                params.getParameters(),
                                params.getGenericParameters(),
                                params.getParameterMetadata(),
                                params.getGenericTypes(),
                                aroundMethodMetadata,
                                JavaModelUtils.isInterface(enclosingElement));
                    }

                } else if (executableMethodWriter == null) {
                    beanWriter.visitExecutableMethod(
                            typeRef,
                            resolvedReturnType,
                            resolvedReturnType,
                            returnTypeGenerics,
                            method.getSimpleName().toString(),
                            params.getParameters(),
                            params.getGenericParameters(),
                            params.getParameterMetadata(),
                            params.getGenericTypes(),
                            methodAnnotationMetadata,
                            JavaModelUtils.isInterface(enclosingElement));
                }
            }
        }

        private void visitAdaptedMethod(ExecutableElement method, AnnotationMetadata methodAnnotationMetadata) {
            Optional<DeclaredType> targetType = methodAnnotationMetadata.getValue(Adapter.class, String.class).flatMap(s -> {
                TypeElement typeElement = elementUtils.getTypeElement(s);
                if (typeElement != null) {
                    TypeMirror typeMirror = typeElement.asType();
                    if (typeMirror instanceof DeclaredType) {
                        return Optional.of((DeclaredType) typeMirror);
                    }
                }
                return Optional.empty();
            });

            if (targetType.isPresent()) {
                DeclaredType typeToImplement = targetType.get();
                Element element = typeToImplement.asElement();
                if (element instanceof TypeElement) {
                    TypeElement typeElement = (TypeElement) element;
                    boolean isInterface = JavaModelUtils.isInterface(element);
                    if (isInterface) {

                        PackageElement packageElement = elementUtils.getPackageOf(concreteClass);
                        String packageName = packageElement.getQualifiedName().toString();
                        String declaringClassSimpleName = concreteClass.getSimpleName().toString();
                        String beanClassName = generateAdaptedMethodClassName(method, typeElement, declaringClassSimpleName);

                        AopProxyWriter aopProxyWriter = new AopProxyWriter(
                                packageName,
                                beanClassName,
                                true,
                                false,
                                methodAnnotationMetadata,
                                new Object[]{modelUtils.resolveTypeReference(typeToImplement)},
                                ArrayUtils.EMPTY_OBJECT_ARRAY);

                        aopProxyWriter.visitBeanDefinitionConstructor(methodAnnotationMetadata, false);

                        beanDefinitionWriters.put(elementUtils.getName(packageName + '.' + beanClassName), aopProxyWriter);

                        List<? extends TypeMirror> typeArguments = ((DeclaredType) typeElement.asType()).getTypeArguments();
                        Map<String, TypeMirror> typeVariables = new HashMap<>(typeArguments.size());

                        for (TypeMirror typeArgument : typeArguments) {
                            typeVariables.put(typeArgument.toString(), typeArgument);
                        }

                        typeToImplement.accept(new PublicAbstractMethodVisitor<Object, AopProxyWriter>(typeElement, modelUtils, elementUtils) {
                            boolean first = true;

                            @Override
                            protected void accept(DeclaredType type, Element element, AopProxyWriter aopProxyWriter) {
                                if (!first) {
                                    error(method, "Interface to adapt [" + typeToImplement + "] is not a SAM type. More than one abstract method declared.");
                                    return;
                                }
                                first = false;
                                ExecutableElement targetMethod = (ExecutableElement) element;
                                List<? extends VariableElement> targetParameters = targetMethod.getParameters();
                                List<? extends VariableElement> sourceParameters = method.getParameters();

                                int paramLen = targetParameters.size();
                                if (paramLen == sourceParameters.size()) {

                                    Map<String, Object> genericTypes = new HashMap<>();
                                    for (int i = 0; i < paramLen; i++) {

                                        VariableElement targetElement = targetParameters.get(i);
                                        VariableElement sourceElement = sourceParameters.get(i);

                                        TypeMirror targetType = targetElement.asType();
                                        TypeMirror sourceType = sourceElement.asType();

                                        if (targetType.getKind() == TypeKind.TYPEVAR) {
                                            TypeVariable tv = (TypeVariable) targetType;
                                            String variableName = tv.toString();


                                            if (typeVariables.containsKey(variableName)) {
                                                TypeMirror variableMirror = typeVariables.get(variableName);
                                                if (variableMirror.getKind() == TypeKind.TYPEVAR) {
                                                    TypeVariable tv2 = (TypeVariable) variableMirror;
                                                    TypeMirror lowerBound = tv2.getLowerBound();
                                                    if (lowerBound.getKind() == TypeKind.DECLARED) {
                                                        targetType = lowerBound;
                                                    } else {
                                                        TypeMirror upperBound = tv2.getUpperBound();
                                                        if (upperBound.getKind() == TypeKind.DECLARED) {
                                                            targetType = upperBound;
                                                        }
                                                    }
                                                }
                                                genericTypes.put(variableName, modelUtils.resolveTypeReference(sourceType));
                                            } else {
                                                TypeMirror lowerBound = tv.getLowerBound();
                                                if (lowerBound.getKind() == TypeKind.DECLARED) {
                                                    targetType = lowerBound;
                                                } else {
                                                    TypeMirror upperBound = tv.getUpperBound();
                                                    if (upperBound.getKind() == TypeKind.DECLARED) {
                                                        targetType = upperBound;
                                                    }
                                                }
                                            }

                                        }

                                        TypeMirror thisType = typeUtils.erasure(sourceType);
                                        TypeMirror thatType = typeUtils.erasure(targetType);

                                        if (!typeUtils.isAssignable(thisType, thatType)) {
                                            error(method, "Cannot adapt method [" + method + "] to target method [" + targetMethod + "]. Type [" + sourceType + "] is not a subtype of type [" + targetType + "] for argument at position " + i);
                                            return;
                                        }
                                    }


                                    if (!genericTypes.isEmpty()) {
                                        Map<String, Map<String, Object>> typeData = Collections.singletonMap(
                                                modelUtils.resolveTypeReference(typeToImplement).toString(),
                                                genericTypes
                                        );
                                        aopProxyWriter.visitTypeArguments(
                                                typeData
                                        );
                                    }

                                    Map<String, Object> boundTypes = genericTypes;
                                    ExecutableElementParamInfo params = populateParameterData(typeElement.getQualifiedName().toString(), targetMethod, boundTypes);
                                    Object owningType = modelUtils.resolveTypeReference(targetMethod.getEnclosingElement());
                                    if (owningType == null) {
                                        throw new IllegalStateException("Owning type cannot be null");
                                    }
                                    TypeMirror returnTypeMirror = targetMethod.getReturnType();
                                    Object resolvedReturnType = genericUtils.resolveTypeReference(returnTypeMirror, boundTypes);
                                    Map<String, Object> returnTypeGenerics = genericUtils.resolveGenericTypes(returnTypeMirror, boundTypes);

                                    String methodName = targetMethod.getSimpleName().toString();
                                    Map<String, Object> methodParameters = params.getParameters();
                                    Map<String, Object> genericParameters = params.getGenericParameters();
                                    Map<String, AnnotationMetadata> methodQualifier = params.getParameterMetadata();
                                    Map<String, Map<String, Object>> methodGenericTypes = params.getGenericTypes();

                                    AnnotationClassValue[] adaptedArgumentTypes = new AnnotationClassValue[paramLen];
                                    for (int i = 0; i < adaptedArgumentTypes.length; i++) {
                                        VariableElement ve = sourceParameters.get(i);
                                        Object r = modelUtils.resolveTypeReference(ve.asType());
                                        if (r instanceof Class) {
                                            adaptedArgumentTypes[i] = new AnnotationClassValue((Class) r);
                                        } else {
                                            adaptedArgumentTypes[i] = new AnnotationClassValue(r.toString());
                                        }

                                    }

                                    Map members = CollectionUtils.mapOf(
                                            Adapter.InternalAttributes.ADAPTED_BEAN, new AnnotationClassValue<>(modelUtils.resolveTypeName(concreteClass.asType())),
                                            Adapter.InternalAttributes.ADAPTED_METHOD, method.getSimpleName().toString(),
                                            Adapter.InternalAttributes.ADAPTED_ARGUMENT_TYPES, adaptedArgumentTypes
                                    );

                                    String qualifier = annotationUtils.getAnnotationMetadata(concreteClass)
                                            .getValue(Named.class, String.class).orElse(null);

                                    if (StringUtils.isNotEmpty(qualifier)) {
                                        members.put(Adapter.InternalAttributes.ADAPTED_QUALIFIER, qualifier);
                                    }

                                    AnnotationMetadata annotationMetadata = DefaultAnnotationMetadata.mutateMember(
                                            methodAnnotationMetadata,
                                            Adapter.class.getName(),
                                            members
                                    );

                                    aopProxyWriter.visitAroundMethod(
                                            owningType,
                                            modelUtils.resolveTypeReference(returnTypeMirror),
                                            resolvedReturnType,
                                            returnTypeGenerics,
                                            methodName,
                                            methodParameters,
                                            genericParameters,
                                            methodQualifier,
                                            methodGenericTypes,
                                            annotationMetadata,
                                            JavaModelUtils.isInterface(method.getEnclosingElement())
                                    );


                                } else {
                                    error(method, "Cannot adapt method [" + method + "] to target method [" + targetMethod + "]. Argument lengths don't match.");
                                }
                            }
                        }, aopProxyWriter);
                    }
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
                            constructorParameterInfo.getGenericTypes()
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
            ExecutableElementParamInfo params = populateParameterData(null, method, Collections.emptyMap());
            TypeMirror returnType = method.getReturnType();
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

            if (annotationMetadata.hasDeclaredStereotype(ProcessedTypes.POST_CONSTRUCT)) {
                BeanDefinitionVisitor writer = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName());
                final AopProxyWriter aopWriter = resolveAopWriter(writer);
                if (aopWriter != null && !aopWriter.isProxyTarget()) {
                    writer = aopWriter;
                }
                writer.visitPostConstructMethod(
                        modelUtils.resolveTypeReference(declaringClass),
                        requiresReflection,
                        modelUtils.resolveTypeReference(returnType),
                        method.getSimpleName().toString(),
                        params.getParameters(),
                        params.getParameterMetadata(),
                        params.getGenericTypes(),
                        annotationMetadata
                );
            } else if (annotationMetadata.hasDeclaredStereotype(ProcessedTypes.PRE_DESTROY)) {
                BeanDefinitionVisitor writer = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName());
                final AopProxyWriter aopWriter = resolveAopWriter(writer);
                if (aopWriter != null && !aopWriter.isProxyTarget()) {
                    writer = aopWriter;
                }
                writer.visitPreDestroyMethod(
                        modelUtils.resolveTypeReference(declaringClass),
                        requiresReflection,
                        modelUtils.resolveTypeReference(returnType),
                        method.getSimpleName().toString(),
                        params.getParameters(),
                        params.getParameterMetadata(),
                        params.getGenericTypes(),
                        annotationMetadata
                );
            } else if (annotationMetadata.hasDeclaredStereotype(Inject.class) || annotationMetadata.hasDeclaredStereotype(ConfigurationInject.class)) {
                BeanDefinitionVisitor writer = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName());
                writer.visitMethodInjectionPoint(
                        modelUtils.resolveTypeReference(declaringClass),
                        requiresReflection,
                        modelUtils.resolveTypeReference(returnType),
                        method.getSimpleName().toString(),
                        params.getParameters(),
                        params.getParameterMetadata(),
                        params.getGenericTypes(),
                        annotationMetadata
                );
            } else {
                error("Unexpected call to visitAnnotatedMethod(%s)", method);
            }
        }

        private @Nullable AopProxyWriter resolveAopWriter(BeanDefinitionVisitor writer) {
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

            if (modelUtils.isStatic(variable) || modelUtils.isFinal(variable)) {
                return null;
            }

            AnnotationMetadata fieldAnnotationMetadata = annotationUtils.getAnnotationMetadata(variable);
            if (fieldAnnotationMetadata.hasDeclaredAnnotation("org.jetbrains.annotations.Nullable")) {
                fieldAnnotationMetadata = DefaultAnnotationMetadata.mutateMember(fieldAnnotationMetadata, "javax.annotation.Nullable", Collections.emptyMap());
            }

            boolean isInjected = fieldAnnotationMetadata.hasStereotype(Inject.class);
            boolean isValue = !isInjected &&
                    (fieldAnnotationMetadata.hasStereotype(Value.class) || fieldAnnotationMetadata.hasStereotype(Property.class));

            if (isInjected || isValue) {
                Name fieldName = variable.getSimpleName();
                BeanDefinitionVisitor writer = getOrCreateBeanDefinitionWriter(concreteClass, concreteClass.getQualifiedName());

                TypeElement declaringClass = modelUtils.classElementFor(variable);

                if (declaringClass == null) {
                    return null;
                }

                boolean isPrivate = modelUtils.isPrivate(variable);
                boolean requiresReflection = isPrivate
                        || modelUtils.isInheritedAndNotPublic(this.concreteClass, declaringClass, variable);

                if (!writer.isValidated()
                        && fieldAnnotationMetadata.hasStereotype("javax.validation.Constraint")) {
                    writer.setValidated(true);
                }

                TypeMirror type = variable.asType();
                if ((type.getKind() == TypeKind.ERROR) && !processingOver) {
                    throw new PostponeToNextRoundException();
                }

                Object fieldType = modelUtils.resolveTypeReference(type);

                if (isValue) {
                    writer.visitFieldValue(
                            modelUtils.resolveTypeReference(declaringClass),
                            fieldType,
                            fieldName.toString(),
                            requiresReflection,
                            fieldAnnotationMetadata,
                            genericUtils.resolveGenericTypes(type, Collections.emptyMap()),
                            isConfigurationPropertiesType
                    );
                } else {
                    writer.visitFieldInjectionPoint(
                            modelUtils.resolveTypeReference(declaringClass),
                            fieldType,
                            fieldName.toString(),
                            requiresReflection,
                            fieldAnnotationMetadata,
                            genericUtils.resolveGenericTypes(type, Collections.emptyMap())
                    );
                }
            }
            return null;
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
                if (!writer.isValidated() && fieldAnnotationMetadata.hasStereotype("javax.validation.Constraint")) {
                    writer.setValidated(true);
                }
                TypeMirror fieldTypeMirror = field.asType();
                Object fieldType = modelUtils.resolveTypeReference(fieldTypeMirror);

                TypeElement declaringClass = modelUtils.classElementFor(field);

                if (declaringClass == null) {
                    return null;
                }

                String fieldName = field.getSimpleName().toString();

                if (fieldAnnotationMetadata.hasStereotype(ConfigurationBuilder.class)) {

                    boolean accessible = false;
                    if (modelUtils.isPublic(field)) {
                        accessible = true;
                    } else if (modelUtils.isPackagePrivate(field) || modelUtils.isProtected(field)) {
                        PackageElement declaringPackage = elementUtils.getPackageOf(declaringClass);
                        PackageElement concretePackage = elementUtils.getPackageOf(this.concreteClass);
                        accessible = declaringPackage.getQualifiedName().equals(concretePackage.getQualifiedName());
                    }

                    if (accessible) {
                        writer.visitConfigBuilderField(fieldType, fieldName, fieldAnnotationMetadata, metadataBuilder);
                    } else {
                        // Using the field would throw a IllegalAccessError, use the method instead
                        Optional<ExecutableElement> getterMethod = modelUtils.findGetterMethodFor(field);
                        if (getterMethod.isPresent()) {
                            writer.visitConfigBuilderMethod(fieldType, getterMethod.get().getSimpleName().toString(), fieldAnnotationMetadata, metadataBuilder);
                        } else {
                            error(field, "ConfigurationBuilder applied to a non accessible (private or package-private/protected in a different package) field must have a corresponding non-private getter method.");
                        }
                    }
                    try {
                        visitConfigurationBuilder(declaringClass, field, fieldTypeMirror, writer);
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
                                getPropertyMetadataTypeReference(fieldTypeMirror),
                                fieldName,
                                docComment,
                                null
                        );
                    } else {
                        boolean isPrivate = modelUtils.isPrivate(field);
                        boolean requiresReflection = isInheritedAndNotPublic(modelUtils.classElementFor(field), field.getModifiers());

                        if (!isPrivate) {
                            Object declaringType = modelUtils.resolveTypeReference(declaringClass);
                            String docComment = elementUtils.getDocComment(field);

                            PropertyMetadata propertyMetadata = metadataBuilder.visitProperty(
                                    concreteClass,
                                    declaringClass,
                                    getPropertyMetadataTypeReference(fieldTypeMirror),
                                    fieldName,
                                    docComment,
                                    null
                            );

                            fieldAnnotationMetadata = addPropertyMetadata(fieldAnnotationMetadata, propertyMetadata);
                            writer.visitFieldValue(
                                    declaringType,
                                    fieldType,
                                    fieldName,
                                    requiresReflection,
                                    fieldAnnotationMetadata,
                                    genericUtils.resolveGenericTypes(fieldTypeMirror, Collections.emptyMap()),
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

            PublicMethodVisitor visitor = new PublicMethodVisitor(typeUtils) {
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
                    if (paramCount < 2) {
                        VariableElement paramType = paramCount == 1 ? params.get(0) : null;
                        Object expectedType = paramType != null ? modelUtils.resolveTypeReference(paramType.asType()) : null;

                        PropertyMetadata metadata = metadataBuilder.visitProperty(
                                concreteClass,
                                declaringClass,
                                expectedType != null ? expectedType.toString() : null,
                                configurationPrefix + propertyName,
                                null,
                                null
                        );

                        writer.visitConfigBuilderMethod(
                                prefix,
                                modelUtils.resolveTypeReference(method.getReturnType()),
                                methodName,
                                expectedType,
                                paramType != null ? genericUtils.resolveGenericTypes(paramType.asType(), Collections.emptyMap()) : null,
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
                                    modelUtils.resolveTypeReference(method.getReturnType()),
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
            AnnotationMetadata annotationMetadata = annotationUtils.getAnnotationMetadata(typeElement);

            PackageElement packageElement = elementUtils.getPackageOf(typeElement);
            String beanClassName = modelUtils.simpleBinaryNameFor(typeElement);

            boolean isInterface = JavaModelUtils.isInterface(typeElement);

            if (configurationMetadata != null) {
                // unfortunate we have to do this
                String existingPrefix = annotationMetadata.getValue(
                        ConfigurationReader.class,
                        "prefix", String.class)
                        .orElse("");

                annotationMetadata = DefaultAnnotationMetadata.mutateMember(
                        annotationMetadata,
                        ConfigurationReader.class.getName(),
                        "prefix",
                        StringUtils.isNotEmpty(existingPrefix) ? existingPrefix + "." + configurationMetadata.getName() : configurationMetadata.getName()
                );
            }


            BeanDefinitionWriter beanDefinitionWriter = new BeanDefinitionWriter(
                    packageElement.getQualifiedName().toString(),
                    beanClassName,
                    providerTypeParam == null
                            ? elementUtils.getBinaryName(typeElement).toString()
                            : providerTypeParam.toString(),
                    isInterface,
                    annotationMetadata);

            visitTypeArguments(typeElement, beanDefinitionWriter);

            return beanDefinitionWriter;
        }

        private void visitTypeArguments(TypeElement typeElement, BeanDefinitionWriter beanDefinitionWriter) {
            Map<String, Map<String, Object>> typeArguments = genericUtils.buildGenericTypeArgumentInfo(typeElement);
            beanDefinitionWriter.visitTypeArguments(
                    typeArguments
            );
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
                    annotationMetadata,
                    interfaceTypes,
                    interceptorTypes);

            if (ArrayUtils.isNotEmpty(interfaceTypes)) {
                List<? extends AnnotationMirror> annotationMirrors = typeElement.getAnnotationMirrors();
                Set<TypeElement> additionalInterfaces = new HashSet<>(3);
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
                    annotationMetadata);
        }

        private ExecutableElementParamInfo populateParameterData(@Nullable String declaringTypeName, ExecutableElement element, Map<String, Object> boundTypes) {
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
            final boolean isConstructBinding = elementMetadata.hasDeclaredStereotype(ConfigurationInject.class);
            if (isConstructBinding) {
                    this.configurationMetadata = metadataBuilder.visitProperties(
                            concreteClass,
                            null);

            }

            element.getParameters().forEach(paramElement -> {

                String argName = paramElement.getSimpleName().toString();
                
                AnnotationMetadata annotationMetadata = annotationUtils.getAnnotationMetadata(paramElement);
                if (annotationMetadata.hasDeclaredAnnotation("org.jetbrains.annotations.Nullable")) {
                    annotationMetadata = DefaultAnnotationMetadata.mutateMember(annotationMetadata, "javax.annotation.Nullable", Collections.emptyMap());
                }

                if (annotationMetadata.hasStereotype(ANN_CONSTRAINT)) {
                    params.setValidated(true);
                }

                TypeMirror typeMirror = paramElement.asType();
                if (isConstructBinding) {
                    if (Stream.of(Property.class, Value.class, Parameter.class).noneMatch(annotationMetadata::hasAnnotation)) {
                        final Element parameterElement = typeUtils.asElement(typeMirror);
                        final AnnotationMetadata parameterTypeMetadata = parameterElement != null ? annotationUtils.getAnnotationMetadata(parameterElement) : AnnotationMetadata.EMPTY_METADATA;
                        if (!parameterTypeMetadata.hasStereotype(Scope.class)) {
                            annotationMetadata = addPropertyMetadata(annotationMetadata, paramElement, argName);
                        }
                    }
                }
                params.addAnnotationMetadata(argName, annotationMetadata);


                TypeKind kind = typeMirror.getKind();
                if ((kind == TypeKind.ERROR) && !processingOver) {
                    throw new PostponeToNextRoundException();    
                }

                switch (kind) {
                    case ARRAY:
                        ArrayType arrayType = (ArrayType) typeMirror;
                        TypeMirror componentType = arrayType.getComponentType();
                        Object resolvedType = modelUtils.resolveTypeReference(arrayType);
                        params.addParameter(
                                argName,
                                resolvedType,
                                genericUtils.resolveTypeReference(arrayType, boundTypes)
                        );
                        params.addGenericTypes(argName, Collections.singletonMap("E", modelUtils.resolveTypeReference(componentType)));

                        break;
                    case TYPEVAR:
                        TypeVariable typeVariable = (TypeVariable) typeMirror;

                        DeclaredType parameterType = genericUtils.resolveTypeVariable(paramElement, typeVariable);
                        if (parameterType != null) {

                            params.addParameter(
                                    argName,
                                    modelUtils.resolveTypeReference(typeVariable),
                                    genericUtils.resolveTypeReference(typeVariable, boundTypes)
                            );
                            params.addGenericTypes(argName, Collections.singletonMap(typeVariable.toString(), genericUtils.resolveTypeReference(typeVariable, boundTypes)));
                        } else {
                            error(element, "Unprocessable generic type [%s] for param [%s] of element %s", typeVariable, paramElement, element);
                        }

                        break;
                    case DECLARED:
                        DeclaredType declaredType = (DeclaredType) typeMirror;


                        TypeElement typeElement = elementUtils.getTypeElement(typeUtils.erasure(declaredType).toString());
                        if (typeElement == null) {
                            typeElement = (TypeElement) declaredType.asElement();
                        }

                        Object type = modelUtils.resolveTypeReference(typeElement);
                        params.addParameter(
                                argName,
                                type,
                                type
                        );

                        Map<String, Object> resolvedParameters = genericUtils.resolveGenericTypes(declaredType, typeElement, boundTypes);
                        if (!resolvedParameters.isEmpty()) {
                            params.addGenericTypes(argName, resolvedParameters);
                        }
                        break;
                    default:
                        if (kind.isPrimitive()) {
                            String typeName;
                            if (typeMirror instanceof DeclaredType) {
                                DeclaredType dt = (DeclaredType) typeMirror;
                                typeName = dt.asElement().getSimpleName().toString();
                            } else {
                                typeName = modelUtils.resolveTypeName(typeMirror);
                            }
                            Object argType = modelUtils.classOfPrimitiveFor(typeName);
                            params.addParameter(argName, argType, argType);
                        } else {
                            error(element, "Unprocessable element type [%s] for param [%s] of element %s", kind, paramElement, element);
                        }
                }
            });

            return params;
        }

        private boolean shouldExclude(Set<String> includes, Set<String> excludes, String propertyName) {
            if (!includes.isEmpty() && !includes.contains(propertyName)) {
                return true;
            }
            if (!excludes.isEmpty() && excludes.contains(propertyName)) {
                return true;
            }
            return false;
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

    /**
     * Exception to indicate postponing processing to next round.
     */
    private static class PostponeToNextRoundException extends RuntimeException {

    }

}
