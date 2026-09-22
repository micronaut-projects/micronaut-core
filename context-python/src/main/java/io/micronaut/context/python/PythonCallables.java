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
import io.micronaut.core.reflect.ClassUtils;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoublePredicate;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleToIntFunction;
import java.util.function.DoubleToLongFunction;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.IntToDoubleFunction;
import java.util.function.IntToLongFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.LongBinaryOperator;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;
import java.util.function.LongToDoubleFunction;
import java.util.function.LongToIntFunction;
import java.util.function.LongUnaryOperator;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.ObjLongConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongBiFunction;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;

/**
 * Adapts Python callables to Java functional interfaces.
 * <p>
 * GraalPy converts any executable value to any functional interface, so a Java method that is
 * overloaded on functional interfaces of different arities (for example {@code Predicate} and
 * {@code BiPredicate}) cannot be called with a Python lambda: every overload applies. The host
 * access built by {@link GraalPyHostAccessFactory} registers a target type mapping for each of the
 * {@link #STANDARD_INTERFACES standard functional interfaces} and for every functional interface
 * the Python compiler found in the Java types the Python sources reference (a
 * {@link PythonFunctionalInterfaceProvider} per compilation) that only applies when the number of
 * positional parameters of the callable matches the arity of the interface method, which lets the
 * overload selection of the host interop pick the overload by arity. The mappings also take
 * precedence over the default conversions of the host interop, so a callable selects a functional
 * interface overload over an {@code Iterable} one (an {@code Iterable} counts as a functional
 * interface for the host interop) or an {@code Object} one. A callable declaring exactly the
 * parameters of the interface method wins
 * over one that accepts them through default values, and value-returning interfaces take
 * precedence over void ones so that a zero-argument lambda selects {@code Supplier} over
 * {@code Runnable}, like a Java lambda expression does. The mappings stay out of the decision for
 * a callable whose signature cannot be read (a {@code functools.partial}, a builtin) or that only
 * fits an arity through {@code *args}: the mapping of every overload would apply and the call be
 * ambiguous, where the default conversion of the host interop has an answer ({@code Function} is
 * a loose conversion of any executable, other interfaces are function proxies).
 * <p>
 * {@link PythonInterop#fn(Class, Value)} adapts a callable to any functional interface explicitly,
 * which remains the way to select between overloads on functional interfaces of the same arity, or
 * on an interface the compiler did not see (one only reachable through objects obtained at run time).
 *
 * @since 5.2.3
 */
@Internal
final class PythonCallables {

    /**
     * The functional interfaces converted by arity: every interface of {@code java.util.function}
     * plus {@link Runnable}, {@link Callable} and {@link Comparator}.
     */
    static final List<Class<?>> STANDARD_INTERFACES = List.of(
        Runnable.class, Callable.class, Comparator.class,
        BiConsumer.class, BiFunction.class, BinaryOperator.class, BiPredicate.class, BooleanSupplier.class,
        Consumer.class, DoubleBinaryOperator.class, DoubleConsumer.class, DoubleFunction.class, DoublePredicate.class,
        DoubleSupplier.class, DoubleToIntFunction.class, DoubleToLongFunction.class, DoubleUnaryOperator.class,
        Function.class, IntBinaryOperator.class, IntConsumer.class, IntFunction.class, IntPredicate.class,
        IntSupplier.class, IntToDoubleFunction.class, IntToLongFunction.class, IntUnaryOperator.class,
        LongBinaryOperator.class, LongConsumer.class, LongFunction.class, LongPredicate.class, LongSupplier.class,
        LongToDoubleFunction.class, LongToIntFunction.class, LongUnaryOperator.class, ObjDoubleConsumer.class,
        ObjIntConsumer.class, ObjLongConsumer.class, Predicate.class, Supplier.class, ToDoubleBiFunction.class,
        ToDoubleFunction.class, ToIntBiFunction.class, ToIntFunction.class, ToLongBiFunction.class,
        ToLongFunction.class, UnaryOperator.class
    );

    private static final Logger LOG = LoggerFactory.getLogger(PythonCallables.class);

    /** Bound while a callable is converted with the default host interop conversion. */
    private static final ScopedValue<Boolean> DEFAULT_CONVERSION = ScopedValue.newInstance();

    /** {@code CO_VARARGS} of a Python code object. */
    private static final int CO_VARARGS = 0x04;

    private static final String CODE = "__code__";
    private static final String DEFAULTS = "__defaults__";
    private static final String SELF = "__self__";
    private static final String FUNC = "__func__";

    private PythonCallables() {
    }

