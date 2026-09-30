package io.micronaut.core.io.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.core.io.service.ServiceIndex.ClassPathEntry;
import io.micronaut.core.optim.StaticOptimizations;
import io.micronaut.core.util.NativeImageUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

public class ServiceIndexTest {

    private static final String SERVICE = "io.test.Service";
    private static final String OTHER_SERVICE = "io.test.OtherService";
    private static final String MISSING_SERVICE = "io.test.MissingService";
    private static final String BEANS = "io.micronaut.inject.BeanDefinitionReference";

    @TempDir
    Path tempDir;

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void captureTheLog() {
        logged.start();
        ((Logger) LoggerFactory.getLogger(ServiceIndex.class)).addAppender(logged);
    }

    @AfterEach
    void releaseTheLog() {
        ((Logger) LoggerFactory.getLogger(ServiceIndex.class)).detachAppender(logged);
    }

    @Test
    void servesTheRegisteredIndexForItsClassLoader() {
        ClassLoader classLoader = TestServiceIndexLoader.CLASS_LOADER;

        assertSame(TestServiceIndexLoader.INDEX, ServiceScanner.findServiceIndex(classLoader));
        // the index lists the standard names first, then the META-INF/micronaut names
        assertEquals(List.of(Hello.class, Hi.class, Hey.class), types(SoftServiceLoader.load(Greeter.class, classLoader).collectAll()));
        assertEquals(List.of(Hey.class), types(MicronautMetaServiceLoaderUtils.findMetaMicronautServiceEntries(classLoader, Greeter.class, null)));
    }

    @Test
    void scansTheClassPathOfAnyOtherClassLoader() throws IOException {
        // the test class path has no service files for Greeter, so only the index knows its services
        try (URLClassLoader child = new URLClassLoader(new URL[0], TestServiceIndexLoader.CLASS_LOADER)) {
            for (ClassLoader classLoader : List.of(ServiceIndexTest.class.getClassLoader(), child)) {
                assertNull(ServiceScanner.findServiceIndex(classLoader));
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
            assertNull(ServiceScanner.findServiceIndex(TestServiceIndexLoader.CLASS_LOADER));
            assertEquals(List.of(), SoftServiceLoader.load(Greeter.class, TestServiceIndexLoader.CLASS_LOADER).collectAll());

            System.setProperty(ServiceIndex.ENABLED_PROPERTY, "true");
            assertSame(TestServiceIndexLoader.INDEX, ServiceScanner.findServiceIndex(TestServiceIndexLoader.CLASS_LOADER));
        } finally {
            restoreProperty(ServiceIndex.ENABLED_PROPERTY, previous);
        }
    }

    @Test
    void isAskedForOnceWhenALookupStarts() {
        ClassLoader classLoader = TestServiceIndexLoader.CLASS_LOADER;
        String hey = Hey.class.getName();
        String previous = System.getProperty(ServiceIndex.ENABLED_PROPERTY);
        try {
            // the index only starts to apply after the lookup has started: the lookup goes on scanning
            System.setProperty(ServiceIndex.ENABLED_PROPERTY, "false");
            SoftServiceLoader.ServiceCollector<String> scanning = SoftServiceLoader.newCollector(Greeter.class.getName(), name -> true, classLoader, Function.identity());
            System.setProperty(ServiceIndex.ENABLED_PROPERTY, "true");
            for (boolean fork : List.of(true, false)) {
                List<String> names = new ArrayList<>();
                scanning.collect(names, fork);
                assertEquals(List.of(), names);
            }

            // and the other way round
            SoftServiceLoader.ServiceCollector<String> indexed = SoftServiceLoader.newCollector(Greeter.class.getName(), name -> true, classLoader, Function.identity());
            System.setProperty(ServiceIndex.ENABLED_PROPERTY, "false");
            List<String> names = new ArrayList<>();
            indexed.collect(names, true);
            assertEquals(List.of(Hello.class.getName(), Hi.class.getName(), hey), names);
        } finally {
            restoreProperty(ServiceIndex.ENABLED_PROPERTY, previous);
        }
    }

    @Test
    void isAskedForOnceWhenACollectionOfMicronautServicesStarts() {
        // the collector that loads the bean definitions: findMetaMicronautServiceEntries creates one and collects
        ClassLoader classLoader = TestServiceIndexLoader.CLASS_LOADER;
        String type = Greeter.class.getName();
        String previous = System.getProperty(ServiceIndex.ENABLED_PROPERTY);
        try {
            // with fork, a task of the pool reads the entries, and without, the calling thread does
            for (boolean fork : List.of(true, false)) {
                // the index only starts to apply after the collector was created: it goes on scanning
                System.setProperty(ServiceIndex.ENABLED_PROPERTY, "false");
                MicronautMetaServiceLoaderUtils.MicronautServiceCollector<Greeter> scanning = new MicronautMetaServiceLoaderUtils.MicronautServiceCollector<>(classLoader, type, null);
                System.setProperty(ServiceIndex.ENABLED_PROPERTY, "true");
                assertEquals(List.of(), scanning.collect(fork), "fork: " + fork);

                // and the other way round
                MicronautMetaServiceLoaderUtils.MicronautServiceCollector<Greeter> indexed = new MicronautMetaServiceLoaderUtils.MicronautServiceCollector<>(classLoader, type, null);
                System.setProperty(ServiceIndex.ENABLED_PROPERTY, "false");
                assertEquals(List.of(Hey.class), types(indexed.collect(fork)), "fork: " + fork);
            }
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
                assertNull(ServiceScanner.findServiceIndex(TestServiceIndexLoader.CLASS_LOADER));
            }
        } finally {
            restoreProperty(NativeImageUtils.PROPERTY_IMAGE_CODE_KEY, previous);
        }
        assertNotNull(ServiceScanner.findServiceIndex(TestServiceIndexLoader.CLASS_LOADER));
    }

