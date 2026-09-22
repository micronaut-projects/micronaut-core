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

/**
 * Support for the Java classes generated for Python exceptions that extend a Java exception class.
 * <p>
 * At runtime such a Python class is a plain Python exception whose {@code args} hold the arguments
 * of its {@code super().__init__(...)} call. The generated Java class reads them back through these
 * helpers to call the matching Java super constructor, so the message, the cause and any other
 * state of the Java base class survive the crossing into Java.
 *
 * @since 5.2.0
 */
@Internal
public final class PythonExceptions {

    private static final String ARGS = "args";
    private static final String CAUSE = "__cause__";
    private static final String CONTEXT = "__context__";
    private static final String STR_HELPER = "builtin:str";
    /**
     * The bookkeeping Truffle attaches to a host throwable that crosses guest frames. The class is
     * package private to {@code com.oracle.truffle.api}, so it is recognised by name.
     */
    private static final String TRUFFLE_LAZY_STACK_TRACE = "com.oracle.truffle.api.TruffleStackTrace$LazyStackTrace";
    private static final Throwable[] NO_SUPPRESSED = new Throwable[0];

    private PythonExceptions() {
    }

    /**
     * Whether a suppressed exception is Truffle bookkeeping rather than an exception the application
     * suppressed.
     * <p>
     * A Java exception that Python creates and raises is wrapped by Truffle, which shares the guest
     * frames it unwinds through with the host throwable by attaching a
     * {@code TruffleStackTrace.LazyStackTrace} to it as a suppressed exception. That entry carries no
     * message and no stack trace of its own, it is not an exception the application suppressed, and a
     * serializer rendering the suppressed exceptions of an error response has nothing to write for it:
     * the problem+json body of a Java problem raised from Python failed with
     * {@code No serializable introspection present for type LazyStackTrace} and the client saw a 500.
     *
     * @param suppressed A suppressed exception
     * @return {@code true} when the entry is Truffle's guest stack trace
     * @since 5.2.4
     */
    public static boolean isTruffleStackTrace(@Nullable Throwable suppressed) {
        return suppressed != null && TRUFFLE_LAZY_STACK_TRACE.equals(suppressed.getClass().getName());
    }

    /**
     * The exceptions the application suppressed on a throwable that crossed Python.
     * <p>
     * {@link Throwable#getSuppressed()} of a Java exception that crossed Python also reports Truffle's
     * guest stack trace (see {@link #isTruffleStackTrace(Throwable)}). The entry cannot be taken off the
     * throwable: {@link Throwable#addSuppressed(Throwable)} and {@link Throwable#getSuppressed()} are
     * final, there is no removal, and the field behind them is in {@code java.base/java.lang}, which is
     * not open. This is the view of the suppressed exceptions without it, for code that reports or
     * serializes them; the genuine entries keep their order.
     *
     * @param throwable The throwable
     * @return The suppressed exceptions of the application
     * @since 5.2.4
     */
    public static Throwable[] suppressed(Throwable throwable) {
        Throwable[] all = throwable.getSuppressed();
        int genuine = 0;
        for (Throwable suppressed : all) {
            if (!isTruffleStackTrace(suppressed)) {
                genuine++;
            }
        }
        if (genuine == all.length) {
            return all;
        }
        if (genuine == 0) {
            return NO_SUPPRESSED;
        }
        Throwable[] kept = new Throwable[genuine];
        int index = 0;
        for (Throwable suppressed : all) {
            if (!isTruffleStackTrace(suppressed)) {
                kept[index++] = suppressed;
            }
        }
        return kept;
    }

    /**
     * Positional argument {@code index} of the Python exception ({@code exception.args[index]}).
     *
     * @param exception The Python exception
     * @param index The argument index
     * @return The argument, or {@code null} when the exception has fewer arguments
     */
    @UsedByGeneratedCode
    public static @Nullable Value argument(Value exception, int index) {
        Value args = arguments(exception);
        if (args == null || index < 0 || index >= args.getArraySize()) {
            return null;
        }
        return args.getArrayElement(index);
    }

