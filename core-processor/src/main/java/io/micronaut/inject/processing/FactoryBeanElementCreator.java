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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
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

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

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
    /**
     * The exception the Java processor throws when an element references a type missing from the classpath.
     */
    private static final String JAVA_POSTPONE_EXCEPTION = "io.micronaut.annotation.processing.PostponeToNextRoundException";

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

        List<MethodElement> producedMethods = producedType.isPrimitive() || producedType.isArray() ? List.of() : listProducedMethods(producedType);
        warnAboutUninvokedLifecycleCallbacks(producedMethods, producingElement, preDestroyMethodName);

        boolean isInterceptor = !producedType.isPrimitive() && !producedType.isArray() && producedType.isAssignable("io.micronaut.aop.Interceptor");
        // Advice declared by the producing element or the produced type applies to every advisable method, and
        // advice a method of the produced type declares itself applies to that method, so it requires a proxy as well
        boolean typeAdvice = InterceptedMethodUtil.hasAroundStereotype(producedAnnotationMetadata) && !isInterceptor;
        boolean methodAdvice = !typeAdvice && !isInterceptor
            && producedMethods.stream().anyMatch(this::isMethodDeclaringAdvice)
            && isProxyableForMethodAdvice(originalProducedType, producedType, producingElement);
        Predicate<MethodElement> advisedMethod = typeAdvice ? this::isAdvisableProducedMethod : this::isMethodDeclaringAdvice;

        if (typeAdvice || methodAdvice) {
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
            // Method-level advice only reaches this point when the type can be proxied without constructor arguments
            if (typeAdvice && !producedType.isInterface() && constructorElement != null && defaultConstructor == null) {
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
                .filter(advisedMethod)
                .filter(m -> !isPreDestroyCallback(m, preDestroyMethodName))
                .toList();
            methodElements
                .forEach(methodElement -> visitAroundMethod(proxyBuilder, methodElement.getDeclaringType(), methodElement));
            List<PropertyElement> syntheticBeanProperties = producedType.getSyntheticBeanProperties();
            for (PropertyElement syntheticBeanProperty : syntheticBeanProperties) {
                syntheticBeanProperty.getReadMethod().ifPresent(m -> {
                        if (advisedMethod.test(m) && !isPreDestroyCallback(m, preDestroyMethodName)) {
                            visitAroundMethod(proxyBuilder, m.getDeclaringType(), m);
                        }
                    }
                );
                syntheticBeanProperty.getWriteMethod().ifPresent(m -> {
                        if (advisedMethod.test(m)) {
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
     * Warns about the {@code @PostConstruct} and {@code @PreDestroy} methods a produced type declares, which are not
     * invoked for a bean produced from a {@code @Factory}.
     *
     * <p>A produced instance is constructed by the factory method or field and not by Micronaut, so it is not a
     * managed instance whose lifecycle Micronaut drives; this is also what the Jakarta CDI specification says about
     * the return value of a producer method. The documented way to give a produced bean a destroy callback is
     * {@link Bean#preDestroy()} on the producing element, and there is no equivalent for construction because the
     * factory method can initialize the instance itself. That the callbacks are silently skipped is the surprising
     * part, so it is reported once per produced bean, pointing at the producing element the user owns.</p>
     *
     * @param producedMethods      The methods of the produced type
     * @param producingElement     The producing method or field
     * @param preDestroyMethodName The pre-destroy callback named by {@link Bean#preDestroy()}, which is invoked
     */
    private void warnAboutUninvokedLifecycleCallbacks(List<MethodElement> producedMethods,
                                                      MemberElement producingElement,
                                                      @Nullable String preDestroyMethodName) {
        List<String> callbacks = producedMethods.stream()
            .filter(m -> m.hasDeclaredAnnotation(AnnotationUtil.POST_CONSTRUCT) || m.hasDeclaredAnnotation(AnnotationUtil.PRE_DESTROY))
            .filter(m -> !isPreDestroyCallback(m, preDestroyMethodName))
            .map(m -> m.getDeclaringType().getSimpleName() + "." + m.getName() + "()")
            .distinct()
            .toList();
        if (callbacks.isEmpty()) {
            return;
        }
        visitorContext.warn("The type produced by this element declares the lifecycle callback(s) " + String.join(", ", callbacks)
            + ", which are not invoked for a bean produced from a @Factory: the instance is constructed by the factory and is not "
            + "a managed instance of the produced type. Initialize the instance in the producing element, and name a destroy "
            + "method with @Bean(preDestroy = \"...\").", producingElement);
    }

    /**
     * The methods of a produced type, for the checks that inspect every method of it before deciding how to build the
     * produced bean.
     *
     * <p>The Java processor fails building the methods of a type when one of them references a class missing from the
     * classpath. That must not fail or postpone the produced bean, which the processors have always built without
     * looking at those methods, so no methods are returned instead; the pre-destroy lookup avoids the same failure by
     * filtering on the method name before building elements. Any other failure is a processor bug and is not hidden.</p>
     *
     * @param producedType The produced type
     * @return The methods, or none when they cannot be built
     */
    private List<MethodElement> listProducedMethods(ClassElement producedType) {
        try {
            return producedType.getEnclosedElements(ElementQuery.ALL_METHODS);
        } catch (RuntimeException e) {
            if (JAVA_POSTPONE_EXCEPTION.equals(e.getClass().getName())) {
                return List.of();
            }
            throw e;
        }
    }

    /**
     * Whether a method of a produced type declares around advice itself and can carry it.
     *
     * @param methodElement The method
     * @return true if the method is advised when neither the producing element nor the produced type declares advice
     */
    private boolean isMethodDeclaringAdvice(MethodElement methodElement) {
        return isAdvisableProducedMethod(methodElement)
            && InterceptedMethodUtil.hasDeclaredAroundAdvice(methodElement.getMethodAnnotationMetadata());
    }

    /**
     * Whether a produced type whose only advice is declared by its methods can be proxied.
     *
     * <p>Advice declared by the producing element or the produced type is an explicit request for a proxy, so a type
     * that cannot be proxied fails the compilation. A type that only has advice on its methods compiled without a
     * proxy before those methods were honoured, and is often a third-party type, so it keeps compiling without one
     * and the unapplied advice is reported instead.</p>
     *
     * @param originalProducedType The produced type, with its own annotation metadata
     * @param producedType         The produced type
     * @param producingElement     The producing element
     * @return true if the produced bean can be proxied
     */
    private boolean isProxyableForMethodAdvice(ClassElement originalProducedType, ClassElement producedType, MemberElement producingElement) {
        String reason = null;
        if (originalProducedType.isFinal()) {
            reason = "the type is final";
        } else if (originalProducedType.isSealed()) {
            reason = "the type is sealed";
        } else if (!producedType.isInterface()
            && producedType.getPrimaryConstructor().isPresent()
            && producedType.getDefaultConstructor().isEmpty()) {
            reason = "the type has no accessible no arguments constructor";
        }
        if (reason == null) {
            return true;
        }
        visitorContext.warn("Methods of the type produced by this element declare AOP advice, which is not applied because "
            + reason + ", so the produced bean cannot be proxied.", producingElement);
        return false;
    }

    /**
     * Whether a method of a produced type carries the around advice of the produced bean.
     *
     * <p>This is the rule {@link #visitAopMethod} gives a bean that declares the advice itself - a method inherits
     * class-level advice when it is public or package-private, and carries advice it declares itself whatever its
     * visibility - bounded by what the generated code can reach. The proxy of a produced bean is a subclass of the
     * produced type generated into the factory's package, and its executable methods invoke the target from there, so
     * a non-public method is only reachable when the factory's package can see it.</p>
     *
     * <p>A final method is left unadvised rather than reported as an error, which is what a final method inheriting
     * class-level advice on a bean that declares it gets: a produced type is often a third-party type whose methods
     * the user cannot change.</p>
     *
     * @param methodElement The method
     * @return true if the method carries the advice
     */
    private boolean isAdvisableProducedMethod(MethodElement methodElement) {
        if (methodElement.isStatic() || methodElement.isFinal() || !methodElement.isAccessible(classElement, false)) {
            return false;
        }
        return methodElement.isPublic()
            || methodElement.isPackagePrivate()
            || InterceptedMethodUtil.hasDeclaredAroundAdvice(methodElement.getMethodAnnotationMetadata());
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
            for (String scope : producedTypeAnnotationMetadata.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE)) {
                if (!producingElementAnnotationMetadata.hasStereotype(scope)) {
                    producedAnnotationMetadata.removeAnnotation(scope);
                }
            }
            // Remove any qualifier coming from the type
            for (String qualifier : producedTypeAnnotationMetadata.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER)) {
                if (!producingElementAnnotationMetadata.hasStereotype(qualifier)) {
                    producedAnnotationMetadata.removeAnnotation(qualifier);
                }
            }
        }
    }

}
