/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.inject.processing;

import io.micronaut.aop.internal.intercepted.InterceptedMethodUtil;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.processing.definition.ElementBeanDefinitionBuilder;
import io.micronaut.inject.processing.definition.ElementBeanDefinitionBuilderFactory;
import io.micronaut.inject.processing.definition.ElementProxyBuilder;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.configuration.ConfigurationUtils;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Factory bean builder.
 *
 * @param <R> The builder result type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
final class FactoryBeanElementCreator<R> extends DeclaredBeanElementCreator<R> {

    private static final String MEMBER_PRE_DESTROY = "preDestroy";

    FactoryBeanElementCreator(ClassElement classElement, VisitorContext visitorContext, boolean isAopProxy, ElementBeanDefinitionBuilderFactory<R> beanDefinitionBuilder) {
        super(classElement, visitorContext, isAopProxy, beanDefinitionBuilder);
    }

    @Override
    protected boolean visitMethod(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, MethodElement methodElement) {
        if (methodElement.hasDeclaredStereotype(Bean.class.getName(), AnnotationUtil.SCOPE)) {
            visitBeanFactoryElement(beanDefinitionBuilder, methodElement.getGenericReturnType(), methodElement);
            return true;
        }
        return super.visitMethod(beanDefinitionBuilder, methodElement);
    }

    @Override
    protected boolean visitField(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, FieldElement fieldElement) {
        if (fieldElement.hasDeclaredStereotype(Bean.class.getName())) {
            if (!fieldElement.isAccessible(classElement)) {
                throw new ProcessingException(fieldElement, "Beans produced from fields cannot be private");
            }
            visitBeanFactoryElement(beanDefinitionBuilder, fieldElement.getType(), fieldElement);
            return true;
        }
        return super.visitField(beanDefinitionBuilder, fieldElement);
    }

    @Override
    protected boolean visitPropertyReadElement(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, PropertyElement propertyElement, MemberElement readElement) {
        if (readElement.hasDeclaredStereotype(Bean.class.getName())) {
            ClassElement beanType;
            if (readElement instanceof MethodElement methodElement) {
                beanType = methodElement.getGenericReturnType();
            } else if (readElement instanceof FieldElement fieldElement) {
                beanType = fieldElement.getGenericType();
            } else {
                throw new IllegalStateException();
            }
            visitBeanFactoryElement(beanDefinitionBuilder, beanType, readElement);
            return true;
        }
        return super.visitPropertyReadElement(beanDefinitionBuilder, propertyElement, readElement);
    }

    @Override
    protected boolean visitPropertyWriteElement(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, PropertyElement propertyElement, MemberElement writeElement) {
        if (writeElement.hasDeclaredStereotype(Bean.class.getName())) {
            // Ignore bean producer accessor
            return true;
        }
        return super.visitPropertyWriteElement(beanDefinitionBuilder, propertyElement, writeElement);
    }

    void visitBeanFactoryElement(ElementBeanDefinitionBuilder<R> beanDefinitionBuilder, ClassElement producedType, MemberElement producingElement) {
        if (producedType.isPrimitive()) {
            buildProducedBeanDefinition(producedType, producedType, producingElement, producingElement.getAnnotationMetadata());
        } else {
            AnnotationMetadata producedTypeAnnotationMetadata = createProducedTypeAnnotationMetadata(producedType, producingElement);
            ClassElement newProducedType = producedType.withAnnotationMetadata(producedTypeAnnotationMetadata);
            AnnotationMetadata producingElementAnnotationMetadata = createProducingElementAnnotationMetadata(producedTypeAnnotationMetadata);
            producingElement = producingElement.withAnnotationMetadata(producingElementAnnotationMetadata);

//            producingElement = producingElement.withAnnotationMetadata(producedTypeAnnotationMetadata);
            buildProducedBeanDefinition(newProducedType, producedType, producingElement, newProducedType.getAnnotationMetadata());

            if (producingElement instanceof MethodElement methodElement) {
                if (isAopProxy && visitAopMethod(beanDefinitionBuilder, methodElement)) {
                    return;
                }
                visitExecutableMethod(beanDefinitionBuilder, methodElement);
            }
        }
    }

