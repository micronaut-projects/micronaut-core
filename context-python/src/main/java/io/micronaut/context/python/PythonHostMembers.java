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

import io.micronaut.aop.InterceptedProxy;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private static final String HAND_OVER = "__micronaut_hand_over";
    private static final String ATTACH_WRAPPERS = "__micronaut_attach_wrappers";
    private static final String PYTHON_LIST_OF = "__micronaut_python_list_of";

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

    /**
     * The value Python is handed for an element of a Java list it indexes, as
     * {@link #pythonElements(Value)} hands over the elements it iterates.
     * <p>
     * Python calls it with a {@link Value}, which reaches Java without the target type mappings an
     * {@code Object} parameter runs: those probe a proxy through its members, each a call back into
     * Python, and cost more than the attribute reads the hand-over saves.
     *
     * @param value The element
     * @return The Python object of a wrapper, or the value itself
     */
    @UsedByGeneratedCode
    public static Value pythonObject(Value value) {
        return handOverOne(value.isProxyObject() ? value.asProxyObject() : value);
    }

    /**
     * The elements of a Java collection as a new Python list of the context the caller runs in, in one
     * call for the collection a Python loop iterates rather than one for each attribute it reads: the
     * element that is a generated wrapper is handed over as its Python object, so that its attributes are
     * read as plain Python attributes, rather than through the proxy of the wrapper.
     * <p>
     * The object keeps a back-reference to the wrapper, and its class reports the attributes Python
     * writes on it ({@link #written(Value, Value, Value)}), so that the Java code that holds the wrapper
     * (the collection a loop updates and saves) reads what Python wrote. An AOP proxy is handed over as
     * it is, so that its advice still applies.
     *
     * @param collection The collection, a Java {@link Collection}
     * @return The Python list
     */
    @UsedByGeneratedCode
    public static Value pythonElements(Value collection) {
        return handOver(collection.asHostObject() instanceof Collection<?> c ? c.toArray() : new Object[0]);
    }

    /**
     * Reports an attribute that Python wrote on the Python object of a generated wrapper to the wrapper,
     * which keeps it in its Java field, and writes it to its own Python object when that is the object of
     * another context (a pooled context reads a copy).
     *
     * @param reference The back-reference the object keeps
     * @param name The attribute
     * @param value The value written
     */
    @UsedByGeneratedCode
    public static void written(Value reference, Value name, Value value) {
        if (reference.isHostObject()
            && reference.asHostObject() instanceof ValueCoercible.HostObjectReference(Object owner)
            && owner instanceof ValueCoercible.GeneratedPropertyMembers members) {
            members.micronautValueCoerciblePutMember(name.asString(), value);
        }
    }

    /**
     * The content of a Java {@link Optional} Python reads ({@code get()}, {@code orElseThrow()}), handed
     * over as {@link #pythonElements(Value)} hands over the elements of a collection.
     *
     * @param optional The optional
     * @return The content
     */
    @UsedByGeneratedCode
    public static Value optionalElement(Value optional) {
        return handOverOne(((Optional<?>) optional.asHostObject()).orElseThrow());
    }

    /**
     * The content of a Java {@link Optional} Python reads with {@code orElse(other)}, handed over as
     * {@link #pythonElements(Value)} hands over the elements of a collection, or the other value.
     *
     * @param optional The optional
     * @param other The value of an empty optional
     * @return The content, or the other value
     */
    @UsedByGeneratedCode
    public static Value optionalElementOrElse(Value optional, Value other) {
        Optional<?> content = (Optional<?>) optional.asHostObject();
        return content.isPresent() ? handOverOne(content.get()) : other;
    }

    /**
     * The JavaBean accessor of the wrapper of a handed-over object that a Python attribute lookup did not
     * find on the object ({@code point.getX()}): {@code get:x} for a getter alias of the attribute
     * {@code x}, {@code set:x} for a setter alias, or {@code null}.
     *
     * @param reference The back-reference the object keeps
     * @param name The attribute looked up
     * @return The accessor kind and attribute, or {@code null}
     */
    @UsedByGeneratedCode
    public static @Nullable String accessorAlias(Value reference, Value name) {
        if (reference.isHostObject()
            && reference.asHostObject() instanceof ValueCoercible.HostObjectReference(Object owner)
            && owner instanceof ValueCoercible.GeneratedPropertyMembers members) {
            String key = name.asString();
            String getter = members.micronautValueCoercibleGetterPropertyName(key);
            if (getter != null) {
                return "get:" + getter;
            }
            String setter = members.micronautValueCoercibleSetterPropertyName(key);
            if (setter != null) {
                return "set:" + setter;
            }
        }
        return null;
    }

    private static Value handOverOne(Object element) {
        return handOver(new Object[] {element}).getArrayElement(0);
    }

    private static Value handOver(Object[] elements) {
        Context context = Context.getCurrent();
        Object[] items = new Object[elements.length];
        int[] wrapperIndexes = new int[elements.length];
        ValueCoercible[] wrappers = new ValueCoercible[elements.length];
        int wrapperCount = 0;
        for (int i = 0; i < elements.length; i++) {
            Object element = elements[i];
            if (element instanceof ValueCoercible wrapper && !(element instanceof InterceptedProxy<?>)
                && !(element instanceof Enum<?>) && element instanceof ValueCoercible.GeneratedPropertyMembers) {
                items[i] = PythonCoercion.pythonObjectInCurrentContext(wrapper);
                wrapperIndexes[wrapperCount] = i;
                wrappers[i] = wrapper;
                wrapperCount++;
            } else {
                items[i] = element;
            }
        }
        if (wrapperCount == 0) {
            return PythonContextRuntime.helper(context, PYTHON_LIST_OF).execute(items);
        }
        // the elements enter Python with the call, followed by the indexes of the objects of wrappers: Python
        // answers with the list, or, the first time, with the list and the indexes of the objects that do not
        // keep their back-reference yet
        Object[] arguments = new Object[1 + items.length + wrapperCount];
        arguments[0] = items.length;
        System.arraycopy(items, 0, arguments, 1, items.length);
        for (int i = 0; i < wrapperCount; i++) {
            arguments[1 + items.length + i] = wrapperIndexes[i];
        }
        Value handed = PythonContextRuntime.helper(context, HAND_OVER).execute(arguments);
        if (!handed.hasHashEntries()) {
            return handed;
        }
        Value list = handed.getHashValue("list");
        Value unattached = handed.getHashValue("unattached");
        long unattachedCount = unattached.getArraySize();
        if (unattachedCount > 0) {
            Object[] attachments = new Object[1 + (int) unattachedCount * 3];
            attachments[0] = list;
            for (int i = 0; i < unattachedCount; i++) {
                int index = unattached.getArrayElement(i).asInt();
                attachments[1 + i * 3] = index;
                attachments[2 + i * 3] = new ValueCoercible.HostObjectReference(wrappers[index]);
                attachments[3 + i * 3] = wrappers[index];
            }
            PythonContextRuntime.helper(context, ATTACH_WRAPPERS).execute(attachments);
        }
        return list;
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
