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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
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
        ServiceIndex second = ServiceIndex.copyOf(ServiceIndexTest.class.getClassLoader(), Map.of(), Map.of(), null);
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

        ServiceIndex index = ServiceIndex.copyOf(ServiceIndexTest.class.getClassLoader(), micronautServices, standardServices, classPath);
        micronautServices.get(SERVICE).add("d");
        micronautServices.put(OTHER_SERVICE, Set.of());
        standardServices.get(SERVICE).clear();
        classPath.clear();

        assertEquals(List.of("c", "a", "b"), List.copyOf(index.micronautServices().get(SERVICE)));
        assertEquals(List.of("z", "x", "z"), index.standardServices().get(SERVICE));
        assertEquals(Set.of(SERVICE), index.micronautServices().keySet());
        assertEquals(List.of(new ClassPathEntry("app.jar", 1)), index.classPath());
        Set<String> entries = index.micronautServices().get(SERVICE);
        Map<String, List<String>> names = index.standardServices();
        List<String> noNames = List.of();
        List<ClassPathEntry> indexedClassPath = index.classPath();
        ClassPathEntry otherEntry = new ClassPathEntry("other.jar", 1);
        assertThrows(UnsupportedOperationException.class, () -> entries.add("d"));
        assertThrows(UnsupportedOperationException.class, () -> names.put(OTHER_SERVICE, noNames));
        assertThrows(UnsupportedOperationException.class, () -> indexedClassPath.add(otherEntry));
        assertNull(ServiceIndex.copyOf(ServiceIndexTest.class.getClassLoader(), micronautServices, standardServices, null).classPath());
    }

    @Test
    void usesTheCollectionsOfATrustedProducerAsTheyAre() {
        Set<String> entries = new LinkedHashSet<>(List.of("c", "a", "b"));
        List<String> names = new ArrayList<>(List.of("z", "x", "z"));
        Map<String, Set<String>> micronautServices = new LinkedHashMap<>(Map.of(SERVICE, entries));
        Map<String, List<String>> standardServices = new LinkedHashMap<>(Map.of(SERVICE, names));
        ClassLoader classLoader = ServiceIndexTest.class.getClassLoader();

        ServiceIndex trusted = ServiceIndex.ofTrusted(classLoader, micronautServices, standardServices, null);

        // nothing is copied, where copyOf copies every set and list
        assertSame(entries, trusted.micronautServices().get(SERVICE));
        assertSame(names, trusted.standardServices().get(SERVICE));
        ServiceIndex copied = ServiceIndex.copyOf(classLoader, micronautServices, standardServices, null);
        assertNotSame(entries, copied.micronautServices().get(SERVICE));
        assertNotSame(names, copied.standardServices().get(SERVICE));
        // the two hold the same names, and the maps of both refuse changes
        assertSame(copied.classLoader(), trusted.classLoader());
        assertEquals(copied.micronautServices(), trusted.micronautServices());
        assertEquals(copied.standardServices(), trusted.standardServices());
        assertNull(trusted.classPath());
        assertEquals(List.of(SERVICE), List.copyOf(trusted.micronautServices().keySet()));
        assertEquals(Set.of(), trusted.micronautServices().getOrDefault(MISSING_SERVICE, Set.of()));
        Map<String, Set<String>> trustedEntries = trusted.micronautServices();
        Map<String, List<String>> trustedNames = trusted.standardServices();
        Set<String> noEntries = Set.of();
        List<String> noNames = List.of();
        assertThrows(UnsupportedOperationException.class, () -> trustedEntries.put(OTHER_SERVICE, noEntries));
        assertThrows(UnsupportedOperationException.class, () -> trustedEntries.remove(SERVICE));
        assertThrows(UnsupportedOperationException.class, () -> trustedNames.put(OTHER_SERVICE, noNames));
        // the index serves the names as the scan would
        assertEquals(List.of("z", "x", "z", "c", "a", "b"), names(classLoader, SERVICE, trusted, false));

        // a copy of the maps of a trusted index copies them too
        ServiceIndex copyOfTrusted = ServiceIndex.copyOf(classLoader, trusted.micronautServices(), trusted.standardServices(), null);
        assertNotSame(entries, copyOfTrusted.micronautServices().get(SERVICE));
        assertNotSame(names, copyOfTrusted.standardServices().get(SERVICE));
    }

    @Test
    void logsOnceThatTheIndexIsInUse() throws IOException {
        try (URLClassLoader classLoader = new URLClassLoader(new URL[0], null)) {
            ServiceIndex index = ServiceIndex.copyOf(classLoader,
                Map.of(SERVICE, Set.of("a.A", "b.B"), BEANS, Set.of("x.$X$Definition")),
                Map.of(SERVICE, List.of("s.S"), OTHER_SERVICE, List.of()), null);

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
            ServiceIndex index = ServiceIndex.copyOf(classLoader, Map.of(), Map.of(), null);

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
            // the log says what was compared: with two JARs, their manifests are not read
            assertTrue(logged.list.get(0).getFormattedMessage().contains("(class path compared: the 3 entries of the URLs of the class loader, without the Class-Path of the manifests of its JARs)"),
                logged.list.get(0).getFormattedMessage());

            // the order of the class path does not matter, and an entry without a size matches a file of any size
            ServiceIndex reordered = ServiceIndex.copyOf(classLoader, built.micronautServices(), built.standardServices(), List.of(
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
            ServiceIndex stale = ServiceIndex.copyOf(classLoader, built.micronautServices(), built.standardServices(), built.classPath());

            assertNull(stale.forLookup(classLoader));
            assertNull(stale.forLookup(classLoader));
            assertEquals(List.of("a.A", "b.B", "d.D", "e.E"), names(classLoader, SERVICE, stale.forLookup(classLoader), false));
            assertEquals(1, logged.list.size(), () -> logged.list.toString());
            assertEquals(Level.WARN, logged.list.get(0).getLevel());
            assertEquals("Not using the service index registered for class loader " + classLoader
                + ", and scanning the class path instead, because the index was built for a different class path: the class path has [second.jar ("
                + Files.size(second) + " bytes)], which the index was not built for"
                + " (compared with the 2 entries of the URLs of the class loader, without the Class-Path of the manifests of its JARs)", logged.list.get(0).getFormattedMessage());
        }

        // a JAR was replaced by one of the same name, and another one was removed
        Path replaced = Files.createDirectories(tempDir.resolve("replaced")).resolve("first.jar");
        Files.copy(second, replaced);
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{replaced.toUri().toURL()}, null)) {
            ServiceIndex stale = ServiceIndex.copyOf(classLoader, built.micronautServices(), built.standardServices(), List.of(
                new ClassPathEntry("first.jar", Files.size(first) + 1),
                new ClassPathEntry("removed.jar", -1)
            ));

            assertNull(stale.forLookup(classLoader));
            assertTrue(logged.list.get(1).getFormattedMessage().endsWith("the class path lacks [first.jar (" + (Files.size(first) + 1) + " bytes), removed.jar], which the index was built for,"
                + " and has [first.jar (" + Files.size(second) + " bytes)], which it was not built for"
                + " (compared with the 1 entry of the URLs of the class loader, to which the manifest of first.jar adds nothing)"), logged.list.get(1).getFormattedMessage());
        }
    }

    @Test
    void doesNotCompareTheClassPathOfAParentClassLoaderAndSaysSo() throws IOException {
        Path first = jar("parent.jar", Map.of("META-INF/services/" + SERVICE, "p.One\n"), List.of());
        Path added = jar("added.jar", Map.of("META-INF/services/" + SERVICE, "p.Two\n"), List.of());
        Path child = jar("child.jar", Map.of("META-INF/services/" + SERVICE, "c.C\n"), List.of());
        String notCompared = "(class path compared: the 1 entry of the URLs of the class loader, to which the manifest of child.jar adds nothing;"
            + " the class path of its parent class loaders is not compared)";
        ServiceIndex built;
        try (URLClassLoader parent = new URLClassLoader(new URL[]{first.toUri().toURL()}, null);
             URLClassLoader classLoader = new URLClassLoader(new URL[]{child.toUri().toURL()}, parent)) {
            built = ServiceIndexBuilder.build(classLoader, List.of(SERVICE));

            // the services of the parent are indexed, and its class path is not listed
            assertEquals(List.of("p.One", "c.C"), built.standardServices().get(SERVICE));
            assertEquals(List.of(new ClassPathEntry("child.jar", Files.size(child))), built.classPath());
            assertSame(built, built.forLookup(classLoader));
            assertTrue(logged.list.get(0).getFormattedMessage().contains(notCompared), logged.list.get(0).getFormattedMessage());
        }

        // so a JAR that is added to the class path of the parent is not detected: the index is served, and misses p.Two
        try (URLClassLoader parent = new URLClassLoader(new URL[]{first.toUri().toURL(), added.toUri().toURL()}, null);
             URLClassLoader classLoader = new URLClassLoader(new URL[]{child.toUri().toURL()}, parent)) {
            ServiceIndex stale = ServiceIndex.copyOf(classLoader, built.micronautServices(), built.standardServices(), built.classPath());

            assertSame(stale, stale.forLookup(classLoader));
            assertEquals(List.of("p.One", "c.C"), names(classLoader, SERVICE, stale, false));
            assertEquals(List.of("p.One", "p.Two", "c.C"), names(classLoader, SERVICE, null, false));
            assertEquals(List.of(Level.INFO, Level.INFO), logged.list.stream().map(ILoggingEvent::getLevel).toList());
            assertTrue(logged.list.get(1).getFormattedMessage().contains(notCompared), logged.list.get(1).getFormattedMessage());
        }

        // a class loader whose parent is the platform class loader has no such class path
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{child.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            ServiceIndex index = ServiceIndexBuilder.build(classLoader, List.of(SERVICE));

            assertSame(index, index.forLookup(classLoader));
            assertTrue(logged.list.get(2).getFormattedMessage().contains("(class path compared: the 1 entry of the URLs of the class loader, to which the manifest of child.jar adds nothing)"),
                logged.list.get(2).getFormattedMessage());
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
        // the tests run with a class path of several JARs, whose manifests are therefore not read
        assumeTrue(expected.stream().filter(entry -> entry.size() >= 0).count() > 1, "the class path of the tests has a single JAR");
        assertEquals(expected, ServiceIndex.classPathOf(system));

        // an index that lists the class path of the system class loader is compared with java.class.path
        ServiceIndex index = ServiceIndex.copyOf(system, Map.of(), Map.of(), expected);
        assertSame(index, index.forLookup(system));
        List<ClassPathEntry> longer = new ArrayList<>(expected);
        longer.add(new ClassPathEntry("removed.jar", 1));
        ServiceIndex stale = ServiceIndex.copyOf(system, Map.of(), Map.of(), longer);
        assertNull(stale.forLookup(system));
        assertTrue(logged.list.get(0).getFormattedMessage().contains("(class path compared: the " + expected.size() + " entries of java.class.path"), logged.list.get(0).getFormattedMessage());
        assertTrue(logged.list.get(1).getFormattedMessage().contains("the class path lacks [removed.jar (1 bytes)], which the index was built for (compared with the " + expected.size() + " entries of java.class.path"),
            logged.list.get(1).getFormattedMessage());

        // the class path of any other class loader is not known, so the producer answers for the index
        ClassLoader custom = new ClassLoader(null) {
        };
        assertNull(ServiceIndex.classPathOf(custom));
        try (URLClassLoader remote = new URLClassLoader(new URL[]{URI.create("http://localhost/app.jar").toURL()}, null)) {
            assertNull(ServiceIndex.classPathOf(remote));
        }
        ServiceIndex unchecked = ServiceIndex.copyOf(custom, Map.of(), Map.of(), longer);
        assertSame(unchecked, unchecked.forLookup(custom));
        assertTrue(logged.list.get(2).getFormattedMessage().contains("(class path not compared: the class loader is neither the system class loader nor a URLClassLoader of files)"),
            logged.list.get(2).getFormattedMessage());
    }

    @Test
    void comparesAJarThatIsAloneOnTheClassPathWithTheClassPathOfItsManifest() throws IOException {
        Path lib = Files.createDirectories(tempDir.resolve("lib"));
        // a library whose own manifest names another JAR
        Path dep = jar(lib.resolve("dep.jar"), Map.of("Class-Path", "nested.jar"), Map.of("META-INF/services/" + SERVICE, "a.A\n"), List.of("META-INF/micronaut/" + SERVICE + "/d.D"));
        jar(lib.resolve("nested.jar"), Map.of(), Map.of("META-INF/services/" + SERVICE, "n.N\n"), List.of());
        Path spaced = jar(lib.resolve("my lib.jar"), Map.of(), Map.of("META-INF/services/" + SERVICE, "s.S\n"), List.of());
        Path config = Files.createDirectories(tempDir.resolve("config"));
        Files.writeString(Files.createDirectories(config.resolve("META-INF/services")).resolve(SERVICE), "c.C\n");
        Path elsewhere = jar(Files.createDirectories(tempDir.resolve("elsewhere")).resolve("abs.jar"), Map.of(), Map.of("META-INF/services/" + SERVICE, "e.E\n"), List.of());
        Path later = lib.resolve("later.jar");
        String classPath = String.join(" ",
            // a JAR and a directory, as URLs relative to the JAR, an absolute URL, and the directory of the JAR
            "lib/dep.jar", "lib/my%20lib.jar", "config/", elsewhere.toUri().toString(), ".",
            // what the class loader does not find: a file that is not there, a directory that is not named as one, a JAR named as a directory, and a URL that is not a file
            "lib/later.jar", "lib", "lib/nested.jar/", "http://localhost/remote.jar",
            // what it already has: a JAR named twice, and the JAR itself
            "lib/../lib/dep.jar", "app.jar");
        Path app = jar(tempDir.resolve("app.jar"), Map.of("Class-Path", classPath), Map.of(), List.of());
        URL[] alone = {app.toUri().toURL()};
        List<ClassPathEntry> listed = List.of(
            new ClassPathEntry("app.jar", Files.size(app)),
            new ClassPathEntry("dep.jar", Files.size(dep)),
            new ClassPathEntry("my lib.jar", Files.size(spaced)),
            new ClassPathEntry("config", -1),
            new ClassPathEntry("abs.jar", Files.size(elsewhere)),
            // named as it is written, and not after the directory, whose name is not the same where the application is deployed
            new ClassPathEntry(".", -1));
        String compared = "the 1 entry of the URLs of the class loader and the 5 entries of the Class-Path of the manifest of app.jar";

        ServiceIndex built;
        try (URLClassLoader classLoader = new URLClassLoader(alone, null)) {
            assertEquals(listed, ServiceIndex.classPathOf(classLoader));
            // the class loader also reads the manifest of dep.jar, so nested.jar is on the class path and is not listed
            assertEquals(List.of("a.A", "c.C", "d.D", "e.E", "n.N", "s.S"), sortedNames(classLoader, SERVICE, null));

            built = ServiceIndexBuilder.build(classLoader, List.of(SERVICE));
            assertEquals(listed, built.classPath());
            assertSame(built, built.forLookup(classLoader));
            assertEquals(Level.INFO, logged.list.get(0).getLevel());
            assertTrue(logged.list.get(0).getFormattedMessage().contains("(class path compared: " + compared + ")"), logged.list.get(0).getFormattedMessage());

            // an index that only lists the JAR was not built for the libraries of its manifest
            ServiceIndex unaware = ServiceIndex.copyOf(classLoader, built.micronautServices(), built.standardServices(), List.of(new ClassPathEntry("app.jar", -1)));
            assertNull(unaware.forLookup(classLoader));
            assertEquals(Level.WARN, logged.list.get(1).getLevel());
            assertTrue(logged.list.get(1).getFormattedMessage().endsWith("the class path has [dep.jar (" + Files.size(dep) + " bytes), my lib.jar (" + Files.size(spaced) + " bytes), config, abs.jar ("
                + Files.size(elsewhere) + " bytes), .], which the index was not built for (compared with " + compared + ")"), logged.list.get(1).getFormattedMessage());
        }

        // a library is replaced, and one that the manifest names and that was not there is added: the index would miss b.B and l.L
        long builtFor = Files.size(dep);
        jar(dep, Map.of("Class-Path", "nested.jar"), Map.of("META-INF/services/" + SERVICE, "a.A\nb.B\n"), List.of("META-INF/micronaut/" + SERVICE + "/d.D"));
        jar(later, Map.of(), Map.of("META-INF/services/" + SERVICE, "l.L\n"), List.of());
        try (URLClassLoader classLoader = new URLClassLoader(alone, null)) {
            ServiceIndex stale = ServiceIndex.copyOf(classLoader, built.micronautServices(), built.standardServices(), built.classPath());

            assertNull(stale.forLookup(classLoader));
            assertEquals(List.of("a.A", "b.B", "c.C", "d.D", "e.E", "l.L", "n.N", "s.S"), sortedNames(classLoader, SERVICE, stale.forLookup(classLoader)));
            assertEquals(3, logged.list.size(), () -> logged.list.toString());
            assertEquals(Level.WARN, logged.list.get(2).getLevel());
            assertTrue(logged.list.get(2).getFormattedMessage().endsWith("the class path lacks [dep.jar (" + builtFor + " bytes)], which the index was built for, and has [dep.jar (" + Files.size(dep)
                + " bytes), later.jar (" + Files.size(later) + " bytes)], which it was not built for (compared with the 1 entry of the URLs of the class loader and the 6 entries of the Class-Path of the manifest of app.jar)"),
                logged.list.get(2).getFormattedMessage());
        }

        // with a second JAR on the class path, no manifest is read, and the log says so
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{app.toUri().toURL(), spaced.toUri().toURL()}, null)) {
            List<ClassPathEntry> withoutManifests = List.of(new ClassPathEntry("app.jar", Files.size(app)), new ClassPathEntry("my lib.jar", Files.size(spaced)));
            assertEquals(withoutManifests, ServiceIndex.classPathOf(classLoader));
            ServiceIndex index = ServiceIndex.copyOf(classLoader, Map.of(), Map.of(), withoutManifests);
            assertSame(index, index.forLookup(classLoader));
            assertTrue(logged.list.get(3).getFormattedMessage().contains("(class path compared: the 2 entries of the URLs of the class loader, without the Class-Path of the manifests of its JARs)"),
                logged.list.get(3).getFormattedMessage());
        }
    }

    @Test
    void comparesTheClassPathOfAnApplicationStartedWithJavaJar() throws Exception {
        Path lib = Files.createDirectories(tempDir.resolve("lib"));
        Path dep = jar(lib.resolve("dep.jar"), Map.of(), Map.of("META-INF/services/" + SERVICE, "a.A\n"), List.of("META-INF/micronaut/" + SERVICE + "/d.D"));
        Path extra = lib.resolve("extra.jar");
        // a thin JAR: its manifest names its libraries, one of which is not there, then core, the tests and their logging
        List<String> libraries = new ArrayList<>(List.of("lib/dep.jar", "lib/extra.jar"));
        for (Class<?> type : List.of(SoftServiceLoader.class, ServiceIndexTest.class, org.slf4j.Logger.class, Logger.class, ListAppender.class)) {
            String location = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toUri().toString();
            if (!libraries.contains(location)) {
                libraries.add(location);
            }
        }
        Map<String, String> manifest = Map.of("Main-Class", ThinJarApplication.class.getName(), "Class-Path", String.join(" ", libraries));
        Map<String, String> files = new HashMap<>();
        files.put("META-INF/services/" + StaticOptimizations.Loader.class.getName(), ThinJarIndexLoader.class.getName() + "\n");
        Path app = jar(tempDir.resolve("app.jar"), manifest, files, List.of());

        // a producer builds the index for the JAR, which lists the JAR and what its manifest names
        ServiceIndex built;
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{app.toUri().toURL()}, null)) {
            built = ServiceIndexBuilder.build(classLoader, List.of(SERVICE));
        }
        assertEquals(libraries.size(), built.classPath().size(), () -> built.classPath().toString());
        assertEquals(List.of(new ClassPathEntry("app.jar", Files.size(app)), new ClassPathEntry("dep.jar", Files.size(dep))), built.classPath().subList(0, 2));
        // or for a class loader of the JAR and all its libraries, which lists the same entries
        List<URL> all = new ArrayList<>();
        all.add(app.toUri().toURL());
        for (String library : libraries) {
            all.add(app.toUri().resolve(library).toURL());
        }
        try (URLClassLoader classLoader = new URLClassLoader(all.toArray(URL[]::new), null)) {
            assertEquals(built.classPath(), ServiceIndexBuilder.build(classLoader, List.of(SERVICE)).classPath());
        }
        // then it adds the index to the JAR, whose size it therefore does not list
        List<ClassPathEntry> classPath = new ArrayList<>(built.classPath());
        classPath.set(0, new ClassPathEntry("app.jar", -1));
        files.put(ThinJarIndexLoader.RESOURCE, ThinJarIndexLoader.describe(built, classPath));
        jar(app, manifest, files, List.of());

        String served = java("-jar", "app.jar");
        assertTrue(served.contains("java.class.path=app.jar" + System.lineSeparator()), served);
        assertTrue(served.contains("Using the service index"), served);
        assertTrue(served.contains("(class path compared: the 1 entry of java.class.path and the " + (libraries.size() - 1) + " entries of the Class-Path of the manifest of app.jar)"), served);
        assertTrue(served.contains("names=[a.A, d.D]"), served);

        // the JAR is deployed under another name: the index is set aside
        Files.copy(app, tempDir.resolve("application.jar"));
        String renamed = java("-jar", "application.jar");
        assertFalse(renamed.contains("Using the service index"), renamed);
        assertTrue(renamed.contains("Not using the service index"), renamed);
        assertTrue(renamed.contains("the class path lacks [app.jar], which the index was built for, and has [application.jar (" + Files.size(app) + " bytes)], which it was not built for"), renamed);
        assertTrue(renamed.contains("names=[a.A, d.D]"), renamed);

        // a library is replaced and the one that was not there is added: the index would miss b.B, e.E and f.F
        long builtFor = Files.size(dep);
        jar(dep, Map.of(), Map.of("META-INF/services/" + SERVICE, "a.A\nb.B\n"), List.of("META-INF/micronaut/" + SERVICE + "/d.D", "META-INF/micronaut/" + SERVICE + "/e.E"));
        jar(extra, Map.of(), Map.of("META-INF/services/" + SERVICE, "f.F\n"), List.of());
        String stale = java("-jar", "app.jar");
        assertFalse(stale.contains("Using the service index"), stale);
        assertTrue(stale.contains("Not using the service index"), stale);
        assertTrue(stale.contains("the class path lacks [dep.jar (" + builtFor + " bytes)], which the index was built for, and has [dep.jar (" + Files.size(dep) + " bytes), extra.jar (" + Files.size(extra)
            + " bytes)], which it was not built for (compared with the 1 entry of java.class.path and the " + libraries.size() + " entries of the Class-Path of the manifest of app.jar)"), stale);
        assertTrue(stale.contains("names=[a.A, b.B, d.D, e.E, f.F]"), stale);
    }

    @Test
    void isSetAsideForAJarThatIsDeployedUnderAnotherName() throws IOException {
        Path packaged = jar("app.jar", Map.of("META-INF/services/" + SERVICE, "a.A\n"), List.of("META-INF/micronaut/" + SERVICE + "/d.D"));
        ServiceIndex built;
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{packaged.toUri().toURL()}, null)) {
            built = ServiceIndexBuilder.build(classLoader, List.of(SERVICE));
        }
        // what a container image often does
        Path deployed = Files.copy(packaged, tempDir.resolve("application.jar"));
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{deployed.toUri().toURL()}, null)) {
            // the entries are compared by name, so the JAR that holds the index does not match the name it was packaged with
            ServiceIndex asPackaged = ServiceIndex.copyOf(classLoader, built.micronautServices(), built.standardServices(), List.of(new ClassPathEntry("app.jar", -1)));
            assertNull(asPackaged.forLookup(classLoader));
            assertEquals(List.of("a.A", "d.D"), names(classLoader, SERVICE, asPackaged.forLookup(classLoader), false));
            assertEquals(1, logged.list.size(), () -> logged.list.toString());
            assertEquals(Level.WARN, logged.list.get(0).getLevel());
            assertTrue(logged.list.get(0).getFormattedMessage().endsWith("the class path lacks [app.jar], which the index was built for, and has [application.jar (" + Files.size(deployed)
                + " bytes)], which it was not built for (compared with the 1 entry of the URLs of the class loader, to which the manifest of application.jar adds nothing)"), logged.list.get(0).getFormattedMessage());

            // a producer that knows the name the JAR is deployed with lists that name
            ServiceIndex asDeployed = ServiceIndex.copyOf(classLoader, built.micronautServices(), built.standardServices(), List.of(new ClassPathEntry("application.jar", -1)));
            assertSame(asDeployed, asDeployed.forLookup(classLoader));
            // and one that does not know it lists no class path, which is then not compared
            ServiceIndex unlisted = ServiceIndex.copyOf(classLoader, built.micronautServices(), built.standardServices(), null);
            assertSame(unlisted, unlisted.forLookup(classLoader));
            assertEquals(List.of(Level.WARN, Level.INFO, Level.INFO), logged.list.stream().map(ILoggingEvent::getLevel).toList());
            assertTrue(logged.list.get(2).getFormattedMessage().contains("(class path not compared: the index does not list one)"), logged.list.get(2).getFormattedMessage());
        }
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
            ServiceIndex wrong = ServiceIndex.copyOf(classLoader,
                Map.of(SERVICE, Set.of("d.D"), BEANS, Set.of("x.$X$Definition", "z.$Z$Definition")),
                Map.of(SERVICE, List.of("a.A", "a.A", "c.C"), OTHER_SERVICE, List.of("o.O"), MISSING_SERVICE, List.of()), null);

            // without the validation, the wrong index is served as it is
            assertSame(wrong, wrong.forLookup(classLoader));

            System.setProperty(ServiceIndex.VALIDATE_PROPERTY, "true");
            assertSame(built, built.forLookup(classLoader));
            assertTrue(logged.list.get(1).getFormattedMessage().contains("(class path compared: the 1 entry of the URLs of the class loader, to which the manifest of app.jar adds nothing, names validated against a scan)"),
                logged.list.get(1).getFormattedMessage());

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
            ServiceIndex longer = ServiceIndex.copyOf(classLoader, Map.of(SERVICE, many, BEANS, built.micronautServices().get(BEANS)), built.standardServices(), null);
            ServiceConfigurationError cut = assertThrows(ServiceConfigurationError.class, () -> longer.forLookup(classLoader));
            assertTrue(cut.getMessage().endsWith("META-INF/micronaut/" + SERVICE + ": not on the class path [n.N10, n.N11, n.N12, n.N13, n.N14, n.N15, n.N16, n.N17, n.N18, n.N19,"
                + " n.N20, n.N21, n.N22, n.N23, n.N24, n.N25, n.N26, n.N27, n.N28, n.N29] and 5 more"), cut.getMessage());

            // an index that was built for another class path fails too, where it is only set aside without the validation
            ServiceIndex stale = ServiceIndex.copyOf(classLoader, built.micronautServices(), built.standardServices(), List.of(new ClassPathEntry("other.jar", -1)));
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

            SoftServiceLoader<Greeter> loader = SoftServiceLoader.load(Greeter.class, classLoader);
            String greeter = Greeter.class.getName();
            ServiceConfigurationError e = assertThrows(ServiceConfigurationError.class, loader::collectAll);
            assertTrue(e.getMessage().contains("META-INF/micronaut/" + greeter + ": not on the class path [" + Hey.class.getName() + "]"), e.getMessage());
            assertTrue(e.getMessage().contains("META-INF/services/" + greeter + ": not on the class path [" + Hello.class.getName() + ", " + Hi.class.getName() + "]"), e.getMessage());
            assertThrows(ServiceConfigurationError.class, () -> MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(classLoader, greeter));
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
        try (RecordingClassLoader application = new RecordingClassLoader(applicationWithLoaders(LookingUpLoader.class, TestServiceIndexLoader.class))) {
            // on a thread of its own, which is given up if the initialization never ends
            List<String> served = assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
                Thread.currentThread().setContextClassLoader(application);
                Class<?> lookingUp = application.loadClass(LookingUpLoader.class.getName());
                lookingUp.getField("lookUp").set(null, true);

                // the initialization runs the loaders
                Class.forName(StaticOptimizations.class.getName(), true, application);

                // the lookup of the first loader scanned the class path, which has no such service
                assertEquals(List.of(), lookingUp.getField("found").get(null));
                Object registered = application.loadClass(StaticOptimizations.SetOnce.class.getName()).getMethod("find", String.class).invoke(null, ServiceIndex.class.getName());
                assertNotNull(registered);
                // and the lookups that follow are served from the index
                Method classLoaderOfTheIndex = registered.getClass().getDeclaredMethod("classLoader");
                classLoaderOfTheIndex.setAccessible(true);
                Object classLoader = classLoaderOfTheIndex.invoke(registered);
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
    void doesNotMakeALookupOnAPoolThreadWaitForTheLoaders() throws Exception {
        assumeTrue(ForkJoinPool.getCommonPoolParallelism() > 1, "the common pool does not run tasks in parallel");
        // an application of its own, without an index, whose optimizations are yet to be initialized. Its loader looks
        // services up, which forks, and the constructor of those services looks services up in turn. The thread that
        // runs the loader waits for the threads of the pool, so their lookups must not wait for the loaders to end
        int services = 8;
        List<URL> classPath = new ArrayList<>(List.of(applicationWithLoaders(NestedLookupLoader.class)));
        for (int i = 0; i < services; i++) {
            // a file of its own for each service, so that each one is a task of its own
            Path directory = Files.createDirectories(tempDir.resolve("services" + i).resolve("META-INF/services"));
            Files.writeString(directory.resolve(NestedLookup.class.getName()), NestedLookupService.class.getName() + "\n");
            classPath.add(tempDir.resolve("services" + i).toUri().toURL());
        }
        try (RecordingClassLoader application = new RecordingClassLoader(classPath.toArray(URL[]::new))) {
            // on a thread of its own, which is given up if the initialization never ends
            List<?> lookedUp = assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
                Thread.currentThread().setContextClassLoader(application);
                // the initialization runs the loader
                Class.forName(StaticOptimizations.class.getName(), true, application);
                return List.of(
                    application.loadClass(NestedLookupLoader.class.getName()).getField("found").get(null),
                    application.loadClass(NestedLookupService.class.getName()).getField("POOL_THREADS").get(null)
                );
            }, "a lookup that a service makes on a thread of the pool waits for the loaders, which wait for that thread");
            assertEquals(services, lookedUp.get(0));
            // the lookups of the services did run on threads of the pool
            assertFalse(((Set<?>) lookedUp.get(1)).isEmpty());
        }
    }

    @Test
    void isReadByAnotherThreadWhileTheLoadersRun() throws Exception {
        // an application of its own, whose optimizations are yet to be initialized, with two loaders: the first one
        // registers the index, and the second one has another thread read it and waits for that thread
        try (RecordingClassLoader application = new RecordingClassLoader(applicationWithLoaders(TestServiceIndexLoader.class, ReadingLoader.class))) {
            List<?> read = assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
                Thread.currentThread().setContextClassLoader(application);
                // the initialization runs the loaders
                Class.forName(StaticOptimizations.class.getName(), true, application);
                Class<?> reading = application.loadClass(ReadingLoader.class.getName());
                Object registered = application.loadClass(StaticOptimizations.SetOnce.class.getName()).getMethod("find", String.class).invoke(null, ServiceIndex.class.getName());
                assertNotNull(registered);
                return List.of(reading.getField("returned").get(null), reading.getField("read").get(null) == registered);
            });
            // the read did not wait for the loaders to end, and it found the index that was registered before it
            assertEquals(List.of(true, true), read);
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
            ServiceIndex index = ServiceIndex.copyOf(classLoader, Map.of(SERVICE, Set.of("f.F")), Map.of(OTHER_SERVICE, List.of("o.O")), null);

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
        ServiceIndex index = ServiceIndex.copyOf(classLoader, Map.of(SERVICE, Set.of("b.B")), Map.of(SERVICE, List.of("a.A")), null);
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

    private static List<String> sortedNames(ClassLoader classLoader, String type, ServiceIndex index) {
        List<String> names = names(classLoader, type, index, false);
        Collections.sort(names);
        return names;
    }

    /**
     * Runs {@code java} with the given arguments in the temporary directory, and waits for it to end.
     *
     * @return What it wrote
     */
    private String java(String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(List.of(arguments));
        Path output = Files.createTempFile(tempDir, "java", ".out");
        Process process = new ProcessBuilder(command).directory(tempDir.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(2, TimeUnit.MINUTES), () -> "java did not end: " + read(output));
        } finally {
            process.destroyForcibly();
        }
        String written = read(output);
        assertEquals(0, process.exitValue(), written);
        return written;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
     * Makes the class path of an application of its own: the classes of core and of the tests, which a class loader
     * loads again, and a directory that registers the given loaders and no other one, in that order.
     *
     * @param loaders The loaders of the optimizations
     * @return The class path
     */
    private URL[] applicationWithLoaders(Class<?>... loaders) throws IOException {
        Path registered = Files.createDirectories(tempDir.resolve("loaders"));
        StringBuilder names = new StringBuilder();
        for (Class<?> loader : loaders) {
            names.append(loader.getName()).append('\n');
        }
        Files.writeString(Files.createDirectories(registered.resolve("META-INF/services")).resolve(StaticOptimizations.Loader.class.getName()), names);
        return new URL[]{
            SoftServiceLoader.class.getProtectionDomain().getCodeSource().getLocation(),
            org.slf4j.Logger.class.getProtectionDomain().getCodeSource().getLocation(),
            ServiceIndexTest.class.getProtectionDomain().getCodeSource().getLocation(),
            registered.toUri().toURL()
        };
    }

    /**
     * Makes the next lookup check its index again, by checking another index: only the last check is kept.
     */
    private static void forgetTheLastCheck() throws IOException {
        try (URLClassLoader classLoader = new URLClassLoader(new URL[0], null)) {
            ServiceIndex.copyOf(classLoader, Map.of(), Map.of(), null).forLookup(classLoader);
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
        return jar(tempDir.resolve(name), Map.of(), files, entries);
    }

    /**
     * Writes a JAR, in the place of the one that is there if there is one.
     *
     * @param jar      The JAR
     * @param manifest The main attributes of its manifest. Without any, the JAR has no manifest
     * @param files    The content of its files
     * @param entries  Its empty entries
     * @return The JAR
     */
    private static Path jar(Path jar, Map<String, String> manifest, Map<String, String> files, List<String> entries) throws IOException {
        Files.deleteIfExists(jar);
        Set<String> directories = new LinkedHashSet<>();
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            if (!manifest.isEmpty()) {
                Manifest content = new Manifest();
                content.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
                manifest.forEach(content.getMainAttributes()::putValue);
                putDirectories(zip, "META-INF/MANIFEST.MF", directories);
                zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
                content.write(zip);
                zip.closeEntry();
            }
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
    }

    public static final class Hi implements Greeter {
    }

    public static final class Hey implements Greeter {
    }

    /**
     * Registers an index for a class loader of its own, so that no other test sees it.
     */
    public static final class TestServiceIndexLoader implements StaticOptimizations.Loader<ServiceIndex> {

        static final ClassLoader CLASS_LOADER = new URLClassLoader(new URL[0], ServiceIndexTest.class.getClassLoader());

        static final ServiceIndex INDEX = ServiceIndex.ofTrusted(
            CLASS_LOADER,
            Map.of(Greeter.class.getName(), Set.of(Hey.class.getName())),
            Map.of(Greeter.class.getName(), List.of(Hello.class.getName(), Hi.class.getName())),
            null
        );

        @Override
        public ServiceIndex load() {
            return INDEX;
        }
    }

    /**
     * The application that the test of {@code java -jar} starts in a JVM of its own, from a thin JAR: it looks the
     * services of a type up and prints their names.
     */
    public static final class ThinJarApplication {

        public static void main(String[] args) {
            List<String> names = new ArrayList<>();
            SoftServiceLoader.newCollector(SERVICE, name -> true, ClassLoader.getSystemClassLoader(), Function.identity()).collect(names, false);
            Collections.sort(names);
            System.out.println("java.class.path=" + System.getProperty("java.class.path"));
            System.out.println("names=" + names);
        }
    }

    /**
     * The loader of the thin JAR of that test, which no other test sees: it registers, for the class loader of the
     * application, the index that the test described in a file of the JAR.
     */
    public static final class ThinJarIndexLoader implements StaticOptimizations.Loader<ServiceIndex> {

        static final String RESOURCE = "service-index.txt";

        static String describe(ServiceIndex index, List<ClassPathEntry> classPath) {
            StringBuilder lines = new StringBuilder();
            index.micronautServices().forEach((type, names) -> names.forEach(name -> lines.append("micronaut\t").append(type).append('\t').append(name).append('\n')));
            index.standardServices().forEach((type, names) -> names.forEach(name -> lines.append("standard\t").append(type).append('\t').append(name).append('\n')));
            classPath.forEach(entry -> lines.append("classpath\t").append(entry.name()).append('\t').append(entry.size()).append('\n'));
            return lines.toString();
        }

        @Override
        public ServiceIndex load() {
            ClassLoader classLoader = ThinJarIndexLoader.class.getClassLoader();
            Map<String, Set<String>> micronautServices = new LinkedHashMap<>();
            Map<String, List<String>> standardServices = new LinkedHashMap<>();
            List<ClassPathEntry> classPath = new ArrayList<>();
            try (InputStream in = classLoader.getResourceAsStream(RESOURCE)) {
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                    String[] fields = line.split("\t");
                    switch (fields[0]) {
                        case "micronaut" -> micronautServices.computeIfAbsent(fields[1], type -> new LinkedHashSet<>()).add(fields[2]);
                        case "standard" -> standardServices.computeIfAbsent(fields[1], type -> new ArrayList<>()).add(fields[2]);
                        default -> classPath.add(new ClassPathEntry(fields[1], Long.parseLong(fields[2])));
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return ServiceIndex.ofTrusted(classLoader, micronautServices, standardServices, classPath);
        }
    }

    /**
     * A loader that looks up services whose constructor looks a service up.
     */
    public static final class NestedLookupLoader implements StaticOptimizations.Loader<NestedLookupLoader.Loaded> {

        public static volatile int found = -1;

        @Override
        public Loaded load() {
            found = SoftServiceLoader.load(NestedLookup.class, NestedLookupLoader.class.getClassLoader()).collectAll().size();
            return new Loaded();
        }

        public static final class Loaded {
        }
    }

    public interface NestedLookup {
    }

    /**
     * A service whose constructor looks services up, as a service that is itself made of services does.
     */
    public static final class NestedLookupService implements NestedLookup {

        public static final Set<String> POOL_THREADS = ConcurrentHashMap.newKeySet();
        private static final CountDownLatch LOOKED_UP_ON_A_POOL_THREAD = new CountDownLatch(1);

        public NestedLookupService() {
            Thread thread = Thread.currentThread();
            if (thread instanceof ForkJoinWorkerThread) {
                ClassLoader classLoader = NestedLookupService.class.getClassLoader();
                // the two kinds of lookup: each one asks for the index when it starts
                SoftServiceLoader.load(Runnable.class, classLoader).collectAll();
                MicronautMetaServiceLoaderUtils.findMetaMicronautServiceEntries(classLoader, Runnable.class, null);
                POOL_THREADS.add(thread.getName());
                LOOKED_UP_ON_A_POOL_THREAD.countDown();
            } else {
                // The thread that started the lookup, which loads a service itself when no thread of the pool has
                // taken it yet. It leaves the other services to the pool, by waiting until one of them was loaded
                // there, so that the test always has a lookup that starts on a thread of the pool
                try {
                    LOOKED_UP_ON_A_POOL_THREAD.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * A loader that has another thread read the registered index, and waits for that thread.
     */
    public static final class ReadingLoader implements StaticOptimizations.Loader<ReadingLoader.Loaded> {

        public static volatile boolean returned;
        public static volatile Object read;

        @Override
        public Loaded load() {
            Thread reader = new Thread(() -> read = StaticOptimizations.SetOnce.find(ServiceIndex.class.getName()), "reader of the service index");
            reader.setDaemon(true);
            reader.start();
            try {
                reader.join(TimeUnit.SECONDS.toMillis(10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            returned = !reader.isAlive();
            return new Loaded();
        }

        public static final class Loaded {
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
