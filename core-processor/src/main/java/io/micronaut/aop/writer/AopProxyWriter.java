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
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.aop.Introduced;
import io.micronaut.aop.chain.CachedProxyTargetHandler;
import io.micronaut.aop.chain.CachedTargetProxyTargetHandler;
import io.micronaut.aop.chain.FixedProxyTargetHandler;
import io.micronaut.aop.chain.HeldTargetProxyTargetHandler;
import io.micronaut.aop.chain.HotSwapProxyTargetHandler;
import io.micronaut.aop.chain.HotSwappableProxyTargetHandler;
import io.micronaut.aop.chain.InterceptorCandidateResolver;
import io.micronaut.aop.chain.InterceptorChainFactory;
import io.micronaut.aop.chain.LazyProxyTargetHandler;
import io.micronaut.aop.chain.ProxyTargetHandler;
import io.micronaut.aop.internal.intercepted.InterceptedMethodUtil;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanDependencyGroup;
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
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.processing.definition.OutputObjectDef;
import io.micronaut.inject.ProxyBeanDefinition;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.proxy.InterceptedBeanProxy;
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

import javax.lang.model.element.Modifier;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

    private static final Method RESOLVE_METHOD_INTERCEPTORS = ReflectionUtils.getRequiredInternalMethod(
        InterceptorCandidateResolver.class, "selectMethodInterceptors", ExecutableMethod.class, Collection.class, InterceptorKind.class);
    private static final Method BUILD_METHOD_CHAIN = ReflectionUtils.getRequiredInternalMethod(
        InterceptorChainFactory.class, "buildMethodChain", Object.class, ExecutableMethod.class,
        Interceptor[].class, Object[].class);
    private static final Method GET_BEAN_BY_ARGUMENT = ReflectionUtils.getRequiredInternalMethod(
        BeanLocator.class, "getBean", Argument.class);
    private static final Method GET_CANDIDATE_RESOLVER = ReflectionUtils.getRequiredInternalMethod(
        InterceptorChainFactory.class, "candidateResolver");
    private static final FieldDef FIELD_CHAIN_FACTORY = FieldDef.builder("$interceptorChainFactory", TypeDef.of(InterceptorChainFactory.class))
        .addModifiers(Modifier.PRIVATE, Modifier.FINAL).build();
    private static final String INTERCEPTORS_PARAMETER = "$interceptors";
    private static final String BEAN_RESOLUTION_CONTEXT_PARAMETER = "$beanResolutionContext";
    private static final String BEAN_CONTEXT_PARAMETER = "$beanContext";
    private static final String QUALIFIER_PARAMETER = "$qualifier";
    private static final String INTERCEPTOR_REGISTRY_PARAMETER = "$interceptorRegistry";

    private static final Method METHOD_PROCEED = ReflectionUtils.getRequiredInternalMethod(MethodInvocationContext.class, "proceed");

    private static final String FIELD_INTERCEPTORS = "$interceptors";
    private static final String FIELD_INTERCEPTOR_REGISTRATIONS = "$interceptorRegistrations";

    // The proxy accessor is named after the field it returns.
    private static final Method GET_INTERCEPTOR_REGISTRATIONS_METHOD =
        ReflectionUtils.getRequiredInternalMethod(Intercepted.class, FIELD_INTERCEPTOR_REGISTRATIONS);
    private static final Method METHOD_INTERCEPTED_TARGET_REGISTRATION = ReflectionUtils.getRequiredInternalMethod(
        InterceptedBeanProxy.class,
        "interceptedTargetRegistration"
    );
    private static final String FIELD_PROXY_METHODS = "$proxyMethods";
    private static final String HANDLER_PARAMETER = "$handler";
    private static final String FIELD_HANDLER = "$handler";
    private static final String FIELD_TARGET_TYPE = "$TARGET_TYPE";
    private static final Method HANDLER_BIND = ReflectionUtils.getRequiredInternalMethod(
        ProxyTargetHandler.class, "bind", Argument.class, boolean.class, boolean.class, String[].class, Class[][].class);
    private static final Method HANDLER_INVOKE = ReflectionUtils.getRequiredInternalMethod(
        ProxyTargetHandler.class, "invoke", int.class, Object[].class);
    private static final Method HANDLER_TARGET = ReflectionUtils.getRequiredInternalMethod(ProxyTargetHandler.class, "target");
    private static final Method HANDLER_HAS_CACHED_TARGET = ReflectionUtils.getRequiredInternalMethod(HeldTargetProxyTargetHandler.class, "hasCachedTarget");
    private static final Method HANDLER_CLEAR_CACHED_TARGET = ReflectionUtils.getRequiredInternalMethod(CachedTargetProxyTargetHandler.class, "clearCachedTarget");
    private static final Method HANDLER_TARGET_REGISTRATION = ReflectionUtils.getRequiredInternalMethod(HeldTargetProxyTargetHandler.class, "targetRegistration");
    private static final Method HANDLER_WITH_QUALIFIER = ReflectionUtils.getRequiredInternalMethod(ProxyTargetHandler.class, "withQualifier", Qualifier.class);
    private static final Method HANDLER_DEPENDENCIES = ReflectionUtils.getRequiredInternalMethod(ProxyTargetHandler.class, "dependencies");
    private static final String METHOD_INTERCEPTED_METHODS = "interceptedMethods";
    private static final Method HANDLER_INTERCEPTED_METHODS = ReflectionUtils.getRequiredInternalMethod(ProxyTargetHandler.class, METHOD_INTERCEPTED_METHODS);
    private static final Method HANDLER_INTERCEPTOR_REGISTRATIONS = ReflectionUtils.getRequiredInternalMethod(ProxyTargetHandler.class, "interceptorRegistrations");
    private static final Method HANDLER_SWAP = ReflectionUtils.getRequiredInternalMethod(HotSwappableProxyTargetHandler.class, "swap", Object.class);

    private final Set<ClassElement> defaultMethodInterfaceTypes = new LinkedHashSet<>();
    private final boolean hotswap;
    private final boolean lazy;
    private final boolean cacheLazyTarget;
    // whether a proxy fronting a separate target resolves its interceptors for each target, see Around#lazyInterceptorsPerTarget
    private final boolean interceptorsPerTarget;
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
            createProxyConstructor(targetType, createProxyType(parent), settings, visitorContext,
                settings.get(Interceptor.PROXY_TARGET).orElse(false) || parent.isInterface()),
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
        this.interceptorsPerTarget = isProxyTarget && settings.get(Interceptor.LAZY_INTERCEPTORS_PER_TARGET).orElse(false);
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
        this.interceptorsPerTarget = false;
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
        return createProxyConstructor(target, proxyClass, OptionalValues.empty(), visitorContext, false);
    }

    private static MethodElement createProxyConstructor(ClassElement target, ClassElement proxyClass, OptionalValues<Boolean> settings, VisitorContext visitorContext,
                                                       boolean proxyTarget) {
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

        if (proxyTarget) {
            // a proxy that fronts a separate target is injected with the handler it delegates to and nothing else
            newConstructorParameters.add(ParameterElement.of(ClassElement.of(handlerType(settings)), HANDLER_PARAMETER));
            return MethodElement.of(
                proxyClass,
                constructor.getAnnotationMetadata(),
                proxyClass,
                proxyClass,
                "<init>",
                newConstructorParameters.toArray(ParameterElement.ZERO_PARAMETER_ELEMENTS)
            );
        }

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
                                           FieldDef interceptorsField,
                                           FieldDef proxyMethodsField) {
        return MethodDef.override(methodElement)
            .build((aThis, methodParameters) -> proceed(
                aThis,
                methodElement,
                methodParameters,
                aThis.field(interceptorsField).arrayElement(index),
                aThis,
                aThis.field(proxyMethodsField).arrayElement(index)
            ));
    }

    /**
     * Runs the interceptor chain of one method and returns what it returns, unless the method returns nothing.
     *
     * @param aThis            The proxy instance
     * @param methodElement    The intercepted method
     * @param methodParameters The parameters of the proxy method
     * @param interceptors     The interceptors
     * @param target           The proxy itself, or the target it fronts
     * @param method           The executable method
     * @return The statement
     */
    private StatementDef proceed(VariableDef.This aThis,
                                 MethodElement methodElement,
                                 List<VariableDef.MethodParameter> methodParameters,
                                 ExpressionDef interceptors,
                                 ExpressionDef target,
                                 ExpressionDef method) {
        ExpressionDef.InvokeInstanceMethod invocation = aThis.field(FIELD_CHAIN_FACTORY)
            .invoke(BUILD_METHOD_CHAIN, target, method, interceptors, argumentsOf(methodParameters))
            .invoke(METHOD_PROCEED);
        if (!methodElement.getReturnType().isVoid() || methodElement.isSuspend()) {
            return invocation.returning();
        }
        return invocation;
    }

    /**
     * The interface the proxy holds its handler as: what the handler can say about the target.
     */
    private Class<?> handlerInterface() {
        if (hotswap) {
            return HotSwappableProxyTargetHandler.class;
        }
        if (cacheLazyTarget) {
            return CachedTargetProxyTargetHandler.class;
        }
        return lazy ? ProxyTargetHandler.class : HeldTargetProxyTargetHandler.class;
    }

    private static Class<?> handlerType(OptionalValues<Boolean> settings) {
        if (settings.get(Interceptor.LAZY).orElse(false)) {
            return settings.get(Interceptor.CACHEABLE_LAZY_TARGET).orElse(false)
                ? CachedProxyTargetHandler.class : LazyProxyTargetHandler.class;
        }
        return settings.get(Interceptor.HOTSWAP).orElse(false)
            ? HotSwapProxyTargetHandler.class : FixedProxyTargetHandler.class;
    }

    /**
     * Builds a proxy that fronts a separate target. It has one field, the handler it is injected with, and every
     * method delegates to it: where the target comes from and which interceptors apply is up to the kind of handler
     * the constructor declares.
     */
    private List<OutputObjectDef> buildProxyTarget(ClassDef.ClassDefBuilder proxyBuilder) {
        ParameterElement handlerParameter = constructor.getParameter(HANDLER_PARAMETER);
        // held and called through the interface that says what the handler can tell about its target; the parameter
        // declares the kind the container injects
        FieldDef handlerField = FieldDef.builder(FIELD_HANDLER, ClassTypeDef.of(handlerInterface()))
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();
        proxyBuilder.addField(handlerField);

        widenBindingWithLifecycle();
        ClassTypeDef classTargetType = ClassTypeDef.of(this.targetType.getName());
        List<MethodElement> interceptedMethods = addTypeAndFindInterceptedMethods(proxyBuilder, classTargetType);

        int index = 0;
        for (MethodElement method : interceptedMethods) {
            int methodIndex = index++;
            proxyBuilder.addMethod(MethodDef.override(method).build((aThis, methodParameters) -> {
                ExpressionDef.InvokeInstanceMethod invocation = aThis.field(handlerField)
                    .invoke(HANDLER_INVOKE, TypeDef.Primitive.INT.constant(methodIndex), argumentsOf(methodParameters));
                if (!method.getReturnType().isVoid() || method.isSuspend()) {
                    return invocation.returning();
                }
                return invocation;
            }));
        }
        if (!interceptedMethods.isEmpty()) {
            proxyBuilder.addMethod(MethodDef.builder(METHOD_INTERCEPTED_METHODS)
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassTypeDef.of(ExecutableMethod.class).array())
                .build((aThis, methodParameters) -> aThis.field(handlerField).invoke(HANDLER_INTERCEPTED_METHODS).returning()));
        }

        // The binding of the proxy travels on the handler parameter, where the handler reads it from its injection
        // point. A proxy that takes the interceptors of each call from its target binds none of its own.
        handlerParameter.annotate(ProxyTargetHandler.BINDING, builder -> {
            if (!interceptorsPerTarget) {
                builder.values(interceptorBinding.toArray(ZERO_ANNOTATION_VALUES));
            }
        });

        addProxyMarkers(proxyBuilder);
        if (lazy) {
            proxyBuilder.addSuperinterface(TypeDef.of(InterceptedProxy.class));
        } else if (hotswap) {
            proxyBuilder.addSuperinterface(TypeDef.parameterized(HotSwappableInterceptedProxy.class, classTargetType));
        } else {
            proxyBuilder.addSuperinterface(TypeDef.parameterized(InterceptedProxy.class, classTargetType));
        }

        proxyBuilder.addMethod(MethodDef.override(GET_INTERCEPTOR_REGISTRATIONS_METHOD)
            .build((aThis, methodParameters) -> aThis.field(handlerField).invoke(HANDLER_INTERCEPTOR_REGISTRATIONS).returning()));
        proxyBuilder.addMethod(MethodDef.builder("$beanDependencies")
            .addModifiers(Modifier.PUBLIC).returns(BeanDependencyGroup.class)
            .build((aThis, methodParameters) -> aThis.field(handlerField).invoke(HANDLER_DEPENDENCIES).returning()));
        proxyBuilder.addMethod(MethodDef.override(WITH_QUALIFIER_METHOD)
            .build((aThis, methodParameters) -> aThis.field(handlerField).invoke(HANDLER_WITH_QUALIFIER, methodParameters.get(0))));
        proxyBuilder.addMethod(MethodDef.override(METHOD_INTERCEPTED_TARGET)
            .build((aThis, methodParameters) -> aThis.field(handlerField).invoke(HANDLER_TARGET).returning()));
        // a proxy that looks its target up for every call holds none: it keeps the defaults of the interface
        if (!lazy || cacheLazyTarget) {
            proxyBuilder.addMethod(MethodDef.override(METHOD_HAS_CACHED_INTERCEPTED_METHOD)
                .build((aThis, methodParameters) -> aThis.field(handlerField).invoke(HANDLER_HAS_CACHED_TARGET).returning()));
            proxyBuilder.addMethod(MethodDef.override(METHOD_INTERCEPTED_TARGET_REGISTRATION)
                .build((aThis, methodParameters) -> aThis.field(handlerField).invoke(HANDLER_TARGET_REGISTRATION).returning()));
        }
        if (cacheLazyTarget) {
            proxyBuilder.addMethod(MethodDef.override(METHOD_CLEAR_CACHED_INTERCEPTED_METHOD)
                .build((aThis, methodParameters) -> aThis.field(handlerField).invoke(HANDLER_CLEAR_CACHED_TARGET)));
        }
        if (hotswap) {
            proxyBuilder.addMethod(MethodDef.override(SWAP_METHOD)
                .build((aThis, methodParameters) -> aThis.field(handlerField).invoke(HANDLER_SWAP, methodParameters.get(0)).returning()));
        }
        if (lazy && !cacheLazyTarget && shouldGenerateLazyProxyTargetToStringMethod(interceptedMethods)) {
            proxyBuilder.addMethod(getLazyProxyTargetToStringMethod());
        }

        // the type of the target, created once with the definition of the proxy rather than for every proxy
        FieldDef targetTypeField = proxyBeanDefinitionWriter.addSharedStaticField(
            FIELD_TARGET_TYPE, TypeDef.of(Argument.class), pushTargetArgument(classTargetType));
        ExpressionDef targetType = ClassTypeDef.of(proxyBeanDefinitionWriter.getBeanDefinitionName()).getStaticField(targetTypeField);
        int handlerIndex = constructor.findParameterIndex(HANDLER_PARAMETER);
        proxyBuilder.addMethod(MethodDef.constructor()
            .addParameters(Arrays.stream(constructor.getParameters()).map(p -> TypeDef.erasure(p.getType())).toList())
            .build((aThis, methodParameters) -> StatementDef.multi(
                invokeSuperConstructor(aThis, methodParameters),
                aThis.field(handlerField).assign(methodParameters.get(handlerIndex)),
                aThis.field(handlerField).invoke(
                    HANDLER_BIND,
                    targetType,
                    TypeDef.Primitive.BOOLEAN.constant(isIntroduction),
                    TypeDef.Primitive.BOOLEAN.constant(interceptorsPerTarget),
                    TypeDef.STRING.array().instantiate(
                        interceptedMethods.stream().map(method -> (ExpressionDef) ExpressionDef.constant(method.getName())).toList()),
                    TypeDef.CLASS.array(2).instantiate(
                        interceptedMethods.stream().map(method -> (ExpressionDef) TypeDef.CLASS.array().instantiate(
                            Arrays.stream(method.getSuspendParameters())
                                .map(p -> ExpressionDef.constant(TypeDef.erasure(p.getGenericType()))).toList())).toList())
                )
            )));

        return output(proxyBuilder);
    }

    /**
     * Widens the binding of a proxy with intercepted lifecycle by the lifecycle bindings of its type, so that the
     * interceptors the proxy is created with are a superset of what lifecycle interception needs; a lifecycle
     * interceptor bound by a different annotation would otherwise be dropped. This is the compile-time half of the
     * rule InterceptedBeanDefinition#resolveInterceptors applies at runtime for beans that get no proxy: whatever a
     * bean binds for construction, post-construct and pre-destroy is resolved as one set. Keep the two in step.
     * Only such a proxy is widened: doing it for every proxy would change which interceptors every proxied bean of
     * every application is given.
     */
    private void widenBindingWithLifecycle() {
        if (proxyBeanDefinitionWriter.hasInterceptedLifecycle()) {
            AnnotationMetadata targetAnnotationMetadata = targetType.getAnnotationMetadata();
            visitInterceptorBinding(InterceptedMethodUtil.resolveInterceptorBinding(targetAnnotationMetadata, InterceptorKind.POST_CONSTRUCT));
            visitInterceptorBinding(InterceptedMethodUtil.resolveInterceptorBinding(targetAnnotationMetadata, InterceptorKind.PRE_DESTROY));
        }
    }

    /**
     * Gives the proxy its supertype and interfaces and the methods that are overridden without interception.
     *
     * @return The methods the proxy intercepts, in the order of their indexes
     */
    private List<MethodElement> addTypeAndFindInterceptedMethods(ClassDef.ClassDefBuilder proxyBuilder, ClassTypeDef classTargetType) {
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
        return interceptedMethods;
    }

    private void addProxyMarkers(ClassDef.ClassDefBuilder proxyBuilder) {
        if (parentWriter != null) {
            proxyBeanDefinitionWriter.visitBeanDefinitionInterface(ProxyBeanDefinition.class);
            proxyBeanDefinitionWriter.generateProxyReference(parentWriter.getBeanDefinitionName(), parentWriter.getBeanTypeName());
        }
        proxyBuilder.addSuperinterface(TypeDef.of(isIntroduction ? Introduced.class : Intercepted.class));
    }

    private List<OutputObjectDef> output(ClassDef.ClassDefBuilder proxyBuilder) {
        List<OutputObjectDef> classes = new ArrayList<>();
        classes.add(new OutputObjectDef(proxyBuilder.build(), null, originatingElements));
        if (executableMethodsDefinitionWriter != null) {
            classes.add(executableMethodsDefinitionWriter.build());
        }
        classes.addAll(proxyBeanDefinitionWriter.build());
        return classes;
    }

    private static ExpressionDef argumentsOf(List<VariableDef.MethodParameter> methodParameters) {
        return methodParameters.isEmpty()
            ? ClassTypeDef.of(ArrayUtils.class).getStaticField("EMPTY_OBJECT_ARRAY", TypeDef.OBJECT.array())
            : TypeDef.OBJECT.array().instantiate(methodParameters);
    }

    @Override
    public List<OutputObjectDef> build() {
        if (parentWriter != null && !isProxyTarget) {
            processAlreadyVisitedMethods(parentWriter);
        }

        ClassDef.ClassDefBuilder proxyBuilder = ClassDef.builder(proxyType.getName()).synthetic();
        if (isProxyTarget) {
            return buildProxyTarget(proxyBuilder);
        }

        // A proxy that is its own target: it subclasses the bean, is injected with the interceptors bound to it and
        // selects those of each method as it is constructed.
        proxyBuilder.addField(FIELD_CHAIN_FACTORY);
        FieldDef interceptorsField = FieldDef.builder(FIELD_INTERCEPTORS, Interceptor[][].class)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();
        proxyBuilder.addField(interceptorsField);

        // The proxy retains the registrations its constructor was given, so that it can report the interceptors
        // bound to it.
        FieldDef interceptorRegistrationsField = FieldDef.builder(FIELD_INTERCEPTOR_REGISTRATIONS, List.class)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();
        proxyBuilder.addField(interceptorRegistrationsField);
        proxyBuilder.addMethod(MethodDef.override(GET_INTERCEPTOR_REGISTRATIONS_METHOD)
            .build((aThis, methodParameters) -> aThis.field(interceptorRegistrationsField).returning()));

        widenBindingWithLifecycle();

        FieldDef proxyMethodsField = FieldDef.builder(FIELD_PROXY_METHODS, ExecutableMethod[].class)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();
        proxyBuilder.addField(proxyMethodsField);

        List<MethodElement> interceptedMethods = addTypeAndFindInterceptedMethods(proxyBuilder, ClassTypeDef.of(this.targetType.getName()));
        int index = 0;
        for (MethodElement method : interceptedMethods) {
            proxyBuilder.addMethod(buildMethodIntercept(method, index++, interceptorsField, proxyMethodsField));
        }
        if (!interceptedMethods.isEmpty()) {
            proxyBuilder.addMethod(MethodDef.builder(METHOD_INTERCEPTED_METHODS)
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassTypeDef.of(ExecutableMethod.class).array())
                .build((aThis, methodParameters) -> aThis.field(proxyMethodsField)
                    .invoke("clone", TypeDef.OBJECT)
                    .cast(ClassTypeDef.of(ExecutableMethod.class).array())
                    .returning()));
        }

        constructor.getParameter(INTERCEPTORS_PARAMETER).annotate(AnnotationUtil.ANN_INTERCEPTOR_BINDING_QUALIFIER,
            builder -> builder.values(interceptorBinding.toArray(ZERO_ANNOTATION_VALUES)));

        addProxyMarkers(proxyBuilder);

        int beanContextIndex = constructor.findParameterIndex(BEAN_CONTEXT_PARAMETER);
        int interceptorsIndex = constructor.findParameterIndex(INTERCEPTORS_PARAMETER);
        proxyBuilder.addMethod(MethodDef.constructor()
            .addParameters(Arrays.stream(constructor.getParameters()).map(p -> TypeDef.erasure(p.getType())).toList())
            .build((aThis, methodParameters) -> StatementDef.multi(
                invokeSuperConstructor(aThis, methodParameters),
                aThis.field(FIELD_CHAIN_FACTORY).assign(methodParameters.get(beanContextIndex).invoke(GET_BEAN_BY_ARGUMENT,
                        ClassTypeDef.of(InterceptorChainFactory.class).getStaticField("ARGUMENT", ClassTypeDef.of(Argument.class)))
                    .cast(InterceptorChainFactory.class)),
                aThis.field(interceptorRegistrationsField).assign(methodParameters.get(interceptorsIndex)),
                initializeProxyMethodsAndInterceptors(aThis, methodParameters, interceptorsField, proxyMethodsField, interceptedMethods)
            )));

        return output(proxyBuilder);
    }

    private static void addInterface(List<ClassTypeDef> interfaces,
                                     Set<String> interfaceNames,
                                     ClassTypeDef interfaceType) {
        if (interfaceNames.add(interfaceType.getName())) {
            interfaces.add(interfaceType);
        }
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

                            return aThis.field(FIELD_CHAIN_FACTORY).invoke(GET_CANDIDATE_RESOLVER).invoke(
                                RESOLVE_METHOD_INTERCEPTORS,
                                // The executable method
                                aThis.field(proxyMethodsField).arrayElement(index.getAndIncrement()),
                                // The acquired candidates
                                parameters.get(constructor.findParameterIndex(INTERCEPTORS_PARAMETER)),
                                ExpressionDef.constant(introduction ? InterceptorKind.INTRODUCTION : InterceptorKind.AROUND)
                            );
                        }
                    ).toList()
                )
            )
        ));
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
}
