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
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The runtime side of a Python class that extends a Java class.
 * <p>
 * The Java class generated for such a Python class extends the Java base and is the only Java
 * instance of it. At run time the Java base is replaced in the Python class by a Python base class
 * (created by {@link #baseClass}) that records the arguments of {@code super().__init__(...)} and
 * defines a Python method for every accessible instance method of the Java base. Those methods
 * reach the base implementation through the Java instance bound to the Python object
 * ({@link #bind}), which the generated constructors create from the recorded arguments. A Python
 * object created in Python code gets its Java instance the first time an inherited method is
 * called.
 *
 * @author Micronaut Team
 * @since 5.2.0
 */
@Internal
public final class PythonJavaBases {

    /** The attribute of a Python object holding the Java instance of its class. */
    public static final String JAVA_INSTANCE_MEMBER = "__micronaut_java__";

    /** The attribute of a Python object holding the arguments of its {@code super().__init__(...)} call. */
    public static final String SUPER_ARGUMENTS_MEMBER = "__micronaut_super_args__";

    private static final String BASE_CLASS_HELPER = "__micronaut_java_base_class";
    private static final String SET_INSTANCE_PROPERTY = "__micronaut_set_instance_property";
    // a ClassValue rather than a map keyed by class: the entry goes away with the class loader
    private static final ClassValue<String[]> METHOD_NAMES = new ClassValue<>() {
        @Override
        protected String[] computeValue(Class<?> type) {
            Set<String> names = new LinkedHashSet<>();
            collectMethodNames(type, names, new LinkedHashSet<>());
            return names.toArray(new String[0]);
        }
    };
    // the constructions in progress on the thread, innermost first; removed once the last one finished
    private static final ThreadLocal<Deque<Construction>> CONSTRUCTIONS = ThreadLocal.withInitial(ArrayDeque::new);

    private PythonJavaBases() {
    }

    /**
     * The Python base class standing in for a Java class, created once per context.
     *
     * @param javaClass The Java class a Python class extends
     * @return The Python base class
     */
    @UsedByGeneratedCode
    public static Value baseClass(Class<?> javaClass) {
        Context context = Context.getCurrent();
        return PythonContextRuntime.helper(context, BASE_CLASS_HELPER)
            .execute(javaClass.getName(), methodNames(javaClass));
    }

    /**
     * Invokes a method of the Java base on the Java instance of a Python object; called by the
     * methods of the Python base class.
     *
     * @param self The Python object
     * @param name The method name
     * @param arguments The Python arguments
     * @return The result, converted for Python
     * @throws Exception The checked exception the base method declares, raised in Python as a host exception
     */
    @SuppressWarnings("java:S112") // the base method may declare any checked exception
    @UsedByGeneratedCode
    public static @Nullable Object invoke(Value self, String name, Value arguments) throws Exception {
        ValueCoercible.JavaBaseMembers javaInstance = javaInstance(self);
        int count = (int) arguments.getArraySize();
        List<Value> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(arguments.getArrayElement(i));
        }
        Object result = javaInstance.micronautInvokeJavaBaseMethod(name, values);
        return PythonCoercion.coerceToContext(result, self.getContext());
    }

    /**
     * Marks the Python object whose Java instance is being constructed on the current thread. The
     * generated {@code (Value)} constructor evaluates this before it runs the Java super constructor,
     * which may call a method the Python class overrides: until the instance holds its Python object,
     * the bridge of that method reaches it through {@link #underConstruction(Class)}.
     *
     * @param pythonObject The Python object
     * @param javaClass The generated Java class being constructed
     * @return The construction, finished by the constructor once the super constructor returned
     */
    @UsedByGeneratedCode
    public static Construction constructing(Value pythonObject, Class<?> javaClass) {
        Construction construction = new Construction(pythonObject, javaClass);
        CONSTRUCTIONS.get().push(construction);
        return construction;
    }

    /**
     * The Python object whose Java instance of the given class is being constructed on the current
     * thread, for a bridge method called by the Java super constructor; {@code null} outside such a
     * construction. Constructions nest when a super constructor creates the Java instance of another
     * Python object, so the asking class selects the innermost construction of its own class.
     *
     * @param javaClass The generated Java class of the asking instance
     * @return The Python object, or {@code null}
     */
    @UsedByGeneratedCode
    public static @Nullable Value underConstruction(Class<?> javaClass) {
        Deque<Construction> constructions = CONSTRUCTIONS.get();
        Iterator<Construction> iterator = constructions.iterator();
        while (iterator.hasNext()) {
            Construction construction = iterator.next();
            Value pythonObject = construction.pythonObject.get();
            if (pythonObject == null) {
                // abandoned by a super constructor that threw, and collected since
                iterator.remove();
            } else if (construction.javaClass == javaClass) {
                return pythonObject;
            }
        }
        if (constructions.isEmpty()) {
            CONSTRUCTIONS.remove();
        }
        return null;
    }

    /**
     * Binds the Java instance of a Python object.
     *
     * @param pythonObject The Python object
     * @param javaInstance The generated Java instance wrapping it
     */
    @UsedByGeneratedCode
    public static void bind(Value pythonObject, ValueCoercible javaInstance) {
        PythonContextRuntime.helper(pythonObject.getContext(), SET_INSTANCE_PROPERTY)
            .execute(pythonObject, JAVA_INSTANCE_MEMBER, javaInstance);
    }

    /**
     * The Java instance already bound to a Python object.
     *
     * @param pythonObject The Python object
     * @param type The generated Java class
     * @param <T> The generated Java class
     * @return The bound instance, or {@code null} when the object has none of that type
     */
    @UsedByGeneratedCode
    public static <T> @Nullable T bound(Value pythonObject, Class<T> type) {
        Object bound = boundInstance(pythonObject);
        return type.isInstance(bound) ? type.cast(bound) : null;
    }

    /**
     * An argument of the {@code super().__init__(...)} call recorded on a Python object, for the
     * Java super constructor.
     *
     * @param pythonObject The Python object
     * @param index The argument index
     * @param constructor A description of the Java constructor, for the error message
     * @return The argument
     */
    @UsedByGeneratedCode
    public static Value argument(Value pythonObject, int index, String constructor) {
        Value arguments = pythonObject.hasMember(SUPER_ARGUMENTS_MEMBER) ? pythonObject.getMember(SUPER_ARGUMENTS_MEMBER) : null;
        long count = arguments != null && arguments.hasArrayElements() ? arguments.getArraySize() : 0;
        if (arguments == null || index >= count) {
            throw new IllegalStateException("The super().__init__() call of Python class [" + className(pythonObject)
                + "] passed " + count + " argument(s) but the Java constructor " + constructor + " needs argument " + (index + 1)
                + "; call super().__init__(...) with the arguments of that constructor in __init__");
        }
        return arguments.getArrayElement(index);
    }

    /**
     * The error for a call of a Java base method that no accessible overload of the base accepts;
     * thrown by the generated {@code micronautInvokeJavaBaseMethod}.
     *
     * @param baseClass The Java base class name
     * @param name The method name
     * @param arguments The Python arguments
     * @return The error
     */
    @UsedByGeneratedCode
    public static IllegalArgumentException noSuchMethod(String baseClass, String name, List<Value> arguments) {
        StringBuilder types = new StringBuilder();
        for (Value argument : arguments) {
            if (!types.isEmpty()) {
                types.append(", ");
            }
            types.append(className(argument));
        }
        return new IllegalArgumentException("No accessible method [" + name + "] of the Java class [" + baseClass
            + "] accepts " + arguments.size() + " argument(s) of type(s) [" + types + "]");
    }

    private static ValueCoercible.JavaBaseMembers javaInstance(Value self) {
        Object bound = boundInstance(self);
        if (bound instanceof ValueCoercible.JavaBaseMembers members) {
            return members;
        }
        // A Python object created in Python code: the registered mapping of its class creates the
        // Java instance (through the generated (Value) constructor, which binds it)
        Object mapped = self.as(Object.class);
        if (mapped instanceof ValueCoercible.JavaBaseMembers members) {
            return members;
        }
        throw new IllegalStateException("Python object of class [" + className(self)
            + "] has no Java instance of its Java base class: the Java instance is created after __init__ returns, "
            + "so inherited Java methods cannot be called from __init__");
    }

    private static @Nullable Object boundInstance(Value pythonObject) {
        if (!pythonObject.hasMembers() || !pythonObject.hasMember(JAVA_INSTANCE_MEMBER)) {
            return null;
        }
        return ValueCoercibles.hostObject(pythonObject.getMember(JAVA_INSTANCE_MEMBER));
    }

    private static String className(Value pythonObject) {
        Value cls = pythonObject.hasMembers() && pythonObject.hasMember(GraalPyHostAccessFactory.CLASS_META) ? pythonObject.getMember(GraalPyHostAccessFactory.CLASS_META) : null;
        Value name = cls != null && cls.hasMember("__name__") ? cls.getMember("__name__") : null;
        return name != null && name.isString() ? name.asString() : pythonObject.toString();
    }

    /**
     * The names of the instance methods of a Java class and its supertypes that a Python subclass
     * can call: public and protected, neither static nor abstract, not declared by {@link Object}.
     *
     * @param javaClass The Java class
     * @return The method names
     */
    static String[] methodNames(Class<?> javaClass) {
        return METHOD_NAMES.get(javaClass);
    }

    private static void collectMethodNames(@Nullable Class<?> type, Set<String> names, Set<Class<?>> visited) {
        if (type == null || type == Object.class || !visited.add(type)) {
            return;
        }
        for (Method method : type.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isAbstract(modifiers) || method.isSynthetic() || method.isBridge()
                || method.getTypeParameters().length > 0) {
                // a generic method (toArray(T[])) is not reachable through the generated dispatcher either
                continue;
            }
            if (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)) {
                names.add(method.getName());
            }
        }
        collectMethodNames(type.getSuperclass(), names, visited);
        for (Class<?> anInterface : type.getInterfaces()) {
            collectMethodNames(anInterface, names, visited);
        }
    }

    /**
     * The construction of the Java instance of a Python object, see {@link #constructing(Value)}.
     */
    public static final class Construction {
        // weak: the constructors hold the object while it is being constructed, and a construction a
        // throwing super constructor left behind must not keep the object, and its context, reachable
        // from the thread
        private final WeakReference<Value> pythonObject;
        private final Class<?> javaClass;

        private Construction(Value pythonObject, Class<?> javaClass) {
            this.pythonObject = new WeakReference<>(pythonObject);
            this.javaClass = javaClass;
        }

        /**
         * Called by the generated constructor once the Java super constructor returned. A super
         * constructor that threw leaves its construction behind until a later one on the thread
         * finishes or a lookup finds it collected, which is harmless: only a bridge called before
         * the instance holds its Python object reads it, and by class.
         */
        @UsedByGeneratedCode
        public void finished() {
            Deque<Construction> constructions = CONSTRUCTIONS.get();
            Construction popped;
            do {
                // pops the constructions left behind by super constructors that threw, down to this one
                popped = constructions.poll();
            } while (popped != null && popped != this);
            if (constructions.isEmpty()) {
                CONSTRUCTIONS.remove();
            }
        }
    }
}
