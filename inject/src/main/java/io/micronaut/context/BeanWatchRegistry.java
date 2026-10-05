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
package io.micronaut.context;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.processor.BeanDefinitionProcessor;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.ResourceKind;
import io.micronaut.context.watch.BeanChange;
import io.micronaut.context.watch.BeanDefinitionChange;
import io.micronaut.context.watch.BeanDefinitionWatcher;
import io.micronaut.context.watch.BeanWatch;
import io.micronaut.context.watch.BeanWatcher;
import io.micronaut.context.watch.ClassChangeWatcher;
import io.micronaut.context.watch.ConfigurationChange;
import io.micronaut.context.watch.ConfigurationWatcher;
import io.micronaut.context.watch.ExecutableMethodChange;
import io.micronaut.context.watch.ExecutableMethodWatcher;
import io.micronaut.context.watch.ResourceChange;
import io.micronaut.context.watch.ResourceSelector;
import io.micronaut.context.watch.ResourceWatcher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/**
 * The watches registered with a {@link DefaultBeanContext}, and the delivery of batches to them.
 *
 * <p>A watch registered before the context has read its definitions receives its startup batch when
 * the context initializes; one registered later receives it at once, so a watcher never needs a
 * separate startup path. A watch registered while a bean is being created belongs to that bean and is
 * closed when the bean is destroyed. Watchers run in {@link OrderUtil} order and a failing one does
 * not stop the others.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@NullMarked
final class BeanWatchRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(BeanWatchRegistry.class);

    /**
     * The watch returned where nothing is registered: it was never active.
     */
    static final BeanWatch INACTIVE = new BeanWatch() {
        @Override
        public void close() {
        }

        @Override
        public boolean isActive() {
            return false;
        }
    };

    private final DefaultBeanContext context;
    private final List<Registration> registrations = new CopyOnWriteArrayList<>();
    private final ThreadLocal<Deque<Owner>> creating = ThreadLocal.withInitial(ArrayDeque::new);
    private final Map<Object, List<Registration>> ownedByBean = new IdentityHashMap<>();
    private final Set<Object> adaptedProcessors = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<ResourceKind, ResourceChange> resourceState = new ConcurrentHashMap<>();
    /**
     * Held, briefly and never across a watcher, to number a change and queue its batches, to register a watch
     * and queue its first batch, and to read or update the resource state: a watch's queue is therefore in the
     * order of the numbers, and its first batch ahead of every change numbered after it was registered.
     */
    private final Object enqueue = new Object();
    /**
     * Numbers the changes as their batches are queued, so that a watch knows which ones its first batch shows.
     */
    private final AtomicLong epoch = new AtomicLong();
    /**
     * How many watches the current thread is delivering to: such a thread never waits for another one to
     * deliver, which is what rules out a cycle of threads each waiting inside a watcher for the other.
     */
    private final ThreadLocal<int[]> draining = ThreadLocal.withInitial(() -> new int[1]);
    private volatile boolean started;

    BeanWatchRegistry(DefaultBeanContext context) {
        this.context = context;
    }

    <T> BeanWatch watchDefinitions(Argument<T> beanType, @Nullable Qualifier<T> qualifier, BeanDefinitionWatcher<T> watcher) {
        return register(new DefinitionRegistration<>(beanType, qualifier, watcher, false));
    }

    <T> BeanWatch watchBeans(Argument<T> beanType, @Nullable Qualifier<T> qualifier, BeanWatcher<T> watcher) {
        return register(new BeanRegistrationWatch<>(beanType, qualifier, watcher));
    }

    <A extends Annotation> BeanWatch watchMethods(Class<A> annotationType, ExecutableMethodWatcher<A> watcher) {
        return register(new MethodRegistration<>(annotationType, watcher, false));
    }

    BeanWatch watchConfiguration(String prefix, ConfigurationWatcher watcher, boolean initial) {
        return register(new ConfigurationRegistration(prefix, watcher, initial));
    }

    BeanWatch watchResources(ResourceSelector selector, ResourceWatcher watcher) {
        return register(new ResourceRegistration(selector, watcher));
    }

    BeanWatch watchClassChanges(ClassChangeWatcher watcher) {
        return register(new ClassChangeRegistration(watcher));
    }

    /**
     * Feeds an {@link ExecutableMethodProcessor} the methods added after startup, as a watcher that sees
     * only additions. That the processor needs this adapter is what tells a launcher it is not
     * reload-capable.
     */
    <A extends Annotation> void adapt(Class<A> annotationType, ExecutableMethodProcessor<A> processor) {
        if (!markAdapted(processor)) {
            return;
        }
        MethodRegistration<A> registration = new MethodRegistration<>(annotationType, change -> {
            if (change.initial() || change.added().isEmpty()) {
                return;
            }
            if (processor instanceof LifeCycle<?> cycle) {
                cycle.start();
            }
            try {
                for (ExecutableMethodChange.Entry<A> entry : change.added()) {
                    // the processor contract: methods marked for processing at startup, as the startup pass feeds it
                    if (entry.definition().requiresMethodProcessing()
                        && entry.method().booleanValue(Executable.class, Executable.MEMBER_PROCESS_ON_STARTUP).orElse(false)) {
                        processor.process((BeanDefinition<Object>) entry.definition(), (ExecutableMethod<Object, ?>) entry.method());
                    }
                }
            } finally {
                if (processor instanceof LifeCycle<?> cycle) {
                    cycle.stop();
                }
            }
        }, true);
        registration.adaptedProcessor = processor;
        register(registration);
    }

    /**
     * Feeds a {@link BeanDefinitionProcessor} the definitions added after startup, as a watcher that sees
     * only additions.
     */
    void adapt(Class<? extends Annotation> annotationType, BeanDefinitionProcessor<?> processor) {
        if (!markAdapted(processor)) {
            return;
        }
        DefinitionRegistration<Object> registration = new DefinitionRegistration<>(null, Qualifiers.byStereotype(annotationType), change -> {
            if (change.initial() || change.added().isEmpty()) {
                return;
            }
            if (processor instanceof LifeCycle<?> cycle) {
                cycle.start();
            }
            try {
                for (BeanDefinition<Object> definition : change.added()) {
                    processor.process(definition, context);
                }
            } finally {
                if (processor instanceof LifeCycle<?> cycle) {
                    cycle.stop();
                }
            }
        }, true);
        registration.adaptedProcessor = processor;
        register(registration);
    }

    private boolean markAdapted(Object processor) {
        synchronized (adaptedProcessors) {
            return adaptedProcessors.add(processor);
        }
    }

    /**
     * @return The processors fed through an adapter, which see additions only
     */
    Collection<Object> adaptedProcessors() {
        List<Object> adapted = new ArrayList<>();
        for (Registration registration : registrations) {
            if (registration.adapted && !registration.closed.get() && registration.adaptedProcessor != null) {
                adapted.add(registration.adaptedProcessor);
            }
        }
        return adapted;
    }

    private BeanWatch register(Registration registration) {
        Deque<Owner> owners = creating.get();
        Owner owner = owners.peek();
        if (owner != null) {
            registration.owner = owner;
            owner.registrations.add(registration);
        }
        boolean deliverNow;
        synchronized (enqueue) {
            registrations.add(registration);
            deliverNow = started;
            if (deliverNow) {
                registration.queueFirstBatch();
                synchronized (registration.queue) {
                    // claimed before any change can be queued behind it: the registering thread delivers the first batch
                    registration.drainer = Thread.currentThread();
                }
            }
        }
        if (deliverNow) {
            drain(registration);
        }
        return registration;
    }

    /**
     * Delivers the startup batch to every watch registered so far; later registrations get theirs at once.
     */
    void start() {
        List<Delivery> firstBatches = new ArrayList<>();
        synchronized (enqueue) {
            if (started) {
                return;
            }
            started = true;
            for (Registration registration : ordered()) {
                firstBatches.add(registration.queueFirstBatch());
            }
        }
        firstBatches.forEach(this::deliver);
    }

    /**
     * Whether a change, numbered when it was queued, is still to be delivered to a watch when its turn comes:
     * the watch is open, and its first batch was read before the change was applied. A first batch that is
     * still to be read, or that was read after the change, shows it already.
     *
     * @param registration The watch
     * @param sequence The number of the change
     * @return True to deliver
     */
    private static boolean followsFirstBatch(Registration registration, long sequence) {
        return !registration.closed.get() && registration.initialDelivered.get() && sequence > registration.readEpoch;
    }

    /**
     * Has a queued batch delivered: by this thread, which drains the watch's queue when no other thread does,
     * or by the thread draining it, which this one waits for unless it is itself delivering to a watch.
     *
     * @param delivery The batch
     * @return Whether it was delivered, or skipped, by the time this returns
     */
    private boolean deliver(Delivery delivery) {
        Registration registration = delivery.registration;
        Thread current = Thread.currentThread();
        boolean claim;
        Thread drainer;
        synchronized (registration.queue) {
            drainer = registration.drainer;
            claim = drainer == null;
            if (claim) {
                registration.drainer = current;
            }
        }
        if (claim) {
            drain(registration);
            return true;
        }
        if (drainer == current || draining.get()[0] > 0) {
            // delivering to a watch already: the thread draining this queue delivers the batch after the ones before it
            return !delivery.handOff();
        }
        delivery.awaitDone();
        return true;
    }

    /**
     * Delivers the queued batches of a watch, in order, until its queue is empty. The current thread has claimed
     * the queue.
     */
    private void drain(Registration registration) {
        int[] depth = draining.get();
        depth[0]++;
        try {
            while (true) {
                Delivery next;
                synchronized (registration.queue) {
                    next = registration.queue.poll();
                    if (next == null) {
                        registration.drainer = null;
                        return;
                    }
                }
                next.run();
            }
        } finally {
            depth[0]--;
        }
    }

    /**
     * Delivers a change of definitions: the removed ones are no longer resolvable, the added ones are.
     * The caller applied the change before calling this.
     */
    void definitionsChanged(Collection<? extends BeanDefinition<?>> removed, Collection<? extends BeanDefinition<?>> added) {
        if (removed.isEmpty() && added.isEmpty()) {
            return;
        }
        List<Delivery> deliveries = new ArrayList<>();
        synchronized (enqueue) {
            if (!started) {
                return;
            }
            long sequence = epoch.incrementAndGet();
            for (Registration registration : ordered()) {
                if (!registration.closed.get() && registration.watchesDefinitions()) {
                    deliveries.add(registration.enqueue(new Delivery(registration, sequence, () -> registration.deliverDefinitions(removed, added))));
                }
            }
        }
        deliveries.forEach(this::deliver);
    }

    /**
     * Delivers a configuration change to the watches whose prefix it touches, after the configuration
     * beans were rebound. A watch that another thread is delivering to while this one delivers to a watch
     * itself is given the change by that thread, which acts on its answer: such an answer is not among the
     * outcomes returned.
     *
     * @return The outcomes, one per watch delivered to
     */
    List<ConfigurationWatcher.Outcome> configurationChanged(ConfigurationChange change) {
        List<Delivery> deliveries = new ArrayList<>();
        synchronized (enqueue) {
            long sequence = epoch.incrementAndGet();
            for (Registration registration : ordered()) {
                if (registration.closed.get() || !(registration instanceof ConfigurationRegistration configurationRegistration)
                    || !change.touches(configurationRegistration.prefix)) {
                    continue;
                }
                Delivery delivery = new Delivery(registration, sequence, null);
                delivery.action = () -> delivery.outcome = configurationRegistration.watcher.onChange(change);
                // answered after the caller stopped waiting: the thread that delivered acts on the answer
                delivery.afterHandOff = () -> recreate(List.of(delivery));
                deliveries.add(registration.enqueue(delivery));
            }
        }
        List<Delivery> answered = new ArrayList<>(deliveries.size());
        for (Delivery delivery : deliveries) {
            if (deliver(delivery)) {
                answered.add(delivery);
            }
        }
        return recreate(answered);
    }

    /**
     * Collects the answers of delivered configuration batches, in order, and replaces the beans whose watch
     * answered {@link ConfigurationWatcher.Outcome#RECREATE}.
     *
     * @return The outcomes, one per batch delivered
     */
    private List<ConfigurationWatcher.Outcome> recreate(List<Delivery> deliveries) {
        List<ConfigurationWatcher.Outcome> outcomes = new ArrayList<>();
        List<Owner> toRecreate = new ArrayList<>();
        // the owner each RECREATE outcome asked for, by the outcome's own index, taken as the outcome is added: a
        // second walk over the registrations would not line up with the outcomes once a watch failed or closed
        Map<Integer, Owner> recreateOutcomes = new LinkedHashMap<>();
        for (Delivery delivery : deliveries) {
            ConfigurationWatcher.Outcome outcome = delivery.outcome;
            if (outcome == null) {
                // skipped, or failed: no answer
                continue;
            }
            if (outcome == ConfigurationWatcher.Outcome.RECREATE) {
                Owner owner = delivery.owner;
                if (owner == null || owner.bean == null) {
                    LOG.warn("A configuration watch on [{}] answered RECREATE but was not registered while its bean was created; nothing to recreate", ((ConfigurationRegistration) delivery.registration).prefix);
                    outcome = ConfigurationWatcher.Outcome.IGNORED;
                } else {
                    if (!toRecreate.contains(owner)) {
                        toRecreate.add(owner);
                    }
                    recreateOutcomes.put(outcomes.size(), owner);
                }
            }
            outcomes.add(outcome);
        }
        Set<Owner> notRecreated = new LinkedHashSet<>();
        for (Owner owner : toRecreate) {
            Object bean = owner.bean;
            if (bean == null || !context.recreateBean(bean)) {
                LOG.warn("A configuration watch answered RECREATE for a bean of [{}] the context does not hold, such as a prototype; nothing recreated", owner.definition.getBeanType().getName());
                notRecreated.add(owner);
            }
        }
        if (!notRecreated.isEmpty()) {
            // the outcome reported is what happened, not what the watcher asked for
            for (Map.Entry<Integer, Owner> entry : recreateOutcomes.entrySet()) {
                if (notRecreated.contains(entry.getValue())) {
                    outcomes.set(entry.getKey(), ConfigurationWatcher.Outcome.IGNORED);
                }
            }
        }
        return outcomes;
    }

    /**
     * Delivers a resource change to the watches whose selector it concerns. An initial change replaces
     * what the registry knows of the kind, a later one updates it, and a watch registered afterwards
     * receives that state as its first batch.
     */
    void resourcesChanged(ResourceChange change) {
        List<Delivery> deliveries = new ArrayList<>();
        synchronized (enqueue) {
            // the state and the number of the change move together, so that a first batch read from the state
            // knows exactly which changes it shows
            ResourceChange known = resourceState.compute(change.kind(), (kind, state) -> {
                if (change.initial() || state == null) {
                    return change.initial() ? change : null;
                }
                // the state a late watch starts from follows every change: what went is gone, what came is present
                Set<Path> present = new LinkedHashSet<>(state.changed());
                change.removed().forEach(present::remove);
                present.addAll(change.changed());
                // the roots are the latest reported: a later batch may add or drop a root
                return new ResourceChange(kind, change.roots(), new ArrayList<>(present), List.of(), true);
            });
            long sequence = epoch.incrementAndGet();
            for (Registration registration : ordered()) {
                if (registration.closed.get() || !(registration instanceof ResourceRegistration resourceRegistration)
                    || resourceRegistration.selector.kind() != change.kind()) {
                    continue;
                }
                ResourceChange selected = change.select(resourceRegistration.selector);
                if (selected.isEmpty() && !change.initial()) {
                    continue;
                }
                Delivery delivery = new Delivery(registration, sequence, () -> resourceRegistration.watcher.onChange(selected));
                delivery.filter = () -> {
                    if (change.initial()) {
                        if (registration.initialDelivered.get() && sequence <= registration.readEpoch) {
                            // its first batch was read from this state already
                            return false;
                        }
                        // reported before the context started: this is the watch's first batch, not to be repeated at startup
                        registration.initialDelivered.set(true);
                        registration.readEpoch = sequence;
                        return true;
                    }
                    if (!registration.initialDelivered.get()) {
                        // the first batch, still to be read, is the state this change left; with no state, nothing will be
                        return known == null;
                    }
                    return sequence > registration.readEpoch;
                };
                deliveries.add(registration.enqueue(delivery));
            }
        }
        deliveries.forEach(this::deliver);
    }

    /**
     * Delivers a class change to the class change watches, in order, failures isolated.
     */
    void classesChanged(ClassChangeEvent change) {
        List<Delivery> deliveries = new ArrayList<>();
        synchronized (enqueue) {
            long sequence = epoch.incrementAndGet();
            for (Registration registration : ordered()) {
                if (!registration.closed.get() && registration instanceof ClassChangeRegistration classChangeRegistration) {
                    deliveries.add(registration.enqueue(new Delivery(registration, sequence, () -> classChangeRegistration.watcher.onChange(change))));
                }
            }
        }
        deliveries.forEach(this::deliver);
    }

    /**
     * One batch queued for one watch, delivered when its turn comes.
     */
    private static final class Delivery {
        final Registration registration;
        final long sequence;
        @Nullable
        final Owner owner;
        @Nullable
        Runnable action;
        /**
         * Decides, when its turn comes, whether the batch is delivered; by default when it follows the first batch.
         */
        @Nullable
        BooleanSupplier filter;
        /**
         * Run by the delivering thread when the batch was delivered after the thread that queued it stopped waiting.
         */
        @Nullable
        Runnable afterHandOff;
        volatile ConfigurationWatcher.@Nullable Outcome outcome;
        private boolean done;
        private boolean handedOff;

        Delivery(Registration registration, long sequence, @Nullable Runnable action) {
            this.registration = registration;
            this.sequence = sequence;
            this.action = action;
            this.owner = registration.owner;
        }

        void run() {
            try {
                boolean deliver = filter != null ? filter.getAsBoolean() && !registration.closed.get() : followsFirstBatch(registration, sequence);
                if (deliver && action != null) {
                    action.run();
                }
            } catch (Throwable e) {
                report(registration, e);
            }
            boolean afterCallerLeft;
            synchronized (this) {
                done = true;
                afterCallerLeft = handedOff;
                notifyAll();
            }
            if (afterCallerLeft && afterHandOff != null) {
                try {
                    afterHandOff.run();
                } catch (Throwable e) {
                    report(registration, e);
                }
            }
        }

        /**
         * Leaves the batch to the thread draining the queue.
         *
         * @return False if it was delivered already, true if it is left
         */
        synchronized boolean handOff() {
            if (done) {
                return false;
            }
            handedOff = true;
            return true;
        }

        synchronized void awaitDone() {
            boolean interrupted = false;
            while (!done) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Marks the start of a bean's creation: watches registered until {@link #endCreation} belong to the bean.
     */
    void beginCreation(BeanDefinition<?> definition) {
        creating.get().push(new Owner(definition));
    }

    /**
     * Marks the end of a bean's creation, attaching the watches registered meanwhile to the instance, or
     * closing them when the creation failed. Paired with {@link #beginCreation} by definition, so an
     * unpaired end from another creation path in between is ignored.
     *
     * @param definition The definition the creation began with
     * @param bean The bean created, as the creation listeners left it, or null if the creation failed
     */
    void endCreation(BeanDefinition<?> definition, @Nullable Object bean) {
        Deque<Owner> owners = creating.get();
        Owner top = owners.peek();
        if (top == null || top.definition != definition) {
            // not the creation this call pairs with: a bean created by another path ended in between
            if (owners.isEmpty()) {
                creating.remove();
            }
            return;
        }
        Owner owner = owners.poll();
        if (owners.isEmpty()) {
            creating.remove();
        }
        if (owner.registrations.isEmpty()) {
            return;
        }
        if (bean == null) {
            for (Registration registration : owner.registrations) {
                registration.close();
            }
            return;
        }
        owner.bean = bean;
        synchronized (ownedByBean) {
            List<Registration> active = new ArrayList<>(owner.registrations.size());
            for (Registration registration : owner.registrations) {
                if (!registration.closed.get()) {
                    active.add(registration);
                }
            }
            if (!active.isEmpty()) {
                ownedByBean.computeIfAbsent(bean, b -> new ArrayList<>()).addAll(active);
            }
        }
    }

    /**
     * Closes the watches a destroyed bean registered while it was created.
     */
    void closeOwnedBy(@Nullable Object bean) {
        if (bean == null) {
            return;
        }
        List<Registration> owned;
        synchronized (ownedByBean) {
            owned = ownedByBean.remove(bean);
        }
        if (owned != null) {
            for (Registration registration : new ArrayList<>(owned)) {
                registration.close();
            }
        }
    }

    /**
     * Closes every watch, when the context stops.
     */
    void clear() {
        for (Registration registration : new ArrayList<>(registrations)) {
            registration.close();
        }
        registrations.clear();
        synchronized (ownedByBean) {
            ownedByBean.clear();
        }
        synchronized (adaptedProcessors) {
            adaptedProcessors.clear();
        }
        resourceState.clear();
        started = false;
    }

    private List<Registration> ordered() {
        List<Registration> ordered = new ArrayList<>(registrations);
        ordered.sort((a, b) -> Integer.compare(OrderUtil.getOrder(a.watcher()), OrderUtil.getOrder(b.watcher())));
        return ordered;
    }

    private static void report(Registration registration, Throwable e) {
        LOG.error("Watcher [{}] failed to process a change: {}", registration.watcher(), e.getMessage(), e);
    }

    /**
     * The bean a watch was registered by, while it was being created.
     */
    private static final class Owner {
        final BeanDefinition<?> definition;
        final List<Registration> registrations = new ArrayList<>(2);
        @Nullable
        Object bean;

        Owner(BeanDefinition<?> definition) {
            this.definition = definition;
        }
    }

    /**
     * A registered watch.
     */
    private abstract class Registration implements BeanWatch {
        final AtomicBoolean closed = new AtomicBoolean();
        final AtomicBoolean initialDelivered = new AtomicBoolean();
        /**
         * The batches queued for the watch, the first one included, delivered one at a time and in order by the
         * thread that drains the queue; also the monitor that guards the queue and its drainer.
         */
        final ArrayDeque<Delivery> queue = new ArrayDeque<>();
        /**
         * The thread delivering the queued batches, null while none is.
         */
        @Nullable
        Thread drainer;
        /**
         * The number of the last change applied before the first batch was read; -1 until it is read, and for
         * a watch without a first batch, which receives every change delivered after it is registered.
         */
        volatile long readEpoch = -1;
        final boolean adapted;
        @Nullable
        Object adaptedProcessor;
        @Nullable
        Owner owner;

        Registration(boolean adapted) {
            this.adapted = adapted;
        }

        Registration(boolean adapted, boolean firstBatch) {
            this(adapted);
            if (!firstBatch) {
                // nothing to read first: every change delivered from now on is for this watch
                initialDelivered.set(true);
            }
        }

        /**
         * Queues a batch. Called with the registry's enqueue lock held, so that batches queue in the order of their numbers.
         *
         * @param delivery The batch, complete
         * @return The batch
         */
        Delivery enqueue(Delivery delivery) {
            synchronized (queue) {
                queue.add(delivery);
            }
            return delivery;
        }

        /**
         * Queues the first batch, which reads the state when its turn comes. Called with the enqueue lock held.
         *
         * @return The batch
         */
        Delivery queueFirstBatch() {
            Delivery first = new Delivery(this, 0, () -> {
                // a watch registered while the context starts gets its first batch once, from whichever queued it first
                if (!closed.get() && initialDelivered.compareAndSet(false, true)) {
                    // taken before the state is read: every change numbered up to here was applied before the read
                    readEpoch = epoch.get();
                    deliverInitial();
                }
            });
            first.filter = () -> true;
            return enqueue(first);
        }

        abstract Object watcher();

        abstract void deliverInitial();

        abstract void deliverDefinitions(Collection<? extends BeanDefinition<?>> removed, Collection<? extends BeanDefinition<?>> added);

        /**
         * @return Whether the watch sees definition changes, so that one is queued for it
         */
        boolean watchesDefinitions() {
            return true;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            // a closed watch is forgotten entirely: the registry must not keep its watcher, or the bean that owned it
            registrations.remove(this);
            Owner owningBean = owner;
            if (owningBean != null) {
                owningBean.registrations.remove(this);
                Object bean = owningBean.bean;
                if (bean != null) {
                    synchronized (ownedByBean) {
                        List<Registration> owned = ownedByBean.get(bean);
                        if (owned != null) {
                            owned.remove(this);
                            if (owned.isEmpty()) {
                                ownedByBean.remove(bean);
                            }
                        }
                    }
                }
                owner = null;
            }
            if (adaptedProcessor != null) {
                synchronized (adaptedProcessors) {
                    adaptedProcessors.remove(adaptedProcessor);
                }
            }
        }

        @Override
        public boolean isActive() {
            return !closed.get();
        }
    }

    private final class DefinitionRegistration<T> extends Registration {
        /**
         * The type watched, or null for every definition the qualifier accepts, whatever type it exposes.
         */
        @Nullable
        private final Argument<T> beanType;
        @Nullable
        private final Qualifier<T> qualifier;
        private final BeanDefinitionWatcher<T> watcher;

        DefinitionRegistration(@Nullable Argument<T> beanType, @Nullable Qualifier<T> qualifier, BeanDefinitionWatcher<T> watcher, boolean adapted) {
            super(adapted);
            this.beanType = beanType;
            this.qualifier = qualifier;
            this.watcher = watcher;
        }

        @Override
        Object watcher() {
            return watcher;
        }

        @Override
        void deliverInitial() {
            Collection<BeanDefinition<T>> current = current();
            watcher.onChange(new BeanDefinitionChange<>(new ArrayList<>(current), List.of(), current, true));
        }

        @Override
        void deliverDefinitions(Collection<? extends BeanDefinition<?>> removed, Collection<? extends BeanDefinition<?>> added) {
            List<BeanDefinition<T>> removedHere = select(beanType, qualifier, removed);
            List<BeanDefinition<T>> addedHere = select(beanType, qualifier, added);
            if (removedHere.isEmpty() && addedHere.isEmpty()) {
                return;
            }
            watcher.onChange(new BeanDefinitionChange<>(addedHere, removedHere, current(), false));
        }

        @SuppressWarnings("unchecked")
        private Collection<BeanDefinition<T>> current() {
            if (beanType != null) {
                return context.getBeanDefinitions(beanType, qualifier);
            }
            return (Collection<BeanDefinition<T>>) (Collection<?>) context.getBeanDefinitions((Qualifier<Object>) qualifier);
        }
    }

    private final class BeanRegistrationWatch<T> extends Registration {
        private final Argument<T> beanType;
        @Nullable
        private final Qualifier<T> qualifier;
        private final BeanWatcher<T> watcher;
        private final Map<BeanDefinition<T>, BeanRegistration<T>> known = new IdentityHashMap<>();

        BeanRegistrationWatch(Argument<T> beanType, @Nullable Qualifier<T> qualifier, BeanWatcher<T> watcher) {
            super(false);
            this.beanType = beanType;
            this.qualifier = qualifier;
            this.watcher = watcher;
        }

        @Override
        Object watcher() {
            return watcher;
        }

        @Override
        void deliverInitial() {
            Collection<BeanRegistration<T>> current = context.getBeanRegistrations(beanType, qualifier);
            remember(current);
            watcher.onChange(new BeanChange<>(new ArrayList<>(current), List.of(), current, true));
        }

        @Override
        void deliverDefinitions(Collection<? extends BeanDefinition<?>> removed, Collection<? extends BeanDefinition<?>> added) {
            List<BeanDefinition<T>> removedHere = select(beanType, qualifier, removed);
            List<BeanDefinition<T>> addedHere = select(beanType, qualifier, added);
            if (removedHere.isEmpty() && addedHere.isEmpty()) {
                return;
            }
            List<BeanRegistration<T>> gone = new ArrayList<>(removedHere.size());
            for (BeanDefinition<T> definition : removedHere) {
                BeanRegistration<T> registration = known.remove(definition);
                if (registration != null) {
                    gone.add(registration);
                }
            }
            // the registrations delivered before stay what they are: only the added definitions are resolved,
            // one by one, so a prototype among the candidates is not created again for every batch
            List<BeanRegistration<T>> came = new ArrayList<>(addedHere.size());
            for (BeanDefinition<T> definition : addedHere) {
                if (known.containsKey(definition)) {
                    // its bean was delivered already, in the first batch read after the definition was added
                    continue;
                }
                BeanRegistration<T> registration = context.getBeanRegistration(definition);
                came.add(registration);
                known.put(definition, registration);
            }
            if (came.isEmpty() && gone.isEmpty()) {
                return;
            }
            watcher.onChange(new BeanChange<>(came, gone, new ArrayList<>(known.values()), false));
        }

        private void remember(Collection<BeanRegistration<T>> current) {
            for (BeanRegistration<T> registration : current) {
                known.put(registration.getBeanDefinition(), registration);
            }
        }
    }

    private final class MethodRegistration<A extends Annotation> extends Registration {
        private final Class<A> annotationType;
        private final ExecutableMethodWatcher<A> watcher;
        private final boolean processedAtStartup;

        MethodRegistration(Class<A> annotationType, ExecutableMethodWatcher<A> watcher, boolean adapted) {
            super(adapted);
            this.annotationType = annotationType;
            this.watcher = watcher;
            this.processedAtStartup = context.resolveMetadata(annotationType)
                .booleanValue(Executable.class, Executable.MEMBER_PROCESS_ON_STARTUP).orElse(false);
        }

        @Override
        Object watcher() {
            return watcher;
        }

        @Override
        void deliverInitial() {
            List<ExecutableMethodChange.Entry<A>> current = current();
            watcher.onChange(new ExecutableMethodChange<>(current, List.of(), current, true));
        }

        @Override
        void deliverDefinitions(Collection<? extends BeanDefinition<?>> removed, Collection<? extends BeanDefinition<?>> added) {
            List<ExecutableMethodChange.Entry<A>> removedHere = entries(removed);
            List<ExecutableMethodChange.Entry<A>> addedHere = entries(added);
            if (removedHere.isEmpty() && addedHere.isEmpty()) {
                return;
            }
            watcher.onChange(new ExecutableMethodChange<>(addedHere, removedHere, current(), false));
        }

        private List<ExecutableMethodChange.Entry<A>> current() {
            // an annotation processed at startup has every bean carrying it in the processed-beans index, which
            // costs nothing to read; any other annotation can sit on a method without marking its bean, and only
            // a scan of the definitions finds those
            Set<BeanDefinition<?>> candidates = new LinkedHashSet<>();
            if (processedAtStartup) {
                candidates.addAll(context.processedBeanDefinitions());
                candidates.addAll(context.getBeanDefinitions(Qualifiers.byStereotype(annotationType)));
            } else {
                candidates.addAll(context.getAllBeanDefinitions());
            }
            return entries(candidates);
        }

        private List<ExecutableMethodChange.Entry<A>> entries(Collection<? extends BeanDefinition<?>> definitions) {
            List<ExecutableMethodChange.Entry<A>> entries = new ArrayList<>();
            for (BeanDefinition<?> definition : definitions) {
                for (ExecutableMethod<?, ?> method : definition.getExecutableMethods()) {
                    if (method.getAnnotationMetadata().hasStereotype(annotationType)) {
                        entries.add(new ExecutableMethodChange.Entry<>(definition, method));
                    }
                }
            }
            return entries;
        }
    }

    private final class ConfigurationRegistration extends Registration {
        private final String prefix;
        private final ConfigurationWatcher watcher;

        private final boolean initial;

        ConfigurationRegistration(String prefix, ConfigurationWatcher watcher, boolean initial) {
            super(false, initial);
            this.prefix = prefix;
            this.watcher = watcher;
            this.initial = initial;
        }

        @Override
        Object watcher() {
            return watcher;
        }

        @Override
        void deliverInitial() {
            if (initial) {
                // the watcher reads the configuration as it is now; what it answers is not acted on, since its
                // bean, if it has one, is still being created
                watcher.onChange(ConfigurationChange.ofInitial());
            }
        }

        @Override
        void deliverDefinitions(Collection<? extends BeanDefinition<?>> removed, Collection<? extends BeanDefinition<?>> added) {
        }

        @Override
        boolean watchesDefinitions() {
            return false;
        }
    }

    private final class ResourceRegistration extends Registration {
        private final ResourceSelector selector;
        private final ResourceWatcher watcher;

        ResourceRegistration(ResourceSelector selector, ResourceWatcher watcher) {
            super(false);
            this.selector = selector;
            this.watcher = watcher;
        }

        @Override
        Object watcher() {
            return watcher;
        }

        @Override
        void deliverInitial() {
            ResourceChange state;
            synchronized (enqueue) {
                // read with the number of the last change it shows, which later changes are compared with
                readEpoch = epoch.get();
                state = resourceState.get(selector.kind());
            }
            if (state != null) {
                watcher.onChange(state.select(selector));
            }
        }

        @Override
        void deliverDefinitions(Collection<? extends BeanDefinition<?>> removed, Collection<? extends BeanDefinition<?>> added) {
        }

        @Override
        boolean watchesDefinitions() {
            return false;
        }
    }

    private final class ClassChangeRegistration extends Registration {
        private final ClassChangeWatcher watcher;

        ClassChangeRegistration(ClassChangeWatcher watcher) {
            super(false, false);
            this.watcher = watcher;
        }

        @Override
        Object watcher() {
            return watcher;
        }

        @Override
        void deliverInitial() {
            // classes have no startup batch: nothing changed yet
        }

        @Override
        void deliverDefinitions(Collection<? extends BeanDefinition<?>> removed, Collection<? extends BeanDefinition<?>> added) {
        }

        @Override
        boolean watchesDefinitions() {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> List<BeanDefinition<T>> select(@Nullable Argument<T> beanType, @Nullable Qualifier<T> qualifier, Collection<? extends BeanDefinition<?>> definitions) {
        if (definitions.isEmpty()) {
            return List.of();
        }
        Set<BeanDefinition<T>> candidates = new LinkedHashSet<>();
        for (BeanDefinition<?> definition : definitions) {
            if (beanType == null || definition.isCandidateBean(beanType)) {
                candidates.add((BeanDefinition<T>) definition);
            }
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        Stream<BeanDefinition<T>> stream = candidates.stream();
        if (qualifier != null) {
            stream = qualifier.reduce(beanType != null ? beanType.getType() : (Class<T>) Object.class, stream);
        }
        return stream.toList();
    }
}
