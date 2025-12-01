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
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ProxyBeanDefinition;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.configuration.builder.ConfigurationBuilderDefinition;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionWriter;
import io.micronaut.inject.writer.ClassOutputWriter;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.OriginatingElements;
import io.micronaut.inject.writer.ProxyingBeanDefinitionVisitor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
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
@Internal
public abstract class ProxyingBeanDefinitionWriter implements ProxyingBeanDefinitionVisitor, ClassOutputWriter {

    protected final ClassElement proxyType;
    protected final ClassElement targetType;
    protected final BeanDefinitionWriter proxyBeanDefinitionWriter;
    protected final Set<AnnotationValue<?>> interceptorBinding;
    protected final BeanDefinitionWriter parentWriter;
    protected final boolean isProxyTarget;
    protected final boolean isIntroduction;
    protected final boolean implementInterface;

    protected final List<Runnable> deferredInjectionPoints = new ArrayList<>();
    protected boolean constructorRequiresReflection;
    protected MethodElement declaredConstructor;
    protected VisitorContext visitorContext;

    protected final OriginatingElements originatingElements;

    /**
     * <p>Constructs a new {@link ProxyingBeanDefinitionWriter} for the given parent {@link BeanDefinitionWriter} and starting interceptors types.</p>
     *
     * <p>Additional {@link Interceptor} types can be added downstream with {@link #visitInterceptorBinding(AnnotationValue[])} .</p>
     *
     * @param suffix             The proxy name suffix
     * @param proxyType          The proxyType
     * @param targetType          The targetType
     * @param parent             The parent {@link BeanDefinitionWriter}
     * @param settings           optional setting
     * @param visitorContext     The visitor context
     * @param interceptorBinding The interceptor binding of the {@link Interceptor} instances to be injected
     */
    public ProxyingBeanDefinitionWriter(@Nullable String suffix,
                                        ClassElement proxyType,
                                        ClassElement targetType,
                                        BeanDefinitionWriter parent,
                                        OptionalValues<Boolean> settings,
                                        VisitorContext visitorContext,
                                        AnnotationValue<?>... interceptorBinding) {

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
        this.proxyBeanDefinitionWriter = createAdviceProxyBeanDefinitionWriter(suffix);
        proxyBeanDefinitionWriter.setRequiresMethodProcessing(parent.requiresMethodProcessing());
        proxyBeanDefinitionWriter.setInterceptedType(targetType.getName());
    }

    /**
     * Constructs a new {@link ProxyingBeanDefinitionWriter} for the purposes of writing {@link io.micronaut.aop.Introduction} advise.
     *
     * @param suffix             The proxy name suffix
     * @param proxyType          The proxy type
     * @param targetType         The target type
     * @param interfaceTypes     The additional interfaces to implement
     * @param visitorContext     The visitor context
     * @param interceptorBinding The interceptor types
     */
    public ProxyingBeanDefinitionWriter(@Nullable String suffix,
                                        ClassElement proxyType,
                                        ClassElement targetType,
                                        ClassElement[] interfaceTypes,
                                        VisitorContext visitorContext,
                                        AnnotationValue<?>... interceptorBinding) {
        this(suffix, proxyType, targetType, true, interfaceTypes, visitorContext, interceptorBinding);
    }

