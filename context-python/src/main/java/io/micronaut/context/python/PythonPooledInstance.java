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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

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
 * <p>So the instances belong to the bean rather than to the pool -- keeping them in the pool
 * would accumulate entries for prototype beans long since collected, which on a long-running
 * server is an unbounded leak.
 *
 * <p>Their lifetime needs both halves, and neither works alone, because the references run in
 * both directions. A stored instance reaches this holder: the host-object back-reference points
 * at the wrapper, and the wrapper holds the holder. This holder reaches every context it has
 * served, because a {@link Value} holds its own {@link Context}. So a weak key never fires,
 * whichever way round the map is: the value in the entry keeps the key reachable.
 *
 * <p>The map therefore lives here, which is what makes a collected bean take its instances with
 * it, and each context keeps a weak set of the holders it has served so that closing it can call
 * {@link #forget}. A closed context is removed from every holder that served it; a collected
 * holder takes its whole map at once.
 *
 * @since 5.2.10
 */
@Internal
@Experimental
@UsedByGeneratedCode
public final class PythonPooledInstance {

    private static final Logger LOG = LoggerFactory.getLogger(PythonPooledInstance.class);

    /**
     * The class to construct in each context. Null for a holder whose value is built by a factory
     * rather than constructed, which is every AOP-proxied pooled bean: the proxy is created around
     * the class rather than being an instance of it.
     */
    @Nullable
    private final PythonContextRuntime.PythonClassReference classReference;

    /**
     * What to call this holder in a diagnostic, when there is no class reference to name.
     */
    private final String displayName;

    /**
     * The generated wrapper these instances belong to. Attached to each instance as
     * {@link ValueCoercible#HOST_OBJECT_MEMBER} so that a value coming back to Java resolves to
     * this wrapper instead of having to be rebuilt from the value, which is impossible: the value
     * does not carry the dependencies the instance was constructed with.
     */
    @Nullable
    private final Object owner;

    @Nullable
    private final Object[] constructorArguments;

    /**
     * This bean's instance per context. Strong keys: the entries are removed when a context closes,
     * through {@link #forget}, so a weak key would only be a backstop -- and could not work anyway,
     * since the instance in the entry reaches this holder and so keeps its own key reachable.
     *
     * <p>A {@link ConcurrentHashMap} rather than a synchronized map: this is the
     * read path of every call on a pooled bean that owns its instances, and it should not queue behind
     * a lock, least of all one shared with every other context.
     */
    private final Map<Context, Value> instances = new ConcurrentHashMap<>();

    /**
     * Builds this bean's value for a context, when the value is not a plain instance of the class.
     * An AOP-proxied pooled bean needs one proxy per context, because a Python proxy belongs to the
     * context it was created in, and the wrapper Micronaut hands out has to work in all of them.
     */
    @Nullable
    private final Function<Context, Value> valueFactory;


    /**
     * @param classReference The Python class
     * @param owner The generated wrapper these instances belong to, for the back-reference
     * @param constructorArguments The arguments to construct it with, already resolved by injection
     */
    @UsedByGeneratedCode
    public PythonPooledInstance(PythonContextRuntime.PythonClassReference classReference,
                                @Nullable Object owner,
                                @Nullable Object[] constructorArguments) {
        this.classReference = classReference;
        this.displayName = classReference.displayName();
        this.owner = owner;
        this.constructorArguments = constructorArguments;
        this.valueFactory = null;
    }

    private PythonPooledInstance(String displayName,
                                 Function<Context, Value> valueFactory) {
        this.classReference = null;
        this.displayName = displayName;
        this.owner = null;
        this.constructorArguments = null;
        this.valueFactory = valueFactory;
    }

    /**
     * A holder whose value for each context is built by the given function.
     *
     * <p>For an AOP-proxied pooled bean. A Python proxy belongs to the context it was created in, so
     * one proxy cannot serve a pooled bean; the function creates a proxy in whichever context asks,
     * and they are cached here like any other per-context value.
     *
     * @param displayName What to call the bean in a diagnostic
     * @param valueFactory Builds the value for a context
     * @return A holder backed by that function
     */
    @UsedByGeneratedCode
    public static PythonPooledInstance producedBy(String displayName,
                                                 Function<Context, Value> valueFactory) {
        return new PythonPooledInstance(displayName, valueFactory);
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
        if (owner == null) {
            return;
        }
        try {
            instance.putMember(ValueCoercible.HOST_OBJECT_MEMBER, new ValueCoercible.HostObjectReference(owner));
        } catch (RuntimeException e) {
            // A class that restricts its attributes -- __slots__ -- refuses the member. Nothing fails
            // here, but a value of this class coming back to Java is then wrapped from the value
            // alone, and such a wrapper cannot materialise in another context: the first request the
            // pool serves on a different one fails with UnsupportedOperationException from #in. That
            // only appears under load, so leave something behind that points at the cause.
            if (LOG.isDebugEnabled()) {
                LOG.debug("The pooled Python class [{}] does not accept the {} member, so a value of it "
                    + "returning to Java cannot be resolved to its bean: {}",
                    displayName, ValueCoercible.HOST_OBJECT_MEMBER, e.getMessage());
            }
        }
    }

    /**
     * A holder seeded with an instance that already exists in a context.
     *
     * <p>Used for the wrapper of an AOP proxy target. The proxy is a Python object created for one
     * context and handed to Java to be wrapped, so the wrapper stands for that object in that
     * context. It carries no constructor arguments, because the proxy is not built from them, so it
     * cannot materialise in a different context -- and it is not asked to: a proxy belongs to the
     * context it was created for.
     *
     * @param classReference The Python class
     * @param existing The instance, belonging to the context it came from
     * @return A holder of that instance
     */
    @UsedByGeneratedCode
    public static PythonPooledInstance seeded(PythonContextRuntime.PythonClassReference classReference, Value existing) {
        PythonPooledInstance holder = new PythonPooledInstance(classReference, null, null);
        holder.publish(existing.getContext(), existing);
        return holder;
    }

    /**
     * @return The Python class these instances are of, or {@code null} for a factory-built holder
     */
    @Nullable
    public PythonContextRuntime.PythonClassReference classReference() {
        return classReference;
    }

    /**
     * This bean's instance in the given context, constructing it there on first use.
     *
     * <p>Construction runs before the value is published, and holds nothing while it runs. Guest code
     * must not run under a lock the runtime might need: Python re-entering for the same bean would wait
     * on the thread holding it, which is itself waiting on the interpreter lock. A duplicate
     * construction is cheaper than that, and the loser is discarded.
     *
     * @param context The context
     * @return The instance belonging to that context
     */
    public Value in(Context context) {
        Value existing = instances.get(context);
        if (existing != null) {
            return existing;
        }
        if (valueFactory != null) {
            Value produced = valueFactory.apply(context);
            return publish(context, produced);
        }
        PythonContextRuntime.PythonClassReference reference = classReference;
        if (constructorArguments == null || reference == null) {
            throw new UnsupportedOperationException("The pooled Python bean [" + displayName
                + "] stands for an object of another context and cannot be materialised in this one: it was wrapped "
                + "from a value, which carries neither the dependencies it was built from nor a reference to the bean.");
        }
        Value type = PythonContextRuntime.findClass(reference, context);
        Value created = type.canInstantiate()
            ? type.newInstance(PythonCoercion.coerceDependenciesToContext(context, constructorArguments))
            : type;
        attachOwner(created);
        return publish(context, created);
    }

    /**
     * Stores this context's instance and tells the context to call {@link #forget} when it closes.
     *
     * <p>Registering with the context takes the registry's lock, which is safe only because this class
     * holds no lock of its own: {@code ContextState.clear} calls {@link #forget} while holding that
     * lock, so a bean-level monitor here would be the other half of a deadlock.
     *
     * @param context The context
     * @param instance The instance created for it
     * @return The stored instance, which is an earlier one when two threads raced
     */
    private Value publish(Context context, Value instance) {
        Value prior = instances.putIfAbsent(context, instance);
        if (prior != null) {
            return prior;
        }
        PythonContextRegistry.state(context).pooledHolders.add(this);
        return instance;
    }

    /**
     * How many contexts hold an instance of this bean. For tests of the lifetime: the map is not
     * observable otherwise, and both of its leaks were invisible rather than wrong.
     *
     * @return The number of contexts with an instance
     */
    int contextCount() {
        return instances.size();
    }

    /**
     * Drops this context's instance, because the context is closing.
     *
     * <p>Takes no monitor: {@code ContextState.clear} calls this under the registry lock.
     *
     * @param context The context being closed
     */
    void forget(Context context) {
        instances.remove(context);
    }
}
