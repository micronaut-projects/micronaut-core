package io.micronaut.core.io.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.micronaut.core.optim.StaticOptimizations;
import io.micronaut.core.util.NativeImageUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

public class ServiceIndexTest {

    private static final String SERVICE = "io.test.Service";
    private static final String OTHER_SERVICE = "io.test.OtherService";
    private static final String MISSING_SERVICE = "io.test.MissingService";
    private static final String BEANS = "io.micronaut.inject.BeanDefinitionReference";

    @TempDir
    Path tempDir;

    @Test
    void servesTheRegisteredIndexForItsClassLoader() {
        ClassLoader classLoader = TestServiceIndexLoader.CLASS_LOADER;

        assertSame(TestServiceIndexLoader.INDEX, ServiceIndex.find(classLoader));
        // the index lists the standard names first, then the META-INF/micronaut names
        assertEquals(List.of(Hello.class, Hi.class, Hey.class), types(SoftServiceLoader.load(Greeter.class, classLoader).collectAll()));
        assertEquals(List.of(Hey.class), types(MicronautMetaServiceLoaderUtils.findMetaMicronautServiceEntries(classLoader, Greeter.class, null)));
    }

    @Test
    void scansTheClassPathOfAnyOtherClassLoader() throws IOException {
        // the test class path has no service files for Greeter, so only the index knows its services
        try (URLClassLoader child = new URLClassLoader(new URL[0], TestServiceIndexLoader.CLASS_LOADER)) {
            for (ClassLoader classLoader : List.of(ServiceIndexTest.class.getClassLoader(), child)) {
                assertNull(ServiceIndex.find(classLoader));
                assertEquals(List.of(), SoftServiceLoader.load(Greeter.class, classLoader).collectAll());
                assertEquals(Set.of(), MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(classLoader, Greeter.class.getName()));
            }
        }
    }

    @Test
    void testsTheNameConditionOnEveryName() {
        List<Greeter> greeters = SoftServiceLoader.load(Greeter.class, TestServiceIndexLoader.CLASS_LOADER,
            name -> !name.equals(Hi.class.getName()) && !name.equals(Hey.class.getName())).collectAll();
        assertEquals(List.of(Hello.class), types(greeters));

        List<String> names = new ArrayList<>();
        SoftServiceLoader.load(Greeter.class, TestServiceIndexLoader.CLASS_LOADER, name -> !name.equals(Hello.class.getName()))
            .forEach(definition -> names.add(definition.getName()));
        assertEquals(List.of(Hi.class.getName(), Hey.class.getName()), names);
    }

    @Test
    void canBeSwitchedOff() {
        String previous = System.getProperty(ServiceIndex.ENABLED_PROPERTY);
        try {
            System.setProperty(ServiceIndex.ENABLED_PROPERTY, "false");
            assertNull(ServiceIndex.find(TestServiceIndexLoader.CLASS_LOADER));
            assertEquals(List.of(), SoftServiceLoader.load(Greeter.class, TestServiceIndexLoader.CLASS_LOADER).collectAll());

            System.setProperty(ServiceIndex.ENABLED_PROPERTY, "true");
            assertSame(TestServiceIndexLoader.INDEX, ServiceIndex.find(TestServiceIndexLoader.CLASS_LOADER));
        } finally {
            restoreProperty(ServiceIndex.ENABLED_PROPERTY, previous);
        }
    }

    @Test
    void isIgnoredInNativeImageCode() {
        String previous = System.getProperty(NativeImageUtils.PROPERTY_IMAGE_CODE_KEY);
        try {
            for (String imageCode : List.of(NativeImageUtils.PROPERTY_IMAGE_CODE_VALUE_BUILDTIME, NativeImageUtils.PROPERTY_IMAGE_CODE_VALUE_RUNTIME)) {
                System.setProperty(NativeImageUtils.PROPERTY_IMAGE_CODE_KEY, imageCode);
                assertNull(ServiceIndex.find(TestServiceIndexLoader.CLASS_LOADER));
            }
        } finally {
            restoreProperty(NativeImageUtils.PROPERTY_IMAGE_CODE_KEY, previous);
        }
        assertNotNull(ServiceIndex.find(TestServiceIndexLoader.CLASS_LOADER));
    }