    /**
     * Constructs a new {@link ProxyingBeanDefinitionWriter} for the purposes of writing {@link io.micronaut.aop.Introduction} advise.
     *
     * @param suffix             The proxy name suffix
     * @param proxyType          The proxy type
     * @param targetType         The target type
     * @param implementInterface Whether the interface should be implemented. If false the {@code interfaceTypes} argument should contain at least one entry
     * @param interfaceTypes     The additional interfaces to implement
     * @param visitorContext     The visitor context
     * @param interceptorBinding The interceptor binding
     */
    public ProxyingBeanDefinitionWriter(@Nullable String suffix,
                                        ClassElement proxyType,
                                        ClassElement targetType,
                                        boolean implementInterface,
                                        ClassElement[] interfaceTypes,
                                        VisitorContext visitorContext,
                                        AnnotationValue<?>... interceptorBinding) {
        this.originatingElements = targetType.getNativeType() instanceof Class<?> || targetType.getNativeType() instanceof String ? OriginatingElements.of() : OriginatingElements.of(targetType);

        if (!implementInterface && ArrayUtils.isEmpty(interfaceTypes)) {
            throw new IllegalArgumentException("if argument implementInterface is false at least one interface should be provided to the 'interfaceTypes' argument");
        }
        this.implementInterface = implementInterface;
        this.proxyType = proxyType;
        this.targetType = targetType;
        this.isProxyTarget = false;
        this.parentWriter = null;
        this.interceptorBinding = toInterceptorBindingMap(interceptorBinding);
        this.isIntroduction = true;
        this.visitorContext = visitorContext;
        this.proxyBeanDefinitionWriter = createIntroductionProxyBeanDefinitionWriter(suffix);
        if (targetType.isInterface()) {
            if (implementInterface) {
                proxyBeanDefinitionWriter.setInterceptedType(targetType.getName());
            }
        } else {
            proxyBeanDefinitionWriter.setInterceptedType(targetType.getName());
        }
        if (interfaceTypes != null && interfaceTypes.length > 0) {
            proxyBeanDefinitionWriter.setExposes(Set.of(interfaceTypes));
        }
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

    /**
     * @param suffix The name suffix
     * @return Create the advice bean definition writer
     */
    protected abstract BeanDefinitionWriter createAdviceProxyBeanDefinitionWriter(String suffix);

    /**
     * @param suffix The name suffix
     * @return Create the introduction bean definition writer
     */
    protected abstract BeanDefinitionWriter createIntroductionProxyBeanDefinitionWriter(String suffix);

    @Override
    public boolean isEnabled() {
        return proxyBeanDefinitionWriter.isEnabled();
    }

    /**
     * Is the target bean being proxied.
     *
     * @return True if the target bean is being proxied
     */
    @Override
    public boolean isProxyTarget() {
        return false;
    }

    @Override
    public Element getOriginatingElement() {
        return originatingElements.getOriginatingElements()[0];
    }

    @Override
    public void visitBeanFactoryMethod(ClassElement factoryClass, MethodElement factoryMethod) {
        proxyBeanDefinitionWriter.visitBeanFactoryMethod(factoryClass, factoryMethod);
    }

    @Override
    public void visitBeanFactoryMethod(ClassElement factoryClass, MethodElement factoryMethod, ParameterElement[] parameters) {
        proxyBeanDefinitionWriter.visitBeanFactoryMethod(factoryClass, factoryMethod, parameters);
    }

    @Override
    public void visitBeanFactoryField(ClassElement factoryClass, FieldElement factoryField) {
        proxyBeanDefinitionWriter.visitBeanFactoryField(factoryClass, factoryField);
    }

    @Override
    public boolean isSingleton() {
        return proxyBeanDefinitionWriter.isSingleton();
    }

    @Override
    public boolean isInterface() {
        return targetType.isInterface();
    }

    @Override
    public void visitBeanDefinitionInterface(Class<? extends BeanDefinition> interfaceType) {
        proxyBeanDefinitionWriter.visitBeanDefinitionInterface(interfaceType);
    }

    @Override
    public String getBeanTypeName() {
        return proxyBeanDefinitionWriter.getBeanTypeName();
    }

    @Override
    public void setValidated(boolean validated) {
        proxyBeanDefinitionWriter.setValidated(validated);
    }

    @Override
    public void setInterceptedType(String typeName) {
        proxyBeanDefinitionWriter.setInterceptedType(typeName);
    }

    @Override
    public void setExposes(Set<ClassElement> exposes) {
        proxyBeanDefinitionWriter.setExposes(exposes);
    }

    @Override
    public Optional<String> getInterceptedType() {
        return proxyBeanDefinitionWriter.getInterceptedType();
    }

    @Override
    public boolean isValidated() {
        return proxyBeanDefinitionWriter.isValidated();
    }

    @Override
    public String getBeanDefinitionName() {
        return proxyBeanDefinitionWriter.getBeanDefinitionName();
    }

    /**
     * Visits a constructor.
     *
     * @param constructor        The constructor
     * @param requiresReflection Whether reflection is required
     * @param visitorContext     The visitor context
     */
    @Override
    public void visitBeanDefinitionConstructor(
        MethodElement constructor,
        boolean requiresReflection,
        VisitorContext visitorContext) {
        this.constructorRequiresReflection = requiresReflection;
        this.declaredConstructor = constructor;
        this.visitorContext = visitorContext;
        AnnotationValue<?>[] interceptorTypes =
            InterceptedMethodUtil.resolveInterceptorBinding(constructor.getAnnotationMetadata(), InterceptorKind.AROUND_CONSTRUCT);
        visitInterceptorBinding(interceptorTypes);
    }

    @Override
    public void visitDefaultConstructor(AnnotationMetadata annotationMetadata, VisitorContext visitorContext) {
        this.constructorRequiresReflection = false;
        this.visitorContext = visitorContext;
        ClassElement classElement = ClassElement.of(proxyType.getName());
        this.declaredConstructor = MethodElement.of(
            classElement,
            annotationMetadata,
            classElement,
            classElement,
            "<init>"
        );
    }

    @NonNull
    @Override
    public String getBeanDefinitionReferenceClassName() {
        return proxyBeanDefinitionWriter.getBeanDefinitionReferenceClassName();
    }

    /**
     * Visit an abstract method that is to be implemented.
     *
     * @param declaringBean The declaring bean of the method.
     * @param methodElement The method element
     */
    public void visitIntroductionMethod(TypedElement declaringBean,
                                        MethodElement methodElement) {
        visitAroundMethod(declaringBean, methodElement);
    }

    /**
     * Visit a method that is to be proxied.
     *
     * @param beanType      The bean type.
     * @param methodElement The method element
     **/
    public void visitAroundMethod(TypedElement beanType,
                                  MethodElement methodElement) {

        if (findOverriddenBy(methodElement) != null) {
            return;
        }

        BeanDefinitionWriter beanDefinitionWriter = parentWriter == null ? proxyBeanDefinitionWriter : parentWriter;
        beanDefinitionWriter.visitExecutableMethod(
            beanType,
            methodElement,
            visitorContext
        );
    }

    /**
     * Finalizes the proxy. This method should be called before writing the proxy to disk with {@link #writeTo(File)}
     */
    @Override
    public void visitBeanDefinitionEnd() {
        if (declaredConstructor == null) {
            throw new IllegalStateException("The method visitBeanDefinitionConstructor(..) should be called at least once");
        }
        if (parentWriter != null && !isProxyTarget) {
            processAlreadyVisitedMethods(parentWriter);
        }

        proxyBeanDefinitionWriter.visitBeanDefinitionConstructor(
            declaredConstructor,
            constructorRequiresReflection,
            visitorContext
        );

        postConstructor();

        if (parentWriter != null) {
            proxyBeanDefinitionWriter.visitBeanDefinitionInterface(ProxyBeanDefinition.class);
            proxyBeanDefinitionWriter.generateProxyReference(parentWriter.getBeanDefinitionName(), parentWriter.getBeanTypeName());
        }

        for (Runnable deferred : deferredInjectionPoints) {
            deferred.run();
        }

        proxyBeanDefinitionWriter.visitBeanDefinitionEnd();
    }

    public void postConstructor() {
    }

    @NonNull
    @Override
    public ClassElement[] getTypeArguments() {
        return proxyBeanDefinitionWriter.getTypeArguments();
    }

    @Override
    public Map<String, ClassElement> getTypeArgumentMap() {
        return proxyBeanDefinitionWriter.getTypeArgumentMap();
    }

    /**
     * Write the class to output via a visitor that manages output destination.
     *
     * @param visitor the writer output visitor
     * @throws IOException If an error occurs
     */
    @Override
    public void accept(ClassWriterOutputVisitor visitor) throws IOException {
        proxyBeanDefinitionWriter.accept(visitor);
    }

    @Override
    public void visitSuperBeanDefinition(String name) {
        proxyBeanDefinitionWriter.visitSuperBeanDefinition(name);
    }

    @Override
    public void visitSuperBeanDefinitionFactory(String beanName) {
        proxyBeanDefinitionWriter.visitSuperBeanDefinitionFactory(beanName);
    }

    @Override
    public void visitSetterValue(
        TypedElement declaringType,
        MethodElement methodElement,
        AnnotationMetadata annotationMetadata,
        boolean requiresReflection,
        boolean isOptional) {
        deferredInjectionPoints.add(() ->
            proxyBeanDefinitionWriter.visitSetterValue(
                declaringType,
                methodElement,
                annotationMetadata,
                requiresReflection,
                isOptional
            )
        );
    }

    @Override
    public void visitPostConstructMethod(
        TypedElement declaringType,
        MethodElement methodElement,
        boolean requiresReflection,
        VisitorContext visitorContext) {
        deferredInjectionPoints.add(() -> proxyBeanDefinitionWriter.visitPostConstructMethod(
            declaringType,
            methodElement,
            requiresReflection,
            visitorContext
        ));
    }

    @Override
    public void visitPreDestroyMethod(
        TypedElement declaringType,
        MethodElement methodElement,
        boolean requiresReflection,
        VisitorContext visitorContext) {
        deferredInjectionPoints.add(() ->
            proxyBeanDefinitionWriter.visitPreDestroyMethod(
                declaringType,
                methodElement,
                requiresReflection,
                visitorContext)
        );
    }

    @Override
    public void visitMethodInjectionPoint(TypedElement beanType,
                                          MethodElement methodElement,
                                          boolean requiresReflection,
                                          VisitorContext visitorContext) {
        deferredInjectionPoints.add(() ->
            proxyBeanDefinitionWriter.visitMethodInjectionPoint(
                beanType,
                methodElement,
                requiresReflection,
                visitorContext)
        );
    }

    @Override
    public int visitExecutableMethod(
        TypedElement declaringBean,
        MethodElement methodElement,
        VisitorContext visitorContext) {
        deferredInjectionPoints.add(() ->
            proxyBeanDefinitionWriter.visitExecutableMethod(
                declaringBean,
                methodElement,
                visitorContext
            )
        );
        return -1;
    }

    @Override
    public void visitFieldInjectionPoint(
        TypedElement declaringType,
        FieldElement fieldType,
        boolean requiresReflection, VisitorContext visitorContext) {
        deferredInjectionPoints.add(() ->
            proxyBeanDefinitionWriter.visitFieldInjectionPoint(
                declaringType,
                fieldType,
                requiresReflection,
                visitorContext
            )
        );
    }

    @Override
    public void visitAnnotationMemberPropertyInjectionPoint(TypedElement annotationMemberBeanType,
                                                            String annotationMemberProperty,
                                                            String requiredValue,
                                                            String notEqualsValue) {
        deferredInjectionPoints.add(() ->
            proxyBeanDefinitionWriter.visitAnnotationMemberPropertyInjectionPoint(
                annotationMemberBeanType,
                annotationMemberProperty,
                requiredValue,
                notEqualsValue));
    }

    @Override
    public void visitFieldValue(TypedElement declaringType,
                                FieldElement fieldType,
                                boolean requiresReflection, boolean isOptional) {
        deferredInjectionPoints.add(() ->
            proxyBeanDefinitionWriter.visitFieldValue(
                declaringType,
                fieldType, requiresReflection, isOptional
            )
        );
    }

    @Override
    public String getPackageName() {
        return proxyBeanDefinitionWriter.getPackageName();
    }

    @Override
    public String getBeanSimpleName() {
        return proxyBeanDefinitionWriter.getBeanSimpleName();
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return proxyBeanDefinitionWriter.getAnnotationMetadata();
    }

    @Override
    public void visitConfigBuilder(ConfigurationBuilderDefinition builderDefinition) {
        proxyBeanDefinitionWriter.visitConfigBuilder(builderDefinition);
    }

    @Override
    public void visitConfigBuilderField(ClassElement type, String field, AnnotationMetadata annotationMetadata, boolean isInterface) {
        proxyBeanDefinitionWriter.visitConfigBuilderField(type, field, annotationMetadata, isInterface);
    }

    @Override
    public void visitConfigBuilderMethod(ClassElement type, String methodName, AnnotationMetadata annotationMetadata, boolean isInterface) {
        proxyBeanDefinitionWriter.visitConfigBuilderMethod(type, methodName, annotationMetadata, isInterface);
    }

    @Override
    public void visitConfigBuilderMethod(String propertyName, ClassElement returnType, String methodName, ClassElement paramType, Map<String, ClassElement> generics, String propertyPath) {
        proxyBeanDefinitionWriter.visitConfigBuilderMethod(propertyName, returnType, methodName, paramType, generics, propertyPath);
    }

    @Override
    public void visitConfigBuilderDurationMethod(String propertyName, ClassElement returnType, String methodName, String propertyPath) {
        proxyBeanDefinitionWriter.visitConfigBuilderDurationMethod(propertyName, returnType, methodName, propertyPath);
    }

    @Override
    public void visitConfigBuilderEnd() {
        proxyBeanDefinitionWriter.visitConfigBuilderEnd();
    }

    @Override
    public void setRequiresMethodProcessing(boolean shouldPreProcess) {
        proxyBeanDefinitionWriter.setRequiresMethodProcessing(shouldPreProcess);
    }

    @Override
    public void visitTypeArguments(Map<String, Map<String, ClassElement>> typeArguments) {
        proxyBeanDefinitionWriter.visitTypeArguments(typeArguments);
    }

    @Override
    public boolean requiresMethodProcessing() {
        return proxyBeanDefinitionWriter.requiresMethodProcessing() || (parentWriter != null && parentWriter.requiresMethodProcessing());
    }

    /**
     * visitInterceptorTypes.
     *
     * @param interceptorBinding the interceptor binding
     */
    public void visitInterceptorBinding(AnnotationValue<?>... interceptorBinding) {
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
        final List<BeanDefinitionWriter.MethodVisitData> postConstructMethodVisits = parent.getPostConstructMethodVisits();
        for (BeanDefinitionWriter.MethodVisitData methodVisit : postConstructMethodVisits) {
            visitPostConstructMethod(
                methodVisit.getBeanType(),
                methodVisit.getMethodElement(),
                methodVisit.isRequiresReflection(),
                visitorContext
            );
        }
    }

    @Override
    public @NonNull Element[] getOriginatingElements() {
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

}
