/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.core.io.service;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.io.IOUtils;
import io.micronaut.core.util.ExceptionUtils;
import io.micronaut.core.io.service.ServiceScanner.ExclusiveStaticServiceDefinitions;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The loader of Micronaut services under META-INF/micronaut/.
 *
 * @author Denis Stepanov
 * @since 4.7
 */
@Internal
public final class MicronautMetaServiceLoaderUtils {

    private static final String MICRONAUT_SERVICES_PATH = "META-INF/micronaut/";

    private static final byte[] MICRONAUT_SERVICES_PREFIX = MICRONAUT_SERVICES_PATH.getBytes(StandardCharsets.US_ASCII);

    // The zip records the central directory scan reads (PKWARE APPNOTE.TXT 4.3.12, 4.3.15 and 4.3.16): their
    // signatures, fixed sizes, and the longest variable part (a name, a comment) a 16-bit length allows
    private static final int CEN_SIGNATURE = 0x02014b50;
    private static final int CEN_SIZE = 46;
    private static final int END_SIGNATURE = 0x06054b50;
    private static final int END_SIZE = 22;
    private static final int ZIP64_LOCATOR_SIGNATURE = 0x07064b50;
    private static final int ZIP64_LOCATOR_SIZE = 20;
    private static final int ZIP64_MAGIC_COUNT = 0xFFFF;
    private static final long ZIP64_MAGIC_VALUE = 0xFFFFFFFFL;
    private static final int MAX_VARIABLE_LENGTH = 0xFFFF;
    // Holds the end of any zip file without ZIP64 (a ZIP64 locator, the end record, the longest comment), and any
    // central directory header with its name
    private static final int SCAN_BUFFER_SIZE = Math.max(ZIP64_LOCATOR_SIZE + END_SIZE, CEN_SIZE) + MAX_VARIABLE_LENGTH;

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();
    private static final MethodType VOID_TYPE = MethodType.methodType(void.class);

    @Nullable
    private static volatile CacheEntry cacheEntry;

    /**
     * Find all instantiated Micronaut service entries.
     *
     * @param classLoader  The classloader
     * @param serviceClass The service class
     * @param predicate    The predicate
     * @param <S>          The service type
     * @return the result
     */
    public static <S> List<S> findMetaMicronautServiceEntries(ClassLoader classLoader,
                                                              Class<S> serviceClass,
                                                              @Nullable Predicate<S> predicate) {
        SoftServiceLoader.StaticServiceLoader<S> staticServiceLoader = (SoftServiceLoader.StaticServiceLoader<S>) SoftServiceLoader.STATIC_SERVICES.get(serviceClass.getName());
        if (staticServiceLoader != null) {
            return staticServiceLoader.load(predicate);
        }
        return new MicronautServiceCollector<>(classLoader, serviceClass.getName(), predicate)
            .collect(true);
    }

    /**
     * Find Micronaut service entries.
     *
     * @param classLoader The classloader
     * @param serviceName The service name
     * @return The entries
     * @throws IOException The exception
     */
    public static Set<String> findMicronautMetaServiceEntries(ClassLoader classLoader, String serviceName) throws IOException {
        ExclusiveStaticServiceDefinitions staticDefinitions = ServiceScanner.findStaticServiceDefinitions();
        if (staticDefinitions != null) {
            Set<String> serviceEntries = staticDefinitions.serviceTypeMap().get(serviceName);
            if (serviceEntries != null) {
                return serviceEntries;
            }
        }
        CacheEntry ce = cacheEntry;
        if (ce == null || ce.classLoader != classLoader) {
            ce = new CacheEntry(classLoader, findAllMicronautMetaServices(classLoader));
            cacheEntry = ce;
        }
        return ce.services.getOrDefault(serviceName, Set.of());
    }

