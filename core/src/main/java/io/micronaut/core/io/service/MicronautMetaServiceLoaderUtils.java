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
import java.lang.ref.WeakReference;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
import java.util.stream.Stream;
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
    // What ZipFile.Source.initCEN and checkAndAddEntry (JDK 25, 27) accept: a central directory of at most
    // ArraysSupport.SOFT_MAX_ARRAY_LENGTH bytes, entries that are stored or deflated and not encrypted, and the ZIP64
    // extra block, which the scan leaves to ZipFile
    private static final long MAX_CEN_LENGTH = Integer.MAX_VALUE - 8L;
    private static final int STORED = 0;
    private static final int DEFLATED = 8;
    private static final int ENCRYPTED_FLAG = 1;
    private static final int ZIP64_EXTRA_TAG = 0x0001;
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
        return findMicronautMetaServiceEntries(classLoader, serviceName, ServiceScanner.findServiceIndex(classLoader));
    }

    /**
     * Find Micronaut service entries for a lookup that has already asked for the service index.
     *
     * <p>A lookup asks for the index once, on the thread that starts it, and hands the answer to its fork-join tasks.
     * A task does not ask again, so the whole lookup uses one answer: an index can be registered, or switched off,
     * while the lookup runs.</p>
     *
     * @param classLoader The classloader
     * @param serviceName The service name
     * @param index       The service index that applies to the class loader, or null to scan the class path
     * @return The entries
     * @throws IOException The exception
     */
    static Set<String> findMicronautMetaServiceEntries(ClassLoader classLoader, String serviceName, @Nullable ServiceIndex index) throws IOException {
        ExclusiveStaticServiceDefinitions staticDefinitions = ServiceScanner.findStaticServiceDefinitions();
        if (staticDefinitions != null) {
            Set<String> serviceEntries = staticDefinitions.serviceTypeMap().get(serviceName);
            if (serviceEntries != null) {
                return serviceEntries;
            }
        }
        if (index != null) {
            return index.micronautServices().getOrDefault(serviceName, Set.of());
        }
        CacheEntry ce = cacheEntry;
        if (ce == null || ce.classLoader.get() != classLoader) {
            ce = new CacheEntry(new WeakReference<>(classLoader), findAllMicronautMetaServices(classLoader));
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
        return findAllMicronautMetaServices(classLoader, false);
    }

    /**
     * Find all Micronaut services.
     *
     * @param classLoader The classloader
     * @param sorted      Whether to list the entries of the directories in the order of their names, instead of the
     *                    order of the file system, so that the result does not depend on the file system. A directory
     *                    that cannot be read then fails the lookup instead of being skipped
     * @return the all entries
     * @throws IOException If a directory cannot be read and the entries are sorted
     */
    static Map<String, Set<String>> findAllMicronautMetaServices(ClassLoader classLoader, boolean sorted) throws IOException {
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
                if (isDotEntry(fileName)) {
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
                    if (sorted) {
                        collectSortedServices(myPath, services);
                    } else {
                        Files.walkFileTree(myPath, Collections.emptySet(), 2, visitor);
                    }
                }
            }
        } catch (IOException e) {
            if (sorted) {
                throw e;
            }
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
     * Collects the services of a {@code META-INF/micronaut/} directory in the order of their names. As walking two levels
     * of the directory does, every directory below it is a service, and every entry of a service that is neither hidden
     * nor named with a leading dot is one of its entries.
     *
     * <p>A plain file directly in {@code META-INF/micronaut/} is ignored here. That differs from the two-level walk,
     * which adds the name of such a file to the service whose directory it visited last, or drops it if it has not
     * visited one yet. A file there belongs to no service, so this method does not reproduce that.</p>
     *
     * @param root     The {@code META-INF/micronaut/} directory
     * @param services The services to add to
     * @throws IOException If a directory cannot be read
     */
    private static void collectSortedServices(Path root, Map<String, Set<String>> services) throws IOException {
        for (Path serviceDir : sortedChildren(root)) {
            if (Files.isDirectory(serviceDir)) {
                Set<String> definitions = services.computeIfAbsent(serviceDir.getFileName().toString(), name -> new LinkedHashSet<>());
                for (Path entry : sortedChildren(serviceDir)) {
                    if (!Files.isHidden(entry) && !isDotEntry(entry.getFileName())) {
                        definitions.add(entry.getFileName().toString());
                    }
                }
            }
        }
    }

    /**
     * Whether the name of an entry starts with a dot. Not every file system marks such an entry as hidden, and
     * {@link Path#startsWith(String)} compares whole names, so the name is compared as a string.
     *
     * @param fileName The name of the entry
     * @return True if the name starts with a dot
     */
    private static boolean isDotEntry(Path fileName) {
        return fileName.toString().startsWith(".");
    }

    private static List<Path> sortedChildren(Path dir) throws IOException {
        try (Stream<Path> children = Files.list(dir)) {
            return children.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
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
     * signature is followed by exactly the comment it declares, with no ZIP64 records or values, whose central
     * directory holds exactly the entries the end record counts, and whose every header passes the checks that
     * {@link ZipFile} makes when it opens the file: an entry that is stored or deflated and not encrypted, a header of
     * at most 65535 bytes, extra field blocks that end within the field, and a name and a comment that are valid
     * UTF-8. For any other file (ZIP64, bytes after the end record, a damaged central directory, a header that
     * {@link ZipFile} rejects) it returns {@code null}, and the caller lists the file as a {@link ZipFile}. So the scan
     * reads no file that {@link ZipFile} rejects, whichever class loader returned its URL.</p>
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
            int tailLength = (int) Math.min(size, (long) ZIP64_LOCATOR_SIZE + END_SIZE + MAX_VARIABLE_LENGTH);
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
                || cenStart < 0 || cenStart < cenOffset || cenSize > MAX_CEN_LENGTH) {
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
                int headerSize = headerSize(buffer, offset);
                if (headerSize < 0 || position + headerSize > cenEnd) {
                    return null;
                }
                if (position + headerSize > windowEnd) {
                    // the buffer holds any header of at most 65535 bytes, so the header is in the window read from here
                    windowStart = position;
                    windowEnd = position + read(zip, buffer, position, (int) Math.min(buffer.length, cenEnd - position));
                    offset = 0;
                }
                if (!addName(buffer, offset, prefix, names)) {
                    return null;
                }
                position += headerSize;
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

    /**
     * Checks the fixed part of a central directory header as {@code ZipFile.Source.checkAndAddEntry} does.
     *
     * <p>This and {@code addName} hold the work done for each entry: the loop of {@code scanCentralDirectory} runs
     * once per jar, too few iterations for it to be compiled, while a method called for every entry is compiled after
     * a few hundred calls.</p>
     *
     * @param buffer The buffer
     * @param offset The offset of the header
     * @return The size of the header with its name, extra field and comment, or {@code -1} if {@link ZipFile} rejects
     * the header or it holds a ZIP64 value, which the scan leaves to {@link ZipFile}
     */
    private static int headerSize(byte[] buffer, int offset) {
        int method = uint16(buffer, offset + 10);
        int size = CEN_SIZE + uint16(buffer, offset + 28) + uint16(buffer, offset + 30) + uint16(buffer, offset + 32);
        if (int32(buffer, offset) != CEN_SIGNATURE
            || (uint16(buffer, offset + 8) & ENCRYPTED_FLAG) != 0
            || method != STORED && method != DEFLATED
            || size > MAX_VARIABLE_LENGTH
            || int32(buffer, offset + 20) == -1 || int32(buffer, offset + 24) == -1 || int32(buffer, offset + 42) == -1
            || uint16(buffer, offset + 34) == ZIP64_MAGIC_COUNT) {
            return -1;
        }
        return size;
    }

    /**
     * Checks the name, the extra field and the comment of a central directory header as
     * {@code ZipFile.Source.checkAndAddEntry} does, and adds the name if it starts with the prefix.
     *
     * @param buffer The buffer, which holds the whole header
     * @param offset The offset of the header
     * @param prefix The prefix
     * @param names  The names to add to
     * @return {@code false} if {@link ZipFile} rejects the header or its extra field holds a ZIP64 block
     */
    private static boolean addName(byte[] buffer, int offset, byte[] prefix, List<String> names) {
        int nameStart = offset + CEN_SIZE;
        int nameLength = uint16(buffer, offset + 28);
        int extraStart = nameStart + nameLength;
        int extraLength = uint16(buffer, offset + 30);
        if (!isUtf8(buffer, nameStart, nameLength) || !isValidExtra(buffer, extraStart, extraLength)
            || !isUtf8(buffer, extraStart + extraLength, uint16(buffer, offset + 32))) {
            return false;
        }
        if (startsWith(buffer, nameStart, nameLength, prefix)) {
            // ZipFile decodes every name as UTF-8; only the names kept become strings
            names.add(new String(buffer, nameStart, nameLength, StandardCharsets.UTF_8));
        }
        return true;
    }

    /**
     * Checks the extra field of a central directory header as {@code ZipFile.Source.checkExtraFields} does: the data
     * of each block ends within the field. A ZIP64 block is left to {@link ZipFile}.
     */
    private static boolean isValidExtra(byte[] bytes, int offset, int length) {
        int end = offset + length;
        while (offset + 4 <= end) {
            int tag = uint16(bytes, offset);
            offset += 4 + uint16(bytes, offset + 2);
            if (tag == ZIP64_EXTRA_TAG || offset > end) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks that bytes are well-formed UTF-8 (Unicode table 3-7), which is what decoding a name or comment with
     * {@link StandardCharsets#UTF_8}, as {@link ZipFile} does, accepts. Creates nothing.
     */
    private static boolean isUtf8(byte[] bytes, int offset, int length) {
        int end = offset + length;
        int i = offset;
        // names are almost always ASCII: OR the bytes eight at a time, and check the sequences only if a byte is not
        int bits = 0;
        while (end - i >= 8) {
            bits |= bytes[i] | bytes[i + 1] | bytes[i + 2] | bytes[i + 3]
                | bytes[i + 4] | bytes[i + 5] | bytes[i + 6] | bytes[i + 7];
            i += 8;
        }
        while (i < end) {
            bits |= bytes[i];
            i++;
        }
        if (bits >= 0) {
            return true;
        }
        i = offset;
        while (i < end) {
            int b = bytes[i];
            if (b >= 0) {
                i++;
                continue;
            }
            b &= 0xFF;
            int continuations;
            int min = 0x80;
            int max = 0xBF;
            if (b >= 0xC2 && b <= 0xDF) {
                continuations = 1;
            } else if (b >= 0xE0 && b <= 0xEF) {
                continuations = 2;
                if (b == 0xE0) {
                    min = 0xA0;
                } else if (b == 0xED) {
                    max = 0x9F;
                }
            } else if (b >= 0xF0 && b <= 0xF4) {
                continuations = 3;
                if (b == 0xF0) {
                    min = 0x90;
                } else if (b == 0xF4) {
                    max = 0x8F;
                }
            } else {
                return false;
            }
            if (end - i <= continuations) {
                return false;
            }
            int second = bytes[i + 1] & 0xFF;
            if (second < min || second > max) {
                return false;
            }
            for (int k = 2; k <= continuations; k++) {
                if ((bytes[i + k] & 0xC0) != 0x80) {
                    return false;
                }
            }
            i += continuations + 1;
        }
        return true;
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
    // package-private, unlike its superclass, for the test that checks when the service index is asked for
    @SuppressWarnings({"java:S1948", "ExposedPrivateType"})
    static final class MicronautServiceCollector<S> extends RecursiveActionValuesCollector<S> {

        private final ClassLoader classLoader;
        private final String serviceName;
        @Nullable
        private final Predicate<S> predicate;
        @Nullable
        private final ServiceIndex index;
        private final List<RecursiveActionValuesCollector<S>> tasks = new ArrayList<>();
        private int size;

        MicronautServiceCollector(ClassLoader classLoader, String serviceName, @Nullable Predicate<S> predicate) {
            this.classLoader = classLoader;
            this.serviceName = serviceName;
            this.predicate = predicate;
            // asked for here, on the thread that starts the lookup, and not in compute(), which a pool thread can run
            this.index = ServiceScanner.findServiceIndex(classLoader);
        }

        @Override
        protected void compute() {
            try {
                Set<String> serviceEntries = MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(classLoader, serviceName, index);
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
                Set<String> serviceEntries = MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(classLoader, serviceName, index);
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

    // Only service names are retained with the weak loader identity; no classes or instances that could
    // indirectly keep the loader alive belong in this cache.
    private record CacheEntry(WeakReference<ClassLoader> classLoader, Map<String, Set<String>> services) {
    }

}
