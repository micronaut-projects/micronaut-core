package io.micronaut.core.io.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The name condition given to {@link SoftServiceLoader#load(Class, ClassLoader, Predicate)} is tested on every entry,
 * whichever source the entry comes from.
 */
class ServiceNameConditionTest {

    private static final String A = ServiceA.class.getName();
    private static final String B = ServiceB.class.getName();
    private static final String C = ServiceC.class.getName();
    private static final String D = ServiceD.class.getName();

    @TempDir
    Path tempDir;

    @Test
    void everyScannedEntryIsLoadedWithoutACondition() throws IOException {
        try (URLClassLoader classLoader = servicesClassLoader()) {
            assertEquals(List.of(A, B, C, D), sortedNames(SoftServiceLoader.load(NamedService.class, classLoader).collectAll()));
            assertEquals(List.of(A, B, C, D), sortedDefinitionNames(SoftServiceLoader.load(NamedService.class, classLoader)));
        }
    }

    @Test
    void theConditionIsTestedOnTheNameOfEveryScannedEntry() throws IOException {
        Predicate<String> condition = Set.of(A, C)::contains;
        try (URLClassLoader classLoader = servicesClassLoader()) {
            assertEquals(List.of(A, C), sortedNames(SoftServiceLoader.load(NamedService.class, classLoader, condition).collectAll()));
            assertEquals(List.of(A, C), sortedNames(SoftServiceLoader.load(NamedService.class, classLoader, condition).disableFork().collectAll()));
            assertEquals(List.of(A, C), sortedDefinitionNames(SoftServiceLoader.load(NamedService.class, classLoader, condition)));
        }
    }

    @Test
    void theConditionIsTestedOnTheStrippedNamesOfMetaInfServicesEntries() throws IOException {
        try (URLClassLoader classLoader = servicesClassLoader()) {
            List<String> testedNames = new CopyOnWriteArrayList<>();
            Predicate<String> condition = name -> {
                testedNames.add(name);
                return true;
            };

            assertEquals(List.of(A, B, C, D), sortedNames(SoftServiceLoader.load(NamedService.class, classLoader, condition).collectAll()));
            // neither the comment nor the whitespace around a name is part of what the condition is tested on
            assertEquals(List.of(A, B, C, D), testedNames.stream().sorted().toList());
            // java.util.ServiceLoader reads the same names from this META-INF/services file, where the whitespace
            // around the names is spaces and tabs
            assertEquals(List.of(A, B), ServiceLoader.load(NamedService.class, classLoader).stream()
                .map(provider -> provider.type().getName())
                .sorted()
                .toList());
        }
    }

    @Test
    void theLineConditionOfACollectorIsTestedOnTheNameOfEveryScannedEntry() throws IOException {
        String serviceName = NamedService.class.getName();
        try (URLClassLoader classLoader = servicesClassLoader()) {
            // the transformer gets the names the condition accepted: without a comment or the whitespace around them
            assertEquals(List.of(A, C), collectedNames(SoftServiceLoader.newCollector(serviceName, Set.of(A, C)::contains, classLoader, Function.identity())));
            // a null condition accepts every entry
            assertEquals(List.of(A, B, C, D), collectedNames(SoftServiceLoader.newCollector(serviceName, null, classLoader, Function.identity())));
        }
    }

    @Test
    void theIteratorOnlyReturnsDefinitionsOfTheStrippedNames() throws IOException {
        try (URLClassLoader classLoader = servicesClassLoader()) {
            List<String> definitions = new ArrayList<>();
            for (ServiceDefinition<NamedService> definition : SoftServiceLoader.load(NamedService.class, classLoader)) {
                definitions.add("'" + definition.getName() + "' " + (definition.isPresent() ? "present" : "not present"));
            }

            // no definition under a name with the whitespace around it, and none for a line without a name
            assertEquals(
                List.of("'" + A + "' present", "'" + B + "' present", "'" + C + "' present", "'" + D + "' present"),
                definitions.stream().sorted().toList()
            );
        }
    }

    @Test
    void theConditionIsTestedOnTheNameOfEveryEntryOfAStaticServiceLoader() throws Exception {
        Map<String, List<String>> results = runInIsolatedClassLoader(StaticServiceLoaderScenario.class);

        assertEquals(List.of(A), results.get("collectAll"));
        assertEquals(List.of(A), results.get("iterator"));
        assertEquals(List.of(A, B), results.get("collectAll without condition"));
        assertEquals(List.of("load(predicate)"), results.get("calls"));
    }

    /**
     * Lists {@code A} and {@code B} under {@code META-INF/services}, and {@code C} and {@code D} under
     * {@code META-INF/micronaut}. In the {@code META-INF/services} file, {@code A} is followed by blanks and a
     * comment, {@code B} is indented and directly followed by a comment, and there are comment and blank lines.
     */
    private URLClassLoader servicesClassLoader() throws IOException {
        Path services = Files.createDirectories(tempDir.resolve("META-INF/services"));
        Files.write(services.resolve(NamedService.class.getName()), List.of(
            "# a comment",
            "  # an indented comment",
            A + "   # a trailing comment",
            "\t  " + B + "#a trailing comment",
            "  "
        ));
        Path micronaut = Files.createDirectories(tempDir.resolve("META-INF/micronaut/" + NamedService.class.getName()));
        Files.createFile(micronaut.resolve(C));
        Files.createFile(micronaut.resolve(D));
        return new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, getClass().getClassLoader());
    }

    private static List<String> sortedNames(List<NamedService> services) {
        return services.stream().map(service -> service.getClass().getName()).sorted().toList();
    }

    private static List<String> collectedNames(SoftServiceLoader.ServiceCollector<String> collector) {
        List<String> names = new ArrayList<>();
        collector.collect(names);
        return names.stream().sorted().toList();
    }

    private static List<String> sortedDefinitionNames(SoftServiceLoader<NamedService> loader) {
        List<String> names = new ArrayList<>();
        for (ServiceDefinition<NamedService> definition : loader) {
            names.add(definition.getName());
        }
        return names.stream().sorted().toList();
    }

    /**
     * {@link SoftServiceLoader} reads the registered static service loaders once, when it is initialized, so the
     * scenario runs with its own copy of the core classes.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> runInIsolatedClassLoader(Class<? extends Supplier<Map<String, List<String>>>> scenario) throws Exception {
        URL[] urls = {location(SoftServiceLoader.class), location(scenario)};
        Thread thread = Thread.currentThread();
        ClassLoader contextClassLoader = thread.getContextClassLoader();
        try (URLClassLoader isolated = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            // StaticOptimizations finds its loaders through the context class loader
            thread.setContextClassLoader(isolated);
            return ((Supplier<Map<String, List<String>>>) isolated.loadClass(scenario.getName()).getConstructor().newInstance()).get();
        } finally {
            thread.setContextClassLoader(contextClassLoader);
        }
    }

    private static URL location(Class<?> type) {
        return type.getProtectionDomain().getCodeSource().getLocation();
    }

    interface NamedService {
    }

    public static final class ServiceA implements NamedService {
    }

    public static final class ServiceB implements NamedService {
    }

    static final class ServiceC implements NamedService {
    }

    static final class ServiceD implements NamedService {
    }
}
