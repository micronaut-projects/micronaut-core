package io.micronaut.inject.context.watch;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.Qualifier;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.RuntimeBeanDefinition;
import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.env.DevelopmentMode;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.context.reload.ClassChange;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.ResourceKind;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.context.watch.BeanChange;
import io.micronaut.context.watch.BeanDefinitionChange;
import io.micronaut.context.watch.BeanDefinitionWatcher;
import io.micronaut.context.watch.BeanWatch;
import io.micronaut.context.watch.ConfigurationChange;
import io.micronaut.context.watch.ConfigurationWatcher.Outcome;
import io.micronaut.context.watch.ExecutableMethodChange;
import io.micronaut.context.watch.ResourceChange;
import io.micronaut.context.watch.ResourceSelector;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.QualifiedBeanType;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeanWatchTest {

    private static final Map<String, Object> PROPERTIES = Map.of("spec.name", "BeanWatchTest");
    private static final Map<String, Object> DEVELOPMENT = Map.of("spec.name", "BeanWatchTest", DevelopmentMode.PROPERTY, true);

    private static RuntimeBeanDefinition<Rule> rule(String name) {
        return RuntimeBeanDefinition.builder(Rule.class, () -> (Rule) () -> name).named(name).singleton(true).build();
    }

    private static ClassChangeEvent classChange(int generation) {
        return new ClassChangeEvent(BeanWatchTest.class, generation, Set.of(), BeanWatchTest.class.getClassLoader(),
            List.of(new ClassChange("app.Greeter", ClassChange.Kind.MODIFIED)), ReloadStrategy.RELOAD);
    }

    private static Set<Class<?>> beanTypes(List<BeanDefinition<Rule>> definitions) {
        return definitions.stream().map(BeanDefinition::getBeanType).collect(Collectors.toSet());
    }

    @Test
    void aDefinitionWatchGetsTheStartupBatchFirstThenOneBatchPerRuntimeRegistration() {
        List<BeanDefinitionChange<Rule>> changes = new ArrayList<>();
        try (ApplicationContext context = ApplicationContext.builder(PROPERTIES).build()) {
            WatchableBeanContext watchable = (WatchableBeanContext) context;
            BeanWatch watch = watchable.watchDefinitions(Rule.class, null, changes::add);

            context.start();

            // the startup batch lists what is present and removes nothing
            assertEquals(1, changes.size());
            assertTrue(changes.get(0).initial());
            assertEquals(Set.of(ARule.class, BRule.class), beanTypes(changes.get(0).added()));
            assertTrue(changes.get(0).removed().isEmpty());
            assertEquals(2, changes.get(0).current().size());
            assertTrue(changes.get(0).replaced().isEmpty());
            assertTrue(watch.isActive());

            // a definition registered at runtime is one batch, with the current set after it
            RuntimeBeanDefinition<Rule> runtime = rule("c");
            context.registerBeanDefinition(runtime);
            assertEquals(2, changes.size());
            assertFalse(changes.get(1).initial());
            assertEquals(List.of(runtime), changes.get(1).added());
            assertEquals(3, changes.get(1).current().size());
            assertEquals("c", context.getBean(runtime).name());

            // a closed watch is not woken
            watch.close();
            context.registerBeanDefinition(rule("d"));
            assertFalse(watch.isActive());
            assertEquals(2, changes.size());
        }
    }

    @Test
    void aWatchRegisteredOnARunningContextGetsItsStartupBatchAtOnceFilteredByQualifier() {
        try (ApplicationContext context = ApplicationContext.run(PROPERTIES)) {
            List<BeanDefinitionChange<Rule>> changes = new ArrayList<>();
            ((WatchableBeanContext) context).watchDefinitions(Argument.of(Rule.class), Qualifiers.byName("a"), changes::add);

            assertEquals(1, changes.size());
            assertTrue(changes.get(0).initial());
            assertEquals(Set.of(ARule.class), beanTypes(changes.get(0).added()));

            // an unqualified rule does not wake the watch on the name
            context.registerBeanDefinition(rule("x"));
            assertEquals(1, changes.size());
        }
    }

    @Test
    void aBeanWatchSeesInstancesAndCreatesThem() {
        try (ApplicationContext context = ApplicationContext.run(PROPERTIES)) {
            List<BeanChange<Rule>> changes = new ArrayList<>();
            ((WatchableBeanContext) context).watchBeans(Rule.class, null, changes::add);

            assertEquals(1, changes.size());
            assertTrue(changes.get(0).initial());
            assertEquals(Set.of("a", "b"), changes.get(0).added().stream().map(r -> r.getBean().name()).collect(Collectors.toSet()));

            RuntimeBeanDefinition<Rule> runtime = rule("c");
            context.registerBeanDefinition(runtime);

            // the added instance is delivered, the current set holds three, the earlier instances stay the same
            assertEquals(2, changes.size());
            assertEquals(List.of("c"), changes.get(1).added().stream().map(r -> r.getBean().name()).toList());
            assertTrue(changes.get(1).removed().isEmpty());
            assertEquals(3, changes.get(1).current().size());
            Set<BeanRegistration<Rule>> before = Set.copyOf(changes.get(0).current());
            assertTrue(changes.get(1).current().containsAll(before));
        }
    }

    @Test
    void aMethodWatchSeesTheAnnotatedMethodsPairsAMethodThatComesBackAndKnowsWhenOnlyTheBodyChanged() {
        try (ApplicationContext context = ApplicationContext.run(PROPERTIES)) {
            List<ExecutableMethodChange<Tick>> changes = new ArrayList<>();
            ((WatchableBeanContext) context).watchMethods(Tick.class, changes::add);

            // the startup batch holds the two annotated methods, not the plain one
            assertEquals(1, changes.size());
            assertTrue(changes.get(0).initial());
            assertEquals(Set.of("tick", "tock"), changes.get(0).added().stream().map(e -> e.method().getMethodName()).collect(Collectors.toSet()));
            assertEquals(2, changes.get(0).current().size());

            // the definition is retired and comes back, as a reload does
            BeanDefinition<Ticker> definition = context.getBeanDefinition(Ticker.class);
            ((DefaultBeanContext) context).notifyDefinitionChange(List.of(definition), List.of(definition));

            assertEquals(2, changes.size());
            ExecutableMethodChange<Tick> change = changes.get(1);
            assertFalse(change.initial());
            assertEquals(2, change.removed().size());
            assertEquals(2, change.added().size());
            assertEquals(2, change.replaced().size());
            assertTrue(change.replaced().stream().allMatch(ExecutableMethodChange.Replacement::metadataUnchanged));
            ExecutableMethodChange.Entry<Tick> gone = change.removed().get(0);
            assertTrue(change.replacementOf(gone).isPresent());
            assertEquals(gone.method().getMethodName(), change.replacementOf(gone).get().method().getMethodName());
        }
    }

    @Test
    void aProcessorWrittenBeforeWatchesIsFedTheAdditionsThroughTheAdapter() {
        try (ApplicationContext context = ApplicationContext.run(PROPERTIES)) {
            DefaultBeanContext beanContext = (DefaultBeanContext) context;
            LegacyTickProcessor processor = context.getBean(LegacyTickProcessor.class);

            // the startup pass processed the two methods, and the adapter knows the processor
            assertEquals(Set.of("tick", "tock"), Set.copyOf(processor.processed));
            assertTrue(beanContext.adaptedProcessors().contains(processor));

            // a destroyed processor loses its adapter, a new one is adapted again
            context.destroyBean(processor);
            assertFalse(beanContext.adaptedProcessors().contains(processor));
            processor = context.getBean(LegacyTickProcessor.class);
            assertTrue(beanContext.adaptedProcessors().contains(processor));

            // the new processor, which the startup pass never ran, sees the addition only
            BeanDefinition<Ticker> definition = context.getBeanDefinition(Ticker.class);
            beanContext.notifyDefinitionChange(List.of(definition), List.of(definition));
            assertEquals(Set.of("tick", "tock"), Set.copyOf(processor.processed));
            assertEquals(2, processor.processed.size());
        }
    }

    @Test
    void aConfigurationWatchRegisteredWhileItsBeanIsCreatedCanRecreateTheBeanAndClosesWithIt() {
        Pool.CREATED.set(0);
        try (ApplicationContext context = ApplicationContext.builder(PROPERTIES).trackBeanDependencies(true).start()) {
            DefaultBeanContext beanContext = (DefaultBeanContext) context;
            PoolUser user = context.getBean(PoolUser.class);
            Pool pool = user.pool;

            // a key the watcher applies live
            assertEquals(List.of(Outcome.APPLIED), beanContext.notifyConfigurationChange(ConfigurationChange.ofKeys(Set.of("pools.main.password"))));
            assertEquals(1, pool.applied);
            assertSame(pool, context.getBean(Pool.class));

            // a key outside the prefix does not consult the watch
            assertTrue(beanContext.notifyConfigurationChange(ConfigurationChange.ofKeys(Set.of("pools.other.url"))).isEmpty());

            // the url changes: the pool is recreated, its old watch closed, the bean that held it created again on top
            assertEquals(List.of(Outcome.RECREATE), beanContext.notifyConfigurationChange(ConfigurationChange.ofKeys(Set.of("POOLS_MAIN_URL"))));
            assertFalse(pool.watch.isActive());
            assertEquals(2, Pool.CREATED.get());
            Pool recreated = context.getBean(Pool.class);
            assertNotSame(pool, recreated);
            assertTrue(recreated.watch.isActive());
            assertNotSame(user, context.getBean(PoolUser.class));
            assertSame(recreated, context.getBean(PoolUser.class).pool);

            // a watch that cannot apply its change says so
            context.getBean(RestartOnly.class);
            assertEquals(List.of(Outcome.REQUIRES_RESTART), beanContext.notifyConfigurationChange(ConfigurationChange.ofKeys(Set.of("server.port"))));

            // the pool is destroyed: its watch went with it, and the registry forgot the watch and the bean
            context.destroyBean(recreated);
            assertFalse(recreated.watch.isActive());
            assertTrue(beanContext.notifyConfigurationChange(ConfigurationChange.ofKeys(Set.of("pools.main.url"))).isEmpty());
        }
    }

    @Test
    void aModuleRecreatesABeanAndItsDependentsThroughThePublicApi() {
        Pool.CREATED.set(0);
        try (ApplicationContext context = ApplicationContext.builder(PROPERTIES).trackBeanDependencies(true).start()) {
            WatchableBeanContext beanContext = (WatchableBeanContext) context;
            // a singleton not created yet is left alone
            assertFalse(beanContext.recreate(Pool.class, null));
            assertEquals(0, Pool.CREATED.get());

            // by instance: the pool is created again, and the bean that received it is destroyed and created again on top
            PoolUser user = context.getBean(PoolUser.class);
            Pool pool = user.pool;
            assertTrue(beanContext.recreate(pool));
            assertEquals(2, Pool.CREATED.get());
            assertFalse(pool.watch.isActive());
            Pool second = context.getBean(Pool.class);
            assertNotSame(pool, second);
            PoolUser recreatedUser = context.getBean(PoolUser.class);
            assertNotSame(user, recreatedUser);
            assertSame(second, recreatedUser.pool);

            // by type, for a singleton the context holds
            assertTrue(beanContext.recreate(Argument.of(Pool.class), null));
            assertEquals(3, Pool.CREATED.get());
            Pool third = context.getBean(Pool.class);
            assertNotSame(second, third);
            assertSame(third, context.getBean(PoolUser.class).pool);

            // a prototype, or an object the context does not hold, is nobody's to recreate
            assertFalse(beanContext.recreate(context.getBean(RecreatingPrototype.class)));
            assertFalse(beanContext.recreate(new Object()));
            // nor is a singleton registered at runtime, whose definition hands back the instance it was given
            StringBuilder registered = new StringBuilder("registered");
            context.registerSingleton(StringBuilder.class, registered);
            assertFalse(beanContext.recreate(registered));
            assertFalse(beanContext.recreate(StringBuilder.class, null));
            assertSame(registered, context.getBean(StringBuilder.class));
        }
    }

    @Test
    void aContextThatDoesNotTrackDependenciesRecreatesNothing() {
        Pool.CREATED.set(0);
        try (ApplicationContext context = ApplicationContext.builder(PROPERTIES).trackBeanDependencies(false).start()) {
            WatchableBeanContext beanContext = (WatchableBeanContext) context;
            PoolUser user = context.getBean(PoolUser.class);
            // its dependents unknown, the pool is kept rather than leave the user holding a destroyed instance
            assertFalse(beanContext.recreate(user.pool));
            assertFalse(beanContext.recreate(Pool.class, null));
            assertEquals(1, Pool.CREATED.get());
            assertSame(user.pool, context.getBean(Pool.class));
            assertTrue(user.pool.watch.isActive());
        }
    }

    @Test
    void aContextInDevelopmentModeByConfigurationRecreatesABeanAndItsDependents() {
        Pool.CREATED.set(0);
        // development mode only in the properties of the context: no system property, no builder switch
        try (ApplicationContext context = ApplicationContext.builder(DEVELOPMENT).start()) {
            WatchableBeanContext beanContext = (WatchableBeanContext) context;
            assertTrue(context.findDependencyGraph().isPresent());
            PoolUser user = context.getBean(PoolUser.class);
            Pool pool = user.pool;
            assertTrue(beanContext.recreate(pool));
            assertEquals(2, Pool.CREATED.get());
            PoolUser recreatedUser = context.getBean(PoolUser.class);
            assertNotSame(user, recreatedUser);
            assertNotSame(pool, recreatedUser.pool);
            assertSame(context.getBean(Pool.class), recreatedUser.pool);
        }
    }

    @Test
    void aRecreatedProcessorIsGivenTheStartupMethodsOnceAndTheDestroyedOneNothingMore() {
        try (ApplicationContext context = ApplicationContext.builder(PROPERTIES).trackBeanDependencies(true).start()) {
            DefaultBeanContext beanContext = (DefaultBeanContext) context;
            LegacyTickProcessor processor = context.getBean(LegacyTickProcessor.class);
            assertEquals(List.of("tick", "tock"), processor.processed.stream().sorted().toList());

            assertTrue(beanContext.recreate(processor));
            LegacyTickProcessor recreated = context.getBean(LegacyTickProcessor.class);
            assertNotSame(processor, recreated);
            // the new processor is given what the startup pass gave the first one, once
            assertEquals(List.of("tick", "tock"), recreated.processed.stream().sorted().toList());
            assertEquals(List.of("tick", "tock"), processor.processed.stream().sorted().toList());
            assertFalse(beanContext.adaptedProcessors().contains(processor));
            assertTrue(beanContext.adaptedProcessors().contains(recreated));

            // an addition reaches the new processor once, through its own adapter, and the destroyed one not at all
            BeanDefinition<Ticker> definition = context.getBeanDefinition(Ticker.class);
            beanContext.notifyDefinitionChange(List.of(definition), List.of(definition));
            assertEquals(List.of("tick", "tick", "tock", "tock"), recreated.processed.stream().sorted().toList());
            assertEquals(2, processor.processed.size());
        }
    }

    @Test
    void aProcessorRecreatedAsADependentIsCreatedAgainAndGivenTheStartupMethodsOnce() {
        Map<String, Object> properties = Map.of("spec.name", "BeanWatchTest", "pool-tick-processor.enabled", true);
        try (ApplicationContext context = ApplicationContext.builder(properties).trackBeanDependencies(true).start()) {
            WatchableBeanContext beanContext = (WatchableBeanContext) context;
            // created and fed by the startup pass, with the pool it received
            PoolTickProcessor processor = context.getBean(PoolTickProcessor.class);
            assertEquals(List.of("tick", "tock"), processor.processed.stream().sorted().toList());
            Pool pool = processor.pool;

            assertTrue(beanContext.recreate(pool));
            // created again at once, as nothing would ask for it, on top of the new pool
            Collection<BeanRegistration<PoolTickProcessor>> active = context.getActiveBeanRegistrations(PoolTickProcessor.class);
            assertEquals(1, active.size());
            PoolTickProcessor recreated = active.iterator().next().bean();
            assertNotSame(processor, recreated);
            assertNotSame(pool, recreated.pool);
            assertSame(context.getBean(Pool.class), recreated.pool);
            assertEquals(List.of("tick", "tock"), recreated.processed.stream().sorted().toList());
            // the destroyed one is given nothing more
            assertEquals(2, processor.processed.size());
            assertSame(recreated, context.getBean(PoolTickProcessor.class));
            assertEquals(2, recreated.processed.size());
        }
    }

    @Test
    void aRecreatedDeprecatedProcessorIsGivenTheMethodsTheLegacyListenerDoesNotGiveItOnce() {
        Map<String, Object> properties = Map.of("spec.name", "BeanWatchTest", "buzz-processor.enabled", true);
        try (ApplicationContext context = ApplicationContext.builder(properties).trackBeanDependencies(true).start()) {
            WatchableBeanContext beanContext = (WatchableBeanContext) context;
            // its annotation is not processed on startup, so the legacy listener feeds it too, by the class only
            assertTrue(context.getBeanDefinition(BuzzProcessor.class).hasAnnotation(Deprecated.class));
            BuzzProcessor processor = context.getBean(BuzzProcessor.class);
            int startup = processor.processed.size();

            assertTrue(beanContext.recreate(processor));
            BuzzProcessor recreated = context.getBean(BuzzProcessor.class);
            assertNotSame(processor, recreated);
            // the methods annotated in a class that is not, which only the startup pass gives, and the method of the
            // annotated class, which the listener gives as the processor is created, each once
            assertEquals(List.of("first", "second", "third"), recreated.processed.stream().sorted().toList());
            assertEquals(startup, processor.processed.size());
        }
    }

    @Test
    void aDeprecatedProcessorRecreatedAsADependentIsGivenEachMethodOnce() {
        Map<String, Object> properties = Map.of("spec.name", "BeanWatchTest", "buzz-processor.enabled", true);
        try (ApplicationContext context = ApplicationContext.builder(properties).trackBeanDependencies(true).start()) {
            WatchableBeanContext beanContext = (WatchableBeanContext) context;
            BuzzProcessor processor = context.getBean(BuzzProcessor.class);
            int startup = processor.processed.size();

            assertTrue(beanContext.recreate(processor.pool));
            Collection<BeanRegistration<BuzzProcessor>> active = context.getActiveBeanRegistrations(BuzzProcessor.class);
            assertEquals(1, active.size());
            BuzzProcessor recreated = active.iterator().next().bean();
            assertNotSame(processor, recreated);
            assertSame(context.getBean(Pool.class), recreated.pool);
            assertEquals(List.of("first", "second", "third"), recreated.processed.stream().sorted().toList());
            assertEquals(startup, processor.processed.size());
        }
    }

    @Test
    void aResourceStateReportedBeforeTheContextStartsIsTheFirstBatchDeliveredOnce() {
        try (ApplicationContext context = ApplicationContext.builder(PROPERTIES).build()) {
            DefaultBeanContext beanContext = (DefaultBeanContext) context;
            Path root = Path.of("/app/static").toAbsolutePath();
            List<ResourceChange> seen = new ArrayList<>();
            beanContext.watchResources(ResourceSelector.of(ResourceKind.STATIC), seen::add);

            // the launcher reports the state, then the context starts: one initial batch, not one per path
            beanContext.notifyResourceChange(new ResourceChange(ResourceKind.STATIC, List.of(root), List.of(root.resolve("app.css")), List.of(), true));
            context.start();
            assertEquals(1, seen.size());
            assertTrue(seen.get(0).initial());

            // a later batch carries another root; a late watch is filtered against the latest roots
            Path other = Path.of("/app/public").toAbsolutePath();
            beanContext.notifyResourceChange(new ResourceChange(ResourceKind.STATIC, List.of(root, other), List.of(other.resolve("logo.png")), List.of(), false));
            List<ResourceChange> late = new ArrayList<>();
            beanContext.watchResources(ResourceSelector.of(ResourceKind.STATIC, "*.png"), late::add);
            assertEquals(1, late.size());
            assertEquals(List.of(other.resolve("logo.png")), late.get(0).changed());
            assertEquals(List.of(root, other), late.get(0).roots());
        }
    }

    @Test
    void aResourceWatchIsFedWhatItsSelectorSelectsAndALateWatchGetsTheKnownStateFirst() {
        try (ApplicationContext context = ApplicationContext.run(PROPERTIES)) {
            DefaultBeanContext beanContext = (DefaultBeanContext) context;
            Path root = Path.of("/app/views").toAbsolutePath();
            List<ResourceChange> html = new ArrayList<>();
            List<ResourceChange> all = new ArrayList<>();
            beanContext.watchResources(ResourceSelector.of(ResourceKind.VIEWS, "**/*.html"), html::add);
            beanContext.watchResources(ResourceSelector.of(ResourceKind.VIEWS), all::add);

            // the launcher reports the initial state
            beanContext.notifyResourceChange(new ResourceChange(ResourceKind.VIEWS, List.of(root), List.of(root.resolve("index.html"), root.resolve("mail/welcome.peb")), List.of(), true));
            assertEquals(1, html.size());
            assertTrue(html.get(0).initial());
            assertEquals(List.of(root.resolve("index.html")), html.get(0).changed());
            assertEquals(2, all.get(0).changed().size());

            // a template outside the selector does not wake the html watch
            beanContext.notifyResourceChange(new ResourceChange(ResourceKind.VIEWS, List.of(root), List.of(root.resolve("mail/welcome.peb")), List.of(), false));
            assertEquals(1, html.size());
            assertEquals(2, all.size());
            assertTrue(all.get(1).anyWithExtension("PEB"));

            // a nested html template goes, and a static file changes
            beanContext.notifyResourceChange(new ResourceChange(ResourceKind.VIEWS, List.of(root), List.of(), List.of(root.resolve("mail/goodbye.html")), false));
            beanContext.notifyResourceChange(new ResourceChange(ResourceKind.STATIC, List.of(root), List.of(root.resolve("app.css")), List.of(), false));
            assertEquals(2, html.size());
            assertEquals(List.of(root.resolve("mail/goodbye.html")), html.get(1).removed());
            assertTrue(html.get(1).affects(root.resolve("mail/goodbye.html")));
            assertEquals(3, all.size());

            // a watch registered late, after the index was removed and a page added, starts from the state as the changes left it
            beanContext.notifyResourceChange(new ResourceChange(ResourceKind.VIEWS, List.of(root), List.of(root.resolve("page.html")), List.of(root.resolve("index.html")), false));
            List<ResourceChange> late = new ArrayList<>();
            beanContext.watchResources(ResourceSelector.of(ResourceKind.VIEWS, "*.html"), late::add);
            assertEquals(1, late.size());
            assertTrue(late.get(0).initial());
            assertEquals(List.of(root.resolve("page.html")), late.get(0).changed());

            // nested roots are each tried
            assertTrue(ResourceSelector.of(ResourceKind.VIEWS, "*.html").matches(List.of(Path.of("/app"), Path.of("/app/views")), Path.of("/app/views/index.html")));
        }
    }

    @Test
    void watchersRunInOrderAndAFailingOneDoesNotStopTheOthers() {
        try (ApplicationContext context = ApplicationContext.run(PROPERTIES)) {
            WatchableBeanContext watchable = (WatchableBeanContext) context;
            List<String> order = new ArrayList<>();
            watchable.watchDefinitions(Rule.class, null, new OrderedWatcher(10, () -> order.add("second")));
            watchable.watchDefinitions(Rule.class, null, change -> {
                throw new IllegalStateException("boom");
            });
            watchable.watchDefinitions(Rule.class, null, new OrderedWatcher(-10, () -> order.add("first")));
            order.clear();

            context.registerBeanDefinition(rule("e"));
            assertEquals(List.of("first", "second"), order);
        }
    }

    @Test
    void aRecreateThatCannotHappenIsReportedIgnoredEvenAfterAWatchThatFailed() {
        try (ApplicationContext context = ApplicationContext.run(PROPERTIES)) {
            DefaultBeanContext beanContext = (DefaultBeanContext) context;
            // a watch ahead of it fails and reports no outcome
            beanContext.watchConfiguration("protos", change -> {
                throw new IllegalStateException("boom");
            });
            // a watch that answers APPLIED, and the prototype's, which answers RECREATE
            beanContext.watchConfiguration("protos.main", change -> Outcome.APPLIED);
            context.getBean(RecreatingPrototype.class);

            assertEquals(List.of(Outcome.APPLIED, Outcome.IGNORED), beanContext.notifyConfigurationChange(ConfigurationChange.ofKeys(Set.of("protos.main.url"))));
        }
    }

    @Test
    void aClassChangeWatchIsCalledForEachClassChangeBeforeTheListenersAndClosesWithItsBean() {
        try (ApplicationContext context = ApplicationContext.run(DEVELOPMENT)) {
            ClassCache cache = context.getBean(ClassCache.class);
            context.getBean(ClassChangeOrder.class);
            ClassChangeOrder.SEEN.clear();

            // registered, with no startup batch: nothing changed yet
            assertTrue(cache.watch.isActive());
            assertTrue(cache.evicted.isEmpty());

            // each class change the launcher publishes reaches the watch, ahead of the listeners of the event
            ((WatchableBeanContext) context).watchClassChanges(change -> ClassChangeOrder.SEEN.add("watch"));
            ClassChangeEvent first = classChange(1);
            context.publishEvent(first);
            assertEquals(List.of(first), cache.evicted);
            assertEquals(List.of("watch", "listener"), ClassChangeOrder.SEEN);
            // published through the typed publisher a launcher may inject, rather than through the context
            ApplicationEventPublisher<ClassChangeEvent> publisher = context.getBean(Argument.of(ApplicationEventPublisher.class, ClassChangeEvent.class));
            assertFalse(publisher.isEmpty(), "a publisher with class change watches to call is not empty");
            ClassChangeEvent second = classChange(2);
            publisher.publishEvent(second);
            assertEquals(List.of(first, second), cache.evicted);

            // the cache is destroyed: the watch it registered while it was created goes with it
            context.destroyBean(cache);
            assertFalse(cache.watch.isActive());
            context.publishEvent(classChange(3));
            assertEquals(List.of(first, second), cache.evicted);
        } finally {
            ClassChangeOrder.SEEN.clear();
        }
    }

    @Test
    void aClassChangeWatchOutsideDevelopmentModeRegistersNothing() {
        try (ApplicationContext context = ApplicationContext.run(PROPERTIES)) {
            List<ClassChangeEvent> seen = new ArrayList<>();
            BeanWatch watch = ((WatchableBeanContext) context).watchClassChanges(seen::add);

            // classes never change outside development mode: the watch is inactive from the start
            assertFalse(watch.isActive());
            assertFalse(((DefaultBeanContext) context).hasClassChangeWatches());
            context.publishEvent(classChange(1));
            assertTrue(seen.isEmpty());
            assertFalse(context.getBean(ClassCache.class).watch.isActive());
            watch.close();
        } finally {
            ClassChangeOrder.SEEN.clear();
        }
    }

    @Test
    void aDefinitionChangeMadeWhileTheFirstBatchIsReadIsDeliveredAfterItAndNeverBeforeIt() throws Exception {
        try (ApplicationContext context = ApplicationContext.run(PROPERTIES)) {
            WatchableBeanContext watchable = (WatchableBeanContext) context;
            List<BeanDefinitionChange<Rule>> changes = new CopyOnWriteArrayList<>();
            CountDownLatch reading = new CountDownLatch(1);
            CountDownLatch proceed = new CountDownLatch(1);
            AtomicReference<BeanWatch> watch = new AtomicReference<>();
            Thread registering = new Thread(() -> {
                // the qualifier pauses this thread once, inside the read of the first batch: the watch is registered by then
                PausingQualifier qualifier = new PausingQualifier(Thread.currentThread(), reading, proceed);
                watch.set(watchable.watchDefinitions(Argument.of(Rule.class), qualifier, changes::add));
            }, "registering");
            registering.start();
            assertTrue(reading.await(10, TimeUnit.SECONDS));

            // another thread applies a change while the first batch is read: its delivery waits for the first batch
            RuntimeBeanDefinition<Rule> late = rule("late");
            Thread changing = new Thread(() -> context.registerBeanDefinition(late), "changing");
            changing.start();
            awaitParked(changing);
            assertTrue(changes.isEmpty(), "nothing is delivered ahead of the first batch");

            proceed.countDown();
            registering.join(10_000);
            changing.join(10_000);
            assertFalse(registering.isAlive());
            assertFalse(changing.isAlive());

            // the first batch came first, the change after it, and the late rule reached the watcher
            assertTrue(watch.get().isActive());
            assertTrue(changes.get(0).initial());
            assertTrue(changes.stream().skip(1).noneMatch(BeanDefinitionChange::initial));
            assertTrue(changes.size() <= 2);
            assertTrue(changes.stream().anyMatch(change -> change.added().contains(late)));
            assertTrue(changes.get(changes.size() - 1).current().contains(late));
            assertEquals(Set.of(ARule.class, BRule.class), beanTypes(changes.get(0).added().stream().filter(d -> d != late).toList()));
        }
    }

    @Test
    void aDefinitionChangeAppliedBeforeTheWatchIsRegisteredShowsInTheFirstBatchOnly() {
        try (ApplicationContext context = ApplicationContext.run(PROPERTIES)) {
            RuntimeBeanDefinition<Rule> early = rule("early");
            context.registerBeanDefinition(early);
            List<BeanDefinitionChange<Rule>> changes = new ArrayList<>();
            ((WatchableBeanContext) context).watchDefinitions(Rule.class, null, changes::add);

            assertEquals(1, changes.size());
            assertTrue(changes.get(0).added().contains(early));
        }
    }

    @Test
    void aConfigurationWatchWithAFirstBatchReadsTheConfigurationAsItIsAndMissesNoRefreshMadeMeanwhile() throws Exception {
        try (ApplicationContext context = ApplicationContext.run(PROPERTIES)) {
            DefaultBeanContext beanContext = (DefaultBeanContext) context;
            AtomicReference<String> size = new AtomicReference<>("1");
            List<String> seen = new CopyOnWriteArrayList<>();
            CountDownLatch reading = new CountDownLatch(1);
            CountDownLatch proceed = new CountDownLatch(1);
            Thread registering = new Thread(() -> beanContext.watchConfiguration("pool", change -> {
                if (change.initial()) {
                    // the first batch reads the configuration, slowly
                    seen.add("initial " + size.get() + " all=" + change.all());
                    reading.countDown();
                    await(proceed);
                    return Outcome.RECREATE;
                }
                seen.add("change " + size.get());
                return Outcome.APPLIED;
            }, true), "registering");
            registering.start();
            assertTrue(reading.await(10, TimeUnit.SECONDS));

            // a refresh lands while the first batch is read: it is delivered once the first batch returns
            AtomicReference<List<Outcome>> outcomes = new AtomicReference<>();
            Thread refreshing = new Thread(() -> {
                size.set("2");
                outcomes.set(beanContext.notifyConfigurationChange(ConfigurationChange.ofKeys(Set.of("pool.size"))));
            }, "refreshing");
            refreshing.start();
            awaitParked(refreshing);
            assertEquals(List.of("initial 1 all=true"), seen);

            proceed.countDown();
            registering.join(10_000);
            refreshing.join(10_000);
            assertEquals(List.of("initial 1 all=true", "change 2"), seen);
            assertEquals(List.of(Outcome.APPLIED), outcomes.get());

            // a watch without a first batch is not called until a refresh touches it
            List<ConfigurationChange> plain = new ArrayList<>();
            beanContext.watchConfiguration("pool", change -> {
                plain.add(change);
                return Outcome.APPLIED;
            });
            assertTrue(plain.isEmpty());
            assertTrue(ConfigurationChange.ofInitial().initial());
            assertFalse(ConfigurationChange.ofAll().initial());
        }
    }

    @Test
    void watchersThatMakeChangesOfAnotherKindOnTwoThreadsDoNotDeadlock() throws Exception {
        try (ApplicationContext context = ApplicationContext.run(PROPERTIES)) {
            DefaultBeanContext beanContext = (DefaultBeanContext) context;
            CountDownLatch inConfigurationWatcher = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            List<String> configurationCalls = new CopyOnWriteArrayList<>();
            // a configuration watcher that registers a definition, the first time slowly
            beanContext.watchConfiguration("loop", change -> {
                configurationCalls.add(Thread.currentThread().getName());
                if (configurationCalls.size() == 1) {
                    inConfigurationWatcher.countDown();
                    await(release);
                    context.registerBeanDefinition(rule("z"));
                }
                return Outcome.APPLIED;
            });
            // a definition watcher that refreshes the configuration the other one watches
            beanContext.watchDefinitions(Rule.class, null, change -> {
                if (!change.initial()) {
                    beanContext.notifyConfigurationChange(ConfigurationChange.ofKeys(Set.of("loop.key")));
                }
            });

            Thread refreshing = new Thread(() -> beanContext.notifyConfigurationChange(ConfigurationChange.ofKeys(Set.of("loop.key"))), "refreshing");
            refreshing.start();
            assertTrue(inConfigurationWatcher.await(10, TimeUnit.SECONDS));
            // while the configuration watcher runs on one thread, the definition watcher refreshes on another
            Thread registering = new Thread(() -> context.registerBeanDefinition(rule("y")), "registering");
            registering.start();
            registering.join(10_000);
            assertFalse(registering.isAlive(), () -> "a thread delivering to a watch does not wait for another delivering one: " + java.util.Arrays.toString(registering.getStackTrace()));

            release.countDown();
            refreshing.join(10_000);
            assertFalse(refreshing.isAlive());
            // every refresh reached the configuration watcher, one at a time, on the thread that was delivering to it
            assertEquals(List.of("refreshing", "refreshing", "refreshing"), configurationCalls);
        }
    }

    @Test
    void aConfigurationWatchWithAFirstBatchRegisteredBeforeStartupIsCalledOnceAtStartup() {
        try (ApplicationContext context = ApplicationContext.builder(PROPERTIES).build()) {
            List<ConfigurationChange> seen = new ArrayList<>();
            ((WatchableBeanContext) context).watchConfiguration("pool", change -> {
                seen.add(change);
                return Outcome.APPLIED;
            }, true);
            assertTrue(seen.isEmpty());
            context.start();
            assertEquals(1, seen.size());
            assertTrue(seen.get(0).initial());
        }
    }

    @Test
    void configurationChangesAreMatchedAtDotBoundariesInEverySpelling() {
        assertTrue(ConfigurationChange.ofKeys(Set.of("datasources.default.url")).touches("datasources.default"));
        assertTrue(ConfigurationChange.ofKeys(Set.of("datasources.default")).touches("datasources.default"));
        assertFalse(ConfigurationChange.ofKeys(Set.of("datasources.defaultx.url")).touches("datasources.default"));
        assertTrue(ConfigurationChange.ofKeys(Set.of("DATASOURCES_DEFAULT_URL")).touches("datasources.default"));
        assertTrue(ConfigurationChange.ofKeys(Set.of("datasources.default.driverClassName")).touches("datasources.default.driver-class-name"));
        assertTrue(ConfigurationChange.ofAll().touches("anything"));
        assertTrue(ConfigurationChange.ofKeys(Set.of("a.b")).touchesAny("x", "a"));
        assertTrue(ConfigurationChange.ofKeys(Set.of("MICRONAUT_SERVER_THREAD_SELECTION")).touches("micronaut.server.thread-selection"));
        assertTrue(ConfigurationChange.ofKeys(Set.of("micronaut.server.thread-selection")).touches("micronaut.server"));
    }

    @Test
    void anObjectWatchWithAQualifierSeesABeanWithTypeArgumentsAddedAndRemovedAsItsFirstBatchListsIt() {
        try (ApplicationContext context = ApplicationContext.run(PROPERTIES)) {
            DefaultBeanContext beanContext = (DefaultBeanContext) context;
            BeanDefinition<StringCodec> codec = context.getBeanDefinition(StringCodec.class);
            List<BeanDefinitionChange<Object>> changes = new ArrayList<>();
            BeanWatch watch = beanContext.watchDefinitions(Object.class, Qualifiers.byName("codec"), changes::add);

            assertEquals(1, changes.size());
            assertEquals(List.of(codec), changes.get(0).added());

            // a change is matched as the first batch was: Object selects every definition, not only those exposing it
            beanContext.notifyDefinitionChange(List.of(codec), List.of());
            assertEquals(2, changes.size());
            assertEquals(List.of(codec), changes.get(1).removed());

            beanContext.notifyDefinitionChange(List.of(), List.of(codec));
            assertEquals(3, changes.size());
            assertEquals(List.of(codec), changes.get(2).added());

            // the qualifier still applies
            beanContext.notifyDefinitionChange(List.of(), List.of(context.getBeanDefinition(ARule.class)));
            assertEquals(3, changes.size());
            watch.close();
        }
    }

    @Test
    void aTypedWatchSeesTheChangesOfTheDefinitionsItsFirstBatchLists() {
        try (ApplicationContext context = ApplicationContext.run(PROPERTIES)) {
            DefaultBeanContext beanContext = (DefaultBeanContext) context;
            BeanDefinition<StringCodec> codec = context.getBeanDefinition(StringCodec.class);
            BeanDefinition<MarkedOnly> marked = context.getBeanDefinition(MarkedOnly.class);
            List<BeanDefinitionChange<Codec>> rawChanges = new ArrayList<>();
            List<BeanDefinitionChange<Codec>> typedChanges = new ArrayList<>();
            List<BeanDefinitionChange<IndexMarker>> indexedChanges = new ArrayList<>();
            List<BeanChange<IndexMarker>> indexedBeanChanges = new ArrayList<>();
            List<BeanWatch> watches = List.of(
                beanContext.watchDefinitions(Codec.class, null, rawChanges::add),
                beanContext.watchDefinitions(Argument.of(Codec.class, String.class), null, typedChanges::add),
                beanContext.watchDefinitions(IndexMarker.class, null, indexedChanges::add),
                beanContext.watchBeans(IndexMarker.class, null, indexedBeanChanges::add));

            assertEquals(List.of(codec), rawChanges.get(0).added());
            assertEquals(List.of(codec), typedChanges.get(0).added());
            // a bean indexed by a type it does not implement is listed by that type
            assertEquals(List.of(marked), indexedChanges.get(0).added());

            beanContext.notifyDefinitionChange(List.of(codec, marked), List.of());
            beanContext.notifyDefinitionChange(List.of(), List.of(codec, marked));
            Map<String, List<? extends BeanDefinitionChange<?>>> byWatch = Map.of(
                "Codec", rawChanges, "Codec<String>", typedChanges, "IndexMarker", indexedChanges);
            for (Map.Entry<String, List<? extends BeanDefinitionChange<?>>> entry : byWatch.entrySet()) {
                List<? extends BeanDefinitionChange<?>> changes = entry.getValue();
                assertEquals(3, changes.size(), entry.getKey());
                assertEquals(changes.get(0).added(), changes.get(1).removed());
                assertEquals(changes.get(0).added(), changes.get(2).added());
            }
            // a bean indexed by a type it does not implement is not a bean of that type
            assertEquals(1, indexedBeanChanges.size());
            assertTrue(indexedBeanChanges.get(0).added().isEmpty());
            watches.forEach(BeanWatch::close);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("not released");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void awaitParked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (thread.getState() != Thread.State.WAITING && thread.getState() != Thread.State.BLOCKED) {
            assertTrue(thread.isAlive(), thread.getName() + " finished without waiting");
            assertTrue(System.nanoTime() < deadline, thread.getName() + " never waited");
            Thread.sleep(5);
        }
    }

    /**
     * Qualifies every candidate; the first time the given thread asks, it signals and waits.
     */
    static final class PausingQualifier implements Qualifier<Rule> {
        private final Thread thread;
        private final CountDownLatch reading;
        private final CountDownLatch proceed;
        private final AtomicBoolean paused = new AtomicBoolean();

        PausingQualifier(Thread thread, CountDownLatch reading, CountDownLatch proceed) {
            this.thread = thread;
            this.reading = reading;
            this.proceed = proceed;
        }

        private void pause() {
            if (Thread.currentThread() == thread && paused.compareAndSet(false, true)) {
                reading.countDown();
                await(proceed);
            }
        }

        @Override
        public <BT extends BeanType<Rule>> Stream<BT> reduce(Class<Rule> beanType, Stream<BT> candidates) {
            pause();
            return candidates;
        }

        @Override
        public <BT extends BeanType<Rule>> Collection<BT> filter(Class<Rule> beanType, Collection<BT> candidates) {
            pause();
            return candidates;
        }

        @Override
        public <BT extends QualifiedBeanType<Rule>> Collection<BT> filterQualified(Class<Rule> beanType, Collection<BT> candidates) {
            pause();
            return candidates;
        }

        @Override
        public boolean doesQualify(Class<Rule> beanType, BeanType<Rule> candidate) {
            pause();
            return true;
        }

        @Override
        public boolean doesQualify(Class<Rule> beanType, QualifiedBeanType<Rule> candidate) {
            pause();
            return true;
        }
    }

    static final class OrderedWatcher implements BeanDefinitionWatcher<Rule>, Ordered {
        private final int order;
        private final Runnable action;

        OrderedWatcher(int order, Runnable action) {
            this.order = order;
            this.action = action;
        }

        @Override
        public void onChange(BeanDefinitionChange<Rule> change) {
            action.run();
        }

        @Override
        public int getOrder() {
            return order;
        }
    }
}