    /**
     * Find all Micronaut services.
     *
     * @param classLoader The classloader
     * @return the all entries
     * @throws IOException
     */
    public static Map<String, Set<String>> findAllMicronautMetaServices(ClassLoader classLoader) throws IOException {
        List<URI> resourceDefs = IOUtils.getResources(classLoader, MICRONAUT_SERVICES_PATH);
        if (resourceDefs.isEmpty()) {
            return Map.of();
        }

        Map<String, Set<String>> services = new LinkedHashMap<>();

        FileVisitor<Path> visitor = new FileVisitor<>() {

            @Nullable
            private Set<String> definitions;

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.endsWith(MICRONAUT_SERVICES_PATH)) {
                    return FileVisitResult.CONTINUE;
                }
                String serviceName = dir.getFileName().toString();
                definitions = services.get(serviceName);
                if (definitions == null) {
                    definitions = new LinkedHashSet<>();
                    services.put(serviceName, definitions);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path currentPath, BasicFileAttributes attrs) throws IOException {
                if (Files.isHidden(currentPath)) {
                    return FileVisitResult.CONTINUE;
                }
                Path fileName = currentPath.getFileName();
                if (fileName.startsWith(".")) {
                    return FileVisitResult.CONTINUE;
                }
                if (definitions != null) {
                    definitions.add(fileName.toString());
                }
                return FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        };

        List<Closeable> toClose = new ArrayList<>();
        byte[] scanBuffer = null;
        try {
            for (URI uri : resourceDefs) {
                File jar = jarFile(uri);
                if (jar != null) {
                    if (scanBuffer == null) {
                        scanBuffer = new byte[SCAN_BUFFER_SIZE];
                    }
                    if (collectJarServices(jar, scanBuffer, services)) {
                        continue;
                    }
                }
                Path myPath = IOUtils.resolvePath(uri, MICRONAUT_SERVICES_PATH, toClose);
                if (myPath != null) {
                    Files.walkFileTree(myPath, Collections.emptySet(), 2, visitor);
                }
            }
        } catch (IOException e) {
            // ignore, can't do anything here and can't log because class used in compiler
        } finally {
            for (Closeable closeable : toClose) {
                try {
                    closeable.close();
                } catch (IOException ignored) {
                }
            }
        }
        return services;
    }

