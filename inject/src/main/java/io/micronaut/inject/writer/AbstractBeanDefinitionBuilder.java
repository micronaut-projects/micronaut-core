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
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementFactory;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import io.micronaut.inject.ast.beans.BeanFieldElement;
import io.micronaut.inject.ast.beans.BeanMethodElement;
import io.micronaut.inject.ast.beans.BeanParameterElement;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Abstract implementation of the {@link BeanElementBuilder} interface that should be implemented by downstream language specific implementations.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
public abstract class AbstractBeanDefinitionBuilder implements BeanElementBuilder {
    private static final Map<String, AtomicInteger> BEAN_COUNTER = new HashMap<>(15);
    private static final Predicate<Set<ElementModifier>> PUBLIC_FILTER = (
            elementModifiers -> elementModifiers.contains(ElementModifier.PUBLIC));
    private static final Predicate<Set<ElementModifier>> NON_PUBLIC_FILTER = (
            elementModifiers -> !elementModifiers.contains(ElementModifier.PUBLIC));
    private final Element originatingElement;
    private final ClassElement originatingType;
    private final ClassElement beanType;
    private final ConfigurationMetadataBuilder<?> metadataBuilder;
    private final VisitorContext visitorContext;
    private final int identifier;
    private final MutableAnnotationMetadata annotationMetadata;
    private final List<BeanMethodElement> executableMethods = new ArrayList<>(5);
    private final List<BeanMethodElement> injectedMethods = new ArrayList<>(5);
    private final List<BeanMethodElement> preDestroyMethods = new ArrayList<>(5);
    private final List<BeanMethodElement> postConstructMethods = new ArrayList<>(5);
    private final List<BeanFieldElement> injectedFields = new ArrayList<>(5);
    private MethodElement constructorElement;
    private Map<String, Map<String, ClassElement>> typeArguments;
    private BeanParameterElement[] beanElementParameters;
    private ClassElement[] exposedTypes;

    /**
     * Default constructor.
     * @param originatingElement The originating element
     * @param beanType The bean type
     * @param metadataBuilder the metadata builder
     * @param visitorContext the visitor context
     */
    protected AbstractBeanDefinitionBuilder(
            Element originatingElement,
            ClassElement beanType,
            ConfigurationMetadataBuilder<?> metadataBuilder,
            VisitorContext visitorContext) {
        this.originatingElement = originatingElement;
        if (originatingElement instanceof MethodElement) {
            this.originatingType = ((MethodElement) originatingElement).getDeclaringType();
        } else if (originatingElement instanceof ClassElement) {
            this.originatingType = (ClassElement) originatingElement;
        } else {
            throw new IllegalArgumentException("Invalid originating element: " + originatingElement);
        }
        this.beanType = beanType;
        this.metadataBuilder = metadataBuilder;
        this.visitorContext = visitorContext;
        this.identifier = BEAN_COUNTER.computeIfAbsent(beanType.getName(), (s) -> new AtomicInteger(0))
                                      .getAndIncrement();
        final AnnotationMetadata annotationMetadata = beanType.getAnnotationMetadata();
        if (annotationMetadata instanceof MutableAnnotationMetadata) {
            this.annotationMetadata = ((MutableAnnotationMetadata) annotationMetadata).clone();
        } else {
            this.annotationMetadata = new MutableAnnotationMetadata();
        }
        this.annotationMetadata.addDeclaredAnnotation(Bean.class.getName(), Collections.emptyMap());
        final ParameterElement[] constructorParameters = beanType.getPrimaryConstructor().map(MethodElement::getParameters).orElse(null);
        this.beanElementParameters = initBeanParameters(constructorParameters);
    }

    @Override
    public BeanElementBuilder inject() {
        processInjectedMethods();
        processInjectedFields();
        return this;
    }

