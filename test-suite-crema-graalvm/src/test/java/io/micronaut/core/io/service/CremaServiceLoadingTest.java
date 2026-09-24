package io.micronaut.core.io.service;

import example.crema.RuntimeProbeResults;
import example.crema.ScanService;
import example.crema.TableService;
import example.crema.TableServiceA;
import example.crema.TableServiceB;
import io.micronaut.core.util.NativeImageUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Service loading in a native image built with runtime class loading ({@code -H:+RuntimeClassLoading}, Crema).
 *
 * <p>{@code nativeTest} runs these tests in such an image, where {@link ServiceScanner#findStaticServiceDefinitions()}
 * must still return the table that {@code ServiceLoaderFeature} stored in the image singletons, including for
 * callers that pass a class loader created at run time and for classes that are loaded at run time. The classes of
 * the {@code cremaRuntime} source set are not part of the image: the tests load them from
 * {@code micronaut.test.crema.runtime.path} with a new class loader. {@code test} runs the same tests on the JVM,
 * where there is no table.</p>
 */
class CremaServiceLoadingTest {

    private static final String IMAGE_SINGLETONS_ENABLED = "micronaut.graalvm.imagesingletons.enabled";
    private static final String RUNTIME_PATH = "micronaut.test.crema.runtime.path";
    private static final String TABLE_SERVICE_A = TableServiceA.class.getName();
    private static final String TABLE_SERVICE_B = TableServiceB.class.getName();
    private static final String RUNTIME_TABLE_SERVICE = "example.crema.runtime.RuntimeTableService";
    private static final String RUNTIME_SCAN_SERVICE = "example.crema.runtime.RuntimeScanService";
    private static final Predicate<String> NOT_ENDING_WITH_B = name -> !name.endsWith("B");

    private final boolean inImage = NativeImageUtils.inImageRuntimeCode();

    @AfterEach
    void clearImageSingletonsFlag() {
        System.clearProperty(IMAGE_SINGLETONS_ENABLED);
    }

    @Test
    void imageCodeFindsTheStaticServiceTable() {
        assertEquals(inImage, NativeImageUtils.inImageCode());
        ServiceScanner.ExclusiveStaticServiceDefinitions table = ServiceScanner.findStaticServiceDefinitions();
        if (inImage) {
            assertNotNull(table, "the image singletons table is not found in a Crema image");
            assertEquals(Set.of(TABLE_SERVICE_A, TABLE_SERVICE_B), table.serviceTypeMap().get(TableService.class.getName()));
        } else {
            assertNull(table);
        }
        assertEquals(Set.of(TABLE_SERVICE_A, TABLE_SERVICE_B), classNames(SoftServiceLoader.load(TableService.class).collectAll()));
        assertEquals(Set.of(TABLE_SERVICE_A), classNames(SoftServiceLoader.load(TableService.class, CremaServiceLoadingTest.class.getClassLoader(), NOT_ENDING_WITH_B).collectAll()));
    }

    @Test
    void classLoaderCreatedAtRunTimeGetsTheStaticServiceTable() throws IOException {
        try (URLClassLoader runtimeClassLoader = newRuntimeClassLoader()) {
            // the table is exclusive: in the image, the entry of the run time class path is not scanned
            Set<String> expected = inImage
                ? Set.of(TABLE_SERVICE_A, TABLE_SERVICE_B)
                : Set.of(TABLE_SERVICE_A, TABLE_SERVICE_B, RUNTIME_TABLE_SERVICE);
            assertEquals(expected, presentNames(SoftServiceLoader.load(TableService.class, runtimeClassLoader)));
            assertEquals(inImage ? Set.of(TABLE_SERVICE_A) : Set.of(TABLE_SERVICE_A, RUNTIME_TABLE_SERVICE),
                presentNames(SoftServiceLoader.load(TableService.class, runtimeClassLoader, NOT_ENDING_WITH_B)));
            assertEquals(expected, classNames(SoftServiceLoader.load(TableService.class, runtimeClassLoader).collectAll()));
        }
    }

    @Test
    void serviceMissingFromTheTableIsScannedAndItsClassIsDefinedAtRunTime() throws Exception {
        try (URLClassLoader runtimeClassLoader = newRuntimeClassLoader()) {
            assertEquals(Set.of(RUNTIME_SCAN_SERVICE), presentNames(SoftServiceLoader.load(ScanService.class, runtimeClassLoader)));
            assertEquals(Set.of(), presentNames(SoftServiceLoader.load(ScanService.class, runtimeClassLoader, name -> !name.equals(RUNTIME_SCAN_SERVICE))));
            assertSame(runtimeClassLoader, Class.forName(RUNTIME_SCAN_SERVICE, false, runtimeClassLoader).getClassLoader());
        }
    }

    @Test
    void imageSingletonsFlagMakesTheRunTimeClassPathScanned() throws IOException {
        System.setProperty(IMAGE_SINGLETONS_ENABLED, "false");
        assertNull(ServiceScanner.findStaticServiceDefinitions());
        try (URLClassLoader runtimeClassLoader = newRuntimeClassLoader()) {
            // only the run time class path is checked: in the image, the META-INF/micronaut entries of the image
            // itself are not listed by the scan
            Set<String> names = presentNames(SoftServiceLoader.load(TableService.class, runtimeClassLoader));
            assertTrue(names.contains(RUNTIME_TABLE_SERVICE), () -> "the entry of the run time class path is not found: " + names);
            Set<String> conditioned = presentNames(SoftServiceLoader.load(TableService.class, runtimeClassLoader, name -> !name.equals(RUNTIME_TABLE_SERVICE)));
            assertFalse(conditioned.contains(RUNTIME_TABLE_SERVICE), () -> "the condition is not applied: " + conditioned);
        }
    }

    @Test
    void classLoadedAtRunTimeRunsInImageCodeAndGetsTheStaticServiceTable() throws Exception {
        RuntimeProbeResults.clear();
        try (URLClassLoader runtimeClassLoader = newRuntimeClassLoader()) {
            Class<?> probe = Class.forName("example.crema.runtime.RuntimeProbe", true, runtimeClassLoader);
            assertSame(runtimeClassLoader, probe.getClassLoader());
            // Crema in Oracle GraalVM 25.0.3 defines classes at run time but does not initialize them. The rest of
            // this test runs where Crema also interprets the code of these classes.
            assumeTrue(RuntimeProbeResults.get(RuntimeProbeResults.INITIALIZED) != null,
                "this GraalVM defines classes at run time but does not run their static initializer");
            assertEquals(inImage ? NativeImageUtils.PROPERTY_IMAGE_CODE_VALUE_RUNTIME : "null", RuntimeProbeResults.get(RuntimeProbeResults.IMAGE_CODE_PROPERTY));
            assertEquals(inImage, RuntimeProbeResults.get(RuntimeProbeResults.IN_IMAGE_CODE));
            assertEquals(inImage ? List.of(TABLE_SERVICE_A, TABLE_SERVICE_B) : List.of(TABLE_SERVICE_A, TABLE_SERVICE_B, RUNTIME_TABLE_SERVICE),
                sorted(RuntimeProbeResults.get(RuntimeProbeResults.TABLE_SERVICES)));
            assertEquals(inImage ? List.of(TABLE_SERVICE_A) : List.of(TABLE_SERVICE_A, RUNTIME_TABLE_SERVICE),
                sorted(RuntimeProbeResults.get(RuntimeProbeResults.CONDITIONED_TABLE_SERVICES)));
            assertEquals(List.of(RUNTIME_SCAN_SERVICE), sorted(RuntimeProbeResults.get(RuntimeProbeResults.SCANNED_SERVICES)));
        } finally {
            RuntimeProbeResults.clear();
        }
    }

    private static URLClassLoader newRuntimeClassLoader() throws MalformedURLException {
        String path = System.getProperty(RUNTIME_PATH);
        assertNotNull(path, RUNTIME_PATH + " is not set");
        List<URL> urls = new ArrayList<>();
        for (String entry : path.split(Pattern.quote(File.pathSeparator))) {
            urls.add(new File(entry).toURI().toURL());
        }
        return new URLClassLoader(urls.toArray(URL[]::new), CremaServiceLoadingTest.class.getClassLoader());
    }

    private static <S> Set<String> presentNames(SoftServiceLoader<S> loader) {
        Set<String> names = new TreeSet<>();
        Iterator<ServiceDefinition<S>> definitions = loader.iterator();
        while (definitions.hasNext()) {
            ServiceDefinition<S> definition = definitions.next();
            if (definition.isPresent()) {
                names.add(definition.getName());
            }
        }
        return names;
    }

    private static Set<String> classNames(List<?> services) {
        Set<String> names = new TreeSet<>();
        for (Object service : services) {
            names.add(service.getClass().getName());
        }
        return names;
    }

    private static List<String> sorted(Object names) {
        assertNotNull(names);
        List<String> sorted = new ArrayList<>();
        for (Object name : (List<?>) names) {
            sorted.add((String) name);
        }
        sorted.sort(null);
        return sorted;
    }
}
