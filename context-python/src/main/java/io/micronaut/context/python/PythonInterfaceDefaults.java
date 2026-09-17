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
package io.micronaut.context.python;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.reflect.ClassUtils;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The default methods of the Java interfaces a Python class implements, made available to Python code.
 * <p>
 * The runtime form of a Python class does not extend the Java interfaces it implements (GraalPy cannot
 * subclass a host interface), so a Python instance is a plain Python object: injected into another Python
 * bean, looked up from the context in Python or created in Python, it has the methods of its class but not
 * the default methods of the interface, which only the generated Java stub inherits. The runtime code of
 * such a class is decorated with {@link #install}, which adds a Python method for every default method the
 * class, or a Python base class, does not define. The added method converts the Python instance to the Java
 * view the generated stub provides and invokes the default method on it, so the Java implementation runs
 * with a Java view of the Python object as {@code this}, its abstract calls reaching the Python methods
 * through the stub, while the Python object keeps its identity ({@code is}, {@code isinstance}, {@code ==}).
 * A class without a generated stub (one defined inside a function) has no such view: the polyglot proxy
 * GraalPy creates for the interface dispatches every member to the Python object first, which would reach
 * the installed method again, so the default method is run through a proxy of this class that invokes the
 * default implementation of that one method itself and delegates every other call to the polyglot proxy:
 * an abstract method or a default method the Python class overrides reaches the Python code, a default
 * method the class does not override comes back through its installed method.
 * <p>
 * A default method overridden in Python is not installed: the Python definition is what both the Python
 * object and, through the stub bridge, the Java view run.
 *
 * @since 5.2.3
 */
@Internal
@Experimental
public final class PythonInterfaceDefaults {

    private static final String INSTALL_HELPER = "__micronaut_install_java_interface_defaults";

    private PythonInterfaceDefaults() {
    }

    /**
     * Adds the default methods of the given Java interfaces (and of the interfaces they extend) to a Python
     * class, except those the class already defines or inherits from a Python base.
     *
     * @param pythonClass    The Python class
     * @param interfaceNames The names of the Java interfaces the class implements
     * @return The Python class
     */
    @UsedByGeneratedCode
    public static Value install(Value pythonClass, Value interfaceNames) {
        Map<String, List<Method>> methodsByName = new LinkedHashMap<>();
        int size = (int) interfaceNames.getArraySize();
        Class<?>[] interfaceTypes = new Class<?>[size];
        for (int i = 0; i < size; i++) {
            Class<?> interfaceType = loadInterface(interfaceNames.getArrayElement(i).asString());
            interfaceTypes[i] = interfaceType;
            for (Method method : interfaceType.getMethods()) {
                if (method.isDefault()) {
                    List<Method> overloads = methodsByName.computeIfAbsent(method.getName(), name -> new ArrayList<>(1));
                    if (overloads.stream().noneMatch(existing -> Arrays.equals(existing.getParameterTypes(), method.getParameterTypes()))) {
                        overloads.add(method);
                    }
                }
            }
        }
        if (methodsByName.isEmpty()) {
            return pythonClass;
        }
        DefaultMethod[] defaultMethods = methodsByName.entrySet().stream()
            .map(entry -> new DefaultMethod(entry.getKey(), orderBySpecificity(entry.getValue()), interfaceTypes))
            .toArray(DefaultMethod[]::new);
        Context context = pythonClass.getContext();
        PythonContextRuntime.helper(context, INSTALL_HELPER).executeVoid(pythonClass, defaultMethods);
        return pythonClass;
    }

    /**
     * Orders overloads so that the more specific parameter types are tried first: a conversion to {@code Object}
     * or a collection type accepts any Python value, so {@code m(Object)} must not win over {@code m(String)} for a
     * string argument. Overloads no parameter list subsumes (the order {@link Class#getMethods()} returns them in
     * is unspecified) fall back to the order of their signatures.
     */
    private static Method[] orderBySpecificity(List<Method> overloads) {
        List<Method> bySignature = new ArrayList<>(overloads);
        bySignature.sort(Comparator.comparing(Method::toGenericString));
        List<Method> ordered = new ArrayList<>(bySignature.size());
        for (Method method : bySignature) {
            int index = 0;
            while (index < ordered.size() && !isMoreSpecific(method, ordered.get(index))) {
                index++;
            }
            ordered.add(index, method);
        }
        return ordered.toArray(new Method[0]);
    }

    private static boolean isMoreSpecific(Method method, Method other) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Class<?>[] otherParameterTypes = other.getParameterTypes();
        if (parameterTypes.length != otherParameterTypes.length) {
            return parameterTypes.length < otherParameterTypes.length;
        }
        boolean narrower = false;
        for (int i = 0; i < parameterTypes.length; i++) {
            if (!otherParameterTypes[i].isAssignableFrom(parameterTypes[i])) {
                return false;
            }
            narrower |= parameterTypes[i] != otherParameterTypes[i];
        }
        return narrower;
    }

    private static Class<?> loadInterface(String name) {
        return PythonContextRuntime.withContextClassLoader(() -> {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = PythonInterfaceDefaults.class.getClassLoader();
            }
            return ClassUtils.forName(name, classLoader)
                .orElseThrow(() -> new IllegalStateException("Java interface [" + name + "] implemented by a Python class is not on the classpath"));
        });
    }

    /**
     * A default method (with its overloads) installed on a Python class; {@link #invoke} is what the
     * installed Python method calls.
     */
    @Internal
    public static final class DefaultMethod {

        private final String name;
        private final Method[] overloads;
        private final Class<?>[] interfaceTypes;

        DefaultMethod(String name, Method[] overloads, Class<?>[] interfaceTypes) {
            this.name = name;
            this.overloads = overloads;
            this.interfaceTypes = interfaceTypes;
        }

        /**
         * The Java method name, which is the name the method is installed under in Python.
         *
         * @return The name
         */
        public String name() {
            return name;
        }

        /**
         * Invokes the default method on the Java view of a Python instance.
         *
         * @param self      The Python instance
         * @param arguments The Python arguments
         * @return The result, coerced back to the Python context
         */
        public @Nullable Object invoke(Value self, Value arguments) {
            int argumentCount = (int) arguments.getArraySize();
            Value[] values = new Value[argumentCount];
            for (int i = 0; i < argumentCount; i++) {
                values[i] = arguments.getArrayElement(i);
            }
            Method method = null;
            Object[] converted = null;
            RuntimeException conversionFailure = null;
            for (Method candidate : overloads) {
                if (candidate.getParameterCount() != argumentCount) {
                    continue;
                }
                try {
                    converted = convertArguments(candidate, values);
                    method = candidate;
                    break;
                } catch (ClassCastException | IllegalArgumentException | IllegalStateException | UnsupportedOperationException | PolyglotException e) {
                    conversionFailure = e;
                }
            }
            if (method == null) {
                throw new IllegalArgumentException("No default method [" + name + "] of " + overloads[0].getDeclaringClass().getName()
                    + " accepts " + argumentCount + " argument(s)", conversionFailure);
            }
            Class<?> interfaceType = method.getDeclaringClass();
            Object target = PythonConversion.convertValue(self, interfaceType);
            if (target == null) {
                throw new IllegalStateException("Python instance cannot be converted to " + interfaceType.getName());
            }
            if (Proxy.isProxyClass(target.getClass())) {
                // no generated stub: the polyglot proxy would dispatch the default method to the installed
                // Python method again, so run this default implementation here and leave every other call,
                // an overridden or another default method included, to the proxy
                target = Proxy.newProxyInstance(proxyClassLoader(), interfaceTypes, new DefaultInvoker(self, method, target));
            }
            Object result;
            try {
                result = method.invoke(target, converted);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Default method [" + method + "] is not accessible", e);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException("Default method [" + method + "] threw " + cause, cause);
            }
            if (result == target) {
                // the default method returned this: the Python object it is a view of
                return self;
            }
            return PythonCoercion.coerceToContext(result, self.getContext());
        }

        /**
         * A class loader that sees all the interfaces of the class: the first application loader among theirs
         * (an application loader sees the JDK interfaces as well), or the bootstrap loader when every interface
         * is a JDK one.
         */
        private ClassLoader proxyClassLoader() {
            for (Class<?> interfaceType : interfaceTypes) {
                ClassLoader classLoader = interfaceType.getClassLoader();
                if (classLoader != null) {
                    return classLoader;
                }
            }
            return interfaceTypes[0].getClassLoader();
        }

        private static Object[] convertArguments(Method method, Value[] values) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            Object[] converted = new Object[values.length];
            for (int i = 0; i < values.length; i++) {
                converted[i] = PythonConversion.convertValue(values[i], parameterTypes[i]);
            }
            return converted;
        }
    }

    /**
     * The Java view of a Python instance without a generated stub, for one default method call: that method
     * runs its Java implementation, every other method is delegated to the polyglot proxy of the Python object,
     * so an abstract method, a default method the Python class overrides and one it does not (which comes
     * back through its installed method) all reach the Python object. A method of an interface the polyglot
     * proxy does not implement (a default casting {@code this} to another interface of the class) is invoked
     * on a polyglot proxy of that interface.
     */
    private static final class DefaultInvoker implements InvocationHandler {

        private final Value self;
        private final Method defaultMethod;
        private final Object polyglotProxy;

        DefaultInvoker(Value self, Method defaultMethod, Object polyglotProxy) {
            this.self = self;
            this.defaultMethod = defaultMethod;
            this.polyglotProxy = polyglotProxy;
        }

        @Override
        public @Nullable Object invoke(Object proxy, Method method, Object @Nullable [] args) throws Throwable {
            if (method.getName().equals(defaultMethod.getName()) && Arrays.equals(method.getParameterTypes(), defaultMethod.getParameterTypes())) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            Class<?> declaringType = method.getDeclaringClass();
            Object target = declaringType.isInstance(polyglotProxy) ? polyglotProxy : PythonConversion.convertValue(self, declaringType);
            if (target == null) {
                throw new IllegalStateException("Python instance cannot be converted to " + declaringType.getName());
            }
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
