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

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * Invokes a method as a scheduled task, so that interceptors can tell the invocation apart from a direct call.
 *
 * <p>The scheduler fires a {@code @Scheduled} method through {@link #invoke(ExecutableMethod, Object, Object...)}.
 * The invocation carries a thread-bound marker which the interceptor chain built for that method claims, and
 * {@link MethodInvocationContext#isScheduled()} then returns {@code true} for every interceptor in that chain.
 * A direct call to the same method from application code carries no marker, and neither do methods the scheduled
 * method calls in turn: the marker is bound to one method and is claimed by the first chain built for it, so
 * only the scheduled invocation itself observes it.</p>
 *
 * <p>Other schedulers or timer services can use the same entry point to fire methods, and interceptors that apply
 * timer-specific advice, such as Jakarta Interceptors' around-timeout methods, can rely on the same flag.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
public final class ScheduledInvocation {

    private static final ScopedValue<ScheduledInvocation> CURRENT = ScopedValue.newInstance();

    private final ExecutableMethod<?, ?> method;
    private boolean claimed;

    private ScheduledInvocation(ExecutableMethod<?, ?> method) {
        this.method = method;
    }

    /**
     * Invokes the method on the target as a scheduled task.
     *
     * <p>Interceptors of the method see {@link MethodInvocationContext#isScheduled()} return {@code true}.
     * The marker applies only to the invocation of this method and only on the calling thread. Methods the
     * invocation calls in turn, including a recursive call of the same method, are not marked.</p>
     *
     * @param method    The method to invoke
     * @param target    The target bean, which may be an AOP proxy
     * @param arguments The arguments
     * @param <T>       The target type
     * @param <R>       The return type
     * @return The return value
     */
    @Nullable
    public static <T, R> R invoke(ExecutableMethod<T, R> method, T target, @Nullable Object... arguments) {
        return ScopedValue.where(CURRENT, new ScheduledInvocation(method))
            .call(() -> method.invoke(target, arguments));
    }

    /**
     * Claims the current scheduled invocation marker for a method being intercepted.
     *
     * <p>Returns {@code true} at most once per scheduled invocation, for the first chain whose method is the one
     * that was scheduled: the same method name and argument types declared by a type the target is an instance of.
     * Every other chain built while the marker is bound, and every chain built after the marker has been claimed,
     * gets {@code false}.</p>
     *
     * @param target The target of the interception
     * @param method The intercepted method
     * @return Whether the interception is the scheduled invocation
     */
    @Internal
    public static boolean claim(Object target, ExecutableMethod<?, ?> method) {
        if (!CURRENT.isBound()) {
            return false;
        }
        ScheduledInvocation current = CURRENT.get();
        if (current.claimed) {
            return false;
        }
        ExecutableMethod<?, ?> scheduled = current.method;
        if (scheduled == method || (scheduled.getMethodName().equals(method.getMethodName())
            && Arrays.equals(scheduled.getArgumentTypes(), method.getArgumentTypes())
            && scheduled.getDeclaringType().isInstance(target))) {
            current.claimed = true;
            return true;
        }
        return false;
    }
}
