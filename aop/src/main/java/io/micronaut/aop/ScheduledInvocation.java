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
package io.micronaut.aop;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;

/**
 * Describes a method invocation fired as a scheduled task, as opposed to a direct call of the same method.
 *
 * <p>When the scheduler fires a {@code @Scheduled} method, the interceptor chain of that invocation carries a
 * {@code ScheduledInvocation} in its {@link InvocationContext#getAttributes() attributes} under
 * {@link #ATTRIBUTE}. Interceptors read it with {@link #find(InvocationContext)}:</p>
 *
 * <pre>{@code
 * ScheduledInvocation scheduled = ScheduledInvocation.find(context).orElse(null);
 * if (scheduled != null) {
 *     // fired by the scheduler; scheduled.getSchedule() is the @Scheduled annotation that fired
 * }
 * }</pre>
 *
 * <p>The attribute is present only on the chain of the scheduled method itself. A direct call of the method from
 * application code, a recursive call, and every method the scheduled invocation calls in turn do not carry it.
 * Interceptors that apply timer-specific advice, such as Jakarta Interceptors' around-timeout methods, can use it
 * where Jakarta Interceptors use a non-null timer.</p>
 *
 * <p>Code that fires methods on its own schedule, such as a timer service integration, marks its invocations the
 * same way with {@link #invoke(ExecutableMethod, AnnotationValue, Object, Object...)}.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
public final class ScheduledInvocation {

    /**
     * The name of the {@link InvocationContext} attribute that holds the {@code ScheduledInvocation} of a method
     * invocation fired as a scheduled task.
     */
    public static final String ATTRIBUTE = "micronaut.scheduled.invocation";

    private static final ScopedValue<ScheduledInvocation> CURRENT = ScopedValue.newInstance();

    private final ExecutableMethod<?, ?> method;
    private final @Nullable AnnotationValue<?> schedule;
    private boolean claimed;

    private ScheduledInvocation(ExecutableMethod<?, ?> method, @Nullable AnnotationValue<?> schedule) {
        this.method = method;
        this.schedule = schedule;
    }

    /**
     * @return The method fired as a scheduled task
     */
    public ExecutableMethod<?, ?> getMethod() {
        return method;
    }

    /**
     * The annotation whose schedule fired the invocation, for example the {@code @Scheduled} annotation. A method
     * with several schedules is fired once for each of them, and each invocation carries its own annotation.
     *
     * @return The annotation, or {@code null} if the code that fired the invocation provided none
     */
    public @Nullable AnnotationValue<?> getSchedule() {
        return schedule;
    }

    /**
     * Finds the scheduled invocation an interceptor chain carries.
     *
     * @param context The invocation context
     * @return The scheduled invocation, or empty if the invocation was not fired as a scheduled task
     */
    public static Optional<ScheduledInvocation> find(InvocationContext<?, ?> context) {
        return context.getAttribute(ATTRIBUTE, ScheduledInvocation.class);
    }

    /**
     * Invokes the method on the target as a scheduled task.
     *
     * <p>The interceptor chain of the invocation carries a {@code ScheduledInvocation} under {@link #ATTRIBUTE}.
     * The target is usually the bean as the context returns it, which may be an AOP proxy: the method is invoked on
     * it and the proxy builds the chain. The chain picks the invocation up from a value bound to the calling thread
     * for the duration of the call; the first chain built for the method claims it, so a recursive call and the
     * methods the invocation calls in turn do not carry it.</p>
     *
     * @param method    The method to invoke
     * @param schedule  The annotation whose schedule fired the invocation, if any
     * @param target    The target bean, which may be an AOP proxy
     * @param arguments The arguments
     * @param <T>       The target type
     * @param <R>       The return type
     * @return The return value
     */
    @Nullable
    public static <T, R> R invoke(ExecutableMethod<T, R> method,
                                  @Nullable AnnotationValue<?> schedule,
                                  T target,
                                  @Nullable Object... arguments) {
        return ScopedValue.where(CURRENT, new ScheduledInvocation(method, schedule))
            .call(() -> method.invoke(target, arguments));
    }

    /**
     * Claims the scheduled invocation bound to the calling thread for an interceptor chain being built.
     *
     * <p>Returns the invocation at most once, for the first chain whose method is the one that was scheduled: the
     * same method name and argument types, declared by a type the target is an instance of. Every other chain built
     * while the invocation is bound, and every chain built after it was claimed, gets {@code null}.</p>
     *
     * @param target The target of the interception
     * @param method The intercepted method
     * @return The scheduled invocation, or {@code null}
     */
    @Internal
    public static @Nullable ScheduledInvocation claim(Object target, ExecutableMethod<?, ?> method) {
        if (!CURRENT.isBound()) {
            return null;
        }
        ScheduledInvocation current = CURRENT.get();
        if (current.claimed) {
            return null;
        }
        ExecutableMethod<?, ?> scheduled = current.method;
        if (scheduled == method || (scheduled.getMethodName().equals(method.getMethodName())
            && Arrays.equals(scheduled.getArgumentTypes(), method.getArgumentTypes())
            && scheduled.getDeclaringType().isInstance(target))) {
            current.claimed = true;
            return current;
        }
        return null;
    }

    @Override
    public String toString() {
        return "ScheduledInvocation{method=" + method + ", schedule=" + schedule + '}';
    }
}
