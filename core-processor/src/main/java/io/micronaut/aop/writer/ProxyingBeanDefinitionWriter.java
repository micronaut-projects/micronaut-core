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
package io.micronaut.aop.writer;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.internal.intercepted.InterceptedMethodUtil;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.inject.ElementBeanDefinitionBuilder;
import io.micronaut.inject.ElementProxyBuilder;
import io.micronaut.inject.OutputObjectDef;
import io.micronaut.inject.ProxyBeanDefinition;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.utils.BeanInjectionUtils;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionWriter;
import io.micronaut.inject.writer.ExecutableMethodsDefinitionWriter;
import io.micronaut.inject.writer.OriginatingElements;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * An abstract class for writing proxy bean definitions.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@NullUnmarked
@Internal
public abstract class ProxyingBeanDefinitionWriter implements ElementProxyBuilder<OutputObjectDef> {

    protected final ClassElement proxyType;
    protected final MethodElement constructor;
    protected final ClassElement targetType;
    protected final BeanDefinitionWriter proxyBeanDefinitionWriter;
    protected final Set<AnnotationValue<?>> interceptorBinding;
    protected final BeanDefinitionWriter parentWriter;
    protected final boolean isProxyTarget;
    protected final boolean isIntroduction;
    protected final boolean implementInterface;
    protected final Set<ClassElement> interfaceTypes = new LinkedHashSet<>();

    protected VisitorContext visitorContext;

    protected final OriginatingElements originatingElements;
    protected final ExecutableMethodsDefinitionWriter executableMethodsDefinitionWriter;

    /**
     * <p>Constructs a new {@link ProxyingBeanDefinitionWriter} for the given parent {@link BeanDefinitionWriter} and starting interceptors types.</p>
     *
     * <p>Additional {@link Interceptor} types can be added downstream with {@link #visitInterceptorBinding(AnnotationValue[])} .</p>
     *
     * @param constructor        The constructor used to materialize the proxy
     * @param proxyType          The proxyType
     * @param targetType         The targetType
     * @param parent             The parent {@link BeanDefinitionWriter}
     * @param settings           optional setting
     * @param visitorContext     The visitor context
     * @param interceptorBinding The interceptor binding of the {@link Interceptor} instances to be injected
     */
    public ProxyingBeanDefinitionWriter(MethodElement constructor,
                                        ClassElement proxyType,
                                        ClassElement targetType,
                                        BeanDefinitionWriter parent,
                                        OptionalValues<Boolean> settings,
                                        VisitorContext visitorContext,
                                        AnnotationValue<?>... interceptorBinding) {
        this.constructor = constructor;
        this.originatingElements = OriginatingElements.of(parent.getOriginatingElements());
        this.proxyType = proxyType;
        this.targetType = targetType;
        this.parentWriter = parent;
        this.isProxyTarget = getProxyTarget(targetType, parent, settings);
        parent.setProxiedBean(true, isProxyTarget);
        this.isIntroduction = false;
        this.implementInterface = true;
        this.interceptorBinding = toInterceptorBindingMap(interceptorBinding);
        this.visitorContext = visitorContext;
        this.proxyBeanDefinitionWriter = new BeanDefinitionWriter(
            BeanInjectionUtils.createConstructorDefinition(constructor, visitorContext),
            getCustomBeanDefinitionName(),
            null,
            OriginatingElements.of(targetType),
            visitorContext
        );
        proxyBeanDefinitionWriter.setInterceptedType(targetType.getName());

        AnnotationValue<?>[] interceptorTypes =
            InterceptedMethodUtil.resolveInterceptorBinding(constructor.getAnnotationMetadata(), InterceptorKind.AROUND_CONSTRUCT);
        visitInterceptorBinding(interceptorTypes);

        executableMethodsDefinitionWriter = parentWriter.getExecutableMethodsWriter();
        proxyBeanDefinitionWriter.setExecutableMethodsWriter(executableMethodsDefinitionWriter);
        parentWriter.dontGenerateExecutableMethodsDefinitionWriter(); // Proxy will produce the writer
    }

    /**
     * Constructs a new {@link ProxyingBeanDefinitionWriter} for the purposes of writing {@link io.micronaut.aop.Introduction} advise.
     *
     * @param constructor        The constructor
     * @param proxyType          The proxy type
     * @param targetType         The target type
     * @param visitorContext     The visitor context
     * @param interceptorBinding The interceptor types
     */
    public ProxyingBeanDefinitionWriter(MethodElement constructor,
                                        ClassElement proxyType,
                                        ClassElement targetType,
                                        VisitorContext visitorContext,
                                        AnnotationValue<?>... interceptorBinding) {
        this(constructor, proxyType, targetType, true, visitorContext, interceptorBinding);
    }