    /**
     * Positional argument {@code index} of the Python exception for a primitive parameter of the
     * Java super constructor, which cannot take a missing or {@code None} argument.
     *
     * @param exception The Python exception
     * @param index The argument index
     * @param parameterType The name of the primitive parameter type
     * @return The argument
     * @throws IllegalArgumentException When the exception has fewer arguments or the argument is {@code None}
     */
    @UsedByGeneratedCode
    public static Value primitiveArgument(Value exception, int index, String parameterType) {
        Value argument = argument(exception, index);
        if (argument == null || argument.isNull()) {
            throw new IllegalArgumentException("Argument " + index + " of the super().__init__(...) call of Python exception ["
                + exception.getMetaObject() + "] is " + (argument == null ? "missing" : "None")
                + ", but the matching parameter of the Java super constructor is of the primitive type [" + parameterType + "]");
        }
        return argument;
    }

    /**
     * Positional argument {@code index} of the Python exception as a string: the value itself when
     * it is a string, its {@code str()} otherwise, the way a Python exception message reads.
     *
     * @param exception The Python exception
     * @param index The argument index
     * @return The argument, or {@code null} when the exception has fewer arguments or the argument is {@code None}
     */
    @UsedByGeneratedCode
    public static @Nullable String argumentAsString(Value exception, int index) {
        return asString(argument(exception, index));
    }

    /**
     * The message of the Python exception: {@code str(exception)} when the exception carries
     * arguments, {@code null} otherwise (a Python exception raised without arguments has an empty
     * message, a Java exception a {@code null} one).
     *
     * @param exception The Python exception
     * @return The message, or {@code null}
     */
    @UsedByGeneratedCode
    public static @Nullable String message(Value exception) {
        Value args = arguments(exception);
        if (args == null || args.getArraySize() == 0) {
            return null;
        }
        return asString(exception);
    }

    /**
     * Attach the Python cause ({@code raise ... from cause}, or the exception being handled when the
     * exception was raised inside an {@code except} block) to the generated Java exception when the
     * cause is a Java exception and the Java exception has no cause yet.
     *
     * @param throwable The generated Java exception
     * @param exception The Python exception it was created from
     */
    @UsedByGeneratedCode
    public static void attachCause(Throwable throwable, Value exception) {
        if (throwable.getCause() != null) {
            return;
        }
        Throwable cause = hostCause(exception, CAUSE, true);
        if (cause == null) {
            // the exception handled when this one was raised: attached when it is a Java exception,
            // a Python one would need to be raised again to be represented in Java
            cause = hostCause(exception, CONTEXT, false);
        }
        if (cause == null || cause == throwable) {
            return;
        }
        try {
            throwable.initCause(cause);
        } catch (IllegalStateException | IllegalArgumentException e) {
            // the base class fixed the cause in its constructor; keep it
        }
    }

    /**
     * {@code str(value)}: the value itself when it is a string, its Python {@code str()} otherwise.
     *
     * @param value The value
     * @return The string, or {@code null} for {@code None}
     */
    @UsedByGeneratedCode
    public static @Nullable String asString(@Nullable Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isString()) {
            return value.asString();
        }
        Context context = value.getContext();
        if (context == null) {
            return value.toString();
        }
        Value str = PythonContextRegistry.state(context).helpers.computeIfAbsent(STR_HELPER, key -> context.eval(PythonContextRuntime.PYTHON, "str"));
        return str.execute(value).asString();
    }

    private static @Nullable Value arguments(@Nullable Value exception) {
        if (exception == null || !exception.hasMembers() || !exception.hasMember(ARGS)) {
            return null;
        }
        Value args = exception.getMember(ARGS);
        if (args == null || !args.hasArrayElements()) {
            return null;
        }
        return args;
    }

    private static @Nullable Throwable hostCause(Value exception, String member, boolean guestExceptions) {
        if (!exception.hasMembers() || !exception.hasMember(member)) {
            return null;
        }
        Value cause = exception.getMember(member);
        if (PythonConversion.isNone(cause)) {
            return null;
        }
        if (!guestExceptions && !(cause.isHostObject() && cause.asHostObject() instanceof Throwable)) {
            return null;
        }
        return GraalPyExceptionHandler.toHostThrowable(cause);
    }
}
