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

    /**
     * The generated wrapper these instances belong to. Attached to each instance as
     * {@link ValueCoercible#HOST_OBJECT_MEMBER} so that a value coming back to Java resolves to
     * this wrapper instead of having to be rebuilt from the value, which is impossible: the value
     * does not carry the dependencies the instance was constructed with.
     */
    private final Object owner;

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
    public PythonPooledInstance(PythonContextRuntime.PythonClassReference classReference,
                                Object owner,
                                Object[] constructorArguments) {
        this.classReference = classReference;
        this.owner = owner;
        this.constructorArguments = constructorArguments;
    }



    /**
     * Points the instance back at its wrapper.
     *
     * <p>A value of a pooled class returns to Java whenever the bean is handed to Python and read
     * again -- building the bean's own AOP proxy does it, before a request is ever served. Without
     * this, the conversion has only the value to work from, and a wrapper cannot be rebuilt from
     * one: the dependencies the instance was constructed with are not in it. Asking injection for
     * the bean instead is circular, since the bean is what is being built.
     *
     * <p>With the back-reference the conversion finds the wrapper directly, through the same
     * {@link ValueCoercible#HOST_OBJECT_MEMBER} a wrapper handed to Python exposes.
     *
     * <p>Best effort: a Python class that restricts its attributes refuses the member, and such a
     * class simply keeps the old behaviour of being rebuilt, or failing to be.
     *
     * @param instance The instance to mark
     */
    private void attachOwner(Value instance) {
        try {
            instance.putMember(ValueCoercible.HOST_OBJECT_MEMBER, new ValueCoercible.HostObjectReference(owner));
        } catch (RuntimeException ignored) {
            // the class does not accept the member; nothing else depends on it being there
        }
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
        attachOwner(created);
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
