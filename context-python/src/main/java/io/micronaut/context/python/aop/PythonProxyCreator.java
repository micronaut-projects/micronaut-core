/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.context.python.aop;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.aop.Adapter;
import io.micronaut.aop.AroundConstruct;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.chain.MethodInterceptorChain;
import io.micronaut.aop.runtime.RuntimeProxyCreator;
import io.micronaut.aop.runtime.RuntimeProxyDefinition;
import io.micronaut.context.python.ContextHolder;
import io.micronaut.context.python.GraalPyRuntimeUtil;
import io.micronaut.context.python.PythonAsyncioRuntime;
import io.micronaut.context.python.TargetTypeMapping;
import io.micronaut.context.python.ValueCoercible;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.FieldInjectionPoint;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.micronaut.aop.Adapter.InternalAttributes.ADAPTED_BEAN;
import static io.micronaut.context.python.GraalPyRuntimeUtil.PYTHON;

/**
 * Creates Micronaut runtime proxies backed by GraalPy values.
 */
@Internal
@Singleton
@NullMarked
@SuppressWarnings({"checkstyle:InnerTypeLast", "checkstyle:MissingJavadocType"})
@Experimental
public final class PythonProxyCreator implements RuntimeProxyCreator {

    private static final String SCOPED_PROXY_FACTORY = "__micronaut_create_scoped_proxy";
    private static final String RAW_INSTANCE_FACTORY = "__micronaut_create_raw_instance";
    private static final String SCOPED_PROXY_OVERRIDE_METHOD = "_micronaut_put_override";
    private static final String SCOPED_PROXY_SETTER_OVERRIDE_METHOD = "_micronaut_put_setter_override";
    private static final String SCOPED_PROXY_REGISTER_MEMBER_METHOD = "_micronaut_register_member";

    private final Collection<TargetTypeMapping<?>> targetTypeMappings;

    public PythonProxyCreator(Collection<TargetTypeMapping<?>> targetTypeMappings) {
        this.targetTypeMappings = targetTypeMappings;
    }

    @Override
    public <T> T createProxy(RuntimeProxyDefinition<T> proxyDefinition) {
        if (proxyDefinition.introduction()) {
            return createIntroductionProxy(proxyDefinition);
        }
        if (proxyDefinition.proxyTarget()) {
            return createProxyTargetProxy(proxyDefinition);
        }
        throw new IllegalStateException("Python runtime proxies require proxyTarget=true");
    }

    private <T> T createIntroductionProxy(RuntimeProxyDefinition<T> proxyDefinition) {
        Class<?> pythonBeanType = resolvePythonBeanType(proxyDefinition);
        Value value = ContextHolder.findClass(pythonBeanType.getPackageName(), pythonBeanType.getSimpleName());
        AtomicReference<Object> targetBeanRef = new AtomicReference<>();
        Map<String, List<RuntimeProxyDefinition.InterceptedMethod<T>>> interceptedMethodsByName = new LinkedHashMap<>();
        for (RuntimeProxyDefinition.InterceptedMethod<T> interceptedMethod : proxyDefinition.interceptedMethods()) {
            interceptedMethodsByName.computeIfAbsent(
                interceptedMethod.executableMethod().getMethodName(),
                ignored -> new ArrayList<>()
            ).add(interceptedMethod);
        }
        Map<String, ProxyExecutable> introductionFunctions = new LinkedHashMap<>();
        for (Map.Entry<String, List<RuntimeProxyDefinition.InterceptedMethod<T>>> entry : interceptedMethodsByName.entrySet()) {
            String methodName = entry.getKey();
            List<RuntimeProxyDefinition.InterceptedMethod<T>> interceptedMethods = entry.getValue()
                .stream()
                .map(interceptedMethod -> withoutConcreteIntroductionInterceptors(proxyDefinition, interceptedMethod))
                .toList();
            Value originalFunction = GraalPyRuntimeUtil.getRawClassMember(value, methodName);
            ProxyExecutable proxiedFunction = createProxiedFunction(
                true,
                true,
                value,
                null,
                null,
                targetBeanRef,
                methodSelector(methodName, interceptedMethods),
                originalFunction
            );
            introductionFunctions.put(methodName, proxiedFunction);
        }
        fillAllAbstractMethods(value);
        Class<T> type = proxyDefinition.proxyBeanDefinition().getBeanType();
        Value targetValue = newIntroductionTarget(proxyDefinition, value);
        T target = box(type, targetValue);
        if (target == null) {
            throw new IllegalStateException("Introduction proxy target cannot be null");
        }
        targetBeanRef.set(target);
        for (Map.Entry<String, ProxyExecutable> entry : introductionFunctions.entrySet()) {
            targetValue.putMember(entry.getKey(), entry.getValue());
        }
        return target;
    }

