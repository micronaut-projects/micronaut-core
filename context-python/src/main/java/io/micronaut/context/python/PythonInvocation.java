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
package io.micronaut.context.python;

import io.micronaut.core.annotation.Internal;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;

/**
 * Invocation of Python methods and descriptors from generated Java bridges.
 *
 * @since 5.2.0
 */
@Internal
public final class PythonInvocation {

    private static final String INVOKE_METHOD = "__micronaut_invoke_method";

    private static final String RAW_CLASS_MEMBER = "__micronaut_get_raw_class_member";

    private static final Object[] EMPTY_ARGUMENTS = new Object[0];

    private PythonInvocation() {
    }

    /**
     * Invoke a generated bridge method on a Python receiver.
     * <p>
     * Generated Java stubs use this method instead of calling {@link Value#invokeMember(String, Object...)}
     * directly so every Python call enters the {@link PythonContextRuntime} execution tracker for the
     * receiver's actual context. The execution frame keeps graceful shutdown and pooled-context cleanup
     * from observing the context as idle while a bridge invocation, or nested bridge invocation, is still
     * unwinding.
     * <p>
     * Invocation is delegated to a context-local Python helper because Python method lookup is not just
     * a map lookup. The helper first uses {@code getattr} for normal bound-method behavior, then falls
     * back to walking the class {@code __mro__} and applying {@code __get__} so descriptors and inherited
     * methods are invoked with Python semantics before the result crosses back to Java.
     *
     * @param receiver The Python receiver
     * @param name The method name
     * @param arguments The method arguments
     * @return The invocation result
     */
    public static Value invokePythonMethod(Value receiver, String name, Object[] arguments) {
        Context context = receiver.getContext();
        Object[] args = arguments == null ? EMPTY_ARGUMENTS : arguments;
        return PythonContextRegistry.withExecutionFrame(context, () -> {
            Value member = receiver.getMember(name);
            if (member == null) {
                throw new IllegalArgumentException("No Python member [" + name + "] found");
            }
            if (member.canExecute()) {
                // Fast path: a bound method or any other callable attribute is invoked directly. The
                // Python helper is only needed to bind raw descriptors that getattr does not resolve
                // to a callable.
                return member.execute(args);
            }
            return PythonContextRuntime.helper(context, INVOKE_METHOD).execute(receiver, name, args);
        });
    }

    /**
     * Read a Python class member directly from the MRO dictionaries, bypassing descriptor binding.
     *
     * @param pythonClass The Python class
     * @param name The member name
     * @return The raw member, or null if none exists
     */
    public static @Nullable Value getRawClassMember(Value pythonClass, String name) {
        Value member = getRawClassMemberFunction(pythonClass.getContext()).execute(pythonClass, name);
        if (PythonConversion.isNone(member)) {
            return null;
        }
        return member;
    }

    /**
     * Bind a raw Python descriptor to a receiver when the descriptor protocol is available.
     *
     * @param descriptor The raw descriptor
     * @param receiver The receiver object
     * @param owner The owner class
     * @return The bound descriptor, or the original descriptor if it cannot be bound
     */
    public static Value bindPythonDescriptor(Value descriptor, Object receiver, Value owner) {
        Value getter = descriptor.getMember("__get__");
        if (getter != null && getter.canExecute()) {
            Value receiverValue = receiver instanceof Value value
                ? value
                : receiver instanceof ValueCoercible valueCoercible ? valueCoercible.asPolyglotValue() : null;
            if (receiverValue != null) {
                return getter.execute(receiverValue, owner);
            }
        }
        return descriptor;
    }

    private static Value getRawClassMemberFunction(Context context) {
        return PythonContextRuntime.helper(context, RAW_CLASS_MEMBER);
    }
}
