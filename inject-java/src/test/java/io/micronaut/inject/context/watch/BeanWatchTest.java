package io.micronaut.inject.context.watch;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.RuntimeBeanDefinition;
import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.reload.ResourceKind;
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
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeanWatchTest {

    private static final Map<String, Object> PROPERTIES = Map.of("spec.name", "BeanWatchTest");

    private static RuntimeBeanDefinition<Rule> rule(String name) {
        return RuntimeBeanDefinition.builder(Rule.class, () -> (Rule) () -> name).named(name).singleton(true).build();
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
