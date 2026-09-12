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
import io.micronaut.aop.beandefinition.TargetInterceptorRegistrations;
import io.micronaut.aop.chain.InterceptorChain;
import io.micronaut.aop.chain.MethodInterceptorChain;
import io.micronaut.aop.internal.intercepted.InterceptedMethodUtil;
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
import io.micronaut.inject.processing.definition.OutputObjectDef;
import io.micronaut.inject.ProxyBeanDefinition;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.qualifiers.Qualified;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.ArgumentExpUtils;
import io.micronaut.inject.writer.BeanDefinitionWriter;
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
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.Modifier;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
import static io.micronaut.inject.writer.BeanDefinitionVisitor.PROXY_SUFFIX;

/**
 * A class that generates AOP proxy classes at compile time.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@NullUnmarked
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

    private static final Method METHOD_GET_PROXY_TARGET_BEAN_REGISTRATION = ReflectionUtils.getRequiredInternalMethod(
        BeanResolutionContext.class,
        "getProxyTargetBeanRegistration",
        BeanDefinition.class,
        Argument.class,
        Qualifier.class
    );

    private static final Method METHOD_REGISTRATION_GET_BEAN = ReflectionUtils.getRequiredMethod(
        BeanRegistration.class,
        "getBean"
    );

    private static final Method METHOD_SELECT_TARGET_INTERCEPTORS = ReflectionUtils.getRequiredInternalMethod(
        TargetInterceptorRegistrations.class,
        "select",
        InterceptorRegistry.class,
        BeanRegistration.class,
        ExecutableMethod.class,
        int.class,
        Interceptor[].class
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

    private static final Method METHOD_OBJECT_TO_STRING = ReflectionUtils.getRequiredMethod(
        Object.class,
        "toString"
    );

    private static final Method METHOD_HAS_CACHED_INTERCEPTED_METHOD = ReflectionUtils.getRequiredInternalMethod(
        InterceptedProxy.class,
        "hasCachedInterceptedTarget"
    );

    private static final Method METHOD_CLEAR_CACHED_INTERCEPTED_METHOD = ReflectionUtils.getRequiredInternalMethod(
        InterceptedProxy.class,
        "clearCachedInterceptedTarget"
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
    private static final String FIELD_TARGET_REGISTRATION = "$targetRegistration";
    private static final String FIELD_INTERCEPTOR_REGISTRY = "$interceptorRegistry";
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
    private static final String BEAN_RESOLUTION_CONTEXT_PARAMETER = "$beanResolutionContext";
    private static final String BEAN_CONTEXT_PARAMETER = "$beanContext";
    private static final String QUALIFIER_PARAMETER = "$qualifier";
    private static final String INTERCEPTOR_REGISTRY_PARAMETER = "$interceptorRegistry";

    private static final Method METHOD_PROCEED = ReflectionUtils.getRequiredInternalMethod(InterceptorChain.class, "proceed");

    private static final Method COPY_BEAN_CONTEXT_FOR_LAZY_PROXY_TARGET_METHOD = ReflectionUtils.getRequiredMethod(
        BeanResolutionContext.class,
        "copyForLazyProxyTarget",
        BeanDefinition.class
    );

    private static final String FIELD_INTERCEPTORS = "$interceptors";
    private static final String FIELD_INTERCEPTOR_REGISTRATIONS = "$interceptorRegistrations";

    // The proxy accessor is named after the field it returns.
    private static final Method GET_INTERCEPTOR_REGISTRATIONS_METHOD =
        ReflectionUtils.getRequiredInternalMethod(Intercepted.class, FIELD_INTERCEPTOR_REGISTRATIONS);
    private static final String FIELD_BEAN_LOCATOR = "$beanLocator";
    private static final String FIELD_BEAN_QUALIFIER = "$beanQualifier";
    private static final String FIELD_PROXY_METHODS = "$proxyMethods";
    private static final String FIELD_PROXY_BEAN_DEFINITION = "$proxyBeanDefinition";
    private static final ClassTypeDef METHOD_INTERCEPTOR_CHAIN_TYPE = ClassTypeDef.of(MethodInterceptorChain.class);

    private final Set<ClassElement> defaultMethodInterfaceTypes = new LinkedHashSet<>();
    private final boolean hotswap;
    private final boolean lazy;
    private final boolean cacheLazyTarget;
    private final MethodElement targetConstructor;

    private final Map<MethodElement, MethodElement> overriddenMethods = new LinkedHashMap<>();
    private final List<MethodElement> aroundMethods = new ArrayList<>();

    /**
     * <p>Constructs a new {@link AopProxyWriter} for the given parent {@link BeanDefinitionWriter} and starting interceptors types.</p>
     *
     * <p>Additional {@link Interceptor} types can be added downstream with {@link #visitInterceptorBinding(AnnotationValue[])} .</p>
     *
     * @param targetType         The classElement
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
            createProxyConstructor(targetType, createProxyType(parent), settings, visitorContext),
//            null,
            createProxyType(parent),
            targetType,
            parent,
            settings,
            visitorContext,
            interceptorBinding
        );
        this.hotswap = isProxyTarget && settings.get(Interceptor.HOTSWAP).orElse(false);
        this.lazy = isProxyTarget && settings.get(Interceptor.LAZY).orElse(false);
        this.cacheLazyTarget = lazy && settings.get(Interceptor.CACHEABLE_LAZY_TARGET).orElse(false);
        this.targetConstructor = selectProxyConstructor(targetType, settings);
    }

    /**
     * Constructs a new {@link AopProxyWriter} for the purposes of writing {@link io.micronaut.aop.Introduction} advise.
     *
     * @param targetType         The source element
     * @param implementInterface Whether the interface should be implemented. If false the {@code interfaceTypes} argument should contain at least one entry
     * @param visitorContext     The visitor context
     * @param interceptorBinding The interceptor binding
     */
    public AopProxyWriter(ClassElement targetType,
                          boolean implementInterface,
                          VisitorContext visitorContext,
                          AnnotationValue<?>... interceptorBinding) {
        super(
            createProxyConstructor(targetType, createProxyType(targetType), visitorContext),
//            null,
            createProxyType(targetType),
            targetType,
            implementInterface,
            visitorContext,
            interceptorBinding
        );
        this.hotswap = false;
        this.lazy = false;
        this.cacheLazyTarget = false;
        this.targetConstructor = getConstructor(targetType);
    }

    private static ClassElement createProxyType(BeanDefinitionWriter parent) {
        ClassElement target = parent.getBeanTypeElement();
        return createProxyType(target, parent.getBeanDefinitionName() + PROXY_SUFFIX, parent.getAnnotationMetadata());
    }

    private static ClassElement createProxyType(ClassElement target) {
        String proxyName = target.getName() + PROXY_SUFFIX;
        return createProxyType(target, proxyName, target.getAnnotationMetadata());
    }

    private static ClassElement createProxyType(ClassElement target, String proxyName, AnnotationMetadata annotationMetadata) {
        if (target.isInterface()) {
            return ClassElement.of(proxyName, false, annotationMetadata, Map.of(), null, List.of(target));
        }
        return ClassElement.of(proxyName, false, annotationMetadata, Map.of(), target, List.of());
    }

    private static MethodElement createProxyConstructor(ClassElement target, ClassElement proxyClass, VisitorContext visitorContext) {
        return createProxyConstructor(target, proxyClass, OptionalValues.empty(), visitorContext);
    }

    private static MethodElement createProxyConstructor(ClassElement target, ClassElement proxyClass, OptionalValues<Boolean> settings, VisitorContext visitorContext) {
        MethodElement constructor = selectProxyConstructor(target, settings);

        final ClassElement interceptorList = ClassElement.of(List.class, AnnotationMetadata.EMPTY_METADATA, Collections.singletonMap(
            "E", ClassElement.of(BeanRegistration.class, AnnotationMetadata.EMPTY_METADATA, Collections.singletonMap(
                "T", ClassElement.of(Interceptor.class)
            ))
        ));

        ParameterElement interceptorsListParameter = ParameterElement.of(interceptorList, INTERCEPTORS_PARAMETER);

        ParameterElement[] constructorParameters = constructor.getParameters();
        List<ParameterElement> newConstructorParameters = new ArrayList<>(constructorParameters.length + 5);
        newConstructorParameters.addAll(List.of(constructorParameters));

        ParameterElement qualifierParameter = ParameterElement.of(Qualifier.class, QUALIFIER_PARAMETER);
        qualifierParameter.annotate(AnnotationUtil.NULLABLE);

        newConstructorParameters.add(ParameterElement.of(BeanResolutionContext.class, BEAN_RESOLUTION_CONTEXT_PARAMETER));
        newConstructorParameters.add(ParameterElement.of(BeanContext.class, BEAN_CONTEXT_PARAMETER));
        newConstructorParameters.add(qualifierParameter);
        newConstructorParameters.add(interceptorsListParameter);
        newConstructorParameters.add(ParameterElement.of(ClassElement.of(InterceptorRegistry.class), INTERCEPTOR_REGISTRY_PARAMETER));

        return MethodElement.of(
            proxyClass,
            constructor.getAnnotationMetadata(),
            proxyClass,
            proxyClass,
            "<init>",
            newConstructorParameters.toArray(ParameterElement.ZERO_PARAMETER_ELEMENTS)
        );
    }

    private static MethodElement selectProxyConstructor(ClassElement target, OptionalValues<Boolean> settings) {
        MethodElement constructor = getConstructor(target);
        if (constructor.hasParameters()
            && settings.get(Interceptor.PROXY_TARGET).orElse(false)
            && settings.get(Interceptor.LAZY).orElse(false)) {
            return target.getDefaultConstructor()
                .filter(defaultConstructor -> !defaultConstructor.isStatic())
                .orElse(constructor);
        }
        return constructor;
    }

    /**
     * Visit a method that is to be proxied.
     *
     * @param methodElement The method element
     **/
    @Override
    public AopProxyWriter addAroundMethod(MethodElement methodElement) {
        AnnotationMetadata methodAnnotationMetadata = methodElement.getMethodAnnotationMetadata();

        if (InterceptedMethodUtil.hasAroundStereotype(methodAnnotationMetadata)) {
            visitInterceptorBinding(
                InterceptedMethodUtil.resolveInterceptorBinding(methodAnnotationMetadata, InterceptorKind.AROUND)
            );
        }
        ExecutableMethodsDefinitionWriter methodsWriter = executableMethodsDefinitionWriter != null
            ? executableMethodsDefinitionWriter
            : proxyBeanDefinitionWriter.getExecutableMethodsWriter();

        MethodElement overriddenBy = findOverriddenBy(methodElement);
        if (overriddenBy != null) {
            overriddenMethods.put(methodElement, overriddenBy);
        } else {
            methodsWriter.addExecutableMethod(methodElement.getDeclaringType(), methodElement);
        }
        aroundMethods.add(methodElement);

        return this;
    }

    private void addInterceptedIfNeeded(ClassDef.ClassDefBuilder proxyBuilder,
                                        MethodElement methodElement,
                                        Set<MethodRef> uniqueInterceptedMethodsRefs,
                                        List<MethodElement> methods) {
        String methodName = methodElement.getName();
        List<ParameterElement> argumentTypeList = Arrays.asList(methodElement.getSuspendParameters());
        ClassElement returnType = methodElement.isSuspend() ? ClassElement.of(Object.class) : methodElement.getReturnType();
        MethodRef methodKey = new MethodRef(methodName, argumentTypeList, returnType);

        if (!uniqueInterceptedMethodsRefs.contains(methodKey)) {
            if (!isProxyTarget) {
                // if the target is not being proxied then we need to generate a bridge method and executable method that knows about it

                if (!methodElement.isAbstract() || methodElement.isDefault()) {
                    ClassElement owningType = methodElement.getOwningType();
                    if (methodElement.isDefault() && owningType.isInterface()) {
                        defaultMethodInterfaceTypes.add(owningType);
                    }
                    MethodDef interceptedProxyBridgeMethod = MethodDef.builder("$$access$$" + methodName)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameters(argumentTypeList.stream().map(p -> ParameterDef.of(p.getName(), TypeDef.erasure(p.getType()))).toList())
                        .returns(TypeDef.erasure(returnType))
                        .build((aThis, methodParameters) -> aThis.superRef((ClassTypeDef) TypeDef.erasure(owningType))
                            .invoke(methodElement, methodParameters)
                            .returning()
                        );

                    // now build a bridge to invoke the original method
                    proxyBuilder.addMethod(
                        interceptedProxyBridgeMethod
                    );

                    MethodElement proxyMethod = MethodElement.of(
                        proxyType,
                        AnnotationMetadata.EMPTY_METADATA,
                        returnType,
                        returnType,
                        interceptedProxyBridgeMethod.getName(),
                        methodElement.getSuspendParameters());

                    ExecutableMethodsDefinitionWriter methodsWriter = executableMethodsDefinitionWriter != null
                        ? executableMethodsDefinitionWriter
                        : proxyBeanDefinitionWriter.getExecutableMethodsWriter();

                    methodsWriter.setProxyType(ClassTypeDef.of(proxyType));
                    methodsWriter.addBridgeMethod(methodElement, proxyMethod);
                }
            }

            uniqueInterceptedMethodsRefs.add(methodKey);

            methods.add(methodElement);
        }
    }

    private MethodDef buildMethodIntercept(MethodElement methodElement,
                                           int index,
                                           @Nullable FieldDef targetField,
                                           FieldDef interceptorsField,
                                           FieldDef proxyMethodsField,
                                           @Nullable ProxyTargetFields proxyTargetFields) {
        return MethodDef.override(methodElement)
            .build((aThis, methodParameters) -> {
                ExpressionDef proxyInterceptors = aThis.field(interceptorsField).arrayElement(index);
                ExpressionDef method = aThis.field(proxyMethodsField).arrayElement(index);
                if (isProxyTarget && lazy) {
                    // The target is resolved on each call and need not be the one of the last call, so the
                    // interceptors are selected for the target of this call rather than taken from the set the
                    // proxy holds; see TargetInterceptorRegistrations#select for which they are.
                    ExpressionDef registry = aThis.field(proxyTargetFields.interceptorRegistry());
                    if (cacheLazyTarget) {
                        // interceptedTarget() resolves the target and its registration once and keeps both
                        return aThis.invoke(METHOD_INTERCEPTED_TARGET).newLocal("target", target -> proceed(
                            methodElement,
                            methodParameters,
                            selectTargetInterceptors(registry, aThis.field(proxyTargetFields.targetRegistration()), method, index, proxyInterceptors),
                            target,
                            method
                        ));
                    }
                    return resolveProxyTargetRegistration(aThis, proxyTargetFields).newLocal("targetRegistration", targetRegistration -> proceed(
                        methodElement,
                        methodParameters,
                        selectTargetInterceptors(registry, targetRegistration, method, index, proxyInterceptors),
                        targetRegistration.invoke(METHOD_REGISTRATION_GET_BEAN),
                        method
                    ));
                }
                ExpressionDef targetArgument;
                if (isProxyTarget) {
                    if (hotswap) {
                        targetArgument = aThis.invoke(METHOD_INTERCEPTED_TARGET);
                    } else {
                        targetArgument = aThis.field(targetField);
                    }
                } else {
                    targetArgument = aThis;
                }
                return proceed(methodElement, methodParameters, proxyInterceptors, targetArgument, method);
            });
    }

    /**
     * Runs the interceptor chain of one method and returns what it returns, unless the method returns nothing.
     *
     * @param methodElement    The intercepted method
     * @param methodParameters The parameters of the proxy method
     * @param interceptors     The interceptors to apply
     * @param target           The target of the call: the proxy itself, or the target it fronts
     * @param method           The executable method
     * @return The statement
     */
    private static StatementDef proceed(MethodElement methodElement,
                                        List<VariableDef.MethodParameter> methodParameters,
                                        ExpressionDef interceptors,
                                        ExpressionDef target,
                                        ExpressionDef method) {
        ExpressionDef.InvokeInstanceMethod invocation;
        if (methodParameters.isEmpty()) {
            // invoke MethodInterceptorChain constructor without parameters
            invocation = METHOD_INTERCEPTOR_CHAIN_TYPE.instantiate(
                CONSTRUCTOR_METHOD_INTERCEPTOR_CHAIN_NO_PARAMS,

                // 1st argument: interceptors
                interceptors,
                // 2nd argument: this or target
                target,
                // 3rd argument: the executable method
                method
                // fourth argument: array of the argument values
            ).invoke(METHOD_PROCEED);
        } else {
            // invoke MethodInterceptorChain constructor with parameters
            invocation = METHOD_INTERCEPTOR_CHAIN_TYPE.instantiate(
                CONSTRUCTOR_METHOD_INTERCEPTOR_CHAIN,

                // 1st argument: interceptors
                interceptors,
                // 2nd argument: this or target
                target,
                // 3rd argument: the executable method
                method,
                // 4th argument: array of the argument values
                TypeDef.OBJECT.array().instantiate(methodParameters)
            ).invoke(METHOD_PROCEED);
        }
        if (!methodElement.getReturnType().isVoid() || methodElement.isSuspend()) {
            return invocation.returning();
        }
        return invocation;
    }

    /**
     * Selects the interceptors of one method for the target of a call, see
     * {@link TargetInterceptorRegistrations#select}.
     *
     * @param registry          The interceptor registry
     * @param targetRegistration The registration of the target
     * @param method            The executable method
     * @param index             The index of the method in the proxy
     * @param proxyInterceptors The interceptors the proxy selected for the method from its own registrations
     * @return The expression
     */
    private static ExpressionDef selectTargetInterceptors(ExpressionDef registry,
                                                          ExpressionDef targetRegistration,
                                                          ExpressionDef method,
                                                          int index,
                                                          ExpressionDef proxyInterceptors) {
        return ClassTypeDef.of(TargetInterceptorRegistrations.class).invokeStatic(
            METHOD_SELECT_TARGET_INTERCEPTORS,
            registry,
            targetRegistration,
            method,
            ExpressionDef.constant(index),
            proxyInterceptors
        );
    }

    @Override
    public List<OutputObjectDef> build() {

        if (parentWriter != null && !isProxyTarget) {
            processAlreadyVisitedMethods(parentWriter);
        }

        ClassDef.ClassDefBuilder proxyBuilder = ClassDef.builder(proxyType.getName()).synthetic();

        FieldDef interceptorsField = FieldDef.builder(FIELD_INTERCEPTORS, Interceptor[][].class)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();

        proxyBuilder.addField(interceptorsField);

        // Every proxy retains the registrations its constructor was given, so a proxy can always report the
        // interceptors bound to it. For an around-only proxy that is exactly the around/introduction set it already
        // receives; only a proxy with intercepted lifecycle widens the constructor qualifier below.
        FieldDef interceptorRegistrationsField = FieldDef.builder(FIELD_INTERCEPTOR_REGISTRATIONS, List.class)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();
        proxyBuilder.addField(interceptorRegistrationsField);
        proxyBuilder.addMethod(MethodDef.override(GET_INTERCEPTOR_REGISTRATIONS_METHOD)
            .build((aThis, methodParameters) -> aThis.field(interceptorRegistrationsField).returning()));

        if (proxyBeanDefinitionWriter.hasInterceptedLifecycle()) {
            // The constructor argument is qualified by the accumulated around/introduction bindings only. Widen it
            // with the lifecycle bindings so the retained list is a superset of what lifecycle interception needs,
            // otherwise a lifecycle interceptor bound by a different annotation would be dropped.
            // This is the compile-time half of the same rule InterceptedBeanDefinition#resolveInterceptors applies at
            // runtime for beans that get no proxy: whatever a bean binds for construction, post-construct and
            // pre-destroy is resolved as one set. Keep the two in step.
            // Only widen here: doing it for every proxy would change which interceptors are injected into every
            // proxied bean in every application.
            AnnotationMetadata targetAnnotationMetadata = targetType.getAnnotationMetadata();
            visitInterceptorBinding(InterceptedMethodUtil.resolveInterceptorBinding(targetAnnotationMetadata, InterceptorKind.POST_CONSTRUCT));
            visitInterceptorBinding(InterceptedMethodUtil.resolveInterceptorBinding(targetAnnotationMetadata, InterceptorKind.PRE_DESTROY));
        }

        FieldDef proxyMethodsField = FieldDef.builder(FIELD_PROXY_METHODS, ExecutableMethod[].class)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();

        proxyBuilder.addField(proxyMethodsField);

        FieldDef targetField;
        if (cacheLazyTarget || hotswap) {
            targetField = FieldDef.builder(FIELD_TARGET, TypeDef.OBJECT).addModifiers(Modifier.PRIVATE).build();
            proxyBuilder.addField(targetField);
        } else if (!lazy) {
            targetField = FieldDef.builder(FIELD_TARGET, TypeDef.OBJECT).addModifiers(Modifier.PRIVATE, Modifier.FINAL).build();
            proxyBuilder.addField(targetField);
        } else {
            targetField = null;
        }

        ClassTypeDef classTargetType = ClassTypeDef.of(this.targetType.getName());
        if (!targetType.isInterface()) {
            proxyBuilder.superclass(classTargetType);
        }

        proxyBuilder.addAnnotation(Generated.class);

        List<MethodElement> interceptedMethods = new ArrayList<>();
        final Set<MethodRef> uniqueInterceptedMethodsRefs = new LinkedHashSet<>();

        for (MethodElement aroundMethod : aroundMethods) {
            MethodElement overriddenByMethod = overriddenMethods.get(aroundMethod);
            if (overriddenByMethod != null) {
                proxyBuilder.addMethod(MethodDef.override(aroundMethod)
                    .build((aThis, methodParameters) -> aThis.invoke(overriddenByMethod, methodParameters).returning())
                );
            } else {
                addInterceptedIfNeeded(proxyBuilder, aroundMethod, uniqueInterceptedMethodsRefs, interceptedMethods);
            }
        }

        List<ClassTypeDef> interfaces = new ArrayList<>();
        Set<String> interfaceNames = new HashSet<>();
        interfaceTypes.stream().map(typedElement -> (ClassTypeDef) TypeDef.erasure(typedElement)).forEach(interfaceType -> addInterface(interfaces, interfaceNames, interfaceType));
        defaultMethodInterfaceTypes.stream().map(typedElement -> (ClassTypeDef) TypeDef.erasure(typedElement)).forEach(interfaceType -> addInterface(interfaces, interfaceNames, interfaceType));
        if (targetType.isInterface() && implementInterface) {
            addInterface(interfaces, interfaceNames, classTargetType);
        }
        interfaces.sort(Comparator.comparing(ClassTypeDef::getName));
        interfaces.forEach(proxyBuilder::addSuperinterface);

        ProxyTargetFields proxyTargetFields = isProxyTarget ? createProxyTargetFields() : null;

        int index = 0;
        for (MethodElement method : interceptedMethods) {
            proxyBuilder.addMethod(
                buildMethodIntercept(method, index++, targetField, interceptorsField, proxyMethodsField, proxyTargetFields)
            );
        }
        if (!interceptedMethods.isEmpty()) {
            proxyBuilder.addMethod(MethodDef.builder("interceptedMethods")
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassTypeDef.of(ExecutableMethod.class).array())
                .build((aThis, methodParameters) -> aThis.field(proxyMethodsField)
                    .invoke("clone", TypeDef.OBJECT)
                    .cast(ClassTypeDef.of(ExecutableMethod.class).array())
                    .returning()));
        }

        constructor.getParameter(INTERCEPTORS_PARAMETER).annotate(AnnotationUtil.ANN_INTERCEPTOR_BINDING_QUALIFIER, builder -> {
            builder.values(interceptorBinding.toArray(ZERO_ANNOTATION_VALUES));
        });

        if (parentWriter != null) {
            proxyBeanDefinitionWriter.visitBeanDefinitionInterface(ProxyBeanDefinition.class);
            proxyBeanDefinitionWriter.generateProxyReference(parentWriter.getBeanDefinitionName(), parentWriter.getBeanTypeName());
        }

        proxyBuilder.addSuperinterface(TypeDef.of(isIntroduction ? Introduced.class : Intercepted.class));

        addConstructor(proxyBuilder, classTargetType, targetField, interceptorsField, interceptorRegistrationsField, proxyMethodsField, interceptedMethods, proxyTargetFields);

        List<OutputObjectDef> classes = new ArrayList<>();
        classes.add(new OutputObjectDef(proxyBuilder.build(), null, originatingElements));
        if (executableMethodsDefinitionWriter != null) {
            classes.add(executableMethodsDefinitionWriter.build());
        }
        classes.addAll(proxyBeanDefinitionWriter.build());

        return classes;
    }

    private static void addInterface(List<ClassTypeDef> interfaces,
                                     Set<String> interfaceNames,
                                     ClassTypeDef interfaceType) {
        if (interfaceNames.add(interfaceType.getName())) {
            interfaces.add(interfaceType);
        }
    }

    private void addConstructor(ClassDef.ClassDefBuilder proxyBuilder,
                                ClassTypeDef targetType,
                                @Nullable FieldDef targetField,
                                FieldDef interceptorsField,
                                FieldDef interceptorRegistrationsField,
                                FieldDef proxyMethodsField,
                                List<MethodElement> interceptedMethods,
                                @Nullable ProxyTargetFields proxyTargetFields) {

        List<MethodDef.MethodBodyBuilder> bodyBuilders = new ArrayList<>();
        bodyBuilders.add((aThis, methodParameters) -> aThis.field(interceptorRegistrationsField).assign(
            methodParameters.get(constructor.findParameterIndex(INTERCEPTORS_PARAMETER))
        ));

        if (isProxyTarget) {

            int beanResolutionContextArgumentIndex = constructor.findParameterIndex(BEAN_RESOLUTION_CONTEXT_PARAMETER);
            int beanContextArgumentIndex = constructor.findParameterIndex(BEAN_CONTEXT_PARAMETER);
            int qualifierIndex = constructor.findParameterIndex(QUALIFIER_PARAMETER);


            FieldDef proxyBeanDefinitionField = proxyTargetFields.proxyBeanDefinition();
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

            FieldDef beanQualifierField = proxyTargetFields.beanQualifier();
            proxyBuilder.addField(beanQualifierField);
            proxyBuilder.addMethod(writeWithQualifierMethod(beanQualifierField));
            bodyBuilders.add((aThis, methodParameters) ->
                aThis.field(beanQualifierField).assign(methodParameters.get(qualifierIndex)));

            MethodDef interceptedTargetMethod;
            if (lazy) {
                proxyBuilder.addSuperinterface(TypeDef.of(InterceptedProxy.class));

                FieldDef beanResolutionContextField = proxyTargetFields.beanResolutionContext();
                proxyBuilder.addField(beanResolutionContextField);

                // Selects the interceptors of each call for the target of that call, see buildMethodIntercept
                FieldDef interceptorRegistryField = proxyTargetFields.interceptorRegistry();
                proxyBuilder.addField(interceptorRegistryField);

                FieldDef beanLocatorField = FieldDef.builder(FIELD_BEAN_LOCATOR, BeanLocator.class)
                    .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                    .build();

                proxyBuilder.addField(beanLocatorField);

                if (cacheLazyTarget) {
                    FieldDef targetRegistrationField = proxyTargetFields.targetRegistration();
                    proxyBuilder.addField(targetRegistrationField);
                    interceptedTargetMethod = getCacheLazyTargetInterceptedTargetMethod(
                        targetField,
                        targetRegistrationField,
                        beanResolutionContextField,
                        proxyBeanDefinitionField,
                        beanQualifierField
                    );
                    proxyBuilder.addMethod(
                        getHasCachedInterceptedTargetMethod(targetField)
                    );
                    proxyBuilder.addMethod(getClearCachedInterceptedTargetMethod(targetField, targetRegistrationField));
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
                            .invoke(COPY_BEAN_CONTEXT_FOR_LAZY_PROXY_TARGET_METHOD, aThis.field(proxyBeanDefinitionField))
                    ),
                    aThis.field(interceptorRegistryField).assign(
                        methodParameters.get(constructor.findParameterIndex(INTERCEPTOR_REGISTRY_PARAMETER))
                    )
                ));
                if (shouldGenerateLazyProxyTargetToStringMethod(interceptedMethods)) {
                    proxyBuilder.addMethod(getLazyProxyTargetToStringMethod());
                }
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

                // The target of a non-lazy proxy is resolved by the constructor and held by the proxy, so it is
                // always cached. Saying so is what lets the context destroy the target when the proxy is destroyed.
                proxyBuilder.addMethod(getHasCachedInterceptedTargetMethod(targetField));

                // Non-lazy target: resolved once, here, along with the interceptors of its methods, which are the
                // target's own when the target is not a singleton
                bodyBuilders.add((aThis, methodParameters) -> resolveProxyTargetRegistration(
                    methodParameters.get(beanResolutionContextArgumentIndex),
                    aThis.field(proxyBeanDefinitionField),
                    methodParameters.get(qualifierIndex)
                ).newLocal("targetRegistration", targetRegistration -> StatementDef.multi(
                    aThis.field(targetField).assign(targetRegistration.invoke(METHOD_REGISTRATION_GET_BEAN).cast(targetType)),
                    initializeProxyTargetMethodsAndInterceptors(aThis, methodParameters, proxyBeanDefinitionField, interceptorsField, proxyMethodsField, interceptedMethods, targetRegistration)
                )));
            }

            proxyBuilder.addMethod(interceptedTargetMethod);

            if (lazy) {
                // The set the proxy selects for itself: what a singleton target is intercepted by, and what a
                // target that carries no registrations of its own falls back to
                bodyBuilders.add((aThis, methodParameters) -> initializeProxyTargetMethodsAndInterceptors(aThis, methodParameters, proxyBeanDefinitionField, interceptorsField, proxyMethodsField, interceptedMethods, null));
            }
        } else {
            bodyBuilders.add((aThis, methodParameters) -> initializeProxyMethodsAndInterceptors(aThis, methodParameters, interceptorsField, proxyMethodsField, interceptedMethods));
        }

        proxyBuilder.addMethod(MethodDef.constructor()
            .addParameters(Arrays.stream(constructor.getParameters()).map(p -> TypeDef.erasure(p.getType())).toList())
            .build((aThis, methodParameters) -> {
                List<StatementDef> constructorStatements = new ArrayList<>();
                constructorStatements.add(
                    invokeSuperConstructor(aThis, methodParameters)
                );
                bodyBuilders.forEach(bodyBuilder -> constructorStatements.add(bodyBuilder.apply(aThis, methodParameters)));
                return StatementDef.multi(constructorStatements);
            }));
    }

    private StatementDef initializeProxyMethodsAndInterceptors(VariableDef.This aThis,
                                                               List<VariableDef.MethodParameter> parameters,
                                                               FieldDef interceptorsField,
                                                               FieldDef proxyMethodsField,
                                                               List<MethodElement> methods) {
        if (methods.isEmpty()) {
            return StatementDef.multi();
        }
        ExecutableMethodsDefinitionWriter methodsWriter;
        if (executableMethodsDefinitionWriter == null) {
            methodsWriter = proxyBeanDefinitionWriter.getExecutableMethodsWriter();
        } else {
            methodsWriter = executableMethodsDefinitionWriter;
        }

        ClassTypeDef executableMethodsType = methodsWriter.getClassTypeDef();
        ExpressionDef.NewInstance executableMethodsInstance;
        if (methodsWriter.isSupportsInterceptedProxy()) {
            executableMethodsInstance = executableMethodsType.instantiate(TypeDef.Primitive.BOOLEAN.constant(true));
        } else {
            executableMethodsInstance = executableMethodsType.instantiate();
        }
        AtomicInteger index = new AtomicInteger();
        return executableMethodsInstance.newLocal("executableMethods", executableMethodsVar -> StatementDef.multi(
            aThis.field(proxyMethodsField).assign(
                ClassTypeDef.of(ExecutableMethod.class).array().instantiate(
                    methods.stream().map(methodElement ->
                        executableMethodsVar.invoke(
                            ExecutableMethodsDefinitionWriter.GET_EXECUTABLE_AT_INDEX_METHOD,

                            TypeDef.Primitive.INT.constant(methodsWriter.findIndexOfExecutableMethod(methodElement))
                        )).toList()
                )
            ),
            aThis.field(interceptorsField).assign(
                ClassTypeDef.of(Interceptor.class).array(2).instantiate(
                    methods.stream().map(methodElement -> {
                            boolean introduction = isIntroduction && (methodElement.isAbstract() || (methodElement.getDeclaringType().isInterface() && !methodElement.isDefault()));

                            return ClassTypeDef.of(InterceptorChain.class).invokeStatic(
                                (introduction ? RESOLVE_INTRODUCTION_INTERCEPTORS_METHOD : RESOLVE_AROUND_INTERCEPTORS_METHOD),

                                // First argument. The interceptor registry
                                parameters.get(constructor.findParameterIndex(INTERCEPTOR_REGISTRY_PARAMETER)),
                                // Second argument i.e. proxyMethods[0]
                                aThis.field(proxyMethodsField).arrayElement(index.getAndIncrement()),
                                // Third argument i.e. interceptors
                                parameters.get(constructor.findParameterIndex(INTERCEPTORS_PARAMETER))
                            );
                        }
                    ).toList()
                )
            )
        ));
    }

    /**
     * Assigns the executable methods of a proxy that fronts a separate target and the interceptors of each.
     *
     * @param aThis                  The proxy
     * @param parameters             The constructor parameters
     * @param proxyBeanDefinitionField The field holding the target definition
     * @param interceptorsField      The interceptors field
     * @param proxyMethodsField      The methods field
     * @param methods                The intercepted methods
     * @param targetRegistration     The registration of the target the proxy holds for its life, whose own
     *                               interceptors are selected when it is not a singleton, or {@code null} for a
     *                               proxy that resolves its target on each call and selects them then
     * @return The statement
     */
    private StatementDef initializeProxyTargetMethodsAndInterceptors(VariableDef.This aThis,
                                                                     List<VariableDef.MethodParameter> parameters,
                                                                     FieldDef proxyBeanDefinitionField,
                                                                     FieldDef interceptorsField,
                                                                     FieldDef proxyMethodsField,
                                                                     List<MethodElement> methods,
                                                                     @Nullable ExpressionDef targetRegistration) {
        AtomicInteger index = new AtomicInteger();
        ExpressionDef registry = parameters.get(constructor.findParameterIndex(INTERCEPTOR_REGISTRY_PARAMETER));
        ExpressionDef registrations = parameters.get(constructor.findParameterIndex(INTERCEPTORS_PARAMETER));
        return StatementDef.multi(
            aThis.field(proxyMethodsField).assign(
                ClassTypeDef.of(ExecutableMethod.class).array().instantiate(
                    methods.stream().map(methodElement ->
                        aThis.field(proxyBeanDefinitionField).invoke(
                            METHOD_BEAN_DEFINITION_GET_REQUIRED_METHOD,

                            ExpressionDef.constant(methodElement.getName()),
                            TypeDef.CLASS.array().instantiate(
                                Arrays.stream(methodElement.getSuspendParameters()).map(p -> ExpressionDef.constant(TypeDef.erasure(p.getGenericType()))).toList()
                            )
                        )
                    ).toList()
                )
            ),
            aThis.field(interceptorsField).assign(
                ClassTypeDef.of(Interceptor.class).array(2).instantiate(
                    methods.stream().<ExpressionDef>map(methodElement -> {
                        int methodIndex = index.getAndIncrement();
                        ExpressionDef method = aThis.field(proxyMethodsField).arrayElement(methodIndex);
                        ExpressionDef proxyInterceptors = ClassTypeDef.of(InterceptorChain.class).invokeStatic(
                            (isIntroduction ? RESOLVE_INTRODUCTION_INTERCEPTORS_METHOD : RESOLVE_AROUND_INTERCEPTORS_METHOD),

                            // First argument. The interceptor registry
                            registry,
                            // Second argument i.e. proxyMethods[0]
                            method,
                            // Third argument i.e. interceptors
                            registrations
                        );
                        if (targetRegistration == null) {
                            return proxyInterceptors;
                        }
                        return selectTargetInterceptors(registry, targetRegistration, method, methodIndex, proxyInterceptors);
                    }).toList()
                )
            )
        );
    }

    private ProxyTargetFields createProxyTargetFields() {
        FieldDef proxyBeanDefinition = FieldDef.builder(FIELD_PROXY_BEAN_DEFINITION, BeanDefinition.class)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();
        FieldDef beanQualifier = FieldDef.builder(FIELD_BEAN_QUALIFIER, TypeDef.of(Qualifier.class))
            .addModifiers(Modifier.PRIVATE)
            .build();
        if (!lazy) {
            return new ProxyTargetFields(proxyBeanDefinition, beanQualifier, null, null, null);
        }
        FieldDef beanResolutionContext = FieldDef.builder(FIELD_BEAN_RESOLUTION_CONTEXT, BeanResolutionContext.class)
            .addModifiers(Modifier.PRIVATE)
            .build();
        FieldDef interceptorRegistry = FieldDef.builder(FIELD_INTERCEPTOR_REGISTRY, InterceptorRegistry.class)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();
        FieldDef targetRegistration = cacheLazyTarget
            ? FieldDef.builder(FIELD_TARGET_REGISTRATION, BeanRegistration.class).addModifiers(Modifier.PRIVATE).build()
            : null;
        return new ProxyTargetFields(proxyBeanDefinition, beanQualifier, beanResolutionContext, interceptorRegistry, targetRegistration);
    }

    private ExpressionDef.InvokeInstanceMethod resolveProxyTargetRegistration(VariableDef.This aThis, ProxyTargetFields fields) {
        return resolveProxyTargetRegistration(
            aThis.field(fields.beanResolutionContext()),
            aThis.field(fields.proxyBeanDefinition()),
            aThis.field(fields.beanQualifier())
        );
    }

    /**
     * Resolves the registration of the target, which carries the interceptors resolved for it when it is not a
     * singleton.
     *
     * @param resolutionContext   The resolution context to resolve through
     * @param proxyBeanDefinition The definition of the target
     * @param qualifier           The qualifier
     * @return The expression
     */
    private ExpressionDef.InvokeInstanceMethod resolveProxyTargetRegistration(ExpressionDef resolutionContext,
                                                                              ExpressionDef proxyBeanDefinition,
                                                                              ExpressionDef qualifier) {
        return resolutionContext.invoke(
            METHOD_GET_PROXY_TARGET_BEAN_REGISTRATION,

            // 1st argument: this.$proxyBeanDefinition
            proxyBeanDefinition,
            // 2nd argument: the type
            pushTargetArgument(ClassTypeDef.of(targetType.getName())),
            // 3rd argument: the qualifier
            qualifier
        );
    }

    private StatementDef invokeSuperConstructor(VariableDef.This aThis, List<VariableDef.MethodParameter> methodParameters) {
        if (targetType.isInterface()) {
            return aThis.superRef().invokeConstructor();
        }
        List<ExpressionDef> values = new ArrayList<>();
        Iterator<VariableDef.MethodParameter> iterator = methodParameters.iterator();
        for (ParameterElement ignored : targetConstructor.getParameters()) {
            values.add(iterator.next());
        }
        List<StatementDef> statements = new ArrayList<>();
        statements.add(MethodGenUtils.invokeSuperConstructor(
            aThis.superRef(),
            targetConstructor,
            true,
            values,
            values.stream().map(ExpressionDef::isNonNull).toList(),
            statements
        ));
        return StatementDef.multi(statements);
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
                proxyBeanDefinitionWriter.getBeanDefinitionName(),
                proxyBeanDefinitionWriter.getAnnotationMetadata()
            ),
            parentWriter != null ? parentWriter.getTypeArguments() : proxyBeanDefinitionWriter.getTypeArguments()
        );
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

    private MethodDef getLazyProxyTargetToStringMethod() {
        return MethodDef.override(METHOD_OBJECT_TO_STRING)
            .build((aThis, methodParameters) -> aThis.invoke(METHOD_INTERCEPTED_TARGET).invoke(METHOD_OBJECT_TO_STRING).returning());
    }

    private static boolean isToStringMethod(MethodElement methodElement) {
        return methodElement.getName().equals(METHOD_OBJECT_TO_STRING.getName()) && methodElement.getParameters().length == 0;
    }

    private boolean shouldGenerateLazyProxyTargetToStringMethod(List<MethodElement> interceptedMethods) {
        return interceptedMethods.stream().noneMatch(AopProxyWriter::isToStringMethod)
            && targetType.getMethods().stream().noneMatch(method -> method.isFinal() && isToStringMethod(method));
    }

    private MethodDef getCacheLazyTargetInterceptedTargetMethod(FieldDef targetField,
                                                                FieldDef targetRegistrationField,
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
//                                        BeanRegistration var2 = this.$beanResolutionContext.getProxyTargetBeanRegistration(this.$proxyBeanDefinition, Argument.of(B.class, $B$Definition$Intercepted$Definition.$ANNOTATION_METADATA, new Class[0]), this.$beanQualifier);
//                                        this.$targetRegistration = var2;
//                                        this.$target = var2.getBean();
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
                                        resolveProxyTargetRegistration(
                                            aThis.field(beanResolutionContextField),
                                            aThis.field(proxyBeanDefinitionField),
                                            aThis.field(beanQualifierField)
                                        ).newLocal("targetRegistration", targetRegistration -> StatementDef.multi(
                                            aThis.field(targetRegistrationField).assign(targetRegistration),
                                            targetFieldAccess.assign(targetRegistration.invoke(METHOD_REGISTRATION_GET_BEAN)),
                                            aThis.field(beanResolutionContextField).assign(ExpressionDef.nullValue())
                                        ))
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

    private MethodDef getClearCachedInterceptedTargetMethod(FieldDef targetField, FieldDef targetRegistrationField) {
        Objects.requireNonNull(targetField);
        return MethodDef.builder(METHOD_CLEAR_CACHED_INTERCEPTED_METHOD.getName())
            .addModifiers(Modifier.PUBLIC)
            .addParameters(METHOD_CLEAR_CACHED_INTERCEPTED_METHOD.getParameterTypes())
            .build((aThis, methodParameters) -> StatementDef.multi(
                aThis.field(targetField).assign(ExpressionDef.nullValue()),
                aThis.field(targetRegistrationField).assign(ExpressionDef.nullValue())
            ));
    }

    /**
     * Method Reference class with names and a list of argument types. Used as the targets.
     */
    private static final class MethodRef {
        private final String name;
        private final String returnType;
        private final List<String> rawTypes;

        public MethodRef(String name, List<ParameterElement> parameterElements, ClassElement returnType) {
            this.name = name;
            List<ClassElement> argumentTypes = parameterElements.stream().map(ParameterElement::getType).toList();
            this.rawTypes = argumentTypes.stream().map(AopProxyWriter::toTypeString).toList();
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

    /**
     * The fields of a proxy that fronts a separate target. They are created ahead of the intercepted methods and
     * the constructor, which both refer to them.
     *
     * @param proxyBeanDefinition   The definition of the target
     * @param beanQualifier         The qualifier of the target
     * @param beanResolutionContext The context a lazy proxy resolves its target through, or {@code null}
     * @param interceptorRegistry   The registry a lazy proxy selects the interceptors of each call with, or
     *                              {@code null}
     * @param targetRegistration    The registration a lazy proxy that caches its target keeps, or {@code null}
     */
    private record ProxyTargetFields(FieldDef proxyBeanDefinition,
                                     FieldDef beanQualifier,
                                     @Nullable FieldDef beanResolutionContext,
                                     @Nullable FieldDef interceptorRegistry,
                                     @Nullable FieldDef targetRegistration) {
    }
}