    @Test
    void cannotBeRegisteredTwice() {
        ServiceIndex second = new ServiceIndex(ServiceIndexTest.class.getClassLoader(), Map.of(), Map.of());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> StaticOptimizations.set(second));
        assertTrue(e.getMessage().contains("at most one service index"), e.getMessage());
        assertSame(TestServiceIndexLoader.INDEX, StaticOptimizations.get(ServiceIndex.class).orElseThrow());
    }

    @Test
    void holdsImmutableCopiesThatKeepTheOrder() {
        Map<String, Set<String>> micronautServices = new HashMap<>();
        micronautServices.put(SERVICE, new LinkedHashSet<>(List.of("c", "a", "b")));
        Map<String, List<String>> standardServices = new HashMap<>();
        standardServices.put(SERVICE, new ArrayList<>(List.of("z", "x", "z")));

        ServiceIndex index = new ServiceIndex(ServiceIndexTest.class.getClassLoader(), micronautServices, standardServices);
        micronautServices.get(SERVICE).add("d");
        micronautServices.put(OTHER_SERVICE, Set.of());
        standardServices.get(SERVICE).clear();

        assertEquals(List.of("c", "a", "b"), List.copyOf(index.micronautServices().get(SERVICE)));
        assertEquals(List.of("z", "x", "z"), index.standardServices().get(SERVICE));
        assertEquals(Set.of(SERVICE), index.micronautServices().keySet());
        assertThrows(UnsupportedOperationException.class, () -> index.micronautServices().get(SERVICE).add("d"));
        assertThrows(UnsupportedOperationException.class, () -> index.standardServices().put(OTHER_SERVICE, List.of()));
    }

    @Test
    void servesTheSameNamesInTheSameOrderAsTheScanOfAFatJar() throws IOException {
        Path jar = jar("app.jar", Map.of(
            "META-INF/services/" + SERVICE, "a.A\n# a comment\n\nb.B # the implementation of b\nc.C\n",
            "META-INF/services/" + OTHER_SERVICE, "o.O\n"
        ), List.of(
            "META-INF/micronaut/" + SERVICE + "/d.D",
            "META-INF/micronaut/" + SERVICE + "/e.E",
            "META-INF/micronaut/" + BEANS + "/x.$X$Definition",
            "META-INF/micronaut/" + BEANS + "/y.$Y$Definition",
            "META-INF/micronaut/" + BEANS + "/z.$Z$Definition"
        ));
        assertSameAsTheScan(jar);
    }

    @Test
    void servesTheSameNamesInTheSameOrderAsTheScanOfALayeredClassPath() throws IOException {
        Path first = jar("first.jar", Map.of(
            "META-INF/services/" + SERVICE, "a.A\nb.B\n"
        ), List.of(
            "META-INF/micronaut/" + SERVICE + "/d.D",
            "META-INF/micronaut/" + BEANS + "/x.$X$Definition"
        ));
        Path second = jar("second.jar", Map.of(
            // a name listed twice is loaded twice
            "META-INF/services/" + SERVICE, "b.B\nc.C\n",
            "META-INF/services/" + OTHER_SERVICE, "o.O\n"
        ), List.of(
            "META-INF/micronaut/" + SERVICE + "/e.E",
            "META-INF/micronaut/" + BEANS + "/y.$Y$Definition",
            "META-INF/micronaut/" + OTHER_SERVICE + "/p.P"
        ));
        assertSameAsTheScan(first, second);
    }

    @Test
    void scansTheServiceFilesOfATypeThatIsNotIndexed() throws IOException {
        Path jar = jar("app.jar", Map.of(
            "META-INF/services/" + SERVICE, "a.A\n"
        ), List.of(
            "META-INF/micronaut/" + SERVICE + "/d.D"
        ));
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
            // the index lists no standard names for SERVICE, and different META-INF/micronaut names
            ServiceIndex index = new ServiceIndex(classLoader, Map.of(SERVICE, Set.of("f.F")), Map.of(OTHER_SERVICE, List.of("o.O")));

            assertEquals(List.of("a.A", "f.F"), names(classLoader, SERVICE, index, true));
            assertEquals(List.of("a.A"), names(classLoader, SERVICE, index, name -> !name.equals("f.F"), false));
            assertEquals(List.of("o.O"), names(classLoader, OTHER_SERVICE, index, true));
            // the META-INF/micronaut names of the index are exhaustive
            assertEquals(List.of(), names(classLoader, MISSING_SERVICE, index, true));
        }
    }

    @Test
    @Timeout(120)
    void forksOneTaskPerIndexedName() {
        assumeTrue(ForkJoinPool.getCommonPoolParallelism() > 1, "the common pool does not run tasks in parallel");
        ClassLoader classLoader = ServiceIndexTest.class.getClassLoader();
        ServiceIndex index = new ServiceIndex(classLoader, Map.of(SERVICE, Set.of("b.B")), Map.of(SERVICE, List.of("a.A")));
        // each name waits for the other, which only completes if the names are loaded in parallel
        CyclicBarrier barrier = new CyclicBarrier(2);
        Function<String, String> transformer = name -> {
            try {
                barrier.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                throw new IllegalStateException("The names were not loaded in parallel", e);
            }
            return name;
        };
        List<String> names = new ArrayList<>();
        new ServiceScanner<>(classLoader, SERVICE, name -> true, transformer, index).createCollector().collect(names, true);
        assertEquals(List.of("a.A", "b.B"), names);
    }

    @Test
    void buildsTheSameIndexFromAnyDirectoryOrder() throws IOException {
        Path root = Files.createDirectories(tempDir.resolve("classes"));
        Path services = Files.createDirectories(root.resolve("META-INF/micronaut/" + SERVICE));
        for (String entry : List.of("m.M", "z.Z", "a.A")) {
            Files.createFile(services.resolve(entry));
        }
        // a hidden entry is left out, as the scan does
        boolean hidden = Files.isHidden(Files.createFile(services.resolve(".hidden")));
        Files.createDirectories(root.resolve("META-INF/micronaut/" + OTHER_SERVICE + "/b.B"));
        Files.createDirectories(root.resolve("META-INF/micronaut/" + BEANS));
        Files.writeString(Files.createDirectories(root.resolve("META-INF/services")).resolve(SERVICE), "s.S\n");

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{root.toUri().toURL()}, null)) {
            ServiceIndex index = ServiceIndexBuilder.build(classLoader, List.of(SERVICE));

            assertSame(classLoader, index.classLoader());
            assertEquals(List.of(BEANS, OTHER_SERVICE, SERVICE), List.copyOf(index.micronautServices().keySet()));
            assertEquals(hidden ? List.of("a.A", "m.M", "z.Z") : List.of(".hidden", "a.A", "m.M", "z.Z"), List.copyOf(index.micronautServices().get(SERVICE)));
            assertEquals(List.of("b.B"), List.copyOf(index.micronautServices().get(OTHER_SERVICE)));
            assertEquals(List.of(), List.copyOf(index.micronautServices().get(BEANS)));
            assertEquals(Map.of(SERVICE, List.of("s.S")), index.standardServices());

            // the scan finds the same entries, in the order of the file system
            Map<String, Set<String>> scanned = MicronautMetaServiceLoaderUtils.findAllMicronautMetaServices(classLoader);
            assertEquals(sorted(scanned), sorted(index.micronautServices()));
        }
    }

    private void assertSameAsTheScan(Path... jars) throws IOException {
        URL[] urls = new URL[jars.length];
        for (int i = 0; i < jars.length; i++) {
            urls[i] = jars[i].toUri().toURL();
        }
        try (URLClassLoader classLoader = new URLClassLoader(urls, null)) {
            ServiceIndex index = ServiceIndexBuilder.build(classLoader, List.of(SERVICE, OTHER_SERVICE, MISSING_SERVICE));

            assertEquals(asLists(MicronautMetaServiceLoaderUtils.findAllMicronautMetaServices(classLoader)), asLists(index.micronautServices()));
            assertEquals(List.of(), index.standardServices().get(MISSING_SERVICE));
            Set<String> types = new LinkedHashSet<>(index.standardServices().keySet());
            types.addAll(index.micronautServices().keySet());
            for (String type : types) {
                for (boolean fork : List.of(false, true)) {
                    assertEquals(names(classLoader, type, null, fork), names(classLoader, type, index, fork), type);
                }
            }
        }
    }

    private static List<String> names(ClassLoader classLoader, String type, ServiceIndex index, boolean fork) {
        return names(classLoader, type, index, name -> true, fork);
    }

    private static List<String> names(ClassLoader classLoader, String type, ServiceIndex index, Predicate<String> condition, boolean fork) {
        List<String> names = new ArrayList<>();
        new ServiceScanner<>(classLoader, type, condition, Function.identity(), index).createCollector().collect(names, fork);
        return names;
    }

    private static List<Class<?>> types(List<?> services) {
        List<Class<?>> types = new ArrayList<>();
        for (Object service : services) {
            types.add(service.getClass());
        }
        return types;
    }

    private static Map<String, List<String>> asLists(Map<String, Set<String>> services) {
        Map<String, List<String>> lists = new LinkedHashMap<>();
        services.forEach((service, entries) -> lists.put(service, List.copyOf(entries)));
        return lists;
    }

    private static Map<String, Set<String>> sorted(Map<String, Set<String>> services) {
        Map<String, Set<String>> sorted = new HashMap<>();
        services.forEach((service, entries) -> sorted.put(service, new TreeSet<>(entries)));
        return sorted;
    }

    private static void restoreProperty(String name, String previous) {
        if (previous == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, previous);
        }
    }

    private Path jar(String name, Map<String, String> files, List<String> entries) throws IOException {
        Path jar = tempDir.resolve(name);
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> file : new TreeMap<>(files).entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            for (String entry : entries) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.closeEntry();
            }
        }
        return jar;
    }

    public interface Greeter {
    }

    public static final class Hello implements Greeter {
        public Hello() {
        }
    }

    public static final class Hi implements Greeter {
        public Hi() {
        }
    }

    public static final class Hey implements Greeter {
        public Hey() {
        }
    }

    /**
     * Registers an index for a class loader of its own, so that no other test sees it.
     */
    public static final class TestServiceIndexLoader implements StaticOptimizations.Loader<ServiceIndex> {

        static final ClassLoader CLASS_LOADER = new URLClassLoader(new URL[0], ServiceIndexTest.class.getClassLoader());

        static final ServiceIndex INDEX = new ServiceIndex(
            CLASS_LOADER,
            Map.of(Greeter.class.getName(), Set.of(Hey.class.getName())),
            Map.of(Greeter.class.getName(), List.of(Hello.class.getName(), Hi.class.getName()))
        );

        @Override
        public ServiceIndex load() {
            return INDEX;
        }
    }
}
