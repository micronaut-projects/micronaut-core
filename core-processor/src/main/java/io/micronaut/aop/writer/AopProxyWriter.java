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

import io.micronaut.aop.HotSwappableInterceptedProxy;
import io.micronaut.aop.Intercepted;
import io.micronaut.aop.InterceptedProxy;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.aop.Introduced;
import io.micronaut.aop.chain.InterceptorChain;
import io.micronaut.aop.chain.MethodInterceptorChain;
import io.micronaut.aop.internal.intercepted.InterceptedMethodUtil;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanDefinitionRegistry;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.ProxyBeanDefinition;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.processing.JavaModelUtils;
import io.micronaut.inject.qualifiers.Qualified;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.ArgumentExpUtils;
import io.micronaut.inject.writer.BeanDefinitionWriter;
import io.micronaut.inject.writer.ClassOutputWriter;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.ExecutableMethodsDefinitionWriter;
import io.micronaut.inject.writer.OriginatingElements;
import io.micronaut.inject.writer.ProxyingBeanDefinitionVisitor;
import io.micronaut.inject.writer.MethodGenUtils;
import io.micronaut.sourcegen.bytecode.ByteCodeWriter;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;
import org.objectweb.asm.Type;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.micronaut.core.annotation.AnnotationUtil.ZERO_ANNOTATION_VALUES;
import static io.micronaut.inject.ast.ParameterElement.ZERO_PARAMETER_ELEMENTS;

