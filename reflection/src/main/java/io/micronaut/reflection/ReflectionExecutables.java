/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.reflection;

import io.micronaut.context.BeanContext;
import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.ExecutionHandle;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.MethodReference;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The adapters of a specification API that names an executable by its {@link Method} or {@link Constructor}:
 * the generated metadata is used when it describes the named executable, and the reflective metadata of this
 * package otherwise - the one reflective step an API defined on {@code java.lang.reflect} imposes. The reverse
 * lookups resolve an executable method back to the {@link Method} it describes.
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Experimental
public final class ReflectionExecutables {

    private ReflectionExecutables() {
    }

    /**
     * The executable method of the method named by the caller: the one of the bean definition when the
     * declaring type is a bean, else the one of the bean introspection, generated or reflective, else one
     * read from the method itself.
     *
     * @param locator      The locator of the executable methods of the beans
     * @param introspector The introspector
     * @param method       The method
     * @param <T>          The declaring type
     * @return The executable method
     */
    @SuppressWarnings("unchecked")
    public static <T> ExecutableMethod<T, Object> executableMethod(ExecutionHandleLocator locator,
                                                                   BeanIntrospector introspector,
                                                                   Method method) {
        Class<T> declaringType = (Class<T>) method.getDeclaringClass();
        Optional<ExecutableMethod<T, Object>> found = locator.findExecutableMethod(
            declaringType, method.getName(), method.getParameterTypes());
        // the locator answers for any bean of the type, a sub type overriding the method included: only the bean
        // declared by the type of the method is the method named, and the locator falls back to a match on the
        // name alone, which can be another method of the same name
        if (found.isPresent() && found.get().getDeclaringType() == declaringType && describes(found.get(), method)) {
            return found.get();
        }
        BeanIntrospection<T> introspection = introspector.findIntrospection(declaringType).orElse(null);
        if (introspection != null) {
            for (BeanMethod<T, Object> beanMethod : introspection.getBeanMethods()) {
                if (beanMethod.getName().equals(method.getName())
                    && Arrays.equals(Argument.toClassArray(beanMethod.getArguments()), method.getParameterTypes())) {
                    return new IntrospectedExecutableMethod<>(declaringType, beanMethod, method);
                }
            }
        }
        return new ReflectionExecutableMethod<>(declaringType, method);
    }

    /**
     * Whether an executable method describes the method the caller named. The name and the arity have to be the
     * ones of the method, and every argument type has to be one the parameter of the method holds: the arguments
     * of a generated method of a generic super type carry the types the bean resolves the variables to, where
     * the method itself declares the erasure - {@code save(T)} of a bean extending {@code Base<Book>} carries
     * {@code Book} where {@code Base} declares {@code save(Object)}.
     *
     * <p>An arity match of assignable types can still be another overload - {@code save(List<T>)} carries a
     * {@code List}, which {@code save(Object)} holds too - so the declaration the argument types resolve to has
     * to be the method named.</p>
     */
    private static boolean describes(ExecutableMethod<?, ?> candidate, Method method) {
        Class<?>[] argumentTypes = candidate.getArgumentTypes();
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (!candidate.getMethodName().equals(method.getName()) || argumentTypes.length != parameterTypes.length) {
            return false;
        }
        if (!assignable(parameterTypes, argumentTypes)) {
            return false;
        }
        return method.equals(findMethod(method.getDeclaringClass(), candidate.getMethodName(), argumentTypes).orElse(null));
    }

    /**
     * The executable method of the method named by the caller, resolved against the beans of a context and the
     * introspections of its class loader.
     *
     * @param context The bean context
     * @param method  The method
     * @param <T>     The declaring type
     * @return The executable method
     * @see #executableMethod(ExecutionHandleLocator, BeanIntrospector, Method)
     */
    public static <T> ExecutableMethod<T, Object> executableMethod(BeanContext context, Method method) {
        return executableMethod(context, BeanIntrospector.forClassLoader(context.getClassLoader()), method);
    }

