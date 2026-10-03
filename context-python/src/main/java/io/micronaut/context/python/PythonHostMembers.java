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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Members of Java objects that GraalPy host interop does not expose: the public methods a public
 * class inherits from a non-public superclass.
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

    private PythonHostMembers() {
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
}