    /**
     * Constructs a new {@link ProxyingBeanDefinitionWriter} for the purposes of writing {@link io.micronaut.aop.Introduction} advise.
     *
     * @param constructor        The constructor
     * @param proxyType          The proxy type
     * @param targetType         The target type
     * @param implementInterface Whether the interface should be implemented. If false the {@code interfaceTypes} argument should contain at least one entry
     * @param visitorContext     The visitor context
     * @param interceptorBinding The interceptor binding
     */
    public ProxyingBeanDefinitionWriter(MethodElement constructor,
                                        ClassElement proxyType,
                                        ClassElement targetType,
                                        boolean implementInterface,
                                        VisitorContext visitorContext,
                                        AnnotationValue<?>... interceptorBinding) {
        this.constructor = constructor;
        this.originatingElements = targetType.getNativeType() instanceof Class<?> || targetType.getNativeType() instanceof String ? OriginatingElements.of() : OriginatingElements.of(targetType);

        this.implementInterface = implementInterface;
        this.proxyType = proxyType;
        this.targetType = targetType;
        this.isProxyTarget = false;
        this.parentWriter = null;
        this.interceptorBinding = toInterceptorBindingMap(interceptorBinding);
        this.isIntroduction = true;
        this.visitorContext = visitorContext;
        this.proxyBeanDefinitionWriter = new BeanDefinitionWriter(
            BeanInjectionUtils.createConstructorDefinition(constructor, visitorContext),
            getCustomBeanDefinitionName(),
            targetType.getAnnotationMetadata(),
            implementInterface ? OriginatingElements.of(targetType) : OriginatingElements.of(),
            visitorContext
        );
        if (targetType.isInterface()) {
            if (implementInterface) {
                proxyBeanDefinitionWriter.setInterceptedType(targetType.getName());
            }
        } else {
            proxyBeanDefinitionWriter.setInterceptedType(targetType.getName());
        }
        executableMethodsDefinitionWriter = null;
    }

    /**
     * Allows subclasses to provide a custom bean definition name for the proxy.
     * Implementations must return either {@code null} to use the default naming strategy
     * or a fully qualified class name unique within the module.
     *
     * @return The custom bean definition name or {@code null} to use the default
     */
    @Nullable
    protected String getCustomBeanDefinitionName() {
        return null;
    }

    @Override
    public ProxyingBeanDefinitionWriter implementInterface(ClassElement interfaceElement) {
        interfaceTypes.add(interfaceElement);
        proxyBeanDefinitionWriter.setExposes(interfaceTypes);
        proxyBeanDefinitionWriter.addOriginatingElement(interfaceElement);

        Map<String, ClassElement> typeArguments = interfaceElement.getTypeArguments();
        if (!typeArguments.isEmpty()) {
            proxyBeanDefinitionWriter.addTypeArguments(Map.of(interfaceElement.getName(), typeArguments));
        }

        interfaceElement.getEnclosedElements(ElementQuery.ALL_METHODS)
            .forEach(methodElement -> {

                MutableAnnotationMetadata mutableAnnotationMetadata = MutableAnnotationMetadata.of(targetType.getAnnotationMetadata());
                mutableAnnotationMetadata.addAnnotationMetadata(MutableAnnotationMetadata.of(methodElement.getMethodAnnotationMetadata()));
                methodElement = methodElement.withAnnotationMetadata(mutableAnnotationMetadata);

                addProxyMethodInternal(methodElement, true);
            });
        return this;
    }

    @Override
    public ProxyingBeanDefinitionWriter addProxyMethod(MethodElement methodElement) {
        addProxyMethodInternal(methodElement, false);
        return this;
    }

    private void addProxyMethodInternal(MethodElement methodElement, boolean ignoreNotAbstract) {
        if (!targetType.getName().equals(methodElement.getDeclaringType().getName())) {
            addOriginatingElement(methodElement.getDeclaringType());
        }

        if (methodElement.isAbstract()) {
            addIntroductionMethod(methodElement);
        } else if (!ignoreNotAbstract) {
            // only apply around advise to non-abstract methods of introduction advise
            addAroundMethod(methodElement);
        }
    }

    protected static MethodElement getConstructor(ClassElement type) {
        MethodElement constructor = type.getPrimaryConstructor().orElse(null);
        if (constructor == null) {
            return MethodElement.of(
                type,
                AnnotationMetadata.EMPTY_METADATA,
                type,
                type,
                "<init>"
            );
        }
        return constructor;
    }

    /**
     * @param targetType The target type
     * @param parent The parent
     * @param settings The settings
     * @return is proxy target
     */
    protected boolean getProxyTarget(ClassElement targetType, BeanDefinitionWriter parent, OptionalValues<Boolean> settings) {
        return settings.get(Interceptor.PROXY_TARGET).orElse(false) || parent.isInterface();
    }