    /**
     * An execution handle invoking the method named by the caller on a bean, with the best metadata available
     * for the method.
     *
     * @param locator      The locator of the executable methods of the beans
     * @param introspector The introspector
     * @param bean         The bean
     * @param method       The method
     * @param <T>          The declaring type
     * @return The execution handle
     * @see #executableMethod(ExecutionHandleLocator, BeanIntrospector, Method)
     */
    public static <T> MethodExecutionHandle<T, Object> executionHandle(ExecutionHandleLocator locator,
                                                                       BeanIntrospector introspector,
                                                                       T bean,
                                                                       Method method) {
        return ExecutionHandle.of(bean, executableMethod(locator, introspector, method));
    }

    /**
     * An execution handle invoking a method on a bean reflectively.
     *
     * @param bean   The bean
     * @param method The method
     * @param <T>    The declaring type
     * @return The execution handle
     */
    @SuppressWarnings("unchecked")
    public static <T> MethodExecutionHandle<T, Object> executionHandle(T bean, Method method) {
        return ExecutionHandle.of(bean, new ReflectionExecutableMethod<>((Class<T>) method.getDeclaringClass(), method));
    }

    /**
     * The arguments of the constructor named by the caller. An introspection describes one constructor;
     * when the caller names another one of the type, its arguments are read from the constructor itself.
     *
     * @param introspection The introspection of the declaring type
     * @param constructor   The constructor
     * @return The arguments
     */
    public static Argument<?>[] constructorArguments(BeanIntrospection<?> introspection, Constructor<?> constructor) {
        Argument<?>[] arguments = introspection.getConstructorArguments();
        if (Arrays.equals(Argument.toClassArray(arguments), constructor.getParameterTypes())) {
            return arguments;
        }
        return ReflectionArguments.argumentsOf(constructor);
    }

    /**
     * The constructor named by the caller, with its arguments and its annotation metadata: the one the
     * introspection describes when it is that constructor, another one a {@link ReflectiveIntrospection}
     * knows, else one read from the constructor itself.
     *
     * @param introspection The introspection of the declaring type, can be {@code null}
     * @param constructor   The constructor
     * @param <T>           The declaring type
     * @return The bean constructor
     */
    public static <T> BeanConstructor<T> beanConstructor(@Nullable BeanIntrospection<T> introspection, Constructor<T> constructor) {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        if (introspection != null) {
            List<BeanConstructor<T>> known = introspection instanceof ReflectiveIntrospection<T> reflective
                ? reflective.getConstructors()
                : List.of(introspection.getConstructor());
            for (BeanConstructor<T> candidate : known) {
                if (Arrays.equals(Argument.toClassArray(candidate.getArguments()), parameterTypes)) {
                    return candidate;
                }
            }
        }
        return new ReflectionBeanConstructor<>(constructor);
    }

    /**
     * The {@link Method} an executable method describes. The method the reference reports is returned when
     * it resolves; otherwise the method is found by name and parameter types in the hierarchy of the declaring
     * type, tolerating the erasure: the arguments of a method inherited from a generic super type report the
     * resolved types, where the method declares the erased ones.
     *
     * @param method The executable method, or any method reference
     * @return The method
     * @throws NoSuchMethodError When no method of the declaring type matches
     */
    public static Method targetMethod(MethodReference<?, ?> method) {
        try {
            return method.getTargetMethod();
        } catch (NoSuchMethodError | UnsupportedOperationException e) {
            Class<?> declaringType = method.getDeclaringType();
            Class<?>[] argumentTypes = method.getArgumentTypes();
            return findMethod(declaringType, method.getMethodName(), argumentTypes)
                .orElseThrow(() -> new NoSuchMethodError("No method " + method.getMethodName() + Arrays.toString(argumentTypes)
                    + " on type " + declaringType.getName()));
        }
    }