    private void processInjectedFields() {
        final ElementQuery<FieldElement> baseQuery = ElementQuery.ALL_FIELDS
                .onlyInstance()
                .annotated((metadata) -> metadata.hasDeclaredAnnotation(AnnotationUtil.INJECT));
        Set<FieldElement> accessibleFields = new HashSet<>();
        this.beanType.getEnclosedElements(baseQuery.modifiers(PUBLIC_FILTER))
                .forEach((fieldElement) -> {
                    accessibleFields.add(fieldElement);
                    new InternalBeanElementField(fieldElement, false).inject();
                });
        this.beanType.getEnclosedElements(baseQuery.modifiers(NON_PUBLIC_FILTER))
                .forEach((fieldElement) -> {
                    if (!accessibleFields.contains(fieldElement)) {
                        new InternalBeanElementField(fieldElement, true).inject();
                    }
                });
    }

    private void processInjectedMethods() {
        final ElementQuery<MethodElement> baseQuery = ElementQuery.ALL_METHODS
                .onlyInstance()
                .onlyConcrete()
                .annotated((metadata) ->
                   metadata.hasDeclaredAnnotation(AnnotationUtil.INJECT) ||
                           metadata.hasDeclaredAnnotation(PreDestroy.class) ||
                           metadata.hasDeclaredAnnotation(PostConstruct.class)
                );
        Set<MethodElement> accessibleMethods = new HashSet<>();
        this.beanType.getEnclosedElements(baseQuery.modifiers(PUBLIC_FILTER))
                .forEach((methodElement) -> {
                     accessibleMethods.add(methodElement);
                    handleMethod(methodElement, false);
                });
        this.beanType.getEnclosedElements(baseQuery.modifiers(NON_PUBLIC_FILTER))
                .forEach((methodElement) -> {
                    if (!accessibleMethods.contains(methodElement)) {
                        handleMethod(methodElement, true);
                    }
                });
    }

    private void handleMethod(MethodElement methodElement, boolean requiresReflection) {
        final InternalBeanElementMethod m = new InternalBeanElementMethod(
                methodElement,
                requiresReflection
        );
        if (m.getAnnotationMetadata().hasDeclaredAnnotation(PreDestroy.class)) {
            m.preDestroy();
        } else if (m.getAnnotationMetadata().hasDeclaredAnnotation(PostConstruct.class)) {
            m.postConstruct();
        } else {
            m.inject();
        }
    }

    @NonNull
    @Override
    public Element getOriginatingElement() {
        return originatingElement;
    }

    @NonNull
    @Override
    public ClassElement getBeanType() {
        return beanType;
    }

    private BeanParameterElement[] initBeanParameters(ParameterElement[] constructorParameters) {
        if (ArrayUtils.isNotEmpty(constructorParameters)) {
            return Arrays.stream(constructorParameters)
                    .map(InternalBeanParameter::new)
                    .toArray(BeanParameterElement[]::new);
        } else {
            return new BeanParameterElement[0];
        }
    }

