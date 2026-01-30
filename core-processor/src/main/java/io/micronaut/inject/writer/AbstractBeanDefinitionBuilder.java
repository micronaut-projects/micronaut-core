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
package io.micronaut.inject.writer;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ElementBeanDefinitionBuilder;
import io.micronaut.inject.ElementBeanDefinitionBuilderFactory;
import io.micronaut.inject.ElementProxyBuilder;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.beans.BeanConstructorElement;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import io.micronaut.inject.ast.beans.BeanFieldElement;
import io.micronaut.inject.ast.beans.BeanMethodElement;
import io.micronaut.inject.ast.beans.BeanParameterElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.utils.BeanInjectionUtils;
import io.micronaut.inject.visitor.VisitorContext;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static io.micronaut.inject.ast.beans.BeanParameterElement.ZERO_BEAN_PARAMETER_ELEMENTS;

/**
 * Abstract implementation of the {@link BeanElementBuilder} interface that should be implemented by downstream language specific implementations.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@NullUnmarked
@Internal
public abstract class AbstractBeanDefinitionBuilder implements BeanElementBuilder {
    private static final Map<String, AtomicInteger> BEAN_COUNTER = new HashMap<>(15);
    private static final Predicate<Set<ElementModifier>> PUBLIC_FILTER = (
        elementModifiers -> elementModifiers.contains(ElementModifier.PUBLIC));
    private static final Predicate<Set<ElementModifier>> NON_PUBLIC_FILTER = (
        elementModifiers -> !elementModifiers.contains(ElementModifier.PUBLIC));
    private static final Comparator<MemberElement> SORTER = (o1, o2) -> {
        final ClassElement d1 = o1.getDeclaringType();
        final ClassElement d2 = o2.getDeclaringType();
        final String o1Type = d1.getName();
        final String o2Type = d2.getName();
        if (o1Type.equals(o2Type)) {
            if (o1 instanceof FieldElement && o2 instanceof MethodElement) {
                return -1;
            }
            if (o1 instanceof MethodElement && o2 instanceof FieldElement) {
                return 1;
            }
            return 0;
        }
        if (d1.isAssignable(d2)) {
            return 1;
        }
        if (d2.isAssignable(d1)) {
            return -1;
        }
        return 0;
    };
    protected final VisitorContext visitorContext;
    protected final ElementAnnotationMetadataFactory elementAnnotationMetadataFactory;
    private final Element originatingElement;
    private final ClassElement originatingType;
    private ClassElement beanType;
    private final int identifier;
    private final MutableAnnotationMetadata annotationMetadata;
    private final List<BeanMethodElement> executableMethods = new ArrayList<>(5);
    private final List<BeanMethodElement> interceptedMethods = new ArrayList<>(5);
    private final List<AbstractBeanDefinitionBuilder> childBeans = new ArrayList<>(5);
    private final List<BeanMethodElement> injectedMethods = new ArrayList<>(5);
    private final List<BeanMethodElement> preDestroyMethods = new ArrayList<>(5);
    private final List<BeanMethodElement> postConstructMethods = new ArrayList<>(5);
    private final List<BeanFieldElement> injectedFields = new ArrayList<>(5);
    private BeanConstructorElement constructorElement;
    private ClassElement[] exposedTypes;
    private boolean intercepted;

    /**
     * Default constructor.
     *
     * @param originatingElement               The originating element
     * @param beanType                         The bean type
     * @param visitorContext                   the visitor context
     * @param elementAnnotationMetadataFactory The element annotation metadata factory
     */
    protected AbstractBeanDefinitionBuilder(
        Element originatingElement,
        ClassElement beanType,
        VisitorContext visitorContext,
        ElementAnnotationMetadataFactory elementAnnotationMetadataFactory) {
        this.originatingElement = originatingElement;
        this.elementAnnotationMetadataFactory = elementAnnotationMetadataFactory;
        if (originatingElement instanceof MethodElement element) {
            this.originatingType = element.getDeclaringType();
        } else if (originatingElement instanceof ClassElement element) {
            this.originatingType = element;
        } else {
            throw new IllegalArgumentException("Invalid originating element: " + originatingElement);
        }
        this.beanType = beanType;
        this.visitorContext = visitorContext;
        this.identifier = BEAN_COUNTER.computeIfAbsent(beanType.getName(), (s) -> new AtomicInteger(0))
            .getAndIncrement();
        this.annotationMetadata = MutableAnnotationMetadata.of(beanType.getAnnotationMetadata());
        this.annotationMetadata.addDeclaredAnnotation(Bean.class.getName(), Collections.emptyMap());
        this.constructorElement = initConstructor(beanType);
    }

    @Override
    public BeanElementBuilder intercept(AnnotationValue<?>... annotationValue) {
        for (AnnotationValue<?> value : annotationValue) {
            annotate(value);
        }
        this.intercepted = true;
        return this;
    }

    @Internal
    public static <R> List<R> build(List<AbstractBeanDefinitionBuilder> beanDefinitionBuilders,
                                    ElementBeanDefinitionBuilderFactory<R> beanDefinitionBuilderFactory)
        throws IOException {
        List<R> result = new ArrayList<>();
        for (AbstractBeanDefinitionBuilder beanDefinitionBuilder : beanDefinitionBuilders) {
            result.addAll(beanDefinitionBuilder.build(beanDefinitionBuilderFactory));
            final List<AbstractBeanDefinitionBuilder> childBeans = beanDefinitionBuilder.getChildBeans();
            for (AbstractBeanDefinitionBuilder childBean : childBeans) {
                result.addAll(childBean.build(beanDefinitionBuilderFactory));
            }
        }
        return result;
    }

    private InternalBeanConstructorElement initConstructor(ClassElement beanType) {
        return beanType.getPrimaryConstructor().map(m -> new InternalBeanConstructorElement(
            m,
            !m.isPublic(),
            initBeanParameters(m.getParameters())
        )).orElse(null);
    }

    /**
     * Is the bean to be built intercepted?
     *
     * @return True if it is
     */
    protected boolean isIntercepted() {
        return this.intercepted || !this.interceptedMethods.isEmpty();
    }

    @Override
    public BeanElementBuilder inject() {
        processInjectedFields();
        processInjectedMethods();
        return this;
    }

    /**
     * Any child bean definitions.
     *
     * @return The child beans
     */
    public List<AbstractBeanDefinitionBuilder> getChildBeans() {
        return childBeans;
    }

    private void processInjectedFields() {
        final ElementQuery<FieldElement> baseQuery = ElementQuery.ALL_FIELDS
            .onlyInstance()
            .onlyInjected();
        Set<FieldElement> accessibleFields = new HashSet<>();
        this.beanType.getEnclosedElements(baseQuery.modifiers(PUBLIC_FILTER))
            .forEach(fieldElement -> {
                accessibleFields.add(fieldElement);
                new InternalBeanElementField(fieldElement, false).inject();
            });
        this.beanType.getEnclosedElements(baseQuery.modifiers(NON_PUBLIC_FILTER))
            .forEach(fieldElement -> {
                if (!accessibleFields.contains(fieldElement)) {
                    new InternalBeanElementField(fieldElement, true).inject();
                }
            });
    }

    private void processInjectedMethods() {
        final ElementQuery<MethodElement> baseQuery = ElementQuery.ALL_METHODS
            .onlyInstance()
            .onlyConcrete()
            .onlyInjected();
        Set<MethodElement> accessibleMethods = new HashSet<>();
        this.beanType.getEnclosedElements(baseQuery.modifiers(PUBLIC_FILTER))
            .forEach(methodElement -> {
                accessibleMethods.add(methodElement);
                addMethod(methodElement, false);
            });
        this.beanType.getEnclosedElements(baseQuery.modifiers(NON_PUBLIC_FILTER))
            .forEach(methodElement -> {
                if (!accessibleMethods.contains(methodElement)) {
                    addMethod(methodElement, true);
                }
            });
    }

    private void addMethod(MethodElement methodElement, boolean requiresReflection) {
        boolean lifecycleMethod = false;
        if (methodElement.getAnnotationMetadata().hasDeclaredAnnotation(AnnotationUtil.PRE_DESTROY)) {
            new InternalBeanElementMethod(methodElement, requiresReflection)
                .preDestroy();
            lifecycleMethod = true;
        }
        if (methodElement.getAnnotationMetadata().hasDeclaredAnnotation(AnnotationUtil.POST_CONSTRUCT)) {
            new InternalBeanElementMethod(methodElement, requiresReflection)
                .postConstruct();
            lifecycleMethod = true;
        }
        if (!lifecycleMethod) {
            new InternalBeanElementMethod(methodElement, requiresReflection)
                .inject();
        }
    }

    @Override
    public Element getOriginatingElement() {
        return originatingElement;
    }

    @Override
    public ClassElement getBeanType() {
        return beanType;
    }

    /**
     * Initialize the bean parameters.
     *
     * @param constructorParameters The parameters to use.
     * @return The initialized parameters
     */
    protected final BeanParameterElement[] initBeanParameters(ParameterElement [] constructorParameters) {
        if (ArrayUtils.isNotEmpty(constructorParameters)) {
            return Arrays.stream(constructorParameters)
                .map(InternalBeanParameter::new)
                .toArray(BeanParameterElement[]::new);
        } else {
            return ZERO_BEAN_PARAMETER_ELEMENTS;
        }
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return this.annotationMetadata;
    }

    @Override
    public BeanElementBuilder createWith(MethodElement element) {
        // TODO: handle factories, static methods etc.
        //noinspection ConstantConditions
        if (element != null) {
            constructorElement = new InternalBeanConstructorElement(
                element,
                !element.isPublic(),
                initBeanParameters(element.getParameters())
            );
        }
        return this;
    }

    @Override
    public BeanElementBuilder typed(ClassElement... types) {
        if (ArrayUtils.isNotEmpty(types)) {
            this.exposedTypes = types;
        }
        return this;
    }

    @Override
    public BeanElementBuilder typeArguments(ClassElement... types) {
        final Map<String, ClassElement> typeArguments = this.beanType.getTypeArguments();
        Map<String, ClassElement> resolvedTypes = resolveTypeArguments(typeArguments, types);
        if (resolvedTypes != null) {
            this.beanType = beanType.withTypeArguments(resolvedTypes);
        }
        return this;
    }

    @Override
    public BeanElementBuilder typeArgumentsForType(ClassElement type, ClassElement... types) {
        if (type != null) {
            final Map<String, ClassElement> typeArguments = type.getTypeArguments();
            Map<String, ClassElement> resolvedTypes = resolveTypeArguments(typeArguments, types);
        }
        return this;
    }

    @Nullable
    private Map<String, ClassElement> resolveTypeArguments(Map<String, ClassElement> typeArguments, ClassElement... types) {
        Map<String, ClassElement> resolvedTypes = null;
        if (typeArguments.size() == types.length) {
            resolvedTypes = CollectionUtils.newLinkedHashMap(typeArguments.size());
            final Iterator<String> i = typeArguments.keySet().iterator();
            for (ClassElement type : types) {
                final String variable = i.next();
                resolvedTypes.put(variable, type);
            }
        }
        return resolvedTypes;
    }

    @Override
    public BeanElementBuilder withConstructor(Consumer<BeanConstructorElement> constructorElement) {
        if (constructorElement != null && this.constructorElement != null) {
            constructorElement.accept(this.constructorElement);
        }
        return this;
    }

    @Override
    public BeanElementBuilder withMethods(ElementQuery<MethodElement> methods, Consumer<BeanMethodElement> beanMethods) {
        //noinspection ConstantConditions
        if (methods != null && beanMethods != null) {
            final ElementQuery<MethodElement> baseQuery = methods.onlyInstance();
            this.beanType.getEnclosedElements(baseQuery.modifiers(m -> m.contains(ElementModifier.PUBLIC)))
                .forEach(methodElement ->
                    beanMethods.accept(new InternalBeanElementMethod(methodElement, false))
                );
            this.beanType.getEnclosedElements(baseQuery.modifiers(m -> !m.contains(ElementModifier.PUBLIC)))
                .forEach(methodElement ->
                    beanMethods.accept(new InternalBeanElementMethod(methodElement, true))
                );
        }
        return this;
    }

    @Override
    public BeanElementBuilder withFields(ElementQuery<FieldElement> fields, Consumer<BeanFieldElement> beanFields) {
        //noinspection ConstantConditions
        if (fields != null && beanFields != null) {
            this.beanType.getEnclosedElements(fields.onlyInstance().onlyAccessible(originatingType))
                .forEach((fieldElement) ->
                    beanFields.accept(new InternalBeanElementField(fieldElement, false))
                );
        }
        return this;
    }

    @Override
    public BeanElementBuilder withParameters(Consumer<BeanParameterElement[]> parameters) {
        if (parameters != null && this.constructorElement != null) {
            parameters.accept(getParameters());
        }
        return this;
    }

    /**
     * @return The bean creation parameters.
     */
    protected BeanParameterElement [] getParameters() {
        return constructorElement.getParameters();
    }

    @Override
    public String getName() {
        return beanType.getName();
    }

    @Override
    public boolean isProtected() {
        return beanType.isProtected();
    }

    @Override
    public boolean isPublic() {
        return beanType.isPublic();
    }

    @Override
    public Object getNativeType() {
        return beanType;
    }

    @Override
    public <T extends Annotation> BeanElementBuilder annotate(String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        annotate(this.annotationMetadata, annotationType, consumer);
        return this;
    }

    @Override
    public <T extends Annotation> Element annotate(AnnotationValue<T> annotationValue) {
        annotate(this.annotationMetadata, annotationValue);
        return this;
    }

    @Override
    public BeanElementBuilder removeAnnotation(String annotationType) {
        removeAnnotation(this.annotationMetadata, annotationType);
        return this;
    }

    @Override
    public <T extends Annotation> BeanElementBuilder removeAnnotationIf(Predicate<AnnotationValue<T>> predicate) {
        removeAnnotationIf(this.annotationMetadata, predicate);
        return this;
    }

    @Override
    public BeanElementBuilder removeStereotype(String annotationType) {
        removeStereotype(this.annotationMetadata, annotationType);
        return this;
    }

    private BeanElementBuilder addChildBean(MethodElement producerMethod, Consumer<BeanElementBuilder> childBeanBuilder) {
        final AbstractBeanDefinitionBuilder childBuilder = createChildBean(producerMethod);
        this.childBeans.add(childBuilder);
        if (childBeanBuilder != null) {
            childBeanBuilder.accept(childBuilder);
        }
        return this;
    }

    private BeanElementBuilder addChildBean(FieldElement producerMethod, Consumer<BeanElementBuilder> childBeanBuilder) {
        final AbstractBeanDefinitionBuilder childBuilder = createChildBean(producerMethod);
        this.childBeans.add(childBuilder);
        if (childBeanBuilder != null) {
            childBeanBuilder.accept(childBuilder);
        }
        return this;
    }

    @Override
    public <E extends MemberElement> BeanElementBuilder produceBeans(ElementQuery<E> methodsOrFields,
                                                                     Consumer<BeanElementBuilder> childBeanBuilder) {
        methodsOrFields = methodsOrFields
            .onlyConcrete()
            .modifiers(modifiers -> modifiers.contains(ElementModifier.PUBLIC));
        final List<E> enclosedElements = this.beanType.getEnclosedElements(methodsOrFields);
        for (E enclosedElement : enclosedElements) {
            if (enclosedElement instanceof FieldElement fe) {
                final ClassElement type = fe.getGenericField().getType();
                if (type.isPublic() && !type.isPrimitive()) {
                    addChildBean(fe, childBeanBuilder);
                }
            }

            if (enclosedElement instanceof MethodElement me && !(enclosedElement instanceof ConstructorElement)) {
                final ClassElement type = me.getGenericReturnType().getType();
                if (type.isPublic() && !type.isPrimitive()) {
                    addChildBean(me, childBeanBuilder);
                }
            }
        }
        return this;
    }

    /**
     * Creates a child bean for the given producer field.
     *
     * @param producerField The producer field
     * @return The child bean builder
     */
    protected abstract AbstractBeanDefinitionBuilder createChildBean(FieldElement producerField);

    /**
     * Visit the intercepted methods of this type.
     *
     * @param <R>         The builder result type
     * @param proxyBuilder The proxy builder
     */
    protected <R> void visitInterceptedMethods(ElementProxyBuilder<R> proxyBuilder) {
        ClassElement beanClass = getBeanType();
        if (CollectionUtils.isNotEmpty(interceptedMethods)) {
            for (BeanMethodElement interceptedMethod : interceptedMethods) {
                addProxyMethod(proxyBuilder, interceptedMethod);
            }
        }

        if (this.intercepted) {
            beanClass.getEnclosedElements(
                ElementQuery.ALL_METHODS
                    .onlyInstance()
                    .modifiers(mods -> !mods.contains(ElementModifier.FINAL) && mods.contains(ElementModifier.PUBLIC))
            ).forEach(method -> {
                InternalBeanElementMethod ibem = new InternalBeanElementMethod(
                    method,
                    true
                );
                if (!interceptedMethods.contains(ibem)) {
                    addProxyMethod(proxyBuilder, ibem);
                }
            });
        }
    }

    private <R> void addProxyMethod(ElementProxyBuilder<R> proxyBuilder, MethodElement method) {
        proxyBuilder.addProxyMethod(
            method.withAnnotationMetadata(new AnnotationMetadataHierarchy(getAnnotationMetadata(), method.getAnnotationMetadata()))
        );
    }

    /**
     * Creates a child bean for the given producer method.
     *
     * @param producerMethod The producer method
     * @return The child bean builder
     */
    protected abstract AbstractBeanDefinitionBuilder createChildBean(MethodElement producerMethod);

    /**
     * Build the bean definition writer.
     *
     * @param beanDefinitionBuilderFactory The bean definition builder factory
     * @param <R>                          The builder result type
     * @return The generated bean definitions
     */
    public <R> List<R> build(ElementBeanDefinitionBuilderFactory<R> beanDefinitionBuilderFactory) {
        ElementBeanDefinitionBuilder<R> beanDefinitionBuilder = buildClass(beanDefinitionBuilderFactory);
        AnnotationMetadata thisAnnotationMetadata = getAnnotationMetadata();
        if (isIntercepted()) {
            ElementProxyBuilder<R> proxyBuilder = createProxyBuilder(beanDefinitionBuilderFactory, beanDefinitionBuilder, thisAnnotationMetadata);

            configureInjectionPoints(proxyBuilder.beanDefinitionBuilder());

            visitInterceptedMethods(
                proxyBuilder
            );
            return CollectionUtils.concat(
                beanDefinitionBuilder.build(),
                proxyBuilder.build()
            );
        }
        return beanDefinitionBuilder.build();
    }

    /**
     * Creates the proxy builder.
     *
     * @param beanDefinitionBuilderFactory The factory used to create element builders
     * @param beanDefinitionBuilder The bean definition builder
     * @param annotationMetadata   The annotation metadata
     * @param <R>                  The builder result type
     * @return The proxy builder
     */
    protected final <R> ElementProxyBuilder<R> createProxyBuilder(ElementBeanDefinitionBuilderFactory<R> beanDefinitionBuilderFactory,
                                                           ElementBeanDefinitionBuilder<R> beanDefinitionBuilder,
                                                           AnnotationMetadata annotationMetadata) {
        return beanDefinitionBuilderFactory.aroundProxy(getBeanType(), annotationMetadata, beanDefinitionBuilder);
    }

    private <R> ElementBeanDefinitionBuilder<R> buildClass(ElementBeanDefinitionBuilderFactory<R> beanDefinitionBuilderFactory) {
        final ElementBeanDefinitionBuilder<R> beanDefinitionBuilder = createBeanDefinitionBuilderInternal(beanDefinitionBuilderFactory);
        configureInjectionPoints(beanDefinitionBuilder);

        for (BeanMethodElement postConstructMethod : postConstructMethods) {
            if (postConstructMethod.getDeclaringType().equals(beanType)) {
                beanDefinitionBuilder.addPostConstruct(
                    postConstructMethod,
                    postConstructMethod.isReflectionRequired(),
                    visitorContext
                );
            }
        }

        for (BeanMethodElement preDestroyMethod : preDestroyMethods) {
            if (preDestroyMethod.getDeclaringType().equals(beanType)) {
                beanDefinitionBuilder.addPreDestroy(
                    preDestroyMethod,
                    preDestroyMethod.isReflectionRequired(),
                    visitorContext
                );
            }
        }
        return beanDefinitionBuilder;
    }

    private <R> void configureInjectionPoints(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder) {
        Map<ClassElement, List<MemberElement>> sortedInjections = new LinkedHashMap<>();
        List<MemberElement> allInjected = new ArrayList<>();
        allInjected.addAll(injectedFields);
        allInjected.addAll(injectedMethods);
        allInjected.sort(SORTER);
        for (MemberElement memberElement : allInjected) {
            final List<MemberElement> list = sortedInjections
                .computeIfAbsent(memberElement.getDeclaringType(),
                    classElement -> new ArrayList<>()
                );
            list.add(memberElement);
        }
        for (List<MemberElement> list : sortedInjections.values()) {
            for (MemberElement memberElement : list) {
                if (memberElement instanceof FieldElement) {
                    InternalBeanElementField ibf = (InternalBeanElementField) memberElement;
                    ibf.<InternalBeanElementField>with(element ->
                        visitField(beanDefinitionBuilder, element, element)
                    );

                } else {
                    InternalBeanElementMethod ibm = (InternalBeanElementMethod) memberElement;
                    ibm.<InternalBeanElementMethod>with(element ->
                        beanDefinitionBuilder.addMethodInjection(
                            ibm,
                            ibm.isReflectionRequired(),
                            visitorContext
                        )
                    );

                }
            }
        }

        for (BeanMethodElement executableMethod : executableMethods) {
            beanDefinitionBuilder.addExecutableMethod(
                executableMethod,
                executableMethod.isReflectionRequired(beanType)
            );
        }
    }

    /**
     * Creates the {@link ElementBeanDefinitionBuilder} that will materialize the bean.
     * Subclasses overriding this method should return a builder created via the supplied factory
     * and must ensure the associated constructor has been resolved (for example by invoking
     * {@link #initConstructor(ClassElement)} when customizing the target type).
     *
     * @param elementBeanDefinitionBuilderFactory The factory used to create element builders
     * @param <R>                                 The builder result type
     * @return The element bean definition builder for the current bean
     */
    protected <R> ElementBeanDefinitionBuilder<R> createBeanDefinitionBuilder(ElementBeanDefinitionBuilderFactory<R> elementBeanDefinitionBuilderFactory) {
        Element producingElement = getProducingElement();
        if (producingElement instanceof ClassElement) {
            if (constructorElement == null) {
                constructorElement = initConstructor(beanType);
            }
        }
        if (constructorElement == null) {
            throw new ProcessingException(originatingElement, "Cannot create associated bean with no accessible primary constructor. Consider supply the constructor with createWith(..)");
        }
        return elementBeanDefinitionBuilderFactory.constructor(
            BeanInjectionUtils.createConstructorDefinition(constructorElement, constructorElement, visitorContext, !constructorElement.isPublic()),
            getAssociatedBeanName(identifier, originatingType, beanType),
            annotationMetadata
        );
    }

    private String getAssociatedBeanName(Integer uniqueIdentifier, ClassElement originatingClass, ClassElement beanType) {
        return originatingClass.getPackageName() + "." + prefixClassName(originatingClass.getSimpleName()) + prefixClassName(beanType.getSimpleName()) + uniqueIdentifier;
    }

    private static String prefixClassName(String className) {
        if (className.startsWith("$")) {
            return className;
        }
        return "$" + className;
    }

    private <R> ElementBeanDefinitionBuilder<R> createBeanDefinitionBuilderInternal(ElementBeanDefinitionBuilderFactory<R> elementBeanDefinitionBuilderFactory) {
        addExposedTypes();
        return createBeanDefinitionBuilder(elementBeanDefinitionBuilderFactory);
    }

    private void addExposedTypes() {
        if (exposedTypes != null) {
            final AnnotationClassValue<?>[] annotationClassValues =
                Arrays.stream(exposedTypes).map(ce -> new AnnotationClassValue<>(ce.getName())).toArray(AnnotationClassValue[]::new);
            annotate(Bean.class, builder -> builder.member("typed", annotationClassValues));
        }
    }

    private void visitField(ElementBeanDefinitionBuilder beanDefinitionBuilder,
                            BeanFieldElement injectedField,
                            InternalBeanElementField ibf) {
        if (injectedField.hasAnnotation(Value.class) || injectedField.hasAnnotation(Property.class)) {
            beanDefinitionBuilder.addFieldPropertyInjection(
                injectedField,
                injectedField,
                ibf.isReflectionRequired(),
                visitorContext
            );
        } else {
            beanDefinitionBuilder.addFieldInjection(ibf, ibf.isReflectionRequired(), visitorContext);
        }
    }

    /**
     * Add an annotation to the given metadata.
     *
     * @param annotationMetadata The annotation metadata
     * @param annotationType     the annotation type
     * @param consumer           The builder
     * @param <T>                The annotation generic type
     */
    protected abstract <T extends Annotation> void annotate(AnnotationMetadata annotationMetadata, String annotationType, Consumer<AnnotationValueBuilder<T>> consumer);

    /**
     * Add an annotation to the given metadata.
     *
     * @param annotationMetadata The annotation metadata
     * @param annotationValue    The value
     * @param <T>                The annotation generic type
     * @since 3.3.0
     */
    protected abstract <T extends Annotation> void annotate(AnnotationMetadata annotationMetadata, AnnotationValue<T> annotationValue);

    /**
     * Remove a stereotype from the given metadata.
     *
     * @param annotationMetadata The metadata
     * @param annotationType     The stereotype
     */
    protected abstract void removeStereotype(AnnotationMetadata annotationMetadata, String annotationType);

    /**
     * Remove an annotation if it matches the given condition.
     *
     * @param annotationMetadata The metadata
     * @param predicate          The predicate
     * @param <T>                The annotation type
     */
    protected abstract <T extends Annotation> void removeAnnotationIf(AnnotationMetadata annotationMetadata, Predicate<AnnotationValue<T>> predicate);

    /**
     * Remove an annotation for the given name.
     *
     * @param annotationMetadata The metadata
     * @param annotationType     The type
     */
    protected abstract void removeAnnotation(AnnotationMetadata annotationMetadata, String annotationType);

    /**
     * Super class for all bean elements.
     *
     * @param <E> The element type
     */
    private abstract class InternalBeanElement<E extends Element> implements Element {
        protected AnnotationMetadata currentMetadata;
        private final E element;
        private final MutableAnnotationMetadata elementMetadata;

        private InternalBeanElement(E element, MutableAnnotationMetadata elementMetadata) {
            this.element = element;
            this.elementMetadata = elementMetadata;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            InternalBeanElement<?> that = (InternalBeanElement<?>) o;
            return element.equals(that.element);
        }

        @Override
        public int hashCode() {
            return Objects.hash(element);
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            if (currentMetadata != null) {
                return currentMetadata;
            }
            return elementMetadata;
        }

        @Override
        public String getName() {
            return element.getName();
        }

        @Override
        public boolean isProtected() {
            return element.isProtected();
        }

        @Override
        public boolean isPublic() {
            return element.isPublic();
        }

        @Override
        public Object getNativeType() {
            return element.getNativeType();
        }

        @Override
        public <T extends Annotation> Element annotate(String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
            AbstractBeanDefinitionBuilder.this.annotate(elementMetadata, annotationType, consumer);
            return this;
        }

        @Override
        public <T extends Annotation> Element annotate(AnnotationValue<T> annotationValue) {
            AbstractBeanDefinitionBuilder.this.annotate(elementMetadata, annotationValue);
            return this;
        }

        @Override
        public Element removeAnnotation(String annotationType) {
            AbstractBeanDefinitionBuilder.this.removeAnnotation(elementMetadata, annotationType);
            return this;
        }

        @Override
        public <T extends Annotation> Element removeAnnotationIf(Predicate<AnnotationValue<T>> predicate) {
            AbstractBeanDefinitionBuilder.this.removeAnnotationIf(elementMetadata, predicate);
            return this;
        }

        @Override
        public Element removeStereotype(String annotationType) {
            AbstractBeanDefinitionBuilder.this.removeStereotype(elementMetadata, annotationType);
            return this;
        }

        public <T extends InternalBeanElement<E>> void with(Consumer<T> consumer) {
            try {
                this.currentMetadata = elementMetadata.isEmpty() ? EMPTY_METADATA : elementMetadata;
                //noinspection unchecked
                consumer.accept((T) this);
            } finally {
                currentMetadata = null;
            }
        }
    }

    /**
     * Models a {@link BeanMethodElement}.
     */
    private final class InternalBeanElementMethod extends InternalBeanElement<MethodElement> implements BeanMethodElement {

        private final MethodElement methodElement;
        private final boolean requiresReflection;
        private BeanParameterElement[] beanParameters;

        private InternalBeanElementMethod(MethodElement methodElement, boolean requiresReflection) {
            this(methodElement, requiresReflection, initBeanParameters(methodElement.getParameters()));
        }

        private InternalBeanElementMethod(MethodElement methodElement,
                                          boolean requiresReflection,
                                          BeanParameterElement[] beanParameters) {
            super(methodElement, MutableAnnotationMetadata.of(methodElement.getAnnotationMetadata().getDeclaredMetadata()));
            this.methodElement = methodElement;
            this.requiresReflection = requiresReflection;
            this.beanParameters = beanParameters;
        }

        @Override
        public boolean isReflectionRequired() {
            return requiresReflection;
        }

        @Override
        public boolean isReflectionRequired(ClassElement callingType) {
            return requiresReflection;
        }

        @Override
        public boolean isPackagePrivate() {
            return methodElement.isPackagePrivate();
        }

        @Override
        public boolean isAbstract() {
            return methodElement.isAbstract();
        }

        @Override
        public boolean isStatic() {
            return methodElement.isStatic();
        }

        @Override
        public boolean isPrivate() {
            return methodElement.isPrivate();
        }

        @Override
        public boolean isFinal() {
            return methodElement.isFinal();
        }

        @Override
        public boolean isSuspend() {
            return methodElement.isSuspend();
        }

        @Override
        public boolean isDefault() {
            return methodElement.isDefault();
        }

        @Override
        public boolean isProtected() {
            return methodElement.isProtected();
        }

        @Override
        public boolean isPublic() {
            return methodElement.isPublic();
        }

        @Override
        public BeanMethodElement executable() {
            if (!AbstractBeanDefinitionBuilder.this.executableMethods.contains(this)) {
                AbstractBeanDefinitionBuilder.this.executableMethods.add(this);
            }
            return BeanMethodElement.super.executable();
        }

        @Override
        public BeanMethodElement intercept(AnnotationValue<?>... annotationValue) {
            if (!AbstractBeanDefinitionBuilder.this.interceptedMethods.contains(this)) {
                AbstractBeanDefinitionBuilder.this.interceptedMethods.add(this);
            }
            return BeanMethodElement.super.intercept(annotationValue);
        }

        @Override
        public BeanMethodElement executable(boolean processOnStartup) {
            if (!AbstractBeanDefinitionBuilder.this.executableMethods.contains(this)) {
                AbstractBeanDefinitionBuilder.this.executableMethods.add(this);
            }
            return BeanMethodElement.super.executable(processOnStartup);
        }

        @Override
        public BeanMethodElement inject() {
            if (!AbstractBeanDefinitionBuilder.this.injectedMethods.contains(this)) {
                AbstractBeanDefinitionBuilder.this.injectedMethods.add(this);
            }
            return BeanMethodElement.super.inject();
        }

        @Override
        public BeanMethodElement preDestroy() {
            if (!AbstractBeanDefinitionBuilder.this.preDestroyMethods.contains(this)) {
                AbstractBeanDefinitionBuilder.this.preDestroyMethods.add(this);
            }
            return BeanMethodElement.super.preDestroy();
        }

        @Override
        public BeanMethodElement postConstruct() {
            if (!AbstractBeanDefinitionBuilder.this.postConstructMethods.contains(this)) {
                AbstractBeanDefinitionBuilder.this.postConstructMethods.add(this);
            }
            return BeanMethodElement.super.postConstruct();
        }

        @Override
        public BeanParameterElement[] getParameters() {
            return this.beanParameters;
        }

        @Override
        public ClassElement getReturnType() {
            return methodElement.getReturnType();
        }

        @Override
        public ClassElement getGenericReturnType() {
            return methodElement.getGenericReturnType();
        }

        @Override
        public MethodElement withParameters(ParameterElement... newParameters) {
            this.beanParameters = initBeanParameters(newParameters);
            return this;
        }

        @Override
        public MethodElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
            this.currentMetadata = annotationMetadata;
            return this;
        }

        @Override
        public ClassElement getDeclaringType() {
            return methodElement.getDeclaringType();
        }

        @Override
        public ClassElement getOwningType() {
            return AbstractBeanDefinitionBuilder.this.beanType;
        }
    }

    /**
     * Models a {@link io.micronaut.inject.ast.beans.BeanConstructorElement}.
     */
    private final class InternalBeanConstructorElement extends InternalBeanElement<MethodElement> implements
        BeanConstructorElement {

        private final MethodElement methodElement;
        private final boolean requiresReflection;
        private BeanParameterElement[] beanParameters;

        private InternalBeanConstructorElement(MethodElement methodElement,
                                               boolean requiresReflection,
                                               BeanParameterElement[] beanParameters) {
            super(methodElement, MutableAnnotationMetadata.of(methodElement.getAnnotationMetadata()));
            this.methodElement = methodElement;
            this.requiresReflection = requiresReflection;
            this.beanParameters = beanParameters;
        }

        public boolean isRequiresReflection() {
            return requiresReflection;
        }

        @Override
        public boolean isPackagePrivate() {
            return methodElement.isPackagePrivate();
        }

        @Override
        public boolean isAbstract() {
            return methodElement.isAbstract();
        }

        @Override
        public boolean isStatic() {
            return methodElement.isStatic();
        }

        @Override
        public boolean isPrivate() {
            return methodElement.isPrivate();
        }

        @Override
        public boolean isFinal() {
            return methodElement.isFinal();
        }

        @Override
        public boolean isSuspend() {
            return methodElement.isSuspend();
        }

        @Override
        public boolean isDefault() {
            return methodElement.isDefault();
        }

        @Override
        public boolean isProtected() {
            return methodElement.isProtected();
        }

        @Override
        public boolean isPublic() {
            return methodElement.isPublic();
        }

        @Override
        public BeanParameterElement[] getParameters() {
            return this.beanParameters;
        }

        @Override
        public ClassElement getReturnType() {
            return methodElement.getReturnType();
        }

        @Override
        public ClassElement getGenericReturnType() {
            return methodElement.getGenericReturnType();
        }

        @Override
        public MethodElement withParameters(ParameterElement... newParameters) {
            this.beanParameters = initBeanParameters(newParameters);
            return this;
        }

        @Override
        public ClassElement getDeclaringType() {
            return methodElement.getDeclaringType();
        }

        @Override
        public ClassElement getOwningType() {
            return AbstractBeanDefinitionBuilder.this.beanType;
        }
    }

    /**
     * Models a {@link BeanFieldElement}.
     */
    private final class InternalBeanElementField extends InternalBeanElement<FieldElement> implements BeanFieldElement {
        private final FieldElement fieldElement;
        private ClassElement genericType;

        private InternalBeanElementField(FieldElement element, boolean requiresReflection) {
            super(element, MutableAnnotationMetadata.of(element.getAnnotationMetadata()));
            this.fieldElement = element;
        }

        @Override
        public BeanFieldElement inject() {
            if (!AbstractBeanDefinitionBuilder.this.injectedFields.contains(this)) {
                AbstractBeanDefinitionBuilder.this.injectedFields.add(this);
            }
            return BeanFieldElement.super.inject();
        }

        @Override
        public BeanFieldElement injectValue(String expression) {
            if (!AbstractBeanDefinitionBuilder.this.injectedFields.contains(this)) {
                AbstractBeanDefinitionBuilder.this.injectedFields.add(this);
            }
            return BeanFieldElement.super.injectValue(expression);
        }

        @Override
        public ClassElement getDeclaringType() {
            return fieldElement.getDeclaringType();
        }

        @Override
        public ClassElement getOwningType() {
            return AbstractBeanDefinitionBuilder.this.beanType;
        }

        @Override
        public ClassElement getType() {
            return fieldElement.getType();
        }

        @Override
        public ClassElement getGenericField() {
            if (genericType != null) {
                return genericType;
            } else {
                return fieldElement.getGenericField();
            }
        }

        @Override
        public BeanFieldElement typeArguments(ClassElement... types) {
            final ClassElement genericType = fieldElement.getGenericField();
            final Map<String, ClassElement> typeArguments = genericType.getTypeArguments();
            final Map<String, ClassElement> resolved = resolveTypeArguments(typeArguments, types);
            if (resolved != null) {
                this.genericType = genericType.withTypeArguments(resolved).withAnnotationMetadata(getAnnotationMetadata());
            }
            return this;
        }
    }

    /**
     * Models a {@link BeanParameterElement}.
     */
    private final class InternalBeanParameter extends InternalBeanElement<ParameterElement> implements BeanParameterElement {

        private final ParameterElement parameterElement;
        private ClassElement genericType;

        private InternalBeanParameter(ParameterElement element) {
            super(element, MutableAnnotationMetadata.of(element.getAnnotationMetadata()));
            parameterElement = element;
        }

        @Override
        public ClassElement getGenericType() {
            if (genericType != null) {
                return genericType;
            } else {
                return parameterElement.getGenericType();
            }
        }

        @Override
        public ClassElement getType() {
            return parameterElement.getType();
        }

        @Override
        public BeanParameterElement typeArguments(ClassElement... types) {
            final ClassElement genericType = parameterElement.getGenericType();
            final Map<String, ClassElement> typeArguments = genericType.getTypeArguments();
            final Map<String, ClassElement> resolved = resolveTypeArguments(typeArguments, types);
            if (resolved != null) {
                this.genericType = genericType.withTypeArguments(resolved).withAnnotationMetadata(getAnnotationMetadata());
            }
            return this;
        }
    }
}