    /**
     * Finds the method a type reads under a name, whether the type declares it or inherits it from a super
     * class or an interface, of any visibility, static or not. A method whose parameters are exactly the given
     * types wins; failing that, a method whose parameters are assignable from them - the erasure of a generic
     * declaration - with the same arity. Where more than one declaration applies, the most specific one wins,
     * so the answer does not depend on the order in which a type reports its methods.
     *
     * @param type           The type
     * @param name           The method name
     * @param parameterTypes The parameter types
     * @return The method, empty when the type has none
     */
    public static Optional<Method> findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        List<Method> candidates = new ArrayList<>();
        collectMethods(type, name, parameterTypes.length, new HashSet<>(), candidates);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        // the order of `getDeclaredMethods` is not specified: ordering the candidates by their descriptor makes
        // the selection below the same on every run
        candidates.sort(Comparator.comparing(ReflectionExecutables::descriptor));
        List<Method> exact = candidates.stream()
            .filter(candidate -> Arrays.equals(candidate.getParameterTypes(), parameterTypes))
            .toList();
        List<Method> applicable = exact.isEmpty()
            ? candidates.stream().filter(candidate -> assignable(candidate.getParameterTypes(), parameterTypes)).toList()
            : exact;
        return applicable.stream().min(ReflectionExecutables::compareSpecificity);
    }

    /**
     * Every method of the name and the arity the hierarchy declares, the super classes and the interfaces of
     * each level included, every type visited once. A bridge is a copy of the declaration it forwards to, so it
     * is never a candidate.
     */
    private static void collectMethods(Class<?> type, String name, int parameterCount, Set<Class<?>> visited, List<Method> candidates) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (!visited.add(current)) {
                continue;
            }
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterCount && !method.isSynthetic()) {
                    candidates.add(method);
                }
            }
            for (Class<?> interfaceType : current.getInterfaces()) {
                collectMethods(interfaceType, name, parameterCount, visited, candidates);
            }
        }
    }

    /**
     * Orders the declarations that apply to one call, the most specific first: the parameter types decide, then
     * a type variable is less specific than a type, then the most derived declaring type wins, and finally the
     * descriptor - a stable tiebreak between the declarations of unrelated types.
     */
    private static int compareSpecificity(Method left, Method right) {
        Class<?>[] leftTypes = left.getParameterTypes();
        Class<?>[] rightTypes = right.getParameterTypes();
        int specificity = 0;
        for (int i = 0; i < leftTypes.length; i++) {
            if (leftTypes[i] == rightTypes[i]) {
                continue;
            }
            if (rightTypes[i].isAssignableFrom(leftTypes[i])) {
                specificity--;
            } else if (leftTypes[i].isAssignableFrom(rightTypes[i])) {
                specificity++;
            }
        }
        if (specificity != 0) {
            return specificity < 0 ? -1 : 1;
        }
        int variables = variableParameters(left) - variableParameters(right);
        if (variables != 0) {
            return variables;
        }
        Class<?> leftType = left.getDeclaringClass();
        Class<?> rightType = right.getDeclaringClass();
        if (leftType != rightType) {
            if (rightType.isAssignableFrom(leftType)) {
                return -1;
            }
            if (leftType.isAssignableFrom(rightType)) {
                return 1;
            }
        }
        return descriptor(left).compareTo(descriptor(right));
    }

    private static int variableParameters(Method method) {
        int variables = 0;
        for (Type parameterType : method.getGenericParameterTypes()) {
            if (parameterType instanceof TypeVariable<?>) {
                variables++;
            }
        }
        return variables;
    }

    private static String descriptor(Method method) {
        return method.getDeclaringClass().getName() + Arrays.toString(method.getParameterTypes());
    }

    private static boolean assignable(Class<?>[] declared, Class<?>[] resolved) {
        if (declared.length != resolved.length) {
            return false;
        }
        for (int i = 0; i < declared.length; i++) {
            if (!declared[i].isAssignableFrom(resolved[i])) {
                return false;
            }
        }
        return true;
    }
}