/**
 * A class that generates AOP proxy classes at compile time.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class AopProxyWriter implements ProxyingBeanDefinitionVisitor, ClassOutputWriter {

    public static final int ADDITIONAL_PARAMETERS_COUNT = 5;

    private static final Method METHOD_GET_PROXY_TARGET_BEAN_WITH_BEAN_DEFINITION_AND_CONTEXT = ReflectionUtils.getRequiredInternalMethod(
        DefaultBeanContext.class,
        "getProxyTargetBean",
        BeanResolutionContext.class,
        BeanDefinition.class,
        Argument.class,
        Qualifier.class
    );

    private static final Method METHOD_GET_PROXY_BEAN_DEFINITION = ReflectionUtils.getRequiredInternalMethod(
        BeanDefinitionRegistry.class,
        "getProxyTargetBeanDefinition",
        Argument.class,
        Qualifier.class
    );

    private static final Method METHOD_INTERCEPTED_TARGET = ReflectionUtils.getRequiredInternalMethod(
        InterceptedProxy.class,
        "interceptedTarget"
    );

    private static final Method METHOD_HAS_CACHED_INTERCEPTED_METHOD = ReflectionUtils.getRequiredInternalMethod(
        InterceptedProxy.class,
        "hasCachedInterceptedTarget"
    );

    private static final Method METHOD_BEAN_DEFINITION_GET_REQUIRED_METHOD = ReflectionUtils.getRequiredInternalMethod(
        BeanDefinition.class,
        "getRequiredMethod",
        String.class,
        Class[].class
    );

    private static final Method GET_READ_LOCK_METHOD = ReflectionUtils.getRequiredInternalMethod(
        ReadWriteLock.class,
        "readLock"
    );

    private static final Method GET_WRITE_LOCK_METHOD = ReflectionUtils.getRequiredInternalMethod(
        ReadWriteLock.class,
        "writeLock"
    );

    private static final Method LOCK_METHOD = ReflectionUtils.getRequiredInternalMethod(
        Lock.class,
        "lock"
    );

    private static final Method UNLOCK_METHOD = ReflectionUtils.getRequiredInternalMethod(
        Lock.class,
        "unlock"
    );

    private static final Method SWAP_METHOD = ReflectionUtils.getRequiredInternalMethod(
        HotSwappableInterceptedProxy.class,
        "swap",
        Object.class
    );

    private static final Method WITH_QUALIFIER_METHOD = ReflectionUtils.getRequiredInternalMethod(
        Qualified.class,
        "$withBeanQualifier",
        Qualifier.class
    );

    private static final String FIELD_TARGET = "$target";
    private static final String FIELD_BEAN_RESOLUTION_CONTEXT = "$beanResolutionContext";
    private static final String FIELD_READ_WRITE_LOCK = "$target_rwl";
    private static final String FIELD_READ_LOCK = "$target_rl";
    private static final String FIELD_WRITE_LOCK = "$target_wl";

    private static final Method RESOLVE_INTRODUCTION_INTERCEPTORS_METHOD = ReflectionUtils.getRequiredInternalMethod(InterceptorChain.class, "resolveIntroductionInterceptors", InterceptorRegistry.class, ExecutableMethod.class, List.class);

    private static final Method RESOLVE_AROUND_INTERCEPTORS_METHOD = ReflectionUtils.getRequiredInternalMethod(InterceptorChain.class, "resolveAroundInterceptors", InterceptorRegistry.class, ExecutableMethod.class, List.class);

    private static final Constructor<?> CONSTRUCTOR_METHOD_INTERCEPTOR_CHAIN = ReflectionUtils.findConstructor(MethodInterceptorChain.class, Interceptor[].class, Object.class, ExecutableMethod.class, Object[].class).orElseThrow(() ->
        new IllegalStateException("new MethodInterceptorChain(..) constructor not found. Incompatible version of Micronaut?")
    );

    private static final Constructor<?> CONSTRUCTOR_METHOD_INTERCEPTOR_CHAIN_NO_PARAMS = ReflectionUtils.findConstructor(MethodInterceptorChain.class, Interceptor[].class, Object.class, ExecutableMethod.class).orElseThrow(() ->
        new IllegalStateException("new MethodInterceptorChain(..) constructor not found. Incompatible version of Micronaut?")
    );
    private static final String INTERCEPTORS_PARAMETER = "$interceptors";

    private static final Method METHOD_PROCEED = ReflectionUtils.getRequiredInternalMethod(InterceptorChain.class, "proceed");

    private static final Method COPY_BEAN_CONTEXT_METHOD = ReflectionUtils.getRequiredMethod(BeanResolutionContext.class, "copy");

    private static final String FIELD_INTERCEPTORS = "$interceptors";
    private static final String FIELD_BEAN_LOCATOR = "$beanLocator";
    private static final String FIELD_BEAN_QUALIFIER = "$beanQualifier";
    private static final String FIELD_PROXY_METHODS = "$proxyMethods";
    private static final String FIELD_PROXY_BEAN_DEFINITION = "$proxyBeanDefinition";

    private final String packageName;
    private final String targetClassShortName;
    private final String targetClassFullName;
    private final String proxyFullName;
    private final BeanDefinitionWriter proxyBeanDefinitionWriter;
    private final Set<AnnotationValue<?>> interceptorBinding;
    private final Set<ClassElement> interfaceTypes;
    private final boolean hotswap;
    private final boolean lazy;
    private final boolean cacheLazyTarget;
    private final boolean isInterface;
    private final BeanDefinitionWriter parentWriter;
    private final boolean isIntroduction;
    private final boolean implementInterface;
    private final boolean isProxyTarget;

    private final List<MethodRef> proxiedMethods = new ArrayList<>();
    private final Set<MethodRef> proxiedMethodsRefSet = new HashSet<>();
    private final List<MethodRef> proxyTargetMethods = new ArrayList<>();
    private int proxyMethodCount = 0;
    private int interceptorsListArgumentIndex;
    private int beanResolutionContextArgumentIndex = -1;
    private int beanContextArgumentIndex = -1;
    private int interceptorRegistryArgumentIndex = -1;
    private int qualifierIndex;
    private final List<Runnable> deferredInjectionPoints = new ArrayList<>();
    private boolean constructorRequiresReflection;
    private MethodElement declaredConstructor;
    private MethodElement newConstructor;
    private MethodElement realConstructor;
    private List<Map.Entry<ParameterElement, Integer>> superConstructorParametersBinding;
    private ParameterElement qualifierParameter;
    private ParameterElement interceptorsListParameter;
    private VisitorContext visitorContext;

    private final OriginatingElements originatingElements;

    private final ClassDef.ClassDefBuilder proxyBuilder;
    private final FieldDef interceptorsField;
    private final FieldDef proxyMethodsField;
    private FieldDef targetField;

    /**
     * <p>Constructs a new {@link AopProxyWriter} for the given parent {@link BeanDefinitionWriter} and starting interceptors types.</p>
     *
     * <p>Additional {@link Interceptor} types can be added downstream with {@link #visitInterceptorBinding(AnnotationValue[])} .</p>
     *
     * @param parent             The parent {@link BeanDefinitionWriter}
     * @param settings           optional setting
     * @param visitorContext     The visitor context
     * @param interceptorBinding The interceptor binding of the {@link Interceptor} instances to be injected
     */
    public AopProxyWriter(BeanDefinitionWriter parent,
                          OptionalValues<Boolean> settings,
                          VisitorContext visitorContext,
                          AnnotationValue<?>... interceptorBinding) {

        this.originatingElements = OriginatingElements.of(parent.getOriginatingElements());

        this.isIntroduction = false;
        this.implementInterface = true;
        this.parentWriter = parent;
        this.isProxyTarget = settings.get(Interceptor.PROXY_TARGET).orElse(false) || parent.isInterface();
        parent.setProxiedBean(true, isProxyTarget);
        this.hotswap = isProxyTarget && settings.get(Interceptor.HOTSWAP).orElse(false);
        this.lazy = isProxyTarget && settings.get(Interceptor.LAZY).orElse(false);
        this.cacheLazyTarget = lazy && settings.get(Interceptor.CACHEABLE_LAZY_TARGET).orElse(false);
        this.isInterface = parent.isInterface();
        this.packageName = parent.getPackageName();
        this.targetClassShortName = parent.getBeanSimpleName();
        this.targetClassFullName = packageName + '.' + targetClassShortName;

        this.proxyFullName = parent.getBeanDefinitionName() + PROXY_SUFFIX;
        this.interceptorBinding = toInterceptorBindingMap(interceptorBinding);
        this.interfaceTypes = Collections.emptySet();
        final ClassElement aopElement = ClassElement.of(proxyFullName, isInterface, parent.getAnnotationMetadata());
        this.proxyBeanDefinitionWriter = new BeanDefinitionWriter(
            aopElement,
            parent,
            visitorContext
        );
        proxyBeanDefinitionWriter.setRequiresMethodProcessing(parent.requiresMethodProcessing());
        proxyBeanDefinitionWriter.setInterceptedType(targetClassFullName);

        proxyBuilder = ClassDef.builder(proxyFullName);

        interceptorsField = FieldDef.builder(FIELD_INTERCEPTORS, Interceptor[][].class)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();

        proxyBuilder.addField(interceptorsField);

        proxyMethodsField = FieldDef.builder(FIELD_PROXY_METHODS, ExecutableMethod[].class)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();

        proxyBuilder.addField(proxyMethodsField);

        if (cacheLazyTarget || hotswap) {
            targetField = FieldDef.builder(FIELD_TARGET, ClassTypeDef.of(targetClassFullName)).addModifiers(Modifier.PRIVATE).build();
            proxyBuilder.addField(targetField);
        } else if (!lazy) {
            targetField = FieldDef.builder(FIELD_TARGET, ClassTypeDef.of(targetClassFullName)).addModifiers(Modifier.PRIVATE, Modifier.FINAL).build();
            proxyBuilder.addField(targetField);
        }

        this.visitorContext = visitorContext;
    }

    /**
     * Constructs a new {@link AopProxyWriter} for the purposes of writing {@link io.micronaut.aop.Introduction} advise.
     *
     * @param packageName        The package name
     * @param className          The class name
     * @param isInterface        Is the target of the advice an interface
     * @param originatingElement The originating element
     * @param annotationMetadata The annotation metadata
     * @param interfaceTypes     The additional interfaces to implement
     * @param visitorContext     The visitor context
     * @param interceptorBinding The interceptor types
     */
    public AopProxyWriter(String packageName,
                          String className,
                          boolean isInterface,
                          Element originatingElement,
                          AnnotationMetadata annotationMetadata,
                          ClassElement[] interfaceTypes,
                          VisitorContext visitorContext,
                          AnnotationValue<?>... interceptorBinding) {
        this(packageName, className, isInterface, true, originatingElement, annotationMetadata, interfaceTypes, visitorContext, interceptorBinding);
    }

    /**
     * Constructs a new {@link AopProxyWriter} for the purposes of writing {@link io.micronaut.aop.Introduction} advise.
     *
     * @param packageName        The package name
     * @param className          The class name
     * @param isInterface        Is the target of the advice an interface
     * @param implementInterface Whether the interface should be implemented. If false the {@code interfaceTypes} argument should contain at least one entry
     * @param originatingElement The originating elements
     * @param annotationMetadata The annotation metadata
     * @param interfaceTypes     The additional interfaces to implement
     * @param visitorContext     The visitor context
     * @param interceptorBinding The interceptor binding
     */
    public AopProxyWriter(String packageName,
                          String className,
                          boolean isInterface,
                          boolean implementInterface,
                          Element originatingElement,
                          AnnotationMetadata annotationMetadata,
                          ClassElement[] interfaceTypes,
                          VisitorContext visitorContext,
                          AnnotationValue<?>... interceptorBinding) {
        this.originatingElements = OriginatingElements.of(originatingElement);
        this.isIntroduction = true;
        this.implementInterface = implementInterface;

        if (!implementInterface && ArrayUtils.isEmpty(interfaceTypes)) {
            throw new IllegalArgumentException("if argument implementInterface is false at least one interface should be provided to the 'interfaceTypes' argument");
        }

        this.packageName = packageName;
        this.isInterface = isInterface;
        this.isProxyTarget = false;
        this.hotswap = false;
        this.lazy = false;
        this.cacheLazyTarget = false;
        this.targetClassShortName = className;
        this.targetClassFullName = packageName + '.' + targetClassShortName;
        this.parentWriter = null;
        this.proxyFullName = targetClassFullName + PROXY_SUFFIX;
        this.interceptorBinding = toInterceptorBindingMap(interceptorBinding);
        this.interfaceTypes = interfaceTypes != null ? new LinkedHashSet<>(Arrays.asList(interfaceTypes)) : Collections.emptySet();
        ClassElement aopElement = ClassElement.of(
            proxyFullName,
            isInterface,
            annotationMetadata
        );
        this.proxyBeanDefinitionWriter = new BeanDefinitionWriter(
            aopElement,
            this,
            visitorContext
        );
        if (isInterface) {
            if (implementInterface) {
                proxyBeanDefinitionWriter.setInterceptedType(targetClassFullName);
            }
        } else {
            proxyBeanDefinitionWriter.setInterceptedType(targetClassFullName);
        }

        proxyBuilder = ClassDef.builder(proxyFullName);

        interceptorsField = FieldDef.builder(FIELD_INTERCEPTORS, Interceptor[][].class)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();

        proxyBuilder.addField(interceptorsField);

        proxyMethodsField = FieldDef.builder(FIELD_PROXY_METHODS, ExecutableMethod[].class)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();

        proxyBuilder.addField(proxyMethodsField);

        this.visitorContext = visitorContext;
    }

    /**
     * Find the interceptors list constructor parameter index.
     *
     * @param parameters The constructor parameters
     * @return the index
     */
    public static int findInterceptorsListParameterIndex(List<ParameterElement> parameters) {
        return parameters.indexOf(parameters.stream().filter(p -> p.getName().equals(INTERCEPTORS_PARAMETER)).findFirst().orElseThrow());
    }

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
        return isInterface;
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
    public Type getProvidedType() {
        return proxyBeanDefinitionWriter.getProvidedType();
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
    public Optional<Type> getInterceptedType() {
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
        ClassElement classElement = ClassElement.of(proxyFullName);
        this.declaredConstructor = MethodElement.of(
            classElement,
            annotationMetadata,
            classElement,
            classElement,
            "<init>"
        );
    }

    private void initConstructor(MethodElement constructor) {
        final ClassElement interceptorList = ClassElement.of(List.class, AnnotationMetadata.EMPTY_METADATA, Collections.singletonMap(
            "E", ClassElement.of(BeanRegistration.class, AnnotationMetadata.EMPTY_METADATA, Collections.singletonMap(
                "T", ClassElement.of(Interceptor.class)
            ))
        ));
        this.qualifierParameter = ParameterElement.of(Qualifier.class, "$qualifier");
        this.interceptorsListParameter = ParameterElement.of(interceptorList, INTERCEPTORS_PARAMETER);
        ParameterElement interceptorRegistryParameter = ParameterElement.of(ClassElement.of(InterceptorRegistry.class), "$interceptorRegistry");
        ClassElement proxyClass = ClassElement.of(proxyFullName);
        superConstructorParametersBinding = new ArrayList<>();
        ParameterElement[] constructorParameters = constructor.getParameters();
        List<ParameterElement> newConstructorParameters = new ArrayList<>(constructorParameters.length + 5);
        newConstructorParameters.addAll(Arrays.asList(constructorParameters));
        int superConstructorParameterIndex = 0;
        for (ParameterElement newConstructorParameter : newConstructorParameters) {
            superConstructorParametersBinding.add(Map.entry(newConstructorParameter, superConstructorParameterIndex++));
        }

        ParameterElement beanResolutionContext = ParameterElement.of(BeanResolutionContext.class, "$beanResolutionContext");
        newConstructorParameters.add(beanResolutionContext);
        ParameterElement beanContext = ParameterElement.of(BeanContext.class, "$beanContext");
        newConstructorParameters.add(beanContext);
        newConstructorParameters.add(qualifierParameter);
        newConstructorParameters.add(interceptorsListParameter);
        newConstructorParameters.add(interceptorRegistryParameter);
        superConstructorParameterIndex += 5; // Skip internal parameters
        if (MethodGenUtils.hasKotlinDefaultsParameters(List.of(constructorParameters))) {
            List<ParameterElement> realNewConstructorParameters = new ArrayList<>(newConstructorParameters);
            int count = MethodGenUtils.calculateNumberOfKotlinDefaultsMasks(List.of(constructorParameters));
            for (int j = 0; j < count; j++) {
                ParameterElement mask = ParameterElement.of(PrimitiveElement.INT, "mask" + j);
                realNewConstructorParameters.add(mask);
                superConstructorParametersBinding.add(Map.entry(mask, superConstructorParameterIndex++));
            }
            ParameterElement marker = ParameterElement.of(ClassElement.of("kotlin.jvm.internal.DefaultConstructorMarker"), "marker");
            realNewConstructorParameters.add(marker);
            superConstructorParametersBinding.add(Map.entry(marker, superConstructorParameterIndex));

            this.realConstructor = MethodElement.of(
                proxyClass,
                constructor.getAnnotationMetadata(),
                proxyClass,
                proxyClass,
                "<init>",
                realNewConstructorParameters.toArray(ZERO_PARAMETER_ELEMENTS)
            );
        }
        this.newConstructor = MethodElement.of(
            proxyClass,
            constructor.getAnnotationMetadata(),
            proxyClass,
            proxyClass,
            "<init>",
            newConstructorParameters.toArray(ZERO_PARAMETER_ELEMENTS)
        );
        if (realConstructor == null) {
            realConstructor = newConstructor;
        }

        this.beanResolutionContextArgumentIndex = newConstructorParameters.indexOf(beanResolutionContext);
        this.beanContextArgumentIndex = newConstructorParameters.indexOf(beanContext);
        this.qualifierIndex = newConstructorParameters.indexOf(qualifierParameter);
        this.interceptorsListArgumentIndex = newConstructorParameters.indexOf(interceptorsListParameter);
        this.interceptorRegistryArgumentIndex = newConstructorParameters.indexOf(interceptorRegistryParameter);
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
        visitAroundMethod(
            declaringBean,
            methodElement
        );
    }

    /**
     * Visit a method that is to be proxied.
     *
     * @param beanType      The bean type.
     * @param methodElement The method element
     **/
    public void visitAroundMethod(TypedElement beanType,
                                  MethodElement methodElement) {

        ClassElement returnType = methodElement.isSuspend() ? ClassElement.of(Object.class) : methodElement.getReturnType();
        Type returnTypeObject = JavaModelUtils.getTypeReference(returnType);
        boolean isPrimitive = returnType.isPrimitive();
        boolean isVoidReturn = isPrimitive && returnTypeObject.equals(Type.VOID_TYPE);

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
                proxyBuilder.addMethod(MethodDef.override(methodElement)
                    .build((aThis, methodParameters) -> aThis.superRef().invoke(overriddenBy, methodParameters).returning())
                );
                return;
            }
        }

        String methodName = methodElement.getName();
        List<ParameterElement> argumentTypeList = Arrays.asList(methodElement.getSuspendParameters());
        int argumentCount = argumentTypeList.size();
        MethodRef methodKey = new MethodRef(methodName, argumentTypeList, returnTypeObject);

        if (!proxiedMethodsRefSet.contains(methodKey)) {

            String interceptedProxyClassName = null;
            String interceptedProxyBridgeMethodName = null;

            if (!isProxyTarget) {
                // if the target is not being proxied then we need to generate a bridge method and executable method that knows about it

                if (!methodElement.isAbstract() || methodElement.isDefault()) {
                    interceptedProxyClassName = proxyFullName;
                    interceptedProxyBridgeMethodName = "$$access$$" + methodName;

                    // now build a bridge to invoke the original method
                    ClassTypeDef declaringTypeDef = (ClassTypeDef) TypeDef.erasure(methodElement.getOwningType());
                    proxyBuilder.addMethod(
                        MethodDef.builder(interceptedProxyBridgeMethodName)
                            .addModifiers(Modifier.PUBLIC)
                            .addParameters(argumentTypeList.stream().map(p -> TypeDef.erasure(p.getType())).toList())
                            .returns(TypeDef.erasure(returnType))
                            .build((aThis, methodParameters) -> aThis.superRef(declaringTypeDef)
                                .invoke(methodElement, methodParameters).returning())
                    );
                }
            }

            BeanDefinitionWriter beanDefinitionWriter = parentWriter == null ? proxyBeanDefinitionWriter : parentWriter;
            int methodIndex = beanDefinitionWriter.visitExecutableMethod(
                beanType,
                methodElement,
                interceptedProxyClassName,
                interceptedProxyBridgeMethodName
            );
            int index = proxyMethodCount++;

            methodKey.methodIndex = methodIndex;
            proxiedMethods.add(methodKey);
            proxiedMethodsRefSet.add(methodKey);
            proxyTargetMethods.add(methodKey);

            buildMethodOverride(returnType, methodName, index, argumentTypeList, argumentCount, isVoidReturn);
        }
    }

    private void buildMethodOverride(
        TypedElement returnType,
        String methodName,
        int index,
        List<ParameterElement> argumentTypeList,
        int argumentCount,
        boolean isVoidReturn) {
        // override the original method
        proxyBuilder.addMethod(
            MethodDef.builder(methodName)
                .addModifiers(Modifier.PUBLIC)
                .overrides()
                .returns(TypeDef.erasure(returnType))
                .addParameters(argumentTypeList.stream().map(p -> TypeDef.erasure(p.getType())).toList())
                .build((aThis, methodParameters) -> {

                    ExpressionDef targetArgument;
                    if (isProxyTarget) {
                        if (hotswap || lazy) {
                            targetArgument = aThis.invoke(METHOD_INTERCEPTED_TARGET);
                        } else {
                            targetArgument = aThis.field(targetField);
                        }
                    } else {
                        targetArgument = aThis;
                    }

                    ExpressionDef.InvokeInstanceMethod invocation;
                    if (argumentCount > 0) {
                        // invoke MethodInterceptorChain constructor with parameters
                        invocation = ClassTypeDef.of(MethodInterceptorChain.class).instantiate(
                            CONSTRUCTOR_METHOD_INTERCEPTOR_CHAIN,

                            // 1st argument: interceptors
                            aThis.field(interceptorsField).arrayElement(index),
                            // 2nd argument: this or target
                            targetArgument,
                            // 3rd argument: the executable method
                            aThis.field(proxyMethodsField).arrayElement(index),
                            // 4th argument: array of the argument values
                            TypeDef.OBJECT.array().instantiate(methodParameters)
                        ).invoke(METHOD_PROCEED);
                    } else {
                        // invoke MethodInterceptorChain constructor without parameters
                        invocation = ClassTypeDef.of(MethodInterceptorChain.class).instantiate(
                            CONSTRUCTOR_METHOD_INTERCEPTOR_CHAIN_NO_PARAMS,

                            // 1st argument: interceptors
                            aThis.field(interceptorsField).arrayElement(index),
                            // 2nd argument: this or target
                            targetArgument,
                            // 3rd argument: the executable method
                            aThis.field(proxyMethodsField).arrayElement(index)
                            // fourth argument: array of the argument values
                        ).invoke(METHOD_PROCEED);
                    }
                    if (isVoidReturn) {
                        return invocation;
                    }
                    return invocation.returning();
                })
        );
    }

    /**
     * Finalizes the proxy. This method should be called before writing the proxy to disk with {@link #writeTo(File)}
     */
    @Override
    public void visitBeanDefinitionEnd() {
        ClassTypeDef targetType = ClassTypeDef.of(targetClassFullName);

        if (!isInterface) {
            proxyBuilder.superclass(targetType);
        }
        List<TypeDef> interfaces = new ArrayList<>();
        interfaceTypes.stream().map(TypeDef::erasure).forEach(interfaces::add);
        if (isInterface && implementInterface) {
            interfaces.add(targetType);
        }
        interfaces.forEach(proxyBuilder::addSuperinterface);

        proxyBuilder.addAnnotation(Generated.class);

        if (declaredConstructor == null) {
            throw new IllegalStateException("The method visitBeanDefinitionConstructor(..) should be called at least once");
        } else {
            initConstructor(declaredConstructor);
        }

        if (parentWriter != null && !isProxyTarget) {
            processAlreadyVisitedMethods(parentWriter);
        }

        interceptorsListParameter.annotate(AnnotationUtil.ANN_INTERCEPTOR_BINDING_QUALIFIER, builder -> {
            final AnnotationValue<?>[] interceptorBinding = this.interceptorBinding.toArray(ZERO_ANNOTATION_VALUES);
            builder.values(interceptorBinding);
        });
        qualifierParameter.annotate(AnnotationUtil.NULLABLE);

        proxyBeanDefinitionWriter.visitBeanDefinitionConstructor(
            newConstructor,
            constructorRequiresReflection,
            visitorContext
        );

        if (parentWriter != null) {
            proxyBeanDefinitionWriter.visitBeanDefinitionInterface(ProxyBeanDefinition.class);
            proxyBeanDefinitionWriter.generateProxyReference(parentWriter.getBeanDefinitionName(), parentWriter.getBeanTypeName());
        }

        if (isProxyTarget) {
            generateProxyTarget(targetType);
        } else {
            proxyBuilder.addSuperinterface(TypeDef.of(isIntroduction ? Introduced.class : Intercepted.class));
            proxyBuilder.addMethod(MethodDef.constructor()
                .addParameters(Arrays.stream(realConstructor.getParameters()).map(p -> TypeDef.erasure(p.getType())).toList())
                .build((aThis, methodParameters) -> StatementDef.multi(
                    invokeSuperConstructor(aThis, methodParameters),
                    initializeProxyMethodsAndInterceptors(aThis, methodParameters)
                )));
        }

        for (Runnable fieldInjectionPoint : deferredInjectionPoints) {
            fieldInjectionPoint.run();
        }

        proxyBeanDefinitionWriter.visitBeanDefinitionEnd();
    }

    private void generateProxyTarget(ClassTypeDef targetType) {
        List<MethodDef.MethodBodyBuilder> bodyBuilders = new ArrayList<>();

        FieldDef proxyBeanDefinitionField = FieldDef.builder(FIELD_PROXY_BEAN_DEFINITION, BeanDefinition.class)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();
        proxyBuilder.addField(proxyBeanDefinitionField);
        bodyBuilders.add((aThis, methodParameters) -> aThis.field(proxyBeanDefinitionField).assign(
            methodParameters.get(beanContextArgumentIndex).invoke(
                METHOD_GET_PROXY_BEAN_DEFINITION,

                // 1nd argument: the type
                pushTargetArgument(targetType),
                // 2rd argument: the qualifier
                methodParameters.get(qualifierIndex)
            )
        ));

        FieldDef beanQualifierField = FieldDef.builder(FIELD_BEAN_QUALIFIER, TypeDef.of(Qualifier.class))
            .addModifiers(Modifier.PRIVATE)
            .build();
        proxyBuilder.addField(beanQualifierField);
        proxyBuilder.addMethod(writeWithQualifierMethod(beanQualifierField));
        bodyBuilders.add((aThis, methodParameters) ->
            aThis.field(beanQualifierField).assign(methodParameters.get(qualifierIndex)));

        MethodDef interceptedTargetMethod;
        if (lazy) {
            proxyBuilder.addSuperinterface(TypeDef.of(InterceptedProxy.class));

            FieldDef beanResolutionContextField = FieldDef.builder(FIELD_BEAN_RESOLUTION_CONTEXT, BeanResolutionContext.class)
                .addModifiers(Modifier.PRIVATE)
                .build();

            proxyBuilder.addField(beanResolutionContextField);

            FieldDef beanLocatorField = FieldDef.builder(FIELD_BEAN_LOCATOR, BeanLocator.class)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build();

            proxyBuilder.addField(beanLocatorField);

            if (cacheLazyTarget) {
                interceptedTargetMethod = getCacheLazyTargetInterceptedTargetMethod(
                    targetField,
                    beanLocatorField,
                    beanResolutionContextField,
                    proxyBeanDefinitionField,
                    beanQualifierField
                );
                proxyBuilder.addMethod(
                    getHasCachedInterceptedTargetMethod(targetField)
                );
            } else {
                interceptedTargetMethod = getLazyInterceptedTargetMethod(
                    beanLocatorField,
                    beanResolutionContextField,
                    proxyBeanDefinitionField,
                    beanQualifierField
                );
            }

            bodyBuilders.add((aThis, methodParameters) -> StatementDef.multi(
                aThis.field(beanLocatorField).assign(methodParameters.get(beanContextArgumentIndex)),
                aThis.field(beanResolutionContextField).assign(
                    methodParameters.get(beanResolutionContextArgumentIndex)
                        .invoke(COPY_BEAN_CONTEXT_METHOD)
                )
            ));
        } else {
            if (hotswap) {
                proxyBuilder.addSuperinterface(TypeDef.parameterized(HotSwappableInterceptedProxy.class, targetType));

                ClassTypeDef readWriteLockType = ClassTypeDef.of(ReentrantReadWriteLock.class);
                FieldDef readWriteLockField = FieldDef.builder(FIELD_READ_WRITE_LOCK, readWriteLockType)
                    .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                    .initializer(readWriteLockType.instantiate())
                    .build();

                proxyBuilder.addField(readWriteLockField);

                ClassTypeDef lockType = ClassTypeDef.of(Lock.class);
                FieldDef readLockField = FieldDef.builder(FIELD_READ_LOCK, lockType)
                    .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                    .initializer(new VariableDef.This().field(readWriteLockField).invoke(GET_READ_LOCK_METHOD))
                    .build();

                proxyBuilder.addField(readLockField);

                FieldDef writeLockField = FieldDef.builder(FIELD_WRITE_LOCK, lockType)
                    .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                    .initializer(new VariableDef.This().field(readWriteLockField).invoke(GET_WRITE_LOCK_METHOD))
                    .build();

                proxyBuilder.addField(writeLockField);

                proxyBuilder.addMethod(
                    getSwapMethod(targetField, writeLockField)
                );
                interceptedTargetMethod = getHotSwapInterceptedTargetMethod(targetField, readLockField);
            } else {
                proxyBuilder.addSuperinterface(TypeDef.parameterized(InterceptedProxy.class, targetType));
                interceptedTargetMethod = getSimpleInterceptedTargetMethod(targetField);
            }

            // Non-lazy target
            bodyBuilders.add((aThis, methodParameters) -> aThis.field(targetField).assign(
                    methodParameters.get(beanContextArgumentIndex)
                        .cast(DefaultBeanContext.class).invoke(
                            METHOD_GET_PROXY_TARGET_BEAN_WITH_BEAN_DEFINITION_AND_CONTEXT,

                            // 1st argument: the bean resolution context
                            methodParameters.get(beanResolutionContextArgumentIndex),
                            // 2nd argument: this.$proxyBeanDefinition
                            aThis.field(proxyBeanDefinitionField),
                            // 3rd argument: the type
                            pushTargetArgument(targetType),
                            // 4th argument: the qualifier
                            methodParameters.get(qualifierIndex)
                        ).cast(targetType)
                )
            );
        }

        proxyBuilder.addMethod(interceptedTargetMethod);

        proxyBuilder.addMethod(MethodDef.constructor()
            .addParameters(Arrays.stream(realConstructor.getParameters()).map(p -> TypeDef.erasure(p.getType())).toList())
            .build((aThis, methodParameters) -> {
                List<StatementDef> constructorStatements = new ArrayList<>();
                constructorStatements.add(
                    invokeSuperConstructor(aThis, methodParameters)
                );
                bodyBuilders.forEach(bodyBuilder -> constructorStatements.add(bodyBuilder.apply(aThis, methodParameters)));
                constructorStatements.add(
                    initializeProxyTargetMethodsAndInterceptors(aThis, methodParameters, proxyBeanDefinitionField)
                );
                return StatementDef.multi(constructorStatements);
            }));
    }

    private StatementDef initializeProxyMethodsAndInterceptors(VariableDef.This aThis,
                                                               List<VariableDef.MethodParameter> parameters) {
        if (proxiedMethods.isEmpty()) {
            return StatementDef.multi();
        }
        BeanDefinitionWriter beanDefinitionWriter = parentWriter == null ? proxyBeanDefinitionWriter : parentWriter;
        ExecutableMethodsDefinitionWriter executableMethodsDefinitionWriter = beanDefinitionWriter.getExecutableMethodsWriter();
        ClassTypeDef executableMethodsType = executableMethodsDefinitionWriter.getClassTypeDef();
        ExpressionDef.NewInstance executableMethodsInstance;
        if (executableMethodsDefinitionWriter.isSupportsInterceptedProxy()) {
            executableMethodsInstance = executableMethodsType.instantiate(TypeDef.Primitive.BOOLEAN.constant(true));
        } else {
            executableMethodsInstance = executableMethodsType.instantiate();
        }
        int[] index = {0};
        return executableMethodsInstance.newLocal("executableMethods", executableMethodsVar -> StatementDef.multi(
            aThis.field(proxyMethodsField).assign(
                ClassTypeDef.of(ExecutableMethod.class).array().instantiate(
                    proxyTargetMethods.stream().map(methodRef ->
                        executableMethodsVar.invoke(
                            ExecutableMethodsDefinitionWriter.GET_EXECUTABLE_AT_INDEX_METHOD,

                            TypeDef.Primitive.INT.constant(methodRef.methodIndex)
                        )).toList()
                )
            ),
            aThis.field(interceptorsField).assign(
                ClassTypeDef.of(Interceptor.class).array(2).instantiate(
                    proxyTargetMethods.stream().map(methodRef -> {
                            int methodIndex = methodRef.methodIndex;
                            boolean introduction = isIntroduction && (
                                executableMethodsDefinitionWriter.isAbstract(methodIndex) || (
                                    executableMethodsDefinitionWriter.isInterface(methodIndex) && !executableMethodsDefinitionWriter.isDefault(methodIndex)));

                            return ClassTypeDef.of(InterceptorChain.class).invokeStatic(
                                (introduction ? RESOLVE_INTRODUCTION_INTERCEPTORS_METHOD : RESOLVE_AROUND_INTERCEPTORS_METHOD),

                                // First argument. The interceptor registry
                                parameters.get(interceptorRegistryArgumentIndex),
                                // Second argument i.e. proxyMethods[0]
                                aThis.field(proxyMethodsField).arrayElement(index[0]++),
                                // Third argument i.e. interceptors
                                parameters.get(interceptorsListArgumentIndex)
                            );
                        }
                    ).toList()
                )
            )
        ));
    }

    private StatementDef initializeProxyTargetMethodsAndInterceptors(VariableDef.This aThis,
                                                                     List<VariableDef.MethodParameter> parameters,
                                                                     FieldDef proxyBeanDefinitionField) {
        if (proxiedMethods.size() != proxyMethodCount) {
            throw new IllegalStateException("Expected proxy methods count to match actual methods");
        }
        int[] index = {0};
        return StatementDef.multi(
            aThis.field(proxyMethodsField).assign(
                ClassTypeDef.of(ExecutableMethod.class).array().instantiate(
                    proxyTargetMethods.stream().map(methodRef ->
                        aThis.field(proxyBeanDefinitionField).invoke(
                            METHOD_BEAN_DEFINITION_GET_REQUIRED_METHOD,

                            ExpressionDef.constant(methodRef.name),
                            TypeDef.CLASS.array().instantiate(
                                methodRef.genericArgumentTypes.stream().map(t -> ExpressionDef.constant(TypeDef.erasure(t))).toList()
                            )
                        )
                    ).toList()
                )
            ),
            aThis.field(interceptorsField).assign(
                ClassTypeDef.of(Interceptor.class).array(2).instantiate(
                    proxyTargetMethods.stream().map(methodRef ->
                        ClassTypeDef.of(InterceptorChain.class).invokeStatic(
                            (isIntroduction ? RESOLVE_INTRODUCTION_INTERCEPTORS_METHOD : RESOLVE_AROUND_INTERCEPTORS_METHOD),

                            // First argument. The interceptor registry
                            parameters.get(interceptorRegistryArgumentIndex),
                            // Second argument i.e. proxyMethods[0]
                            aThis.field(proxyMethodsField).arrayElement(index[0]++),
                            // Third argument i.e. interceptors
                            parameters.get(interceptorsListArgumentIndex)
                        )
                    ).toList()
                )
            )
        );
    }

    private ExpressionDef.InvokeInstanceMethod invokeSuperConstructor(VariableDef.This aThis, List<VariableDef.MethodParameter> methodParameters) {
        if (isInterface) {
            return aThis.superRef().invokeConstructor();
        }
        List<ExpressionDef> values = new ArrayList<>();
        List<TypeDef> arguments = new ArrayList<>();
        for (Map.Entry<ParameterElement, Integer> e : superConstructorParametersBinding) {
            values.add(methodParameters.get(e.getValue()));
            arguments.add(TypeDef.erasure(e.getKey().getType()));
        }
        return aThis.superRef().invokeConstructor(arguments, values);
    }

    private ExpressionDef.InvokeInstanceMethod pushResolveLazyProxyTargetBean(VariableDef.This aThis,
                                                                              List<VariableDef.MethodParameter> parameters,
                                                                              FieldDef beanLocatorField,
                                                                              FieldDef beanResolutionContextField,
                                                                              FieldDef proxyBeanDefinitionField,
                                                                              FieldDef beanQualifierField) {
        return aThis.field(beanLocatorField).cast(DefaultBeanContext.class).invoke(
            METHOD_GET_PROXY_TARGET_BEAN_WITH_BEAN_DEFINITION_AND_CONTEXT,

            // 1st argument: the bean resolution context
            aThis.field(beanResolutionContextField),
            // 2nd argument: this.$proxyBeanDefinition
            aThis.field(proxyBeanDefinitionField),
            // 3rd argument: the type
            pushTargetArgument(ClassTypeDef.of(targetClassFullName)),
            // 4th argument: the qualifier
            aThis.field(beanQualifierField)
        );
    }

    private ExpressionDef pushTargetArgument(TypeDef targetType) {
        return ArgumentExpUtils.buildArgumentWithGenerics(
            targetType,
            new AnnotationMetadataReference(
                getBeanDefinitionName(),
                getAnnotationMetadata()
            ),
            parentWriter != null ? parentWriter.getTypeArguments() : proxyBeanDefinitionWriter.getTypeArguments()
        );
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
        try (OutputStream out = visitor.visitClass(proxyFullName, getOriginatingElements())) {
            out.write(new ByteCodeWriter().write(proxyBuilder.build()));
        }
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
    public void visitConfigBuilderField(ClassElement type, String field, AnnotationMetadata annotationMetadata, ConfigurationMetadataBuilder metadataBuilder, boolean isInterface) {
        proxyBeanDefinitionWriter.visitConfigBuilderField(type, field, annotationMetadata, metadataBuilder, isInterface);
    }

    @Override
    public void visitConfigBuilderMethod(ClassElement type, String methodName, AnnotationMetadata annotationMetadata, ConfigurationMetadataBuilder metadataBuilder, boolean isInterface) {
        proxyBeanDefinitionWriter.visitConfigBuilderMethod(type, methodName, annotationMetadata, metadataBuilder, isInterface);
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

    @Override
    public String getProxiedTypeName() {
        return targetClassFullName;
    }

    @Override
    public String getProxiedBeanDefinitionName() {
        return parentWriter != null ? parentWriter.getBeanDefinitionName() : null;
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

    private MethodDef writeWithQualifierMethod(FieldDef beanQualifier) {
        return MethodDef.override(WITH_QUALIFIER_METHOD)
            .build((aThis, methodParameters) -> aThis.field(beanQualifier).put(methodParameters.get(0)));
    }

    private MethodDef getSwapMethod(FieldDef targetField, FieldDef writeField) {
        Objects.requireNonNull(targetField);
        Objects.requireNonNull(writeField);
        return MethodDef.override(SWAP_METHOD)
            .build((aThis, methodParameters) -> {
                VariableDef.Field lock = aThis.field(writeField);
                return StatementDef.multi(
                    lock.invoke(LOCK_METHOD),
                    StatementDef.doTry(
                        aThis.field(targetField).newLocal("target", targetVar -> StatementDef.multi(
                            aThis.field(targetField).assign(methodParameters.get(0)),
                            targetVar.returning()
                        ))
                    ).doFinally(lock.invoke(UNLOCK_METHOD))
                );
            });
    }

    private MethodDef getLazyInterceptedTargetMethod(FieldDef beanLocatorField,
                                                     FieldDef beanResolutionContextField,
                                                     FieldDef proxyBeanDefinitionField,
                                                     FieldDef beanQualifierField) {

        return MethodDef.override(METHOD_INTERCEPTED_TARGET)
            .build((aThis, methodParameters) -> pushResolveLazyProxyTargetBean(
                aThis,
                methodParameters,
                beanLocatorField,
                beanResolutionContextField,
                proxyBeanDefinitionField,
                beanQualifierField
            ).returning());
    }

    private MethodDef getCacheLazyTargetInterceptedTargetMethod(FieldDef targetField,
                                                                FieldDef beanLocatorField,
                                                                FieldDef beanResolutionContextField,
                                                                FieldDef proxyBeanDefinitionField,
                                                                FieldDef beanQualifierField) {

        return MethodDef.override(METHOD_INTERCEPTED_TARGET)
            .build((aThis, methodParameters) -> {
//                            B var1 = this.$target;
//                            if (var1 == null) {
//                                synchronized(this) {
//                                    var1 = this.$target;
//                                    if (var1 == null) {
//                                        this.$target = (B)((DefaultBeanContext)this.$beanLocator).getProxyTargetBean(this.$beanResolutionContext, this.$proxyBeanDefinition, Argument.of(B.class, $B$Definition$Intercepted$Definition.$ANNOTATION_METADATA, new Class[0]), this.$beanQualifier);
//                                        this.$beanResolutionContext = null;
//                                    }
//                                }
//                            }
//                            return this.$target;
                VariableDef.Field targetFieldAccess = aThis.field(targetField);
                return StatementDef.multi(
                    targetFieldAccess.newLocal("target", targetVar ->
                        targetVar.ifNull(
                            new StatementDef.Synchronized(
                                aThis,
                                StatementDef.multi(
                                    targetVar.assign(targetFieldAccess),
                                    targetVar.ifNull(
                                        StatementDef.multi(
                                            targetFieldAccess.assign(
                                                pushResolveLazyProxyTargetBean(
                                                    aThis,
                                                    methodParameters,
                                                    beanLocatorField,
                                                    beanResolutionContextField,
                                                    proxyBeanDefinitionField,
                                                    beanQualifierField)
                                            ),
                                            aThis.field(beanResolutionContextField).assign(ExpressionDef.nullValue())
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    targetFieldAccess.returning()
                );
            });
    }

    private MethodDef getHotSwapInterceptedTargetMethod(FieldDef targetField,
                                                        FieldDef readLockField) {

        return MethodDef.override(METHOD_INTERCEPTED_TARGET)
            .build((aThis, methodParameters) -> {
                //       this.$target_rl.lock();
                //
                //        HotswappableProxyingClass var1;
                //        try {
                //            var1 = this.$target;
                //        } finally {
                //            this.$target_rl.unlock();
                //        }
                //
                //        return var1;
                return StatementDef.multi(
                    aThis.field(readLockField).invoke(LOCK_METHOD),
                    aThis.field(targetField).returning()
                        .doTry()
                        .doFinally(aThis.field(readLockField).invoke(UNLOCK_METHOD))
                );
            });
    }

    private MethodDef getSimpleInterceptedTargetMethod(FieldDef targetField) {
        Objects.requireNonNull(targetField);
        return MethodDef.override(METHOD_INTERCEPTED_TARGET)
            .build((aThis, methodParameters) -> aThis.field(targetField).returning());
    }

    private MethodDef getHasCachedInterceptedTargetMethod(FieldDef targetField) {
        Objects.requireNonNull(targetField);
        return MethodDef.builder(METHOD_HAS_CACHED_INTERCEPTED_METHOD.getName())
            .addModifiers(Modifier.PUBLIC)
            .addParameters(METHOD_HAS_CACHED_INTERCEPTED_METHOD.getParameterTypes())
            .build((aThis, methodParameters) -> aThis.field(targetField).isNonNull().returning());
    }

    private void processAlreadyVisitedMethods(BeanDefinitionWriter parent) {
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

    /**
     * @param p The class element
     * @return The string representation
     */
    private static String toTypeString(ClassElement p) {
        String name = p.getName();
        if (p.isArray()) {
            return name + IntStream.range(0, p.getArrayDimensions()).mapToObj(ignore -> "[]").collect(Collectors.joining());
        }
        return name;
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
     * Method Reference class with names and a list of argument types. Used as the targets.
     */
    private static final class MethodRef {
        int methodIndex;
        private final String name;
        private final List<ClassElement> argumentTypes;
        private final List<ClassElement> genericArgumentTypes;
        private final Type returnType;
        private final List<String> rawTypes;

        public MethodRef(String name, List<ParameterElement> parameterElements, Type returnType) {
            this.name = name;
            this.argumentTypes = parameterElements.stream().map(ParameterElement::getType).toList();
            this.genericArgumentTypes = parameterElements.stream().map(ParameterElement::getGenericType).toList();
            this.rawTypes = this.argumentTypes.stream().map(AopProxyWriter::toTypeString).toList();
            this.returnType = returnType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MethodRef methodRef = (MethodRef) o;
            return Objects.equals(name, methodRef.name) &&
                Objects.equals(rawTypes, methodRef.rawTypes) &&
                Objects.equals(returnType, methodRef.returnType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, rawTypes, returnType);
        }
    }
}
