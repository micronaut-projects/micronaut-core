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
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The per-context Python instances of one pooled bean that has constructor arguments.
 *
 * <p>A pooled bean without arguments needs none of this: the pool caches one instance per
 * context per class, because every such bean of a given class is interchangeable. Arguments
 * break that. {@code @ContextPooled} is prototype scoped, so two beans of the same class can
 * hold different dependencies — a different qualifier, or a prototype dependency — and a
 * cache keyed by class alone would hand the first bean's instance to the second, with the
 * wrong dependencies and no error.
 *
 * <p>So the instances belong to the bean rather than to the pool. That also settles their
 * lifetime: keyed weakly by context, an entry goes when its context is closed and dropped,
 * and the whole map goes when the bean itself does. Keeping them in the pool instead would
 * accumulate entries for prototype beans long since collected, which on a long-running
 * server is an unbounded leak.
 *
 * @since 5.2.0
 */
@Internal
@Experimental
@UsedByGeneratedCode
public final class PythonPooledInstance {

    private final PythonContextRuntime.PythonClassReference classReference;
    private final Object[] constructorArguments;

    /**
     * Guarded by itself. Weakly keyed so that a closed context does not keep its instance, or
     * itself, alive through this map.
     */
    private final Map<Context, Value> instances = new WeakHashMap<>();

    /**
     * @param classReference The Python class
     * @param constructorArguments The arguments to construct it with, already resolved by injection
     */
    @UsedByGeneratedCode
    public PythonPooledInstance(PythonContextRuntime.PythonClassReference classReference, Object... constructorArguments) {
        this.classReference = classReference;
        this.constructorArguments = constructorArguments;
    }

    /**
     * @return The Python class these instances are of
     */
    public PythonContextRuntime.PythonClassReference classReference() {
        return classReference;
    }

    /**
     * This bean's instance in the given context, constructing it there on first use.
     *
     * <p>The construction runs outside the lock. Guest code must never run while a lock on the
     * map is held: Python re-entering the runtime for the same bean would deadlock against the
     * thread that holds it, and against the interpreter lock behind it. A duplicate construction
     * is cheaper than that, and the loser is discarded.
     *
     * @param context The context
     * @return The instance belonging to that context
     */
    public Value in(Context context) {
        Value existing;
        synchronized (instances) {
            existing = instances.get(context);
        }
        if (existing != null) {
            return existing;
        }
        Value type = PythonContextRuntime.findClass(classReference, context);
        Value created = type.canInstantiate()
            ? type.newInstance(PythonCoercion.coerceArgumentsToContext(context, constructorArguments))
            : type;
        synchronized (instances) {
            Value prior = instances.get(context);
            if (prior != null) {
                return prior;
            }
            instances.put(context, created);
            return created;
        }
    }
}
