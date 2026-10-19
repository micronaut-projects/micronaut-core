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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Members of Java objects that GraalPy host interop does not expose: snake_case instance methods,
 * read-only getter properties, and the public methods a public class inherits from a non-public
 * superclass.
 * <p>
 * GraalPy only exposes a method whose declaring class is public, so the methods
 * {@code PullSubscribeOptions.Builder} inherits from the protected generic
 * {@code SubscribeOptions.Builder} are invisible ({@code foreign object has no attribute 'stream'})
 * although Java code calls them through the public subclass. The Python class registered for
 * {@code java.lang.Object} in {@link GraalPyContextFactory} asks {@link #inheritedMember} for such a
 * name after the regular lookup failed; the method is then invoked through a method handle looked
 * up on the public subclass, which is how compiled Java code reaches it.
 * </p>
 *
 * @author Micronaut Team
 * @since 5.2.0
 */
@Internal
public final class PythonHostMembers {

    // a ClassValue rather than a map keyed by class: the entry goes away with the class loader
    private static final ClassValue<Map<String, List<MethodHandle>>> INHERITED_METHODS = new ClassValue<>() {
        @Override
        protected Map<String, List<MethodHandle>> computeValue(Class<?> type) {
            return inheritedMethods(type);
        }
    };

    private static final ClassValue<Map<String, Alias>> INSTANCE_ALIASES = new ClassValue<>() {
        @Override
        protected Map<String, Alias> computeValue(Class<?> type) {
            return instanceAliases(type);
        }
    };

    private PythonHostMembers() {
    }

    /**
     * The original name of an unambiguous snake_case alias of an instance method. The caller reads
     * that member through host interop, retaining its overload selection and conversions.
     *
     * @param receiver The Java instance
     * @param name The Python member name
     * @return The Java method name, or {@code null}
     */
    @UsedByGeneratedCode
    public static @Nullable String methodAlias(Object receiver, String name) {
        Alias alias = INSTANCE_ALIASES.get(receiver.getClass()).get(name);
        return alias != null && !alias.property() ? alias.name() : null;
    }

    /**
     * The original name of an unambiguous, public zero-argument instance getter exposed as a
     * read-only Python property.
     *
     * @param receiver The Java instance
     * @param name The Python property name
     * @return The getter name, or {@code null}
     */
    @UsedByGeneratedCode
    public static @Nullable String propertyGetter(Object receiver, String name) {
        Alias alias = INSTANCE_ALIASES.get(receiver.getClass()).get(name);
        return alias != null && alias.property() ? alias.name() : null;
    }

    private static Map<String, Alias> instanceAliases(Class<?> type) {
        Map<String, Alias> aliases = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        Set<String> staticNames = new HashSet<>();
        Method[] methods = type.getMethods();
        for (Method method : methods) {
            if (Modifier.isStatic(method.getModifiers())) {
                staticNames.add(method.getName());
            }
        }
        for (Method method : methods) {
            if (Modifier.isStatic(method.getModifiers()) || method.isBridge() || method.isSynthetic()) {
                continue;
            }
            String name = method.getName();
            String snakeName = snakeCase(name);
            // A bound host member may include static overloads: leave mixed groups to their original name.
            if (!snakeName.equals(name) && !staticNames.contains(name)) {
                addAlias(aliases, ambiguous, snakeName, new Alias(name, false));
            }
            String property = getterProperty(method);
            if (property != null) {
                addAlias(aliases, ambiguous, property, new Alias(name, true));
            }
        }
        ambiguous.forEach(aliases::remove);
        return Map.copyOf(aliases);
    }

    private static @Nullable String getterProperty(Method method) {
        String name = method.getName();
        if (method.getParameterCount() != 0 || method.getReturnType() == void.class || name.equals("getClass")) {
            return null;
        }
        int prefix = 0;
        if (name.startsWith("get")) {
            prefix = 3;
        } else if (name.startsWith("is") && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
            prefix = 2;
        }
        return prefix > 0 && name.length() > prefix && Character.isUpperCase(name.charAt(prefix))
            ? snakeCase(name.substring(prefix)) : null;
    }

    private static void addAlias(Map<String, Alias> aliases, Set<String> ambiguous, String name, Alias alias) {
        Alias previous = aliases.putIfAbsent(name, alias);
        if (previous != null && !previous.equals(alias)) {
            ambiguous.add(name);
        }
    }

    private static String snakeCase(String name) {
        return name.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
            .replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    /**
     * A callable invoking the public methods of the given name that the class of the receiver
     * inherits from a non-public superclass, or {@code null} when it has none.
     *
     * @param receiver The Java object
     * @param name The member name
     * @return The callable, or {@code null}
     */
    @UsedByGeneratedCode
    public static @Nullable Object inheritedMember(Object receiver, String name) {
        Map<String, List<MethodHandle>> methods = INHERITED_METHODS.get(receiver.getClass());
        List<MethodHandle> handles = methods.get(name);
        if (handles == null) {
            return null;
        }
        return (ProxyExecutable) arguments -> invoke(receiver, name, handles, arguments);
    }

    private static Object invoke(Object receiver, String name, List<MethodHandle> handles, Value[] arguments) {
        MethodHandle selected = null;
        for (MethodHandle handle : handles) {
            MethodType type = handle.type();
            if (type.parameterCount() - 1 != arguments.length) {
                continue;
            }
            boolean matches = true;
            for (int i = 0; i < arguments.length && matches; i++) {
                matches = ValueCoercibles.matchesArgument(arguments[i], type.parameterType(i + 1))
                    || PythonConversion.unwrapHostObject(arguments[i], type.parameterType(i + 1)) != null;
            }
            if (matches) {
                selected = handle;
                break;
            }
        }
        if (selected == null) {
            selected = handles.stream()
                .filter(handle -> handle.type().parameterCount() - 1 == arguments.length)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No method [" + name + "] of [" + receiver.getClass().getName()
                    + "] accepts " + arguments.length + " argument(s)"));
        }
        MethodType type = selected.type();
        Object[] javaArguments = new Object[arguments.length + 1];
        javaArguments[0] = receiver;
        for (int i = 0; i < arguments.length; i++) {
            javaArguments[i + 1] = PythonConversion.convertValue(arguments[i], type.parameterType(i + 1));
        }
        try {
            return selected.invokeWithArguments(javaArguments);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Invocation of [" + name + "] on [" + receiver.getClass().getName() + "] failed: " + e.getMessage(), e);
        }
    }

    /**
     * The public instance methods the nearest public class of a type inherits from non-public
     * superclasses, keyed by name, as method handles looked up on that public class.
     */
    private static Map<String, List<MethodHandle>> inheritedMethods(Class<?> type) {
        Map<String, List<MethodHandle>> methods = new ConcurrentHashMap<>();
        Class<?> publicType = type;
        while (publicType != null && !Modifier.isPublic(publicType.getModifiers())) {
            publicType = publicType.getSuperclass();
        }
        if (publicType == null || publicType == Object.class) {
            return methods;
        }
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        for (Method method : publicType.getMethods()) {
            Class<?> declaringClass = method.getDeclaringClass();
            if (Modifier.isStatic(method.getModifiers()) || Modifier.isPublic(declaringClass.getModifiers()) || method.isBridge()) {
                // GraalPy exposes the methods of public declaring classes itself
                continue;
            }
            try {
                MethodHandle handle = lookup.findVirtual(publicType, method.getName(), MethodType.methodType(method.getReturnType(), method.getParameterTypes()));
                methods.computeIfAbsent(method.getName(), name -> new ArrayList<>()).add(handle);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                // not reachable through the public class: GraalPy's own error stands
            }
        }
        return methods;
    }

    private record Alias(String name, boolean property) {
    }
}