    private Value newIntroductionTarget(RuntimeProxyDefinition<?> proxyDefinition, Value pythonClass) {
        if (isAdapterIntroduction(proxyDefinition)) {
            return rawInstanceFactory(pythonClass.getContext()).execute(pythonClass);
        }
        return pythonClass.newInstance(GraalPyRuntimeUtil.coerceArgumentsToContext(
            pythonClass.getContext(),
            proxyDefinition.constructorValues()
        ));
    }

    private boolean isAdapterIntroduction(RuntimeProxyDefinition<?> proxyDefinition) {
        for (RuntimeProxyDefinition.InterceptedMethod<?> interceptedMethod : proxyDefinition.interceptedMethods()) {
            if (interceptedMethod.executableMethod().classValue(Adapter.class, ADAPTED_BEAN).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static <T> RuntimeProxyDefinition.InterceptedMethod<T> withoutConcreteIntroductionInterceptors(
        RuntimeProxyDefinition<T> proxyDefinition,
        RuntimeProxyDefinition.InterceptedMethod<T> interceptedMethod) {
        if (interceptedMethod.executableMethod().isAbstract()) {
            return interceptedMethod;
        }
        Interceptor<T, Object>[] interceptors = interceptedMethod.interceptors();
        if (interceptors.length == 0) {
            return interceptedMethod;
        }
        Set<String> introductionOnlyBindings = introductionOnlyBindings(interceptedMethod.executableMethod());
        if (introductionOnlyBindings.isEmpty()) {
            return interceptedMethod;
        }
        List<Interceptor<T, Object>> filteredInterceptors = new ArrayList<>(interceptors.length);
        for (Interceptor<T, Object> interceptor : interceptors) {
            if (!hasAnyBinding(proxyDefinition, interceptor, introductionOnlyBindings)) {
                filteredInterceptors.add(interceptor);
            }
        }
        if (filteredInterceptors.size() == interceptors.length) {
            return interceptedMethod;
        }
        // Runtime introduction definitions resolve concrete methods as around interceptors plus
        // introduction interceptors. Python already has a concrete target function for these methods,
        // so introduction-only interceptors must be excluded before proxy execution.
        return new RuntimeProxyDefinition.InterceptedMethod<>(
            interceptedMethod.executableMethod(),
            filteredInterceptors.toArray(Interceptor[]::new)
        );
    }

    private static Set<String> introductionOnlyBindings(ExecutableMethod<?, ?> executableMethod) {
        Set<String> introductionBindings = bindingNames(executableMethod.getAnnotationMetadata(), InterceptorKind.INTRODUCTION);
        if (introductionBindings.isEmpty()) {
            return introductionBindings;
        }
        Set<String> aroundBindings = bindingNames(executableMethod.getAnnotationMetadata(), InterceptorKind.AROUND);
        introductionBindings.removeAll(aroundBindings);
        return introductionBindings;
    }

    private static Set<String> bindingNames(AnnotationMetadata annotationMetadata, InterceptorKind kind) {
        Set<String> bindingNames = new LinkedHashSet<>();
        for (AnnotationValue<InterceptorBinding> binding : annotationMetadata.getAnnotationValuesByType(InterceptorBinding.class)) {
            if (binding.enumValue("kind", InterceptorKind.class).orElse(InterceptorKind.AROUND) == kind) {
                binding.stringValue().ifPresent(bindingNames::add);
            }
        }
        return bindingNames;
    }

    private static <T> boolean hasAnyBinding(
        RuntimeProxyDefinition<T> proxyDefinition,
        Interceptor<T, Object> interceptor,
        Set<String> bindingNames) {
        BeanDefinition<?> interceptorDefinition = proxyDefinition.beanContext()
            .findBeanDefinition(interceptor.getClass())
            .orElse(null);
        if (interceptorDefinition == null) {
            return false;
        }
        for (AnnotationValue<InterceptorBinding> binding : interceptorDefinition.getAnnotationMetadata().getAnnotationValuesByType(InterceptorBinding.class)) {
            if (binding.stringValue().filter(bindingNames::contains).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private <T> T createProxyTargetProxy(RuntimeProxyDefinition<T> proxyDefinition) {
        Class<T> type = proxyDefinition.proxyBeanDefinition().getBeanType();
        Value pythonClass = ContextHolder.findClass(type.getPackageName(), type.getSimpleName());
        Value proxyValue = createScopedProxyValue(pythonClass, () -> asValue(proxyDefinition.targetBean()));
        if (hasAroundConstructAdvice(proxyDefinition)) {
            // Python proxy-target AOP normally instantiates the target lazily through the scoped proxy.
            // Around-construct advice is observable at bean creation time in Micronaut, so force target
            // creation here while leaving method calls to resolve the current scoped target normally.
            proxyDefinition.targetBean();
        }
        for (String memberName : proxyMemberNames(proxyDefinition)) {
            proxyValue.getMember(SCOPED_PROXY_REGISTER_MEMBER_METHOD).execute(memberName);
        }

        Map<String, List<RuntimeProxyDefinition.InterceptedMethod<T>>> interceptedMethodsByName = new LinkedHashMap<>();
        for (RuntimeProxyDefinition.InterceptedMethod<T> interceptedMethod : proxyDefinition.interceptedMethods()) {
            interceptedMethodsByName.computeIfAbsent(
                interceptedMethod.executableMethod().getMethodName(),
                ignored -> new ArrayList<>()
            ).add(interceptedMethod);
        }
        for (Map.Entry<String, List<RuntimeProxyDefinition.InterceptedMethod<T>>> entry : interceptedMethodsByName.entrySet()) {
            String methodName = entry.getKey();
            List<RuntimeProxyDefinition.InterceptedMethod<T>> interceptedMethods = entry.getValue();
            Value originalFunction = GraalPyRuntimeUtil.getRawClassMember(pythonClass, methodName);
            ProxyExecutable proxiedFunction;
            if (isSyntheticPropertySetter(methodName, interceptedMethods, originalFunction)) {
                String propertyName = NameUtils.getPropertyNameForSetter(methodName);
                proxiedFunction = createProxiedPropertySetter(
                    proxyDefinition::targetBean,
                    propertyName,
                    methodSelector(methodName, interceptedMethods)
                );
                // JavaBean setters generated for Python attributes call Value.putMember(), which
                // reaches Python __setattr__ on runtime proxies. Register the same chain by
                // property name so Java and Python-style assignment both see property advice.
                proxyValue.getMember(SCOPED_PROXY_SETTER_OVERRIDE_METHOD).execute(propertyName, proxiedFunction);
            } else {
                proxiedFunction = createProxiedFunction(
                    false,
                    true,
                    pythonClass,
                    proxyDefinition::targetBean,
                    null,
                    null,
                    methodSelector(methodName, interceptedMethods),
                    originalFunction
                );
            }
            proxyValue.getMember(SCOPED_PROXY_OVERRIDE_METHOD).execute(methodName, proxiedFunction);
        }
        T proxy = box(type, proxyValue);
        if (proxy == null) {
            throw new IllegalStateException("Python proxy target cannot be null");
        }
        return proxy;
    }

    private static <T> boolean isSyntheticPropertySetter(
        String methodName,
        List<RuntimeProxyDefinition.InterceptedMethod<T>> interceptedMethods,
        @Nullable Value originalFunction
    ) {
        return originalFunction == null
            && NameUtils.isSetterName(methodName)
            && interceptedMethods.stream().anyMatch(method -> method.executableMethod().getArguments().length == 1);
    }

    private boolean hasAroundConstructAdvice(RuntimeProxyDefinition<?> proxyDefinition) {
        return proxyDefinition.proxyBeanDefinition().getAnnotationMetadata().hasStereotype(AroundConstruct.class)
            || proxyDefinition.proxyBeanDefinition().getConstructor().getAnnotationMetadata().hasStereotype(AroundConstruct.class);
    }

    private Set<String> proxyMemberNames(RuntimeProxyDefinition<?> proxyDefinition) {
        Set<String> memberNames = new LinkedHashSet<>();
        for (Argument<?> argument : proxyDefinition.constructorArguments()) {
            addProxyMemberName(memberNames, argument.getName());
        }
        for (Argument<?> argument : proxyDefinition.proxyBeanDefinition().getConstructor().getArguments()) {
            addProxyMemberName(memberNames, argument.getName());
        }
        for (FieldInjectionPoint<?, ?> fieldInjectionPoint : proxyDefinition.proxyBeanDefinition().getInjectedFields()) {
            addProxyMemberName(memberNames, fieldInjectionPoint.getName());
        }
        return memberNames;
    }

    private void addProxyMemberName(Set<String> memberNames, String memberName) {
        if (memberName != null && !memberName.isBlank() && !memberName.startsWith("_")) {
            memberNames.add(memberName);
        }
    }

    private Class<?> resolvePythonBeanType(RuntimeProxyDefinition<?> proxyDefinition) {
        for (RuntimeProxyDefinition.InterceptedMethod<?> interceptedMethod : proxyDefinition.interceptedMethods()) {
            Optional<Class> adaptedBean = interceptedMethod.executableMethod().classValue(Adapter.class, ADAPTED_BEAN);
            if (adaptedBean.isPresent()) {
                return adaptedBean.get();
            }
        }
        return proxyDefinition.proxyBeanDefinition().getBeanType();
    }

    private <T> ProxyExecutable createProxiedFunction(
        boolean isIntroduction,
        boolean bindOriginalFunction,
        Value owner,
        @Nullable Supplier<T> targetBeanSupplier,
        @Nullable T targetBean,
        @Nullable AtomicReference<Object> targetBeanRef,
        MethodSelector<T> methodSelector,
        @Nullable Value originalFunction
    ) {
        return args -> {
            RuntimeProxyDefinition.InterceptedMethod<T> interceptedMethod = methodSelector.find(args);
            ExecutableMethod<T, ?> executableMethod = interceptedMethod.executableMethod();
            Interceptor<T, ?>[] interceptors = interceptedMethod.interceptors();
            if (isIntroduction && executableMethod.hasStereotype(Adapter.class) && interceptors.length > 1) {
                // Core adapter introduction resolution returns method-level around interceptors plus
                // the adapter introduction. Python target methods are proxied separately, so keeping
                // those around interceptors here would apply the same Python method advice twice.
                interceptors = Arrays.copyOfRange(interceptors, interceptors.length - 1, interceptors.length);
            }
            Object[] javaArgs = fromPolyglotArray(args, executableMethod.getArguments());
            @SuppressWarnings("unchecked")
            T tb;
            if (targetBeanSupplier != null) {
                tb = targetBeanSupplier.get();
            } else if (targetBean != null) {
                tb = targetBean;
            } else if (targetBeanRef != null) {
                tb = (T) targetBeanRef.get();
            } else {
                tb = null;
            }
            if (tb == null) {
                throw new IllegalStateException("Target bean has not been initialized yet");
            }
            Interceptor<T, ?>[] finalInterceptors;
            if (isIntroduction && executableMethod.isAbstract()) {
                finalInterceptors = interceptors;
            } else {
                if (originalFunction == null) {
                    throw new IllegalStateException("No original function found for method: " + executableMethod);
                }
                finalInterceptors = Arrays.copyOf(interceptors, interceptors.length + 1, Interceptor[].class);
                finalInterceptors[finalInterceptors.length - 1] = invocationContext -> {
                    Value executable = bindOriginalFunction
                        ? GraalPyRuntimeUtil.bindPythonDescriptor(originalFunction, tb, owner)
                        : originalFunction;
                    Value result = executable.execute(
                        toPolyglotArray(invocationContext.getParameterValues(), executable.getContext())
                    );
                    if (executableMethod.getReturnType().getType() == void.class) {
                        return null;
                    }
                    return box(executableMethod.getReturnType().asArgument(), result);
                };
            }
            Object result = new MethodInterceptorChain(finalInterceptors, tb, executableMethod, javaArgs).proceed();
            return unbox(owner.getContext(), result);
        };
    }

    private <T> ProxyExecutable createProxiedPropertySetter(
        Supplier<T> targetBeanSupplier,
        String propertyName,
        MethodSelector<T> methodSelector
    ) {
        return args -> {
            RuntimeProxyDefinition.InterceptedMethod<T> interceptedMethod = methodSelector.find(args);
            ExecutableMethod<T, ?> executableMethod = interceptedMethod.executableMethod();
            Object[] javaArgs = fromPolyglotArray(args, executableMethod.getArguments());
            T targetBean = targetBeanSupplier.get();
            if (targetBean == null) {
                throw new IllegalStateException("Target bean has not been initialized yet");
            }
            Interceptor<T, ?>[] interceptors = interceptedMethod.interceptors();
            Interceptor<T, ?>[] finalInterceptors = Arrays.copyOf(interceptors, interceptors.length + 1, Interceptor[].class);
            finalInterceptors[finalInterceptors.length - 1] = invocationContext -> {
                Value targetValue = asValue(targetBean);
                Object value = invocationContext.getParameterValues()[0];
                targetValue.putMember(propertyName, GraalPyRuntimeUtil.coerceToContext(value, targetValue.getContext()));
                return null;
            };
            Object result = new MethodInterceptorChain(finalInterceptors, targetBean, executableMethod, javaArgs).proceed();
            return unbox(asValue(targetBean).getContext(), result);
        };
    }

    private Value createScopedProxyValue(Value pythonClass, Supplier<Value> targetSupplier) {
        Value factory = scopedProxyFactory(pythonClass.getContext());
        return factory.execute(pythonClass, (ProxyExecutable) args -> targetSupplier.get());
    }

    private Value scopedProxyFactory(Context context) {
        Value bindings = context.getBindings(PYTHON);
        Value factory = bindings.getMember(SCOPED_PROXY_FACTORY);
        if (factory == null || GraalPyRuntimeUtil.isNone(factory)) {
            context.eval(
                PYTHON,
                """
                def __micronaut_create_scoped_proxy(cls, target_supplier):
                    class _MicronautScopedProxy(cls):
                        def __init__(self, supplier):
                            object.__setattr__(self, "_micronaut_target_supplier", supplier)
                            object.__setattr__(self, "_micronaut_overrides", {})
                            object.__setattr__(self, "_micronaut_setter_overrides", {})

                        def _micronaut_target(self):
                            target = object.__getattribute__(self, "_micronaut_target_supplier")()
                            object.__getattribute__(self, "_micronaut_sync_target_attributes")(target)
                            return target

                        def _micronaut_put_override(self, name, value):
                            object.__getattribute__(self, "_micronaut_overrides")[name] = value

                        def _micronaut_put_setter_override(self, name, value):
                            object.__getattribute__(self, "_micronaut_setter_overrides")[name] = value

                        def _micronaut_register_member(self, name):
                            if isinstance(name, str) and not name.startswith("_"):
                                object.__setattr__(self, name, None)

                        def _micronaut_sync_target_attributes(self, target):
                            try:
                                attributes = getattr(target, "__dict__", {})
                                names = attributes.keys()
                            except Exception:
                                return
                            for name in names:
                                object.__getattribute__(self, "_micronaut_register_member")(name)

                        def __getattribute__(self, name):
                            if name in ("_micronaut_target_supplier", "_micronaut_overrides", "_micronaut_setter_overrides", "_micronaut_target", "_micronaut_put_override", "_micronaut_put_setter_override", "_micronaut_register_member", "_micronaut_sync_target_attributes"):
                                return object.__getattribute__(self, name)
                            overrides = object.__getattribute__(self, "_micronaut_overrides")
                            if name in overrides:
                                return overrides[name]
                            target = object.__getattribute__(self, "_micronaut_target")()
                            return getattr(target, name)

                        def __setattr__(self, name, value):
                            setter_overrides = object.__getattribute__(self, "_micronaut_setter_overrides")
                            if name in setter_overrides:
                                # Attribute-backed Python beans expose PropertyElement setters, not
                                # Python methods. Route assignments through the precomputed setter
                                # chain so around advice can mutate parameters before the target write.
                                setter_overrides[name](value)
                            else:
                                target = object.__getattribute__(self, "_micronaut_target")()
                                setattr(target, name, value)
                            object.__getattribute__(self, "_micronaut_register_member")(name)

                        def __repr__(self):
                            target = object.__getattribute__(self, "_micronaut_target")()
                            return repr(target)

                    return _MicronautScopedProxy(target_supplier)
                """
            );
            factory = bindings.getMember(SCOPED_PROXY_FACTORY);
        }
        return factory;
    }

    private Value rawInstanceFactory(Context context) {
        Value bindings = context.getBindings(PYTHON);
        Value factory = bindings.getMember(RAW_INSTANCE_FACTORY);
        if (factory == null || GraalPyRuntimeUtil.isNone(factory)) {
            context.eval(
                PYTHON,
                """
                def __micronaut_create_raw_instance(cls):
                    return cls.__new__(cls)
                """
            );
            factory = bindings.getMember(RAW_INSTANCE_FACTORY);
        }
        return factory;
    }

    private Value asValue(Object bean) {
        if (bean instanceof ValueCoercible valueCoercible) {
            return valueCoercible.asPolyglotValue();
        }
        throw new IllegalStateException("Python proxy target must implement ValueCoercible: " + bean);
    }

    private static boolean argumentsMatch(Argument<?>[] arguments, Value[] args) {
        for (int i = 0; i < arguments.length; i++) {
            if (!ValueCoercible.matchesArgument(args[i], arguments[i].getType())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isMoreSpecific(ExecutableMethod<?, ?> candidate, ExecutableMethod<?, ?> current) {
        Argument<?>[] candidateArguments = candidate.getArguments();
        Argument<?>[] currentArguments = current.getArguments();
        boolean moreSpecific = false;
        for (int i = 0; i < candidateArguments.length; i++) {
            Class<?> candidateType = ReflectionUtils.getWrapperType(candidateArguments[i].getType());
            Class<?> currentType = ReflectionUtils.getWrapperType(currentArguments[i].getType());
            if (candidateType.equals(currentType)) {
                continue;
            }
            if (currentType.isAssignableFrom(candidateType)) {
                // When both overloads match a Python value equally, prefer the narrower generated
                // argument type. This keeps Pageable dispatch ahead of inherited Sort dispatch.
                moreSpecific = true;
            } else if (candidateType.isAssignableFrom(currentType)) {
                return false;
            }
        }
        return moreSpecific;
    }

    private static <T> MethodSelector<T> methodSelector(
        String methodName,
        List<RuntimeProxyDefinition.InterceptedMethod<T>> methods
    ) {
        Set<Integer> arities = new HashSet<>();
        Set<Integer> overloadedArities = new HashSet<>();
        for (RuntimeProxyDefinition.InterceptedMethod<T> method : methods) {
            int arity = method.executableMethod().getArguments().length;
            if (!arities.add(arity)) {
                overloadedArities.add(arity);
            }
        }
        if (overloadedArities.isEmpty()) {
            return new SimpleMethodSelector<>(methodName, methods);
        }

        Map<Integer, List<RuntimeProxyDefinition.InterceptedMethod<T>>> overloadedMethodsByArity = new LinkedHashMap<>();
        for (RuntimeProxyDefinition.InterceptedMethod<T> method : methods) {
            int arity = method.executableMethod().getArguments().length;
            if (overloadedArities.contains(arity)) {
                overloadedMethodsByArity.computeIfAbsent(arity, ignored -> new ArrayList<>()).add(method);
            }
        }
        overloadedMethodsByArity.replaceAll((ignored, arityMethods) -> List.copyOf(arityMethods));
        return new OverloadedMethodSelector<>(methodName, methods, overloadedMethodsByArity);
    }

    @Nullable
    private Object unbox(Context context, @Nullable Object result) {
        return switch (result) {
            case null -> null;
            case Value value -> value;
            case ValueCoercible valueCoercible -> valueCoercible;
            case List<?> list -> {
                List<Object> newList = new ArrayList<>(list.size());
                for (Object o : list) {
                    newList.add(unbox(context, o));
                }
                yield newList;
            }
            case Set<?> set -> {
                Set<Object> newSet = new LinkedHashSet<>(set.size());
                for (Object o : set) {
                    newSet.add(unbox(context, o));
                }
                yield newSet;
            }
            case Map<?, ?> map -> {
                Map<Object, Object> newMap = new java.util.HashMap<>(map.size());
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    newMap.put(unbox(context, entry.getKey()), unbox(context, entry.getValue()));
                }
                yield newMap;
            }
            case Object[] array -> {
                Object[] newArray = new Object[array.length];
                for (int i = 0; i < array.length; i++) {
                    newArray[i] = unbox(context, array[i]);
                }
                yield newArray;
            }
            case Collection<?> collection -> {
                List<Object> newList = new ArrayList<>(collection.size());
                for (Object o : collection) {
                    newList.add(unbox(context, o));
                }
                yield newList;
            }
            case Stream<?> stream -> stream.map(value -> unbox(context, value));
            case Optional<?> optional -> optional.map(value -> unbox(context, value));
            case CompletionStage<?> completionStage -> completionStage;
            case Publisher<?> publisher -> Publishers.map(publisher, value -> unbox(context, value));
            default -> result;
        };
    }

    private <T> @Nullable T box(Class<T> type, Value value) {
        if (value.isNull()) {
            return null;
        }
        if (value.isHostObject()) {
            Object hostObject = value.asHostObject();
            if (hostObject == null) {
                return null;
            }
            if (type.isInstance(hostObject)) {
                return type.cast(hostObject);
            }
        }
        if (type.isPrimitive()) {
            return value.as(type);
        }
        if (CompletionStage.class.isAssignableFrom(type)) {
            return type.cast(PythonAsyncioRuntime.toCompletionStage(value));
        }
        Object hostObject = GraalPyRuntimeUtil.unwrapHostObject(value, type);
        if (hostObject != null) {
            return type.cast(hostObject);
        }
        T mappedValue = mapTargetType(type, value);
        if (mappedValue != null) {
            return mappedValue;
        }
        try {
            return type.getDeclaredConstructor(Value.class).newInstance(value);
        } catch (Exception e) {
            return value.as(type);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> @Nullable T mapTargetType(Class<T> type, Value value) {
        for (TargetTypeMapping<?> targetTypeMapping : targetTypeMappings) {
            if (targetTypeMapping.targetType().equals(type)) {
                return (T) targetTypeMapping.convert(value);
            }
        }
        return null;
    }

    @Nullable
    private Object box(Argument<?> argument, Value value) {
        if (value.isNull()) {
            return null;
        }
        Class<?> type = argument.getType();
        if (Iterable.class.isAssignableFrom(type)) {
            Argument<?> elementType = argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            if (elementType == Argument.OBJECT_ARGUMENT) {
                return value.as(type);
            }
            List<Object> values = new ArrayList<>();
            if (value.hasArrayElements()) {
                long size = value.getArraySize();
                for (long i = 0; i < size; i++) {
                    values.add(box(elementType, value.getArrayElement(i)));
                }
            } else if (value.hasIterator()) {
                Value iterator = value.getIterator();
                while (iterator.hasIteratorNextElement()) {
                    values.add(box(elementType, iterator.getIteratorNextElement()));
                }
            } else {
                Iterable<?> iterable = value.as(Iterable.class);
                for (Object element : iterable) {
                    values.add(box(elementType, value.getContext().asValue(element)));
                }
            }
            if (Set.class.isAssignableFrom(type)) {
                return new LinkedHashSet<>(values);
            }
            return values;
        }
        if (type.isArray()) {
            Class<?> componentType = type.getComponentType();
            long size = value.getArraySize();
            Object array = Array.newInstance(componentType, (int) size);
            Argument<?> componentArgument = argument.getFirstTypeVariable().orElseGet(() -> Argument.of(componentType));
            for (int i = 0; i < size; i++) {
                Array.set(array, i, box(componentArgument, value.getArrayElement(i)));
            }
            return array;
        }
        return box(type, value);
    }

    private void fillAllAbstractMethods(Value pythonClass) {
        Context context = pythonClass.getContext();
        if (pythonClass.hasMember("__abstractmethods__")) {
            Value abstractMethodsValue = pythonClass.getMember("__abstractmethods__");
            if (abstractMethodsValue != null && abstractMethodsValue.hasIterator()) {
                Value iterator = abstractMethodsValue.getIterator();
                while (iterator.hasIteratorNextElement()) {
                    Value next = iterator.getIteratorNextElement();
                    if (next != null && next.isString()) {
                        String methodName = next.asString();
                        Value existingMember = GraalPyRuntimeUtil.getRawClassMember(pythonClass, methodName);
                        if (existingMember == null || !existingMember.canExecute()) {
                            pythonClass.putMember(methodName, (ProxyExecutable) args -> null);
                        }
                    }
                }
            }
            Value emptyAbstractMethods = context.eval("python", "frozenset()");
            pythonClass.putMember("__abstractmethods__", emptyAbstractMethods);
            Value isProtocol = pythonClass.getMember("_is_protocol");
            if (isProtocol != null && isProtocol.isBoolean() && isProtocol.asBoolean()) {
                pythonClass.putMember("_is_protocol", false);
            }
        }
    }

    private Object[] toPolyglotArray(Object[] in, Context context) {
        Object[] out = new Object[in.length];
        for (int i = 0; i < in.length; i++) {
            Object parameterValue = in[i];
            out[i] = context.asValue(parameterValue);
        }
        return out;
    }

    private Object[] fromPolyglotArray(Value[] in, Argument<?>[] arguments) {
        Object[] out = new Object[in.length];
        for (int i = 0; i < in.length; i++) {
            Value arg = in[i];
            Argument<?> argType = arguments[i];
            out[i] = box(argType, arg);
        }
        return out;
    }

    private interface MethodSelector<T> {
        RuntimeProxyDefinition.InterceptedMethod<T> find(Value[] args);
    }

    private static final class SimpleMethodSelector<T> implements MethodSelector<T> {
        private final String methodName;
        private final List<RuntimeProxyDefinition.InterceptedMethod<T>> methods;

        SimpleMethodSelector(String methodName, List<RuntimeProxyDefinition.InterceptedMethod<T>> methods) {
            this.methodName = methodName;
            this.methods = methods;
        }

        @Override
        public RuntimeProxyDefinition.InterceptedMethod<T> find(Value[] args) {
            for (RuntimeProxyDefinition.InterceptedMethod<T> method : methods) {
                if (method.executableMethod().getArguments().length == args.length) {
                    return method;
                }
            }
            throw new IllegalArgumentException("No overload found for method " + methodName + " with " + args.length + " arguments");
        }
    }

    private static final class OverloadedMethodSelector<T> implements MethodSelector<T> {
        private final String methodName;
        private final List<RuntimeProxyDefinition.InterceptedMethod<T>> methods;
        private final Map<Integer, List<RuntimeProxyDefinition.InterceptedMethod<T>>> overloadedMethodsByArity;

        OverloadedMethodSelector(
            String methodName,
            List<RuntimeProxyDefinition.InterceptedMethod<T>> methods,
            Map<Integer, List<RuntimeProxyDefinition.InterceptedMethod<T>>> overloadedMethodsByArity
        ) {
            this.methodName = methodName;
            this.methods = methods;
            this.overloadedMethodsByArity = overloadedMethodsByArity;
        }

        @Override
        public RuntimeProxyDefinition.InterceptedMethod<T> find(Value[] args) {
            int arity = args.length;
            List<RuntimeProxyDefinition.InterceptedMethod<T>> arityMatches = overloadedMethodsByArity.get(arity);
            if (arityMatches == null) {
                for (RuntimeProxyDefinition.InterceptedMethod<T> method : methods) {
                    if (method.executableMethod().getArguments().length == arity) {
                        return method;
                    }
                }
                throw new IllegalArgumentException("No overload found for method " + methodName + " with " + arity + " arguments");
            }

            // Only duplicate-arity overload groups need argument-aware selection. Other method
            // groups use SimpleMethodSelector and keep the old declaration-order arity scan.
            RuntimeProxyDefinition.InterceptedMethod<T> bestMatch = null;
            for (RuntimeProxyDefinition.InterceptedMethod<T> method : arityMatches) {
                Argument<?>[] arguments = method.executableMethod().getArguments();
                if (!argumentsMatch(arguments, args)) {
                    continue;
                }
                if (bestMatch == null) {
                    bestMatch = method;
                    continue;
                }
                if (isMoreSpecific(method.executableMethod(), bestMatch.executableMethod())) {
                    bestMatch = method;
                }
            }
            if (bestMatch != null) {
                return bestMatch;
            }
            return arityMatches.get(0);
        }
    }
}
