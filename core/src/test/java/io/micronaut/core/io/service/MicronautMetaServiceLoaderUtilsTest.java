package io.micronaut.core.io.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MicronautMetaServiceLoaderUtilsTest {

    private static final String BEANS = "META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference/";
    private static final String INTROSPECTIONS = "META-INF/micronaut/io.micronaut.core.beans.BeanIntrospectionReference/";
    // an entry names a class, and what it contains is not read to find the services
    private static final byte[] CONTENT = {'M', 'N', 'B', 'D', 0, 1, 0, 0, 0, 2, 0, 0};

    @TempDir
    Path tempDir;

    @Test
    void latestCachedLoaderCanBeCollectedWhileServiceNamesRemainReachable() throws Exception {
        Path jar = jar("disposable.jar", List.of("META-INF/micronaut/", BEANS, BEANS + "test.Bean"));
        CachedServices cached = cacheDisposableLoader(jar);
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!cached.loader().refersTo(null) && System.nanoTime() < deadline) {
                System.gc();
                Thread.sleep(50);
            }
            assertNull(cached.loader().get(), "The latest service-index lookup must not retain its loader");
            assertEquals(Set.of("test.Bean"), cached.services());
        } finally {
            Reference.reachabilityFence(cached.services());
        }
    }

    @Test
    void repeatedLookupsReuseTheIndexAndDifferentLoadersDiscoverTheirOwnServices() throws IOException {
        Path first = jar("first.jar", List.of("META-INF/micronaut/", BEANS, BEANS + "test.First"));
        Path second = jar("second.jar", List.of("META-INF/micronaut/", BEANS, BEANS + "test.Second"));
        AtomicInteger scans = new AtomicInteger();
        try (URLClassLoader firstLoader = new URLClassLoader(new URL[]{first.toUri().toURL()}, null) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                if (name.equals("META-INF/micronaut/")) {
                    scans.incrementAndGet();
                }
                return super.getResources(name);
            }
        }; URLClassLoader secondLoader = new URLClassLoader(new URL[]{second.toUri().toURL()}, null)) {
            Set<String> entries = MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(firstLoader, "io.micronaut.inject.BeanDefinitionReference");
            assertEquals(Set.of("test.First"), entries);
            assertSame(entries, MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(firstLoader, "io.micronaut.inject.BeanDefinitionReference"));
            assertEquals(Set.of(), MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(firstLoader, "missing.Service"));
            assertEquals(1, scans.get());
            assertEquals(Set.of("test.Second"), MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(secondLoader, "io.micronaut.inject.BeanDefinitionReference"));
            assertEquals(Set.of("test.First"), MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(firstLoader, "io.micronaut.inject.BeanDefinitionReference"));
        }
    }

    private static CachedServices cacheDisposableLoader(Path jar) throws IOException {
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
            Set<String> services = MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(loader, "io.micronaut.inject.BeanDefinitionReference");
            assertEquals(Set.of("test.Bean"), services);
            return new CachedServices(new WeakReference<>(loader), services);
        }
    }

    @Test
    void concurrentLookupsKeepTheServicesOfEachLoaderSeparate() throws Exception {
        Path first = jar("concurrent first.jar", List.of("META-INF/micronaut/", BEANS, BEANS + "test.First"));
        Path second = jar("concurrent second.jar", List.of("META-INF/micronaut/", BEANS, BEANS + "test.Second"));
        try (URLClassLoader firstLoader = new URLClassLoader(new URL[]{first.toUri().toURL()}, null);
             URLClassLoader secondLoader = new URLClassLoader(new URL[]{second.toUri().toURL()}, null);
             var executor = Executors.newFixedThreadPool(2)) {
            var firstLookup = executor.submit(() -> {
                for (int i = 0; i < 50; i++) {
                    assertEquals(Set.of("test.First"), MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(firstLoader, "io.micronaut.inject.BeanDefinitionReference"));
                }
                return null;
            });
            var secondLookup = executor.submit(() -> {
                for (int i = 0; i < 50; i++) {
                    assertEquals(Set.of("test.Second"), MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(secondLoader, "io.micronaut.inject.BeanDefinitionReference"));
                }
                return null;
            });
            firstLookup.get(10, TimeUnit.SECONDS);
            secondLookup.get(10, TimeUnit.SECONDS);
        }
    }

    private record CachedServices(WeakReference<ClassLoader> loader, Set<String> services) {
    }

    @Test
    void listsTheServicesOfAJarInTheOrderOfItsZipFileSystem() throws IOException {
        assertSameAsZipFileSystem(jar("with directories.jar", List.of(
            "META-INF/",
            "META-INF/micronaut/",
            BEANS,
            BEANS + "a.$A$Definition",
            BEANS + "b.$B$Definition",
            BEANS + "c.$C$Definition",
            INTROSPECTIONS,
            INTROSPECTIONS + "a.$A$Introspection",
            INTROSPECTIONS + "b.$B$Introspection",
            "META-INF/micronaut/io.micronaut.inject.BeanConfiguration/",
            "META-INF/micronaut/io.micronaut.inject.BeanConfiguration/a.$BeanConfiguration",
            "a/A.class"
        )));
    }

    @Test
    void listsTheServicesOfAJarWithoutServiceDirectoryEntries() throws IOException {
        assertSameAsZipFileSystem(jar("files only.jar", List.of(
            "META-INF/micronaut/",
            BEANS + "z.$Z$Definition",
            BEANS + "y.$Y$Definition",
            INTROSPECTIONS + "x.$X$Introspection",
            BEANS + "x.$X$Definition",
            "META-INF/micronaut/empty/",
            BEANS + "nested/deeper/entry",
            "META-INF/micronaut/stray-file"
        )));
    }

    @Test
    void listsTheSameServicesOfAJarWhenItsEntriesCarryContent() throws IOException {
        List<String> entries = List.of(
            "META-INF/",
            "META-INF/micronaut/",
            BEANS,
            BEANS + "a.$A$Definition",
            BEANS + "b.$B$Definition",
            INTROSPECTIONS,
            INTROSPECTIONS + "a.$A$Introspection",
            "a/A.class"
        );

        Map<String, Set<String>> empty = findAll(jar("empty.jar", entries, new byte[0]));
        Map<String, Set<String>> withContent = findAll(jar("with content.jar", entries, CONTENT));

        assertEquals(Set.of("a.$A$Definition", "b.$B$Definition"), empty.get("io.micronaut.inject.BeanDefinitionReference"));
        assertEquals(asLists(empty), asLists(withContent));
    }

    @Test
    void listsTheSameServicesOfADirectoryWhenItsEntriesCarryContent() throws IOException {
        List<String> entries = List.of(
            BEANS + "a.$A$Definition",
            BEANS + "b.$B$Definition",
            INTROSPECTIONS + "a.$A$Introspection",
            "a/A.class"
        );

        Map<String, Set<String>> empty = findAll(directory("empty", entries, new byte[0]));
        Map<String, Set<String>> withContent = findAll(directory("with content", entries, CONTENT));

        assertEquals(Set.of("a.$A$Definition", "b.$B$Definition"), empty.get("io.micronaut.inject.BeanDefinitionReference"));
        assertEquals(asSets(empty), asSets(withContent));
    }

    private static Map<String, Set<String>> findAll(Path classPathEntry) throws IOException {
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classPathEntry.toUri().toURL()}, null)) {
            return MicronautMetaServiceLoaderUtils.findAllMicronautMetaServices(classLoader);
        }
    }

    // the order a directory is listed in is the one of the file system
    private static Map<String, Set<String>> asSets(Map<String, Set<String>> services) {
        Map<String, Set<String>> sets = new TreeMap<>();
        services.forEach((service, entries) -> sets.put(service, new TreeSet<>(entries)));
        return sets;
    }

    private void assertSameAsZipFileSystem(Path jar) throws IOException {
        Map<String, Set<String>> expected = walkZipFileSystem(jar);
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
            Map<String, Set<String>> actual = MicronautMetaServiceLoaderUtils.findAllMicronautMetaServices(classLoader);
            assertEquals(asLists(expected), asLists(actual));
        }
    }

    private static Map<String, List<String>> asLists(Map<String, Set<String>> services) {
        Map<String, List<String>> lists = new LinkedHashMap<>();
        services.forEach((service, entries) -> lists.put(service, new ArrayList<>(entries)));
        return lists;
    }

    private static Map<String, Set<String>> walkZipFileSystem(Path jar) throws IOException {
        Map<String, Set<String>> services = new LinkedHashMap<>();
        try (FileSystem fs = FileSystems.newFileSystem(jar)) {
            Path root = fs.getPath("META-INF/micronaut/");
            Files.walkFileTree(root, Collections.emptySet(), 2, new SimpleFileVisitor<>() {
                private Set<String> definitions;

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.endsWith("META-INF/micronaut/")) {
                        definitions = services.computeIfAbsent(dir.getFileName().toString(), k -> new LinkedHashSet<>());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getParent() != null && !file.getParent().endsWith("META-INF/micronaut/")) {
                        definitions.add(file.getFileName().toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return services;
    }

    private Path jar(String name, List<String> entries) throws IOException {
        return jar(name, entries, new byte[0]);
    }

    private Path jar(String name, List<String> entries, byte[] content) throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve("a dir"));
        Path jar = dir.resolve(name);
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String entry : entries) {
                zip.putNextEntry(new ZipEntry(entry));
                if (!entry.endsWith("/")) {
                    zip.write(content);
                }
                zip.closeEntry();
            }
        }
        return jar;
    }

    private Path directory(String name, List<String> entries, byte[] content) throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve(name));
        for (String entry : entries) {
            Path file = dir.resolve(entry);
            Files.createDirectories(file.getParent());
            Files.write(file, content);
        }
        return dir;
    }
}
