package io.micronaut.core.io.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.micronaut.core.io.service.ServiceNameConditionTest.NamedService;
import io.micronaut.core.io.service.ServiceNameConditionTest.ServiceA;
import io.micronaut.core.io.service.ServiceNameConditionTest.ServiceB;
import io.micronaut.core.io.service.SoftServiceLoader.StaticDefinition;
import io.micronaut.core.optim.StaticOptimizations;
import org.jspecify.annotations.Nullable;

/**
 * Registers a {@link SoftServiceLoader.StaticServiceLoader} and loads its services, with and without a name condition.
 * It runs in a class loader of its own, see {@link ServiceNameConditionTest}.
 */
public final class StaticServiceLoaderScenario implements Supplier<Map<String, List<String>>> {

    @Override
    public Map<String, List<String>> get() {
        RecordingStaticServiceLoader staticServiceLoader = new RecordingStaticServiceLoader();
        StaticOptimizations.set(new SoftServiceLoader.Optimizations(Map.of(NamedService.class.getName(), staticServiceLoader)));
        ClassLoader classLoader = getClass().getClassLoader();
        Predicate<String> condition = ServiceA.class.getName()::equals;

        Map<String, List<String>> results = new LinkedHashMap<>();
        results.put("collectAll", names(SoftServiceLoader.load(NamedService.class, classLoader, condition).collectAll()));
        List<String> iterated = new ArrayList<>();
        for (ServiceDefinition<NamedService> definition : SoftServiceLoader.load(NamedService.class, classLoader, condition)) {
            iterated.add(definition.getName());
        }
        results.put("iterator", iterated);
        results.put("collectAll without condition", names(SoftServiceLoader.load(NamedService.class, classLoader).collectAll()));
        results.put("calls", staticServiceLoader.calls);
        return results;
    }

    private static List<String> names(List<NamedService> services) {
        return services.stream().map(service -> service.getClass().getName()).toList();
    }

    /**
     * Overrides only {@link #load(Predicate)}, as a loader that forks its tasks might.
     */
    private static final class RecordingStaticServiceLoader implements SoftServiceLoader.StaticServiceLoader<NamedService> {

        private final List<String> calls = new ArrayList<>();

        @Override
        public Stream<StaticDefinition<NamedService>> findAll(Predicate<String> predicate) {
            return Stream.of(
                StaticDefinition.<NamedService>of(ServiceA.class.getName(), ServiceA::new),
                StaticDefinition.<NamedService>of(ServiceB.class.getName(), ServiceB::new)
            ).filter(definition -> predicate.test(definition.getName()));
        }

        @Override
        public List<NamedService> load(@Nullable Predicate<NamedService> predicate) {
            calls.add("load(predicate)");
            return findAll(name -> true)
                .map(StaticDefinition::load)
                .filter(service -> predicate == null || predicate.test(service))
                .toList();
        }
    }
}