    /**
     * Registers the arity-aware target type mappings of the standard functional interfaces and of
     * the given ones. An entry naming a type the class loader does not have (an interface of a
     * compile-time only dependency of the Python sources) is skipped: the callable is then
     * converted by the default conversion of the host interop, as before.
     *
     * @param builder The host access builder
     * @param entries Further functional interfaces; the first entry of a type counts
     * @param classLoader The class loader that resolves the named interfaces, or {@code null} for
     *                    the context class loader of the calling thread
     */
    static void registerFunctionalInterfaces(HostAccess.Builder builder,
                                             Collection<PythonFunctionalInterfaceProvider.Entry> entries,
                                             @Nullable ClassLoader classLoader) {
        Map<Class<?>, PythonFunctionalInterfaceProvider.Entry> byType = new LinkedHashMap<>();
        for (Class<?> type : STANDARD_INTERFACES) {
            Method method = functionalMethod(type);
            if (method != null) {
                byType.put(type, new PythonFunctionalInterfaceProvider.Entry(
                    type.getName(), method.getParameterCount(), method.getReturnType() != void.class));
            }
        }
        for (PythonFunctionalInterfaceProvider.Entry entry : entries) {
            Class<?> type = ClassUtils.forName(entry.typeName(), classLoader).orElse(null);
            if (type == null) {
                LOG.debug("The functional interface {} is absent from the class path: a Python callable passed where it is expected is converted by the default conversion",
                    entry.typeName());
                continue;
            }
            byType.putIfAbsent(type, entry);
        }
        for (Map.Entry<Class<?>, PythonFunctionalInterfaceProvider.Entry> entry : byType.entrySet()) {
            registerArityMapping(builder, entry.getKey(), entry.getValue().arity(), entry.getValue().returnsValue());
        }
    }

    private static <T> void registerArityMapping(HostAccess.Builder builder, Class<T> type, int arity, boolean returnsValue) {
        // a callable declaring exactly the parameters of the interface method wins; a value-returning
        // interface wins over a void one (Supplier over Runnable), like a Java lambda expression
        HostAccess.TargetMappingPrecedence exact = returnsValue
            ? HostAccess.TargetMappingPrecedence.HIGHEST
            : HostAccess.TargetMappingPrecedence.HIGH;
        builder.targetTypeMapping(
            Value.class,
            type,
            value -> acceptsExactArity(value, arity),
            value -> convertWithDefaultConversion(value, type),
            exact
        );
        // a callable that accepts the arity through default values applies next
        builder.targetTypeMapping(
            Value.class,
            type,
            value -> acceptsArity(value, arity),
            value -> convertWithDefaultConversion(value, type),
            HostAccess.TargetMappingPrecedence.LOW
        );
    }

    /**
     * Whether the value is a Python callable that declares exactly the given number of positional
     * parameters.
     *
     * @param value The value
     * @param arity The number of arguments
     * @return Whether the callable declares the arity
     */
    static boolean acceptsExactArity(@Nullable Value value, int arity) {
        Arity declared = arityOf(value);
        return declared != null && declared.parameters() == arity;
    }

    /**
     * Whether the value is a Python callable that can be called with the given number of
     * positional arguments through its declared parameters, default values included. A callable
     * whose signature cannot be inspected, or that only accepts the arity through {@code *args},
     * accepts no arity here: it is left to the default conversion.
     *
     * @param value The value
     * @param arity The number of arguments
     * @return Whether the callable accepts the arity
     */
    static boolean acceptsArity(@Nullable Value value, int arity) {
        Arity declared = arityOf(value);
        return declared != null && declared.accepts(arity);
    }

    private static boolean isCallable(@Nullable Value value) {
        return value != null && !value.isNull() && !value.isHostObject() && !value.isProxyObject()
            && !DEFAULT_CONVERSION.isBound() && (value.canExecute() || value.canInstantiate());
    }

    /**
     * The positional parameters of a Python callable.
     *
     * @param value The value
     * @return The arity, or {@code null} if the value is not a callable whose signature can be inspected
     */
    static @Nullable Arity arityOf(@Nullable Value value) {
        if (value == null || !isCallable(value)) {
            return null;
        }
        try {
            Value code = codeObject(value);
            if (code == null) {
                return null;
            }
            int parameters = intMember(code, "co_argcount");
            int flags = intMember(code, "co_flags");
            if (parameters < 0 || flags < 0) {
                return null;
            }
            if (isBoundMethod(value)) {
                parameters--;
            }
            return new Arity(parameters - defaultsCount(value), parameters, (flags & CO_VARARGS) != 0);
        } catch (RuntimeException e) {
            // an unusual callable: leave the decision to the default conversion
            return null;
        }
    }

    /**
     * Converts the callable to the functional interface with the default host interop conversion,
     * bypassing the arity mappings. The result is the interop function proxy that becomes the
     * original callable again when it returns to Python.
     *
     * @param callable The callable
     * @param type The functional interface
     * @param <T> The interface type
     * @return The proxy
     */
    static <T> T convertWithDefaultConversion(Value callable, Class<T> type) {
        return ScopedValue.where(DEFAULT_CONVERSION, Boolean.TRUE).call(() -> callable.as(type));
    }