    private AnnotationMetadata createProducingElementAnnotationMetadata(AnnotationMetadata producedAnnotationMetadata) {
        MutableAnnotationMetadata factoryClassAnnotationMetadata = MutableAnnotationMetadata.of(classElement.getAnnotationMetadata());

        boolean modifiedFactoryClassAnnotationMetadata = false;
        if (classElement.hasStereotype(AnnotationUtil.QUALIFIER)) {
            // Don't propagate any qualifiers to the factories
            for (String qualifier : classElement.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER)) {
                if (!producedAnnotationMetadata.hasStereotype(qualifier)) {
                    factoryClassAnnotationMetadata.removeAnnotation(qualifier);
                    modifiedFactoryClassAnnotationMetadata = true;
                }
            }
        }
        if (modifiedFactoryClassAnnotationMetadata) {
            return new AnnotationMetadataHierarchy(factoryClassAnnotationMetadata, producedAnnotationMetadata);
        }
        return new AnnotationMetadataHierarchy(classElement, producedAnnotationMetadata);
    }

    private AnnotationMetadata createProducedTypeAnnotationMetadata(ClassElement producedType, MemberElement producingElement) {
        // Original logic is to combine producing element annotation metadata (method or field) with the produced type's annotation metadata
        MutableAnnotationMetadata producedAnnotationMetadata = new AnnotationMetadataHierarchy(
            producedType.getAnnotationMetadata(),
            getElementAnnotationMetadata(producingElement)
        ).merge();
        AnnotationMetadata producedTypeAnnotationMetadata = producedType.getAnnotationMetadata();
        AnnotationMetadata elementAnnotationMetadata = getElementAnnotationMetadata(producingElement);

        cleanupScopeAndQualifierAnnotations(producedAnnotationMetadata, producedTypeAnnotationMetadata, elementAnnotationMetadata);
        return producedAnnotationMetadata;
    }

    private void buildProducedBeanDefinition(ClassElement producedType,
                                             ClassElement originalProducedType,
                                             MemberElement producingElement,
                                             AnnotationMetadata producedAnnotationMetadata) {

        if (producedType.hasStereotype(EachProperty.class)) {
            producedType.annotate(ConfigurationReader.class, builder -> builder.member(ConfigurationReader.PREFIX, ConfigurationUtils.getRequiredTypePath(producedType)));
            producingElement.annotate(ConfigurationReader.class, builder -> builder.member(ConfigurationReader.PREFIX, ConfigurationUtils.getRequiredTypePath(producedType)));
        }

        ElementBeanDefinitionBuilder<R> beanDefinitionBuilder;
        if (producingElement instanceof PropertyElement propertyElement) {
            MethodElement readMethod = propertyElement.getReadMethod().orElse(null);
            if (readMethod != null) {
                beanDefinitionBuilder = beanDefinitionBuilderFactory.factoryMethod(readMethod);
            } else {
                FieldElement fieldElement = propertyElement.getField().orElse(null);
                if (fieldElement != null && fieldElement.isAccessible()) {
                    beanDefinitionBuilder = beanDefinitionBuilderFactory.factoryField(fieldElement);
                } else {
                    throw new ProcessingException(producingElement, "A property element that defines the @Bean annotation must have an accessible getter or field");
                }
            }
        } else if (producingElement instanceof MethodElement methodElement) {
            beanDefinitionBuilder = beanDefinitionBuilderFactory.factoryMethod(methodElement);
        } else {
            beanDefinitionBuilder = beanDefinitionBuilderFactory.factoryField((FieldElement) producingElement);
        }

        if (producedAnnotationMetadata.hasStereotype(ConfigurationReader.class)
            && !producedType.isPrimitive() && !producedType.isArray()) {
            for (PropertyElement propertyElement : producedType.getBeanProperties()) {
                if (!propertyElement.isExcluded()) {
                    ConfigurationReaderBeanElementCreator.visitPropertyValue(beanDefinitionBuilder, producedType, visitorContext, propertyElement);
                }
            }
        }

        // The pre-destroy callback the producing element names is a lifecycle callback of the produced bean, not an
        // executable method of it, so the AOP proxy below must not advise it. This is the same rule a bean that
        // declares @PreDestroy on its own method gets from DeclaredBeanElementCreator, where the callback is claimed
        // as a lifecycle method before any around advice is applied to it.
        String preDestroyMethodName = resolvePreDestroyMethodName(producedType, producedAnnotationMetadata);

        if (InterceptedMethodUtil.hasAroundStereotype(producedAnnotationMetadata) && !producedType.isAssignable("io.micronaut.aop.Interceptor")) {
            if (producedType.isArray()) {
                throw new ProcessingException(producingElement, "Cannot apply AOP advice to arrays");
            }
            if (producedType.isPrimitive()) {
                throw new ProcessingException(producingElement, "Cannot apply AOP advice to primitive beans");
            }
            // Validate the original type to avoid KSP annotations isFinal bypass, because of the new merged annotations
            ProxyableTypeValidator.validateProxyable(originalProducedType, producingElement);

            MethodElement constructorElement = producedType.getPrimaryConstructor().orElse(null);
            MethodElement defaultConstructor = producedType.getDefaultConstructor().orElse(null);
            if (!producedType.isInterface() && constructorElement != null && defaultConstructor == null) {
                final String proxyTargetMode = producedAnnotationMetadata.stringValue(AnnotationUtil.ANN_AROUND, "proxyTargetMode").orElse("ERROR");
                switch (proxyTargetMode) {
                    case "ALLOW":
                        if (constructorElement != null) {
                            allowProxyConstruction(constructorElement);
                        }
                        break;
                    case "WARN":
                        if (constructorElement != null) {
                            allowProxyConstruction(constructorElement);
                        }
                        visitorContext.warn("The produced type of a @Factory method has constructor arguments and is proxied. " +
                            "This can lead to unexpected behaviour. See the javadoc for Around.ProxyTargetConstructorMode for more information: " + producingElement.getName(), producingElement);
                        break;
                    case "ERROR":
                    default:
                        throw new ProcessingException(producingElement, "The produced type from a factory which has AOP proxy advice specified must define an accessible no arguments constructor. " +
                            "Proxying types with constructor arguments can lead to unexpected behaviour. See the javadoc for for Around.ProxyTargetConstructorMode for more information and possible solutions: " + producingElement.getName());
                }
            }
            ElementProxyBuilder<R> proxyBuilder = beanDefinitionBuilderFactory.aroundProxy(producedType, producedAnnotationMetadata, beanDefinitionBuilder);
            additionalBuilders.add(proxyBuilder);

            List<MethodElement> methodElements = producedType.getEnclosedElements(ElementQuery.ALL_METHODS)
                .stream()
                .filter(m -> m.isPublic() && !m.isFinal() && !m.isStatic())
                .filter(m -> !isPreDestroyCallback(m, preDestroyMethodName))
                .toList();
            methodElements
                .forEach(methodElement -> visitAroundMethod(proxyBuilder, methodElement.getDeclaringType(), methodElement));
            List<PropertyElement> syntheticBeanProperties = producedType.getSyntheticBeanProperties();
            for (PropertyElement syntheticBeanProperty : syntheticBeanProperties) {
                syntheticBeanProperty.getReadMethod().ifPresent(m -> {
                        if (!m.isFinal() && !isPreDestroyCallback(m, preDestroyMethodName)) {
                            visitAroundMethod(proxyBuilder, m.getDeclaringType(), m);
                        }
                    }
                );
                syntheticBeanProperty.getWriteMethod().ifPresent(m -> {
                        if (!m.isFinal()) {
                            visitAroundMethod(proxyBuilder, m.getDeclaringType(), m);
                        }
                    }
                );
            }

        } else if (producedAnnotationMetadata.hasStereotype(Executable.class)) {
            if (producedType.isArray()) {
                throw new ProcessingException(producingElement, "Using '@Executable' is not allowed on array type beans");
            }
            if (producedType.isPrimitive()) {
                throw new ProcessingException(producingElement, "Using '@Executable' is not allowed on primitive type beans");
            }
            producedType.getEnclosedElements(ElementQuery.ALL_METHODS)
                .forEach(methodElement -> beanDefinitionBuilder.addExecutableMethod(
                    methodElement, methodElement.isReflectionRequired(producingElement.getDeclaringType())));
        }

        if (producedAnnotationMetadata.isPresent(Bean.class, MEMBER_PRE_DESTROY)) {
            if (producedType.isArray()) {
                throw new ProcessingException(producingElement, "Using 'preDestroy' is not allowed on array type beans");
            }
            if (producedType.isPrimitive()) {
                throw new ProcessingException(producingElement, "Using 'preDestroy' is not allowed on primitive type beans");
            }

            producedType.getValue(Bean.class, MEMBER_PRE_DESTROY, String.class).ifPresent(destroyMethodName -> {
                if (StringUtils.isNotEmpty(destroyMethodName)) {
                    final Optional<MethodElement> destroyMethod = producedType.getEnclosedElement(ElementQuery.ALL_METHODS.onlyAccessible(classElement)
                        .onlyInstance()
                        .named(destroyMethodName) // Named filtering should avoid processing all method and fail on possible missing classes and compilation errors
                        .filter((e) -> !e.hasParameters()));
                    if (destroyMethod.isPresent()) {
                        MethodElement destroyMethodElement = destroyMethod.get();
                        beanDefinitionBuilder.addPreDestroy(destroyMethodElement, false, visitorContext);
                    } else {
                        throw new ProcessingException(producingElement, "@Bean method defines a preDestroy method that does not exist or is not public: " + destroyMethodName);
                    }
                }
            });
        }

        additionalBuilders.add(beanDefinitionBuilder);
    }

    /**
     * The name of the pre-destroy callback the producing element declares with {@link Bean#preDestroy()}, when the
     * produced type can have one.
     *
     * @param producedType               The produced type
     * @param producedAnnotationMetadata The annotation metadata of the produced bean
     * @return The method name, or {@code null} when none is declared
     */
    @Nullable
    private String resolvePreDestroyMethodName(ClassElement producedType, AnnotationMetadata producedAnnotationMetadata) {
        if (producedType.isArray() || producedType.isPrimitive() || !producedAnnotationMetadata.isPresent(Bean.class, MEMBER_PRE_DESTROY)) {
            // The validation of those cases belongs to the pre-destroy handling itself
            return null;
        }
        return producedType.getValue(Bean.class, MEMBER_PRE_DESTROY, String.class)
            .filter(StringUtils::isNotEmpty)
            .orElse(null);
    }

    private static boolean isPreDestroyCallback(MethodElement methodElement, @Nullable String preDestroyMethodName) {
        return preDestroyMethodName != null
            && !methodElement.hasParameters()
            && methodElement.getName().equals(preDestroyMethodName);
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

    private void cleanupScopeAndQualifierAnnotations(MutableAnnotationMetadata producedAnnotationMetadata, AnnotationMetadata producedTypeAnnotationMetadata, AnnotationMetadata producingElementAnnotationMetadata) {
        // If the producing element defines a scope don't inherit it from the type
        if (producingElementAnnotationMetadata.hasStereotype(AnnotationUtil.SCOPE) || producingElementAnnotationMetadata.hasStereotype(AnnotationUtil.QUALIFIER)) {
            // The producing element is declaring the scope then we should remove the scope defined by the type
            List<String> typeScopes = producedTypeAnnotationMetadata.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE);
            Set<String> removedScopes = CollectionUtils.newLinkedHashSet(typeScopes.size());
            for (String scope : typeScopes) {
                if (!producingElementAnnotationMetadata.hasStereotype(scope)) {
                    producedAnnotationMetadata.removeAnnotation(scope);
                    // An annotation that a mapper turned into the scope still carries the scope as its stereotype
                    producedAnnotationMetadata.removeStereotype(scope);
                    removedScopes.add(scope);
                }
            }
            if (!removedScopes.isEmpty()) {
                removeInterceptorBindingsOf(removedScopes, producedAnnotationMetadata);
            }
            // Remove any qualifier coming from the type
            for (String qualifier : producedTypeAnnotationMetadata.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER)) {
                if (!producingElementAnnotationMetadata.hasStereotype(qualifier)) {
                    producedAnnotationMetadata.removeAnnotation(qualifier);
                }
            }
        }
    }

    /**
     * Removes the interceptor bindings that the removed scopes of the produced type declare, such as the around binding
     * of a scoped proxy. An annotation that a mapper turned into the scope is a parent of those bindings too, so they
     * outlive the scope and would make the produced bean a proxy of a scope it does not have.
     *
     * @param removedScopes              The scopes removed from the produced bean
     * @param producedAnnotationMetadata The annotation metadata of the produced bean
     */
    private static void removeInterceptorBindingsOf(Set<String> removedScopes, MutableAnnotationMetadata producedAnnotationMetadata) {
        List<AnnotationValue<Annotation>> bindings = producedAnnotationMetadata.getAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING);
        if (bindings.stream().noneMatch(binding -> isBindingOf(binding, removedScopes))) {
            return;
        }
        List<AnnotationValue<Annotation>> declaredBindings = producedAnnotationMetadata.getDeclaredAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING);
        // Bindings written directly are no stereotype and stay as they are
        Collection<?> directBindings = directBindingsOf(producedAnnotationMetadata);
        List<String> parents = new ArrayList<>(producedAnnotationMetadata.getAnnotationNamesByStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS));
        producedAnnotationMetadata.removeStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS);
        for (AnnotationValue<Annotation> binding : bindings) {
            if (isBindingOf(binding, removedScopes) || directBindings.contains(binding)) {
                continue;
            }
            if (declaredBindings.contains(binding)) {
                producedAnnotationMetadata.addDeclaredRepeatableStereotype(parents, AnnotationUtil.ANN_INTERCEPTOR_BINDINGS, binding);
            } else {
                producedAnnotationMetadata.addRepeatableStereotype(parents, AnnotationUtil.ANN_INTERCEPTOR_BINDINGS, binding);
            }
        }
    }

    private static Collection<?> directBindingsOf(AnnotationMetadata annotationMetadata) {
        if (!annotationMetadata.hasAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS)) {
            return List.of();
        }
        // The values of an annotation written directly take precedence over those of the stereotype
        Object bindings = annotationMetadata.getValues(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS).get(AnnotationMetadata.VALUE_MEMBER);
        if (bindings instanceof Object[] array) {
            return Arrays.asList(array);
        }
        return bindings instanceof Collection<?> collection ? collection : List.of();
    }

    private static boolean isBindingOf(AnnotationValue<Annotation> binding, Set<String> annotationNames) {
        return binding.stringValue().filter(annotationNames::contains).isPresent();
    }

}