    @Override
    public List<OutputObjectDef> build() {
        if (parentWriter != null && !isProxyTarget) {
            processAlreadyVisitedMethods(parentWriter);
        }

        postConstructor();

        if (parentWriter != null) {
            proxyBeanDefinitionWriter.visitBeanDefinitionInterface(ProxyBeanDefinition.class);
            proxyBeanDefinitionWriter.generateProxyReference(parentWriter.getBeanDefinitionName(), parentWriter.getBeanTypeName());
        }
        List<OutputObjectDef> classes = new ArrayList<>(proxyBeanDefinitionWriter.build());
        if (parentWriter != null) {
            ExecutableMethodsDefinitionWriter executableMethodsWriter = parentWriter.findExecutableMethodsWriter();
            if (executableMethodsWriter != null) {
                classes.add(executableMethodsWriter.build());
            }
        }
        return classes;
    }

    public void postConstructor() {
    }

    /**
     * visitInterceptorTypes.
     *
     * @param interceptorBinding the interceptor binding
     */
    protected void visitInterceptorBinding(AnnotationValue<?>... interceptorBinding) {
        if (interceptorBinding != null) {
            for (AnnotationValue<?> annotationValue : interceptorBinding) {
                annotationValue.stringValue().ifPresent(annName ->
                    this.interceptorBinding.add(annotationValue)
                );
            }
        }
    }

    private Set<AnnotationValue<?>> toInterceptorBindingMap(AnnotationValue<?>[] interceptorBinding) {
        return new LinkedHashSet<>(Arrays.asList(interceptorBinding));
    }

    protected final void processAlreadyVisitedMethods(BeanDefinitionWriter parent) {
        parent.getPostConstructMethods().forEach(proxyBeanDefinitionWriter::addPostConstruct);
    }

    @Override
    public Element[] getOriginatingElements() {
        return originatingElements.getOriginatingElements();
    }

    @Override
    public void addOriginatingElement(Element element) {
        originatingElements.addOriginatingElement(element);
    }

    /**
     * @param p The class element
     * @return The string representation
     */
    protected static String toTypeString(ClassElement p) {
        String name = p.getName();
        if (p.isArray()) {
            return name + IntStream.range(0, p.getArrayDimensions()).mapToObj(ignore -> "[]").collect(Collectors.joining());
        }
        return name;
    }

    /**
     * Find overridden by method with a different signature.
     *
     * @param methodElement The method element
     * @return the overridden
     */
    @Nullable
    protected final MethodElement findOverriddenBy(MethodElement methodElement) {
        final Optional<MethodElement> overridden = methodElement.getOwningType()
            .getEnclosedElement(ElementQuery.ALL_METHODS
                .onlyInstance()
                .filter(el -> el.getName().equals(methodElement.getName()) && el.overrides(methodElement)));

        if (overridden.isPresent()) {
            MethodElement overriddenBy = overridden.get();

            String methodElementKey = methodElement.getName() +
                Arrays.stream(methodElement.getSuspendParameters())
                    .map(p -> toTypeString(p.getType()))
                    .collect(Collectors.joining(","));

            String overriddenByKey = overriddenBy.getName() +
                Arrays.stream(methodElement.getSuspendParameters())
                    .map(p -> toTypeString(p.getGenericType()))
                    .collect(Collectors.joining(","));
            if (!methodElementKey.equals(overriddenByKey)) {
                return overriddenBy;
            }
        }

        return null;
    }

    @Override
    public ProxyingBeanDefinitionWriter addAroundMethod(MethodElement methodElement) {
        if (findOverriddenBy(methodElement) != null) {
            return this;
        }

        AnnotationMetadata methodAnnotationMetadata = methodElement.getMethodAnnotationMetadata();

        if (InterceptedMethodUtil.hasAroundStereotype(methodAnnotationMetadata)) {
            visitInterceptorBinding(
                InterceptedMethodUtil.resolveInterceptorBinding(methodAnnotationMetadata, InterceptorKind.AROUND)
            );
        }

        BeanDefinitionWriter beanDefinitionWriter = parentWriter == null ? proxyBeanDefinitionWriter : parentWriter;
        beanDefinitionWriter.addExecutableMethod(methodElement, false);
        return this;
    }

    @Override
    public ProxyingBeanDefinitionWriter addIntroductionMethod(MethodElement methodElement) {
        addAroundMethod(methodElement);
        return this;
    }

    @Override
    public ElementBeanDefinitionBuilder<OutputObjectDef> beanDefinitionBuilder() {
        return proxyBeanDefinitionWriter;
    }

}
