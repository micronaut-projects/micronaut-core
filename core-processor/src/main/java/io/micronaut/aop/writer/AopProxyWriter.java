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
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.aop.Introduced;
import io.micronaut.aop.chain.InterceptorChain;
import io.micronaut.aop.chain.MethodInterceptorChain;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanDefinitionRegistry;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.ProxyBeanDefinition;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.qualifiers.Qualified;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.ArgumentExpUtils;
import io.micronaut.inject.writer.BeanDefinitionWriter;
import io.micronaut.inject.writer.ByteCodeWriterUtils;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.ExecutableMethodsDefinitionWriter;
import io.micronaut.inject.writer.MethodGenUtils;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.ParameterDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static io.micronaut.core.annotation.AnnotationUtil.ZERO_ANNOTATION_VALUES;
import static io.micronaut.inject.ast.ParameterElement.ZERO_PARAMETER_ELEMENTS;

/**
 * A class that generates AOP proxy classes at compile time.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class AopProxyWriter extends ProxyingBeanDefinitionWriter {

    public static final int ADDITIONAL_PARAMETERS_COUNT = 5;

    private static final Method METHOD_GET_PROXY_TARGET_BEAN_WITH_BEAN_DEFINITION_AND_CONTEXT = ReflectionUtils.getRequiredInternalMethod(
        BeanResolutionContext.class,
        "getProxyTargetBean",
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
    private static final ClassTypeDef METHOD_INTERCEPTOR_CHAIN_TYPE = ClassTypeDef.of(MethodInterceptorChain.class);

    private final Set<ClassElement> interfaceTypes;
    private final boolean hotswap;
    private final boolean lazy;
    private final boolean cacheLazyTarget;

    private final List<MethodRef> proxiedMethods = new ArrayList<>();
    private final Set<MethodRef> proxiedMethodsRefSet = new HashSet<>();
    private final List<MethodRef> proxyTargetMethods = new ArrayList<>();
    private int proxyMethodCount = 0;
    private int interceptorsListArgumentIndex;
    private int beanResolutionContextArgumentIndex = -1;
    private int beanContextArgumentIndex = -1;
    private int interceptorRegistryArgumentIndex = -1;
    private int qualifierIndex;
    private MethodElement newConstructor;
    private MethodElement realConstructor;
    private List<Map.Entry<ParameterElement, Integer>> superConstructorParametersBinding;
    private ParameterElement qualifierParameter;
    private ParameterElement interceptorsListParameter;

    private ClassDef.ClassDefBuilder proxyBuilder;
    private final FieldDef interceptorsField;
    private final FieldDef proxyMethodsField;
    private FieldDef targetField;

    private byte[] output;

    /**
     * <p>Constructs a new {@link AopProxyWriter} for the given parent {@link BeanDefinitionWriter} and starting interceptors types.</p>
     *
     * <p>Additional {@link Interceptor} types can be added downstream with {@link #visitInterceptorBinding(AnnotationValue[])} .</p>
     *
     * @param targetType       The classElement
     * @param parent             The parent {@link BeanDefinitionWriter}
     * @param settings           optional setting
     * @param visitorContext     The visitor context
     * @param interceptorBinding The interceptor binding of the {@link Interceptor} instances to be injected
     */
    public AopProxyWriter(ClassElement targetType,
                          BeanDefinitionWriter parent,
                          OptionalValues<Boolean> settings,
                          VisitorContext visitorContext,
                          AnnotationValue<?>... interceptorBinding) {
        super(
            null,
            ClassElement.of(parent.getBeanDefinitionName() + PROXY_SUFFIX, parent.isInterface(), parent.getAnnotationMetadata()),
            targetType,
            parent,
            settings,
            visitorContext,
            interceptorBinding
        );
        this.hotswap = isProxyTarget && settings.get(Interceptor.HOTSWAP).orElse(false);
        this.lazy = isProxyTarget && settings.get(Interceptor.LAZY).orElse(false);
        this.cacheLazyTarget = lazy && settings.get(Interceptor.CACHEABLE_LAZY_TARGET).orElse(false);
        this.interfaceTypes = Collections.emptySet();

        proxyBuilder = ClassDef.builder(proxyType.getName()).synthetic();

        interceptorsField = FieldDef.builder(FIELD_INTERCEPTORS, Interceptor[][].class)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();

        proxyBuilder.addField(interceptorsField);

        proxyMethodsField = FieldDef.builder(FIELD_PROXY_METHODS, ExecutableMethod[].class)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();

        proxyBuilder.addField(proxyMethodsField);

        if (cacheLazyTarget || hotswap) {
            targetField = FieldDef.builder(FIELD_TARGET, TypeDef.OBJECT).addModifiers(Modifier.PRIVATE).build();
            proxyBuilder.addField(targetField);
        } else if (!lazy) {
            targetField = FieldDef.builder(FIELD_TARGET, TypeDef.OBJECT).addModifiers(Modifier.PRIVATE, Modifier.FINAL).build();
            proxyBuilder.addField(targetField);
        }
    }

    /**
     * Constructs a new {@link AopProxyWriter} for the purposes of writing {@link io.micronaut.aop.Introduction} advise.
     *
     * @param targetType       The source element
     * @param interfaceTypes     The additional interfaces to implement
     * @param visitorContext     The visitor context
     * @param interceptorBinding The interceptor types
     */
    public AopProxyWriter(ClassElement targetType,
                          ClassElement[] interfaceTypes,
                          VisitorContext visitorContext,
                          AnnotationValue<?>... interceptorBinding) {
        this(targetType, true, interfaceTypes, visitorContext, interceptorBinding);
    }

    /**
     * Constructs a new {@link AopProxyWriter} for the purposes of writing {@link io.micronaut.aop.Introduction} advise.
     *
     * @param targetType       The source element
     * @param implementInterface Whether the interface should be implemented. If false the {@code interfaceTypes} argument should contain at least one entry
     * @param interfaceTypes     The additional interfaces to implement
     * @param visitorContext     The visitor context
     * @param interceptorBinding The interceptor binding
     */
    public AopProxyWriter(ClassElement targetType,
                          boolean implementInterface,
                          ClassElement[] interfaceTypes,
                          VisitorContext visitorContext,
                          AnnotationValue<?>... interceptorBinding) {
        super(
            null,
            ClassElement.of(targetType.getName() + PROXY_SUFFIX, targetType.isInterface(), targetType.getAnnotationMetadata()),
            targetType,
            implementInterface,
            interfaceTypes,
            visitorContext,
            interceptorBinding
        );
        this.hotswap = false;
        this.lazy = false;
        this.cacheLazyTarget = false;
        this.interfaceTypes = interfaceTypes != null ? new LinkedHashSet<>(Arrays.asList(interfaceTypes)) : Collections.emptySet();

        proxyBuilder = ClassDef.builder(proxyType.getName()).synthetic();

        interceptorsField = FieldDef.builder(FIELD_INTERCEPTORS, Interceptor[][].class)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();

        proxyBuilder.addField(interceptorsField);

        proxyMethodsField = FieldDef.builder(FIELD_PROXY_METHODS, ExecutableMethod[].class)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();

        proxyBuilder.addField(proxyMethodsField);
    }

    @Override
    protected BeanDefinitionWriter createAdviceProxyBeanDefinitionWriter(String suffix) {
        return new BeanDefinitionWriter(
            proxyType,
            parentWriter,
            visitorContext
        );
    }

    @Override
    protected BeanDefinitionWriter createIntroductionProxyBeanDefinitionWriter(String suffix) {
        return new BeanDefinitionWriter(
            proxyType,
            this,
            visitorContext
        );
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

    private void initConstructor(MethodElement constructor) {
        final ClassElement interceptorList = ClassElement.of(List.class, AnnotationMetadata.EMPTY_METADATA, Collections.singletonMap(
            "E", ClassElement.of(BeanRegistration.class, AnnotationMetadata.EMPTY_METADATA, Collections.singletonMap(
                "T", ClassElement.of(Interceptor.class)
            ))
        ));
        this.qualifierParameter = ParameterElement.of(Qualifier.class, "$qualifier");
        this.interceptorsListParameter = ParameterElement.of(interceptorList, INTERCEPTORS_PARAMETER);
        ParameterElement interceptorRegistryParameter = ParameterElement.of(ClassElement.of(InterceptorRegistry.class), "$interceptorRegistry");
        ClassElement proxyClass = ClassElement.of(proxyType.getName());
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

    /**
     * Visit a method that is to be proxied.
     *
     * @param beanType      The bean type.
     * @param methodElement The method element
     **/
    @Override
    public void visitAroundMethod(TypedElement beanType,
                                  MethodElement methodElement) {

        MethodElement overriddenBy = findOverriddenBy(methodElement);
        if (overriddenBy != null) {
            proxyBuilder.addMethod(MethodDef.override(methodElement)
                .build((aThis, methodParameters) -> aThis.invoke(overriddenBy, methodParameters).returning())
            );
            return;
        }

        String methodName = methodElement.getName();
        List<ParameterElement> argumentTypeList = Arrays.asList(methodElement.getSuspendParameters());
        ClassElement returnType = methodElement.isSuspend() ? ClassElement.of(Object.class) : methodElement.getReturnType();
        MethodRef methodKey = new MethodRef(methodName, argumentTypeList, returnType);

        if (!proxiedMethodsRefSet.contains(methodKey)) {

            ClassTypeDef interceptedProxyDef = null;
            MethodDef interceptedProxyBridgeMethod = null;

            if (!isProxyTarget) {
                // if the target is not being proxied then we need to generate a bridge method and executable method that knows about it

                if (!methodElement.isAbstract() || methodElement.isDefault()) {
                    interceptedProxyDef = ClassTypeDef.of(proxyType.getName());
                    interceptedProxyBridgeMethod = MethodDef.builder("$$access$$" + methodName)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameters(argumentTypeList.stream().map(p -> ParameterDef.of(p.getName(), TypeDef.erasure(p.getType()))).toList())
                        .returns(TypeDef.erasure(returnType))
                        .build((aThis, methodParameters) -> aThis.superRef((ClassTypeDef) TypeDef.erasure(methodElement.getOwningType()))
                            .invoke(methodElement, methodParameters)
                            .returning()
                        );

                    // now build a bridge to invoke the original method
                    proxyBuilder.addMethod(
                        interceptedProxyBridgeMethod
                    );
                }
            }

            BeanDefinitionWriter beanDefinitionWriter = parentWriter == null ? proxyBeanDefinitionWriter : parentWriter;
            int methodIndex = beanDefinitionWriter.visitExecutableMethod(
                beanType,
                methodElement,
                interceptedProxyDef,
                interceptedProxyBridgeMethod
            );
            int index = proxyMethodCount++;

            methodKey.methodIndex = methodIndex;
            proxiedMethods.add(methodKey);
            proxiedMethodsRefSet.add(methodKey);
            proxyTargetMethods.add(methodKey);

            proxyBuilder.addMethod(
                buildMethodOverride(methodElement, index)
            );
        }
    }

    private MethodDef buildMethodOverride(MethodElement methodElement, int index) {
        return MethodDef.override(methodElement)
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
                if (methodParameters.isEmpty()) {
                    // invoke MethodInterceptorChain constructor without parameters
                    invocation = METHOD_INTERCEPTOR_CHAIN_TYPE.instantiate(
                        CONSTRUCTOR_METHOD_INTERCEPTOR_CHAIN_NO_PARAMS,

                        // 1st argument: interceptors
                        aThis.field(interceptorsField).arrayElement(index),
                        // 2nd argument: this or target
                        targetArgument,
                        // 3rd argument: the executable method
                        aThis.field(proxyMethodsField).arrayElement(index)
                        // fourth argument: array of the argument values
                    ).invoke(METHOD_PROCEED);
                } else {
                    // invoke MethodInterceptorChain constructor with parameters
                    invocation = METHOD_INTERCEPTOR_CHAIN_TYPE.instantiate(
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
                }
                if (!methodElement.getReturnType().isVoid() || methodElement.isSuspend()) {
                    return invocation.returning();
                }
                return invocation;
            });
    }

    /**
     * Finalizes the proxy. This method should be called before writing the proxy to disk with {@link #writeTo(File)}
     */
    @Override
    public void visitBeanDefinitionEnd() {
        ClassTypeDef classTargetType = ClassTypeDef.of(this.targetType.getName());
        if (!targetType.isInterface()) {
            proxyBuilder.superclass(classTargetType);
        }
        List<ClassTypeDef> interfaces = new ArrayList<>();
        interfaceTypes.stream().map(typedElement -> (ClassTypeDef) TypeDef.erasure(typedElement)).forEach(interfaces::add);
        if (targetType.isInterface() && implementInterface) {
            interfaces.add(classTargetType);
        }
        interfaces.sort(Comparator.comparing(ClassTypeDef::getName));
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
            generateProxyTarget(classTargetType);
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

        output = ByteCodeWriterUtils.writeByteCode(proxyBuilder.build(), visitorContext);
        proxyBuilder = null;
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
                    beanResolutionContextField,
                    proxyBeanDefinitionField,
                    beanQualifierField
                );
                proxyBuilder.addMethod(
                    getHasCachedInterceptedTargetMethod(targetField)
                );
            } else {
                interceptedTargetMethod = getLazyInterceptedTargetMethod(
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
                    methodParameters.get(beanResolutionContextArgumentIndex)
                        .invoke(
                            METHOD_GET_PROXY_TARGET_BEAN_WITH_BEAN_DEFINITION_AND_CONTEXT,
                            // 1st argument: this.$proxyBeanDefinition
                            aThis.field(proxyBeanDefinitionField),
                            // 2nd argument: the type
                            pushTargetArgument(targetType),
                            // 3th argument: the qualifier
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
        AtomicInteger index = new AtomicInteger();
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
                                aThis.field(proxyMethodsField).arrayElement(index.getAndIncrement()),
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
        AtomicInteger index = new AtomicInteger();
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
                            aThis.field(proxyMethodsField).arrayElement(index.getAndIncrement()),
                            // Third argument i.e. interceptors
                            parameters.get(interceptorsListArgumentIndex)
                        )
                    ).toList()
                )
            )
        );
    }

    private ExpressionDef.InvokeInstanceMethod invokeSuperConstructor(VariableDef.This aThis, List<VariableDef.MethodParameter> methodParameters) {
        if (targetType.isInterface()) {
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
                                                                              FieldDef beanResolutionContextField,
                                                                              FieldDef proxyBeanDefinitionField,
                                                                              FieldDef beanQualifierField) {
        return aThis.field(beanResolutionContextField).invoke(
            METHOD_GET_PROXY_TARGET_BEAN_WITH_BEAN_DEFINITION_AND_CONTEXT,

            // 1st argument: this.$proxyBeanDefinition
            aThis.field(proxyBeanDefinitionField),
            // 2nd argument: the type
            pushTargetArgument(ClassTypeDef.of(targetType.getName())),
            // 3rd argument: the qualifier
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

    /**
     * Write the class to output via a visitor that manages output destination.
     *
     * @param visitor the writer output visitor
     * @throws IOException If an error occurs
     */
    @Override
    public void accept(ClassWriterOutputVisitor visitor) throws IOException {
        super.accept(visitor);
        try (OutputStream out = visitor.visitClass(proxyType.getName(), getOriginatingElements())) {
            out.write(output);
        }
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

    private MethodDef getLazyInterceptedTargetMethod(FieldDef beanResolutionContextField,
                                                     FieldDef proxyBeanDefinitionField,
                                                     FieldDef beanQualifierField) {

        return MethodDef.override(METHOD_INTERCEPTED_TARGET)
            .build((aThis, methodParameters) -> pushResolveLazyProxyTargetBean(
                aThis,
                beanResolutionContextField,
                proxyBeanDefinitionField,
                beanQualifierField
            ).returning());
    }

    private MethodDef getCacheLazyTargetInterceptedTargetMethod(FieldDef targetField,
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

    /**
     * Method Reference class with names and a list of argument types. Used as the targets.
     */
    private static final class MethodRef {
        int methodIndex;
        private final String name;
        private final List<ClassElement> argumentTypes;
        private final List<ClassElement> genericArgumentTypes;
        private final String returnType;
        private final List<String> rawTypes;

        public MethodRef(String name, List<ParameterElement> parameterElements, ClassElement returnType) {
            this.name = name;
            this.argumentTypes = parameterElements.stream().map(ParameterElement::getType).toList();
            this.genericArgumentTypes = parameterElements.stream().map(ParameterElement::getGenericType).toList();
            this.rawTypes = this.argumentTypes.stream().map(AopProxyWriter::toTypeString).toList();
            this.returnType = toTypeString(returnType);
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