    /**
     * Creates a host proxy of the interface that invokes the callable. Unlike the interop
     * conversion the proxy is a plain host object: it keeps its class when it returns to Python.
     *
     * @param callable The callable
     * @param type The functional interface
     * @param <T> The interface type
     * @return The proxy
     */
    static <T> T proxy(Value callable, Class<T> type) {
        Method method = functionalMethod(type);
        if (method == null) {
            throw new IllegalArgumentException(type.getName() + " is not a functional interface");
        }
        if (!callable.canExecute() && !callable.canInstantiate()) {
            throw new IllegalArgumentException("The value " + callable + " is not callable");
        }
        return type.cast(Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            new CallableInvocationHandler(callable, method)
        ));
    }

    /**
     * The single abstract method of a functional interface.
     *
     * @param type The type
     * @return The method, or {@code null} if the type is not a functional interface
     */
    static @Nullable Method functionalMethod(Class<?> type) {
        if (!type.isInterface()) {
            return null;
        }
        Method found = null;
        for (Method method : type.getMethods()) {
            if (!Modifier.isAbstract(method.getModifiers()) || isObjectMethod(method)) {
                continue;
            }
            if (found != null && !(found.getName().equals(method.getName())
                && found.getParameterCount() == method.getParameterCount())) {
                return null;
            }
            if (found == null || found.getDeclaringClass().isAssignableFrom(method.getDeclaringClass())) {
                found = method;
            }
        }
        return found;
    }

    private static boolean isObjectMethod(Method method) {
        try {
            Object.class.getMethod(method.getName(), method.getParameterTypes());
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static @Nullable Value codeObject(Value callable) {
        if (!callable.hasMembers()) {
            return null;
        }
        Value function = callable;
        if (callable.hasMember(FUNC)) {
            Value unbound = callable.getMember(FUNC);
            if (unbound != null && !unbound.isNull()) {
                function = unbound;
            }
        }
        if (!function.hasMember(CODE)) {
            return null;
        }
        Value code = function.getMember(CODE);
        return code == null || code.isNull() || !code.hasMembers() ? null : code;
    }

    private static boolean isBoundMethod(Value callable) {
        if (!callable.hasMember(SELF) || !callable.hasMember(FUNC)) {
            return false;
        }
        Value self = callable.getMember(SELF);
        return self != null && !self.isNull();
    }

    private static int defaultsCount(Value callable) {
        Value function = callable.hasMember(FUNC) ? callable.getMember(FUNC) : callable;
        if (function == null || !function.hasMember(DEFAULTS)) {
            return 0;
        }
        Value defaults = function.getMember(DEFAULTS);
        if (defaults == null || defaults.isNull() || !defaults.hasArrayElements()) {
            return 0;
        }
        return (int) defaults.getArraySize();
    }

    private static int intMember(Value value, String name) {
        if (!value.hasMember(name)) {
            return -1;
        }
        Value member = value.getMember(name);
        return member != null && member.fitsInInt() ? member.asInt() : -1;
    }

    /**
     * Invocation handler of {@link #proxy(Value, Class)}.
     *
     * @param callable The callable
     * @param method The interface method
     */
    private record CallableInvocationHandler(Value callable, Method method) implements InvocationHandler {

        @SuppressWarnings("java:S2583") // InvocationHandler passes null arguments for zero-parameter methods.
        @Override
        public @Nullable Object invoke(Object proxy, Method invoked, Object[] args) throws Throwable {
            if (invoked.getDeclaringClass() == Object.class) {
                return switch (invoked.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "PythonInterop.fn(" + method.getDeclaringClass().getName() + ", " + callable + ")";
                    default -> throw new UnsupportedOperationException(invoked.getName());
                };
            }
            if (invoked.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, invoked, args);
            }
            if (!invoked.getName().equals(method.getName()) || invoked.getParameterCount() != method.getParameterCount()) {
                throw new UnsupportedOperationException(invoked.toString());
            }
            Value result = callable.execute(args == null ? new Object[0] : args);
            Class<?> returnType = invoked.getReturnType();
            return returnType == void.class ? null : result.as(returnType);
        }
    }

    /**
     * The positional parameters of a callable.
     *
     * @param required The parameters without a default value
     * @param parameters The declared parameters
     * @param varargs Whether the callable accepts further positional arguments
     */
    record Arity(int required, int parameters, boolean varargs) {

        /**
         * @param arity The number of arguments
         * @return Whether the declared parameters, default values included, take the arguments;
         * {@code *args} does not count, it would fit every overload
         */
        boolean accepts(int arity) {
            return arity >= required && arity <= parameters;
        }
    }
}