    /**
     * Returns the jar file a {@code jar:file:} URI of a {@code META-INF/micronaut/} resource names.
     *
     * @param uri The URI of the {@code META-INF/micronaut/} resource
     * @return The jar file, or {@code null} if the URI does not name a directory in a jar file of the file system
     */
    @Nullable
    private static File jarFile(URI uri) {
        if (!"jar".equals(uri.getScheme())) {
            return null;
        }
        String spec = uri.getRawSchemeSpecificPart();
        int sep = spec.indexOf("!/");
        // nested jars and the WebLogic form without a file: URL are left to the zip file system
        if (sep == -1 || spec.indexOf("!/", sep + 2) != -1 || !spec.startsWith("file:")) {
            return null;
        }
        try {
            return new File(URI.create(spec.substring(0, sep)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Collects the services of a jar file from the names of its entries. Opening the jar as a zip file system reads
     * and indexes its whole central directory again, which for an application jar costs several times more than
     * reading the names.
     *
     * <p>The names come from {@code scanCentralDirectory}, which creates a name only for
     * the entries below {@code META-INF/micronaut/}, or, for a jar that scan does not read, from listing the entries
     * of the jar as a {@link ZipFile}, which creates an entry and a name for each entry of the jar.</p>
     *
     * <p>The entries are added in the order walking the zip file system visits them, the reverse of the order the
     * jar stores them in, so the services are found in the same order as before.</p>
     *
     * @param jar        The jar file
     * @param scanBuffer The buffer of the central directory scan
     * @param services   The services to add to
     * @return True if the services of the jar were collected
     */
    private static boolean collectJarServices(File jar, byte[] scanBuffer, Map<String, Set<String>> services) {
        List<String> names = scanCentralDirectory(jar, MICRONAUT_SERVICES_PREFIX, scanBuffer);
        if (names == null) {
            names = listZipEntries(jar);
            if (names == null) {
                return false;
            }
        }
        for (int i = names.size() - 1; i >= 0; i--) {
            addJarEntry(names.get(i), services);
        }
        return true;
    }

    /**
     * Lists the {@code META-INF/micronaut/} entries of a jar file as a {@link ZipFile}.
     *
     * @param jar The jar file
     * @return The names of the entries in the order of the central directory, or {@code null} if the jar cannot be read
     */
    // S5042: only the names of the entries are read, nothing is expanded, so an archive cannot exhaust memory or disk here
    @SuppressWarnings("java:S5042")
    @Nullable
    private static List<String> listZipEntries(File jar) {
        List<String> names = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith(MICRONAUT_SERVICES_PATH)) {
                    names.add(name);
                }
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
        return names;
    }

    /**
     * Lists the names of the entries of a zip file that start with a prefix, in the order of its central directory,
     * which is the order {@link ZipFile#entries()} gives. The central directory is read through the buffer, each name
     * is compared as bytes, and only a name that starts with the prefix becomes a {@link String}, where listing the
     * zip file creates a {@link ZipEntry} and a name for every entry.
     *
     * <p>Like {@link ZipFile}, the scan finds the central directory from the end record, so a zip file may have data
     * before its first entry (a launcher script, for example). It only reads a zip file whose last end record
     * signature is followed by exactly the comment it declares, with no ZIP64 records, and whose central directory
     * holds exactly the entries the end record counts. For any other file (ZIP64, bytes after the end record, a damaged
     * central directory, a matching name that is not valid UTF-8) it returns {@code null}, and the caller lists the
     * file as a {@link ZipFile}.</p>
     *
     * @param file   The zip file
     * @param prefix The prefix of the names, as UTF-8 bytes
     * @param buffer A buffer of {@code SCAN_BUFFER_SIZE} bytes or more
     * @return The matching names, or {@code null} if this scan does not read the file
     */
    @Nullable
    static List<String> scanCentralDirectory(File file, byte[] prefix, byte[] buffer) {
        try (RandomAccessFile zip = new RandomAccessFile(file, "r")) {
            long size = zip.length();
            int tailLength = (int) Math.min(size, ZIP64_LOCATOR_SIZE + END_SIZE + MAX_VARIABLE_LENGTH);
            if (tailLength < END_SIZE) {
                return null;
            }
            long tailStart = size - tailLength;
            read(zip, buffer, tailStart, tailLength);
            int end = tailLength - END_SIZE;
            while (end >= 0 && int32(buffer, end) != END_SIGNATURE) {
                end--;
            }
            // ZipFile also accepts bytes after the end record, and skips a signature that its comment contains:
            // whenever the last signature does not end the file with its comment, leave the file to ZipFile
            if (end < 0 || end + END_SIZE + uint16(buffer, end + 20) != tailLength) {
                return null;
            }
            // with a full tail and a comment no longer than 65535 bytes, a ZIP64 locator before the record is in the tail
            if (end >= ZIP64_LOCATOR_SIZE && int32(buffer, end - ZIP64_LOCATOR_SIZE) == ZIP64_LOCATOR_SIGNATURE) {
                return null;
            }
            int total = uint16(buffer, end + 10);
            long cenSize = Integer.toUnsignedLong(int32(buffer, end + 12));
            long cenOffset = Integer.toUnsignedLong(int32(buffer, end + 16));
            long cenEnd = tailStart + end;
            long cenStart = cenEnd - cenSize;
            if (total == ZIP64_MAGIC_COUNT || cenSize == ZIP64_MAGIC_VALUE || cenOffset == ZIP64_MAGIC_VALUE
                || cenStart < 0 || cenStart < cenOffset) {
                return null;
            }
            List<String> names = new ArrayList<>();
            long windowStart = tailStart;
            long windowEnd = size;
            long position = cenStart;
            int count = 0;
            while (position < cenEnd) {
                if (cenEnd - position < CEN_SIZE) {
                    return null;
                }
                if (position < windowStart || position + CEN_SIZE > windowEnd) {
                    windowStart = position;
                    windowEnd = position + read(zip, buffer, position, (int) Math.min(buffer.length, cenEnd - position));
                }
                int offset = (int) (position - windowStart);
                if (int32(buffer, offset) != CEN_SIGNATURE) {
                    return null;
                }
                int nameLength = uint16(buffer, offset + 28);
                long nameEnd = position + CEN_SIZE + nameLength;
                if (nameEnd > cenEnd) {
                    return null;
                }
                if (nameEnd > windowEnd) {
                    // the buffer holds any header with its name, so the name is in the window read from here
                    windowStart = position;
                    windowEnd = position + read(zip, buffer, position, (int) Math.min(buffer.length, cenEnd - position));
                    offset = 0;
                }
                int nameStart = offset + CEN_SIZE;
                if (startsWith(buffer, nameStart, nameLength, prefix)) {
                    String name = decodeName(buffer, nameStart, nameLength);
                    if (name == null) {
                        return null;
                    }
                    names.add(name);
                }
                position = nameEnd + uint16(buffer, offset + 30) + uint16(buffer, offset + 32);
                count++;
            }
            return position == cenEnd && count == total ? names : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Reads bytes of a file into the start of a buffer.
     *
     * @param file     The file
     * @param buffer   The buffer
     * @param position The position in the file
     * @param length   The number of bytes, at most the length of the buffer
     * @return The number of bytes read
     * @throws IOException If the file cannot be read or ends first
     */
    private static int read(RandomAccessFile file, byte[] buffer, long position, int length) throws IOException {
        file.seek(position);
        file.readFully(buffer, 0, length);
        return length;
    }

    private static int uint16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | (bytes[offset + 1] & 0xFF) << 8;
    }

    private static int int32(byte[] bytes, int offset) {
        return uint16(bytes, offset) | uint16(bytes, offset + 2) << 16;
    }

    private static boolean startsWith(byte[] bytes, int offset, int length, byte[] prefix) {
        if (length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Decodes an entry name as {@link ZipFile} does for a zip file opened with UTF-8, the default.
     *
     * @param bytes  The bytes
     * @param offset The offset of the name
     * @param length The length of the name
     * @return The name, or {@code null} if it is not valid UTF-8, which {@link ZipFile} rejects
     */
    @Nullable
    private static String decodeName(byte[] bytes, int offset, int length) {
        for (int i = offset; i < offset + length; i++) {
            if (bytes[i] < 0) {
                try {
                    return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes, offset, length)).toString();
                } catch (CharacterCodingException e) {
                    return null;
                }
            }
        }
        return new String(bytes, offset, length, StandardCharsets.ISO_8859_1);
    }

    /**
     * Adds a {@code META-INF/micronaut/<service>/<entry>} jar entry. As walking two levels of the file system does,
     * a directory below the service counts as an entry, and a file directly in {@code META-INF/micronaut/} is not one.
     *
     * @param name     The name of the jar entry
     * @param services The services to add to
     */
    // S3824: computeIfAbsent would add a lambda to link on the startup path this method exists to shorten
    @SuppressWarnings("java:S3824")
    private static void addJarEntry(String name, Map<String, Set<String>> services) {
        int start = MICRONAUT_SERVICES_PATH.length();
        int serviceEnd = name.indexOf('/', start);
        if (serviceEnd <= start) {
            return;
        }
        String serviceName = name.substring(start, serviceEnd);
        Set<String> definitions = services.get(serviceName);
        if (definitions == null) {
            definitions = new LinkedHashSet<>();
            services.put(serviceName, definitions);
        }
        int entryEnd = name.indexOf('/', serviceEnd + 1);
        String entry = entryEnd == -1 ? name.substring(serviceEnd + 1) : name.substring(serviceEnd + 1, entryEnd);
        if (!entry.isEmpty()) {
            definitions.add(entry);
        }
    }

    @Nullable
    private static <S> S instantiate(String className, ClassLoader classLoader) {
        try {
            @SuppressWarnings("unchecked") final Class<S> loadedClass =
                (Class<S>) Class.forName(className, false, classLoader);
            // MethodHandler should more performant than the basic reflection
            return (S) LOOKUP.findConstructor(loadedClass, VOID_TYPE).invoke();
        } catch (NoClassDefFoundError | ClassNotFoundException | NoSuchMethodException |
                 IllegalAccessException | IllegalAccessError e) {
            // Ignore
            return null;
        } catch (Throwable e) {
            return ExceptionUtils.sneakyThrow(e);
        }
    }

    /**
     * Fork-join recursive services loader.
     *
     * @param <S> The service type
     */
    @SuppressWarnings("java:S1948")
    private static final class MicronautServiceCollector<S> extends RecursiveActionValuesCollector<S> {

        private final ClassLoader classLoader;
        private final String serviceName;
        @Nullable
        private final Predicate<S> predicate;
        private final List<RecursiveActionValuesCollector<S>> tasks = new ArrayList<>();
        private int size;

        MicronautServiceCollector(ClassLoader classLoader, String serviceName, @Nullable Predicate<S> predicate) {
            this.classLoader = classLoader;
            this.serviceName = serviceName;
            this.predicate = predicate;
        }

        @Override
        protected void compute() {
            try {
                Set<String> serviceEntries = MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(classLoader, serviceName);
                size = serviceEntries.size();
                for (String serviceEntry : serviceEntries) {
                    final ServiceInstanceLoader<S> task = new ServiceInstanceLoader<>(classLoader, serviceEntry, predicate);
                    tasks.add(task);
                    task.fork();
                }
            } catch (IOException e) {
                throw new ServiceConfigurationError("Failed to load resources for service: " + serviceName, e);
            }
        }

        public List<S> collect(boolean allowFork) {
            if (allowFork && ForkJoinPool.getCommonPoolParallelism() > 1) {
                ForkJoinPool.commonPool().invoke(this);
                List<S> collection = null;
                for (RecursiveActionValuesCollector<S> task : tasks) {
                    task.join();
                    if (collection == null) {
                        collection = new ArrayList<>(size);
                    }
                    task.collect(collection);
                }
                if (collection == null) {
                    return List.of();
                }
                return collection;
            }
            try {
                Set<String> serviceEntries = MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(classLoader, serviceName);
                List<S> collection = new ArrayList<>(serviceEntries.size());
                for (String serviceEntry : serviceEntries) {
                    S val = instantiate(serviceEntry, classLoader);
                    if (val != null && (predicate == null || predicate.test(val))) {
                        collection.add(val);
                    }
                }
                return collection;
            } catch (IOException e) {
                throw new ServiceConfigurationError("Failed to load resources for service: " + serviceName, e);
            }
        }

        @Override
        public void collect(Collection<S> values) {
            throw new IllegalStateException("Only constructor method is supported!");
        }
    }

    /**
     * Initializes and filters the entry.
     *
     * @param <S> The service type
     */
    private static final class ServiceInstanceLoader<S> extends RecursiveActionValuesCollector<S> {

        private final ClassLoader classLoader;
        private final String className;
        @Nullable
        private final Predicate<S> predicate;
        @Nullable
        private S result;
        @Nullable
        private Throwable throwable;

        public ServiceInstanceLoader(ClassLoader classLoader, String className, @Nullable Predicate<S> predicate) {
            this.classLoader = classLoader;
            this.className = className;
            this.predicate = predicate;
        }

        @Override
        protected void compute() {
            try {
                result = instantiate(className, classLoader);
                if (result != null && predicate != null && !predicate.test(result)) {
                    result = null;
                }
            } catch (Throwable e) {
                throwable = e;
            }
        }

        @Override
        public void collect(Collection<S> values) {
            if (throwable != null) {
                throw new SoftServiceLoader.ServiceLoadingException("Failed to load a service: " + throwable.getMessage(), throwable);
            }
            if (result != null) {
                values.add(result);
            }
        }
    }

    /**
     * Abstract recursive action class.
     *
     * @param <S> The type
     */
    private abstract static class RecursiveActionValuesCollector<S> extends RecursiveAction {

        /**
         * Collects loaded values.
         *
         * @param values The values
         */
        public abstract void collect(Collection<S> values);

    }

    private record CacheEntry(ClassLoader classLoader, Map<String, Set<String>> services) {
    }

}