    @NonNull
    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return this.annotationMetadata;
    }

    @NonNull
    @Override
    public BeanElementBuilder createWith(@NonNull MethodElement element) {
        // TODO: handle factories, static methods etc.
        //noinspection ConstantConditions
        if (element != null) {
            constructorElement = element;
            this.beanElementParameters = initBeanParameters(element.getParameters());
        }
        return this;
    }

    @NonNull
    @Override
    public BeanElementBuilder typed(ClassElement... types) {
        if (ArrayUtils.isNotEmpty(types)) {
            this.exposedTypes = types;
        }
        return this;
    }

    @NonNull
    @Override
    public BeanElementBuilder typeArguments(@NonNull ClassElement... types) {
        final Map<String, ClassElement> typeArguments = this.beanType.getTypeArguments();
        Map<String, ClassElement> resolvedTypes = resolveTypeArguments(typeArguments, types);
        if (resolvedTypes != null) {
            if (this.typeArguments == null) {
                this.typeArguments = new LinkedHashMap<>();
            }
            this.typeArguments.put(beanType.getName(), typeArguments);
        }
        return this;
    }

    @NonNull
    @Override
    public BeanElementBuilder typeArgumentsForType(ClassElement type, @NonNull ClassElement... types) {
        if (type != null) {
            final Map<String, ClassElement> typeArguments = type.getTypeArguments();
            Map<String, ClassElement> resolvedTypes = resolveTypeArguments(typeArguments, types);
            if (resolvedTypes != null) {
                if (this.typeArguments == null) {
                    this.typeArguments = new LinkedHashMap<>();
                }
                this.typeArguments.put(type.getName(), typeArguments);
            }
        }
        return this;
    }

    @Nullable
    private Map<String, ClassElement> resolveTypeArguments(Map<String, ClassElement> typeArguments, ClassElement... types) {
        Map<String, ClassElement> resolvedTypes = null;
        if (typeArguments.size() == types.length) {
            resolvedTypes = new LinkedHashMap<>(typeArguments.size());
            final Iterator<String> i = typeArguments.keySet().iterator();
            for (ClassElement type : types) {
                final String variable = i.next();
                resolvedTypes.put(variable, type);
            }
        }
        return resolvedTypes;
    }

    @NonNull
    @Override
    public BeanElementBuilder withMethods(
            @NonNull ElementQuery<MethodElement> methods,
            @NonNull Consumer<BeanMethodElement> beanMethods) {
        //noinspection ConstantConditions
        if (methods != null && beanMethods != null) {
            this.beanType.getEnclosedElements(methods.onlyInstance().onlyAccessible(originatingType))
                    .forEach((methodElement) ->
                            beanMethods.accept(new InternalBeanElementMethod(methodElement, false))
                    );
        }
        return this;
    }

    @NonNull
    @Override
    public BeanElementBuilder withFields(@NonNull ElementQuery<FieldElement> fields, @NonNull Consumer<BeanFieldElement> beanFields) {
        //noinspection ConstantConditions
        if (fields != null && beanFields != null) {
            this.beanType.getEnclosedElements(fields.onlyInstance().onlyAccessible(originatingType))
                    .forEach((fieldElement) ->
                            beanFields.accept(new InternalBeanElementField(fieldElement, false))
                    );
        }
        return this;
    }

    @NonNull
    @Override
    public BeanElementBuilder withParameters(Consumer<BeanParameterElement[]> parameters) {
        if (parameters != null) {
            parameters.accept(beanElementParameters);
        }
        return this;
    }

    @NonNull
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

    @NonNull
    @Override
    public Object getNativeType() {
        return beanType;
    }

    @NonNull
    @Override
    public <T extends Annotation> BeanElementBuilder annotate(@NonNull String annotationType, @NonNull Consumer<AnnotationValueBuilder<T>> consumer) {
        annotate(this.annotationMetadata, annotationType, consumer);
        return this;
    }

    @Override
    public BeanElementBuilder removeAnnotation(@NonNull String annotationType) {
        removeAnnotation(this.annotationMetadata, annotationType);
        return this;
    }

    @Override
    public <T extends Annotation> BeanElementBuilder removeAnnotationIf(@NonNull Predicate<AnnotationValue<T>> predicate) {
        removeAnnotationIf(this.annotationMetadata, predicate);
        return this;
    }

    @Override
    public BeanElementBuilder removeStereotype(@NonNull String annotationType) {
        removeStereotype(this.annotationMetadata, annotationType);
        return this;
    }

    /**
     * Build the bean definition writer.
     * @return The writer, possibly null if it wasn't possible to build it
     */
    @Nullable
    public BeanDefinitionWriter build() {
        if (exposedTypes != null) {
            final AnnotationClassValue[] annotationClassValues =
                    Arrays.stream(exposedTypes).map(ce -> new AnnotationClassValue<>(ce.getName())).toArray(AnnotationClassValue[]::new);
            annotate(Bean.class, (builder) -> builder.member("typed", annotationClassValues));
        }
        final BeanDefinitionWriter beanDefinitionWriter = new BeanDefinitionWriter(
                this,
                OriginatingElements.of(originatingElement),
                metadataBuilder,
                visitorContext,
                identifier
        );
        if (typeArguments != null) {
            beanDefinitionWriter.visitTypeArguments(this.typeArguments);
        }

        if (constructorElement == null) {
            constructorElement = beanType.getPrimaryConstructor().orElse(null);
        }

        if (constructorElement == null) {
            visitorContext.fail("Cannot create associated bean with no accessible primary constructor. Consider supply the constructor with createWith(..)", originatingElement);
            return null;
        } else {
            MethodElement finalConstructor;
            if (constructorElement instanceof ConstructorElement) {
                finalConstructor = new InternalConstructorElement(beanElementParameters);
            } else {
                finalConstructor = new InternalBeanElementMethod(constructorElement, !constructorElement.isPublic(), beanElementParameters);
            }
            beanDefinitionWriter.visitBeanDefinitionConstructor(
                    finalConstructor,
                    !finalConstructor.isPublic(),
                    visitorContext
            );
        }

        Collections.reverse(injectedFields); // visit super types first
        Collections.reverse(executableMethods); // visit super types first
        Collections.reverse(injectedMethods); // visit super types first
        Collections.reverse(postConstructMethods); // visit super types first
        Collections.reverse(preDestroyMethods); // visit super types first
        final Iterator<BeanFieldElement> i = injectedFields.iterator();
        while (i.hasNext()) {
            final BeanFieldElement injectedField = i.next();
            if (!injectedField.getDeclaringType().equals(beanType)) {
                InternalBeanElementField ibf = (InternalBeanElementField) injectedField;
                visitField(beanDefinitionWriter, injectedField, ibf);
                i.remove();
            }
        }

        final Iterator<BeanMethodElement> im = injectedMethods.iterator();
        while (im.hasNext()) {
            final BeanMethodElement injectedMethod = im.next();
            if (!injectedMethod.getDeclaringType().equals(beanType)) {
                InternalBeanElementMethod ibf = (InternalBeanElementMethod) injectedMethod;
                beanDefinitionWriter.visitMethodInjectionPoint(
                        beanType,
                        injectedMethod,
                        ibf.isRequiresReflection(),
                        visitorContext
                );
                im.remove();
            }
        }

        for (BeanFieldElement injectedField : injectedFields) {
            InternalBeanElementField ibf = (InternalBeanElementField) injectedField;
            visitField(beanDefinitionWriter, injectedField, ibf);
        }


        for (BeanMethodElement injectedMethod : injectedMethods) {
            beanDefinitionWriter.visitMethodInjectionPoint(
                    beanType,
                    injectedMethod,
                    ((InternalBeanElementMethod) injectedMethod).isRequiresReflection(),
                    visitorContext
            );
        }

        for (BeanMethodElement executableMethod : executableMethods) {
            beanDefinitionWriter.visitExecutableMethod(
                    beanType,
                    executableMethod,
                    visitorContext
            );
        }

        for (BeanMethodElement postConstructMethod : postConstructMethods) {
            if (postConstructMethod.getDeclaringType().equals(beanType)) {
                beanDefinitionWriter.visitPostConstructMethod(
                        beanType,
                        postConstructMethod,
                        ((InternalBeanElementMethod) postConstructMethod).isRequiresReflection(),
                        visitorContext
                );
            }
        }

        for (BeanMethodElement preDestroyMethod : preDestroyMethods) {
            if (preDestroyMethod.getDeclaringType().equals(beanType)) {
                beanDefinitionWriter.visitPreDestroyMethod(
                        beanType,
                        preDestroyMethod,
                        ((InternalBeanElementMethod) preDestroyMethod).isRequiresReflection(),
                        visitorContext
                );
            }
        }

        beanDefinitionWriter.visitBeanDefinitionEnd();

        return beanDefinitionWriter;
    }

    private void visitField(BeanDefinitionWriter beanDefinitionWriter,
                           BeanFieldElement injectedField,
                           InternalBeanElementField ibf) {
        if (injectedField.hasAnnotation(Value.class)) {
            beanDefinitionWriter.visitFieldValue(
                    this.beanType,
                    injectedField,
                    ibf.isRequiresReflection(),
                    injectedField.isNullable()
            );
        } else {
            beanDefinitionWriter.visitFieldInjectionPoint(
                    this.beanType,
                    injectedField,
                    ibf.isRequiresReflection()
            );
        }
    }

    /**
     * Add an annotation to the given metadata.
     * @param annotationMetadata The annotation metadata
     * @param annotationType the annotation type
     * @param consumer The builder
     * @param <T> The annotation generic type
     */
    protected abstract <T extends Annotation> void annotate(AnnotationMetadata annotationMetadata, String annotationType, Consumer<AnnotationValueBuilder<T>> consumer);

    /**
     * Remove a stereotype from the given metadata.
     * @param annotationMetadata The metadata
     * @param annotationType The stereotype
     */
    protected abstract void removeStereotype(AnnotationMetadata annotationMetadata, String annotationType);

    /**
     * Remove an annotation if it matches the given condition.
     * @param annotationMetadata The metadata
     * @param predicate The predicate
     * @param <T> The annotation type
     */
    protected abstract <T extends Annotation> void removeAnnotationIf(AnnotationMetadata annotationMetadata, Predicate<AnnotationValue<T>> predicate);

    /**
     * Remove an annotation for the given name.
     * @param annotationMetadata The metadata
     * @param annotationType The type
     */
    protected abstract void removeAnnotation(AnnotationMetadata annotationMetadata, String annotationType);

    /**
     * Super class for all bean elements.
     * @param <E> The element type
     */
    private abstract class InternalBeanElement<E extends Element> implements Element {
        private final E element;
        private final MutableAnnotationMetadata elementMetadata;

        private InternalBeanElement(E element) {
            this.element = element;
            final AnnotationMetadata annotationMetadata = element.getAnnotationMetadata();
            if (annotationMetadata instanceof MutableAnnotationMetadata) {
                this.elementMetadata = ((MutableAnnotationMetadata) annotationMetadata).clone();
            } else {
                this.elementMetadata = new MutableAnnotationMetadata();
            }
        }

        @NonNull
        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return elementMetadata;
        }

        @NonNull
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

        @NonNull
        @Override
        public Object getNativeType() {
            return element.getNativeType();
        }

        @NonNull
        @Override
        public <T extends Annotation> Element annotate(@NonNull String annotationType, @NonNull Consumer<AnnotationValueBuilder<T>> consumer) {
            AbstractBeanDefinitionBuilder.this.annotate(elementMetadata, annotationType, consumer);
            return this;
        }

        @Override
        public Element removeAnnotation(@NonNull String annotationType) {
            AbstractBeanDefinitionBuilder.this.removeAnnotation(elementMetadata, annotationType);
            return this;
        }

        @Override
        public <T extends Annotation> Element removeAnnotationIf(@NonNull Predicate<AnnotationValue<T>> predicate) {
            AbstractBeanDefinitionBuilder.this.removeAnnotationIf(elementMetadata, predicate);
            return this;
        }

        @Override
        public Element removeStereotype(@NonNull String annotationType) {
            AbstractBeanDefinitionBuilder.this.removeStereotype(elementMetadata, annotationType);
            return this;
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
            super(methodElement);
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

        @NonNull
        @Override
        public BeanMethodElement executable() {
            if (!AbstractBeanDefinitionBuilder.this.executableMethods.contains(this)) {
                AbstractBeanDefinitionBuilder.this.executableMethods.add(this);
            }
            return BeanMethodElement.super.executable();
        }

        @NonNull
        @Override
        public BeanMethodElement inject() {
            if (!AbstractBeanDefinitionBuilder.this.injectedMethods.contains(this)) {
                AbstractBeanDefinitionBuilder.this.injectedMethods.add(this);
            }
            return BeanMethodElement.super.inject();
        }

        @NonNull
        @Override
        public BeanMethodElement preDestroy() {
            if (!AbstractBeanDefinitionBuilder.this.preDestroyMethods.contains(this)) {
                AbstractBeanDefinitionBuilder.this.preDestroyMethods.add(this);
            }
            return BeanMethodElement.super.preDestroy();
        }

        @NonNull
        @Override
        public BeanMethodElement postConstruct() {
            if (!AbstractBeanDefinitionBuilder.this.postConstructMethods.contains(this)) {
                AbstractBeanDefinitionBuilder.this.postConstructMethods.add(this);
            }
            return BeanMethodElement.super.postConstruct();
        }

        @NonNull
        @Override
        public BeanParameterElement[] getParameters() {
            return this.beanParameters;
        }

        @NonNull
        @Override
        public ClassElement getReturnType() {
            return methodElement.getReturnType();
        }

        @NonNull
        @Override
        public ClassElement getGenericReturnType() {
            return methodElement.getGenericReturnType();
        }

        @NonNull
        @Override
        public MethodElement withNewParameters(@NonNull ParameterElement... newParameters) {
            this.beanParameters = initBeanParameters(ArrayUtils.concat(beanParameters, newParameters));
            return this;
        }

        @Override
        public ClassElement getDeclaringType() {
            return AbstractBeanDefinitionBuilder.this.beanType;
        }
    }

    /**
     * Models a {@link BeanFieldElement}.
     */
    private final class InternalBeanElementField extends InternalBeanElement<FieldElement> implements BeanFieldElement {
        private final FieldElement fieldElement;
        private final boolean requiresReflection;
        private ClassElement genericType;

        private InternalBeanElementField(FieldElement element, boolean requiresReflection) {
            super(element);
            this.fieldElement = element;
            this.requiresReflection = requiresReflection;
        }

        public boolean isRequiresReflection() {
            return requiresReflection;
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
            return AbstractBeanDefinitionBuilder.this.beanType;
        }

        @NonNull
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

        @NonNull
        @Override
        public BeanFieldElement typeArguments(@NonNull ClassElement... types) {
            final ClassElement genericType = fieldElement.getGenericField();
            final Map<String, ClassElement> typeArguments = genericType.getTypeArguments();
            final Map<String, ClassElement> resolved = resolveTypeArguments(typeArguments, types);
            if (resolved != null) {
                final String typeName = genericType.getName();
                this.genericType = ClassElement.of(
                        typeName,
                        genericType.isInterface(),
                        getAnnotationMetadata(),
                        resolved
                );
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
            super(element);
            parameterElement = element;
        }

        @NonNull
        @Override
        public ClassElement getGenericType() {
            if (genericType != null) {
                return genericType;
            } else {
                return parameterElement.getGenericType();
            }
        }

        @NonNull
        @Override
        public ClassElement getType() {
            return parameterElement.getType();
        }

        @SuppressWarnings("rawtypes")
        @NonNull
        @Override
        public BeanParameterElement typeArguments(@NonNull ClassElement... types) {
            final ClassElement genericType = parameterElement.getGenericType();
            final Map<String, ClassElement> typeArguments = genericType.getTypeArguments();
            final Map<String, ClassElement> resolved = resolveTypeArguments(typeArguments, types);
            if (resolved != null) {

                final ElementFactory elementFactory = visitorContext.getElementFactory();
                this.genericType = elementFactory.newClassElement(
                        genericType.getNativeType(),
                        getAnnotationMetadata(),
                        resolved
                );
            }
            return this;
        }
    }

    private final class InternalConstructorElement implements ConstructorElement {
        private final ParameterElement[] parameterElements;

        private InternalConstructorElement(ParameterElement[] parameterElements) {
            this.parameterElements = parameterElements;
        }

        @NonNull
        @Override
        public ParameterElement[] getParameters() {
            return parameterElements;
        }

        @NonNull
        @Override
        public MethodElement withNewParameters(@NonNull ParameterElement... newParameters) {
            return new InternalConstructorElement(ArrayUtils.concat(parameterElements, newParameters));
        }

        @NonNull
        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return constructorElement.getAnnotationMetadata();
        }

        @Override
        public ClassElement getDeclaringType() {
            return beanType;
        }

        @Override
        public boolean isProtected() {
            return constructorElement.isProtected();
        }

        @Override
        public boolean isPublic() {
            return constructorElement.isPublic();
        }

        @NonNull
        @Override
        public Object getNativeType() {
            return constructorElement.getNativeType();
        }
    }
}