    @Test
    void cannotBeRegisteredTwice() {
        ServiceIndex second = new ServiceIndex(ServiceIndexTest.class.getClassLoader(), Map.of(), Map.of());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> StaticOptimizations.set(second));
        assertEquals("An optimization of class io.micronaut.core.io.service.ServiceIndex was already set: it can only be set once", e.getMessage());
        assertSame(TestServiceIndexLoader.INDEX, StaticOptimizations.get(ServiceIndex.class).orElseThrow());
    }

    @Test
    void holdsImmutableCopiesThatKeepTheOrder() {
        Map<String, Set<String>> micronautServices = new HashMap<>();
        micronautServices.put(SERVICE, new LinkedHashSet<>(List.of("c", "a", "b")));
        Map<String, List<String>> standardServices = new HashMap<>();
        standardServices.put(SERVICE, new ArrayList<>(List.of("z", "x", "z")));

        List<ClassPathEntry> classPath = new ArrayList<>(List.of(new ClassPathEntry("app.jar", 1)));

        ServiceIndex index = new ServiceIndex(ServiceIndexTest.class.getClassLoader(), micronautServices, standardServices, classPath);
        micronautServices.get(SERVICE).add("d");
        micronautServices.put(OTHER_SERVICE, Set.of());
        standardServices.get(SERVICE).clear();
        classPath.clear();

        assertEquals(List.of("c", "a", "b"), List.copyOf(index.micronautServices().get(SERVICE)));
        assertEquals(List.of("z", "x", "z"), index.standardServices().get(SERVICE));
        assertEquals(Set.of(SERVICE), index.micronautServices().keySet());
        assertEquals(List.of(new ClassPathEntry("app.jar", 1)), index.classPath());
        assertThrows(UnsupportedOperationException.class, () -> index.micronautServices().get(SERVICE).add("d"));
        assertThrows(UnsupportedOperationException.class, () -> index.standardServices().put(OTHER_SERVICE, List.of()));
        assertThrows(UnsupportedOperationException.class, () -> index.classPath().add(new ClassPathEntry("other.jar", 1)));
        assertNull(new ServiceIndex(ServiceIndexTest.class.getClassLoader(), micronautServices, standardServices).classPath());
    }

    @Test
    void usesTheCollectionsOfATrustedProducerAsTheyAre() {
        Set<String> entries = new LinkedHashSet<>(List.of("c", "a", "b"));
        List<String> names = new ArrayList<>(List.of("z", "x", "z"));
        Map<String, Set<String>> micronautServices = new LinkedHashMap<>(Map.of(SERVICE, entries));
        Map<String, List<String>> standardServices = new LinkedHashMap<>(Map.of(SERVICE, names));
        ClassLoader classLoader = ServiceIndexTest.class.getClassLoader();

        ServiceIndex trusted = ServiceIndex.ofTrusted(classLoader, micronautServices, standardServices, null);

        // nothing is copied, where the constructor copies every set and list
        assertSame(entries, trusted.micronautServices().get(SERVICE));
        assertSame(names, trusted.standardServices().get(SERVICE));
        ServiceIndex copied = new ServiceIndex(classLoader, micronautServices, standardServices);
        assertNotSame(entries, copied.micronautServices().get(SERVICE));
        assertNotSame(names, copied.standardServices().get(SERVICE));
        // the two are the same index, and the maps of both refuse changes
        assertEquals(copied, trusted);
        assertEquals(List.of(SERVICE), List.copyOf(trusted.micronautServices().keySet()));
        assertEquals(Set.of(), trusted.micronautServices().getOrDefault(MISSING_SERVICE, Set.of()));
        assertThrows(UnsupportedOperationException.class, () -> trusted.micronautServices().put(OTHER_SERVICE, Set.of()));
        assertThrows(UnsupportedOperationException.class, () -> trusted.micronautServices().remove(SERVICE));
        assertThrows(UnsupportedOperationException.class, () -> trusted.standardServices().put(OTHER_SERVICE, List.of()));
        // the index serves the names as the scan would
        assertEquals(List.of("z", "x", "z", "c", "a", "b"), names(classLoader, SERVICE, trusted, false));

        // an index made from the maps of a trusted index shares them, and the constructor still copies any other map
        ServiceIndex shared = new ServiceIndex(classLoader, trusted.micronautServices(), trusted.standardServices());
        assertSame(entries, shared.micronautServices().get(SERVICE));
        ServiceIndex mixed = new ServiceIndex(classLoader, trusted.micronautServices(), standardServices);
        assertNotSame(names, mixed.standardServices().get(SERVICE));
    }

    @Test
    void logsOnceThatTheIndexIsInUse() throws IOException {
        try (URLClassLoader classLoader = new URLClassLoader(new URL[0], null)) {
            ServiceIndex index = new ServiceIndex(classLoader,
                Map.of(SERVICE, Set.of("a.A", "b.B"), BEANS, Set.of("x.$X$Definition")),
                Map.of(SERVICE, List.of("s.S"), OTHER_SERVICE, List.of()));

            for (int i = 0; i < 3; i++) {
                assertSame(index, index.forLookup(classLoader));
            }
            // a lookup for another class loader is not served, and says nothing
            assertNull(index.forLookup(ServiceIndexTest.class.getClassLoader()));

            assertEquals(1, logged.list.size(), () -> logged.list.toString());
            ILoggingEvent event = logged.list.get(0);
            assertEquals(Level.INFO, event.getLevel());
            assertEquals("Using the service index registered for class loader " + classLoader
                + ": META-INF/micronaut (3 entries of 2 service types) and the META-INF/services files of 2 service types are not scanned"
                + " (class path not compared: the index does not list one)."
                + " Set the system property micronaut.service.index.enabled to false to scan the class path instead.", event.getFormattedMessage());
        }
    }

    @Test
    void logsNothingWhenItIsSwitchedOff() throws IOException {
        String previous = System.getProperty(ServiceIndex.ENABLED_PROPERTY);
        try (URLClassLoader classLoader = new URLClassLoader(new URL[0], null)) {
            System.setProperty(ServiceIndex.ENABLED_PROPERTY, "false");
            ServiceIndex index = new ServiceIndex(classLoader, Map.of(), Map.of());

            assertNull(index.forLookup(classLoader));
            assertEquals(List.of(), logged.list);
        } finally {
            restoreProperty(ServiceIndex.ENABLED_PROPERTY, previous);
        }
    }

    @Test
    void isServedForTheClassPathItWasBuiltFor() throws IOException {
        Path first = jar("first.jar", Map.of("META-INF/services/" + SERVICE, "a.A\n"), List.of("META-INF/micronaut/" + SERVICE + "/d.D"));
        Path second = jar("second.jar", Map.of("META-INF/services/" + SERVICE, "b.B\n"), List.of("META-INF/micronaut/" + SERVICE + "/e.E"));
        Path classes = Files.createDirectories(tempDir.resolve("classes"));
        URL missing = tempDir.resolve("missing.jar").toUri().toURL();
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{first.toUri().toURL(), missing, second.toUri().toURL(), classes.toUri().toURL()}, null)) {
            ServiceIndex built = ServiceIndexBuilder.build(classLoader, List.of(SERVICE));

            // the builder lists the class path of the class loader, with the size of each file, and without what does not exist
            assertEquals(List.of(
                new ClassPathEntry("first.jar", Files.size(first)),
                new ClassPathEntry("second.jar", Files.size(second)),
                new ClassPathEntry("classes", -1)
            ), built.classPath());
            assertSame(built, built.forLookup(classLoader));
            assertEquals(Level.INFO, logged.list.get(0).getLevel());
            assertTrue(logged.list.get(0).getFormattedMessage().contains("(class path compared)"), logged.list.get(0).getFormattedMessage());

            // the order of the class path does not matter, and an entry without a size matches a file of any size
            ServiceIndex reordered = new ServiceIndex(classLoader, built.micronautServices(), built.standardServices(), List.of(
                new ClassPathEntry("classes", -1),
                new ClassPathEntry("second.jar", -1),
                new ClassPathEntry("first.jar", Files.size(first))
            ));
            assertSame(reordered, reordered.forLookup(classLoader));
            assertEquals(List.of(Level.INFO, Level.INFO), logged.list.stream().map(ILoggingEvent::getLevel).toList());
        }
    }

    @Test
    void scansAClassPathThatItWasNotBuiltFor() throws IOException {
        Path first = jar("first.jar", Map.of("META-INF/services/" + SERVICE, "a.A\n"), List.of("META-INF/micronaut/" + SERVICE + "/d.D"));
        Path second = jar("second.jar", Map.of("META-INF/services/" + SERVICE, "b.B\n"), List.of("META-INF/micronaut/" + SERVICE + "/e.E"));
        ServiceIndex built;
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{first.toUri().toURL()}, null)) {
            built = ServiceIndexBuilder.build(classLoader, List.of(SERVICE));
        }

        // a JAR was added after the index was built: the index would miss b.B and e.E
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{first.toUri().toURL(), second.toUri().toURL()}, null)) {
            ServiceIndex stale = new ServiceIndex(classLoader, built.micronautServices(), built.standardServices(), built.classPath());

            assertNull(stale.forLookup(classLoader));
            assertNull(stale.forLookup(classLoader));
            assertEquals(List.of("a.A", "b.B", "d.D", "e.E"), names(classLoader, SERVICE, stale.forLookup(classLoader), false));
            assertEquals(1, logged.list.size(), () -> logged.list.toString());
            assertEquals(Level.WARN, logged.list.get(0).getLevel());
            assertEquals("Not using the service index registered for class loader " + classLoader
                + ", and scanning the class path instead, because the index was built for a different class path: the class path has [second.jar ("
                + Files.size(second) + " bytes)], which the index was not built for", logged.list.get(0).getFormattedMessage());
        }

        // a JAR was replaced by one of the same name, and another one was removed
        Path replaced = Files.createDirectories(tempDir.resolve("replaced")).resolve("first.jar");
        Files.copy(second, replaced);
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{replaced.toUri().toURL()}, null)) {
            ServiceIndex stale = new ServiceIndex(classLoader, built.micronautServices(), built.standardServices(), List.of(
                new ClassPathEntry("first.jar", Files.size(first) + 1),
                new ClassPathEntry("removed.jar", -1)
            ));

            assertNull(stale.forLookup(classLoader));
            assertTrue(logged.list.get(1).getFormattedMessage().endsWith("the class path lacks [first.jar (" + (Files.size(first) + 1) + " bytes), removed.jar], which the index was built for,"
                + " and has [first.jar (" + Files.size(second) + " bytes)], which it was not built for"), logged.list.get(1).getFormattedMessage());
        }
    }

    @Test
    void knowsTheClassPathOfTheSystemClassLoaderAndOfAUrlClassLoaderOfFiles() throws IOException {
        ClassLoader system = ClassLoader.getSystemClassLoader();
        List<ClassPathEntry> expected = new ArrayList<>();
        for (String element : System.getProperty("java.class.path").split(File.pathSeparator)) {
            Path path = Path.of(element);
            if (Files.exists(path)) {
                expected.add(new ClassPathEntry(path.getFileName().toString(), Files.isRegularFile(path) ? Files.size(path) : -1));
            }
        }
        assertFalse(expected.isEmpty());
        assertEquals(expected, ServiceIndex.classPathOf(system));

        // an index that lists the class path of the system class loader is compared with java.class.path
        ServiceIndex index = new ServiceIndex(system, Map.of(), Map.of(), expected);
        assertSame(index, index.forLookup(system));
        List<ClassPathEntry> longer = new ArrayList<>(expected);
        longer.add(new ClassPathEntry("removed.jar", 1));
        ServiceIndex stale = new ServiceIndex(system, Map.of(), Map.of(), longer);
        assertNull(stale.forLookup(system));
        assertTrue(logged.list.get(1).getFormattedMessage().endsWith("the class path lacks [removed.jar (1 bytes)], which the index was built for"), logged.list.get(1).getFormattedMessage());

        // the class path of any other class loader is not known, so the producer answers for the index
        ClassLoader custom = new ClassLoader(null) {
        };
        assertNull(ServiceIndex.classPathOf(custom));
        try (URLClassLoader remote = new URLClassLoader(new URL[]{URI.create("http://localhost/app.jar").toURL()}, null)) {
            assertNull(ServiceIndex.classPathOf(remote));
        }
        ServiceIndex unchecked = new ServiceIndex(custom, Map.of(), Map.of(), longer);
        assertSame(unchecked, unchecked.forLookup(custom));
        assertTrue(logged.list.get(2).getFormattedMessage().contains("(class path not compared: the class loader is neither the system class loader nor a URLClassLoader of files)"),
            logged.list.get(2).getFormattedMessage());
    }

    @Test
    void failsEveryLookupWhenItsValidationFindsDifferences() throws IOException {
        Path jar = jar("app.jar", Map.of(
            "META-INF/services/" + SERVICE, "a.A\nb.B\n",
            "META-INF/services/" + OTHER_SERVICE, "o.O\n"
        ), List.of(
            "META-INF/micronaut/" + SERVICE + "/d.D",
            "META-INF/micronaut/" + BEANS + "/x.$X$Definition",
            "META-INF/micronaut/" + BEANS + "/y.$Y$Definition"
        ));
        String previous = System.getProperty(ServiceIndex.VALIDATE_PROPERTY);
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
            ServiceIndex built = ServiceIndexBuilder.build(classLoader, List.of(SERVICE, OTHER_SERVICE, MISSING_SERVICE));
            // an index that misses a bean definition, lists a name that is gone, and lists a name once where two files would
            ServiceIndex wrong = new ServiceIndex(classLoader,
                Map.of(SERVICE, Set.of("d.D"), BEANS, Set.of("x.$X$Definition", "z.$Z$Definition")),
                Map.of(SERVICE, List.of("a.A", "a.A", "c.C"), OTHER_SERVICE, List.of("o.O"), MISSING_SERVICE, List.of()));

            // without the validation, the wrong index is served as it is
            assertSame(wrong, wrong.forLookup(classLoader));

            System.setProperty(ServiceIndex.VALIDATE_PROPERTY, "true");
            assertSame(built, built.forLookup(classLoader));
            assertTrue(logged.list.get(1).getFormattedMessage().contains("(class path compared, names validated against a scan)"), logged.list.get(1).getFormattedMessage());

            for (int i = 0; i < 2; i++) {
                ServiceConfigurationError e = assertThrows(ServiceConfigurationError.class, () -> wrong.forLookup(classLoader));
                assertEquals("The service index registered for class loader " + classLoader + " failed its validation: the index differs from a scan of the class path:"
                    + "\n  META-INF/micronaut/" + BEANS + ": missing from the index [y.$Y$Definition], not on the class path [z.$Z$Definition]"
                    + "\n  META-INF/services/" + SERVICE + ": missing from the index [b.B], not on the class path [a.A, c.C]", e.getMessage());
            }

            // a long list of names is cut short
            Set<String> many = new LinkedHashSet<>(built.micronautServices().get(SERVICE));
            for (int i = 10; i < 35; i++) {
                many.add("n.N" + i);
            }
            ServiceIndex longer = new ServiceIndex(classLoader, Map.of(SERVICE, many, BEANS, built.micronautServices().get(BEANS)), built.standardServices());
            ServiceConfigurationError cut = assertThrows(ServiceConfigurationError.class, () -> longer.forLookup(classLoader));
            assertTrue(cut.getMessage().endsWith("META-INF/micronaut/" + SERVICE + ": not on the class path [n.N10, n.N11, n.N12, n.N13, n.N14, n.N15, n.N16, n.N17, n.N18, n.N19,"
                + " n.N20, n.N21, n.N22, n.N23, n.N24, n.N25, n.N26, n.N27, n.N28, n.N29] and 5 more"), cut.getMessage());

            // an index that was built for another class path fails too, where it is only set aside without the validation
            ServiceIndex stale = new ServiceIndex(classLoader, built.micronautServices(), built.standardServices(), List.of(new ClassPathEntry("other.jar", -1)));
            ServiceConfigurationError e = assertThrows(ServiceConfigurationError.class, () -> stale.forLookup(classLoader));
            assertTrue(e.getMessage().contains("failed its validation: the index was built for a different class path: the class path lacks [other.jar]"), e.getMessage());
            assertEquals(List.of(Level.INFO, Level.INFO), logged.list.stream().map(ILoggingEvent::getLevel).toList());
        } finally {
            restoreProperty(ServiceIndex.VALIDATE_PROPERTY, previous);
        }
    }

    @Test
    void failsTheLookupsOfTheRegisteredIndexWhenItsValidationFindsDifferences() throws IOException {
        ClassLoader classLoader = TestServiceIndexLoader.CLASS_LOADER;
        String previous = System.getProperty(ServiceIndex.VALIDATE_PROPERTY);
        try {
            // the registered index names services that no file of the test class path lists
            System.setProperty(ServiceIndex.VALIDATE_PROPERTY, "true");
            forgetTheLastCheck();

            ServiceConfigurationError e = assertThrows(ServiceConfigurationError.class, () -> SoftServiceLoader.load(Greeter.class, classLoader).collectAll());
            assertTrue(e.getMessage().contains("META-INF/micronaut/" + Greeter.class.getName() + ": not on the class path [" + Hey.class.getName() + "]"), e.getMessage());
            assertTrue(e.getMessage().contains("META-INF/services/" + Greeter.class.getName() + ": not on the class path [" + Hello.class.getName() + ", " + Hi.class.getName() + "]"), e.getMessage());
            assertThrows(ServiceConfigurationError.class, () -> MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(classLoader, Greeter.class.getName()));
            // the lookups of any other class loader are not affected
            assertEquals(List.of(), SoftServiceLoader.load(Greeter.class, ServiceIndexTest.class.getClassLoader()).collectAll());
        } finally {
            restoreProperty(ServiceIndex.VALIDATE_PROPERTY, previous);
            forgetTheLastCheck();
        }
        assertEquals(List.of(Hello.class, Hi.class, Hey.class), types(SoftServiceLoader.load(Greeter.class, classLoader).collectAll()));
    }

    @Test
    void isRegisteredAfterALoaderThatLooksAServiceUpWhileTheOptimizationsAreInitialized() throws Exception {
        // an application of its own, whose optimizations are yet to be initialized, with two loaders: the first one
        // looks a service up, which forks, and the second one registers the index
        Path loaders = Files.createDirectories(tempDir.resolve("loaders"));
        Files.writeString(Files.createDirectories(loaders.resolve("META-INF/services")).resolve(StaticOptimizations.Loader.class.getName()),
            LookingUpLoader.class.getName() + "\n" + TestServiceIndexLoader.class.getName() + "\n");
        URL[] classPath = {
            SoftServiceLoader.class.getProtectionDomain().getCodeSource().getLocation(),
            org.slf4j.Logger.class.getProtectionDomain().getCodeSource().getLocation(),
            ServiceIndexTest.class.getProtectionDomain().getCodeSource().getLocation(),
            loaders.toUri().toURL()
        };
        try (RecordingClassLoader application = new RecordingClassLoader(classPath)) {
            // on a thread of its own, which is given up if the initialization never ends
            List<String> served = assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
                Thread.currentThread().setContextClassLoader(application);
                Class<?> lookingUp = application.loadClass(LookingUpLoader.class.getName());
                lookingUp.getField("lookUp").set(null, true);

                // the initialization runs the loaders
                Class<?> optimizations = Class.forName(StaticOptimizations.class.getName(), true, application);

                // the lookup of the first loader scanned the class path, which has no such service
                assertEquals(List.of(), lookingUp.getField("found").get(null));
                Object registered = optimizations.getMethod("findSetOnce", String.class).invoke(null, ServiceIndex.class.getName());
                assertNotNull(registered);
                // and the lookups that follow are served from the index
                Object classLoader = registered.getClass().getMethod("classLoader").invoke(registered);
                Class<?> loader = application.loadClass(SoftServiceLoader.class.getName());
                Object services = loader.getMethod("load", Class.class, ClassLoader.class).invoke(null, application.loadClass(Greeter.class.getName()), classLoader);
                List<String> types = new ArrayList<>();
                for (Object service : (List<?>) loader.getMethod("collectAll").invoke(services)) {
                    types.add(service.getClass().getName());
                }
                return types;
            });
            assertEquals(List.of(Hello.class.getName(), Hi.class.getName(), Hey.class.getName()), served);
        }
    }

    @Test
    void isNotLoadedByAnApplicationWithoutAnIndex() throws Exception {
        // the classes of core, loaded again by a class loader that does not see the loaders the tests register
        URL[] classPath = {
            SoftServiceLoader.class.getProtectionDomain().getCodeSource().getLocation(),
            org.slf4j.Logger.class.getProtectionDomain().getCodeSource().getLocation()
        };
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (RecordingClassLoader application = new RecordingClassLoader(classPath)) {
            Thread.currentThread().setContextClassLoader(application);
            Class<?> loader = application.loadClass(SoftServiceLoader.class.getName());
            assertSame(application, loader.getClassLoader());

            Object services = loader.getMethod("load", Class.class, ClassLoader.class).invoke(null, Runnable.class, application);
            assertEquals(List.of(), loader.getMethod("collectAll").invoke(services));
            assertEquals(Set.of(), application.loadClass(MicronautMetaServiceLoaderUtils.class.getName())
                .getMethod("findMicronautMetaServiceEntries", ClassLoader.class, String.class).invoke(null, application, Runnable.class.getName()));

            assertTrue(application.hasLoaded(ServiceScanner.class.getName()));
            assertTrue(application.hasLoaded(StaticOptimizations.class.getName()));
            assertFalse(application.hasLoaded(ServiceIndex.class.getName()));
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
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
        // an entry named with a leading dot is left out, as the scan does, whether or not the file system marks it hidden
        Files.createFile(services.resolve(".hidden"));
        Files.createDirectories(root.resolve("META-INF/micronaut/" + OTHER_SERVICE + "/b.B"));
        Files.createDirectories(root.resolve("META-INF/micronaut/" + BEANS));
        Files.writeString(Files.createDirectories(root.resolve("META-INF/services")).resolve(SERVICE), "s.S\n");

        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{root.toUri().toURL()}, null)) {
            ServiceIndex index = ServiceIndexBuilder.build(classLoader, List.of(SERVICE));

            assertSame(classLoader, index.classLoader());
            assertEquals(List.of(BEANS, OTHER_SERVICE, SERVICE), List.copyOf(index.micronautServices().keySet()));
            assertEquals(List.of("a.A", "m.M", "z.Z"), List.copyOf(index.micronautServices().get(SERVICE)));
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
            // the scan did find the entries of META-INF/micronaut
            assertFalse(index.micronautServices().get(SERVICE).isEmpty());
            assertFalse(index.micronautServices().get(BEANS).isEmpty());
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

    /**
     * Makes the next lookup check its index again, by checking another index: only the last check is kept.
     */
    private static void forgetTheLastCheck() throws IOException {
        try (URLClassLoader classLoader = new URLClassLoader(new URL[0], null)) {
            new ServiceIndex(classLoader, Map.of(), Map.of()).forLookup(classLoader);
        }
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
        Set<String> directories = new LinkedHashSet<>();
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> file : new TreeMap<>(files).entrySet()) {
                putDirectories(zip, file.getKey(), directories);
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            for (String entry : entries) {
                putDirectories(zip, entry, directories);
                zip.putNextEntry(new ZipEntry(entry));
                zip.closeEntry();
            }
        }
        return jar;
    }

    /**
     * Writes the entries of the directories of an entry, as the tools that build JARs do. Without the entry of
     * {@code META-INF/micronaut/}, a class loader does not find that directory, and the scan finds none of its services.
     */
    private static void putDirectories(ZipOutputStream zip, String entry, Set<String> directories) throws IOException {
        for (int slash = entry.indexOf('/'); slash != -1; slash = entry.indexOf('/', slash + 1)) {
            String directory = entry.substring(0, slash + 1);
            if (directories.add(directory)) {
                zip.putNextEntry(new ZipEntry(directory));
                zip.closeEntry();
            }
        }
    }

    /**
     * A class loader that tells which classes it has loaded.
     */
    private static final class RecordingClassLoader extends URLClassLoader {

        RecordingClassLoader(URL[] urls) {
            super(urls, ClassLoader.getPlatformClassLoader());
        }

        boolean hasLoaded(String name) {
            return findLoadedClass(name) != null;
        }
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

    /**
     * A loader that runs before the loader of the index and that, when it is told to, looks a service up.
     */
    public static final class LookingUpLoader implements StaticOptimizations.Loader<LookingUpLoader.Loaded> {

        public static volatile boolean lookUp;
        public static volatile List<Greeter> found;

        @Override
        public Loaded load() {
            if (lookUp) {
                found = SoftServiceLoader.load(Greeter.class, TestServiceIndexLoader.CLASS_LOADER).collectAll();
            }
            return new Loaded();
        }

        public static final class Loaded {
        }
    }
}
