package io.micronaut.core.io.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MicronautMetaServiceLoaderUtilsTest {

    private static final String SERVICES = "META-INF/micronaut/";
    private static final String BEANS = "META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference/";
    private static final String INTROSPECTIONS = "META-INF/micronaut/io.micronaut.core.beans.BeanIntrospectionReference/";
    private static final byte[] SERVICES_PREFIX = SERVICES.getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ALL_NAMES = new byte[0];

    @TempDir
    Path tempDir;

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
    void listsTheServicesOfAJarWhoseCentralDirectoryIsLargerThanTheScanBuffer() throws IOException {
        // long names, extra fields and comments spread the central directory over many reads of the buffer, so that
        // headers and names of matching entries also cross the end of what one read holds
        String padding = "p".repeat(150);
        Path jar = zip("large directory.jar", zip -> {
            put(zip, "META-INF/");
            put(zip, SERVICES);
            for (int i = 0; i < 6_000; i++) {
                if (i % 97 == 0) {
                    ZipEntry entry = new ZipEntry(BEANS + "com.example." + padding + ".$Bean" + i + "$Definition");
                    entry.setComment("c".repeat(i % 7));
                    zip.putNextEntry(entry);
                    zip.closeEntry();
                } else {
                    ZipEntry entry = new ZipEntry("com/example/" + padding + "/Generated" + i + ".class");
                    entry.setExtra(extraField(i % 13));
                    entry.setComment("comment".repeat(i % 3));
                    zip.putNextEntry(entry);
                    zip.closeEntry();
                }
            }
            put(zip, INTROSPECTIONS + "z.$Z$Introspection");
        });
        assertTrue(Files.size(jar) > 4 * 65_536);
        assertScanMatchesZipFile(jar);
        assertSameAsZipFileSystem(jar);
    }

    @Test
    void listsTheServicesWithNamesThatAreNotAscii() throws IOException {
        Path jar = jar("unicode.jar", List.of(
            SERVICES,
            BEANS + "café.$Crème$Definition",
            BEANS + "日本.$名前$Definition",
            "ü/Ü.class",
            INTROSPECTIONS + "emoji.$😀$Introspection"
        ));
        assertScanMatchesZipFile(jar);
        assertSameAsZipFileSystem(jar);
    }

    @Test
    void matchesOnlyTheExactPrefixOfTheServices() throws IOException {
        Path jar = jar("near misses.jar", List.of(
            "META-INF/micronaut",
            "META-INF/micronautx/a/b",
            "meta-inf/micronaut/a/b",
            "META-INF/MICRONAUT/a/b",
            "META-INF/micronau",
            "META-INF/micronaut/a/b",
            "META-INF/micronaut//c",
            "x/META-INF/micronaut/a/b"
        ));
        assertEquals(List.of("META-INF/micronaut/a/b", "META-INF/micronaut//c"), scan(jar, SERVICES_PREFIX));
        assertScanMatchesZipFile(jar);
    }

    @Test
    void readsTheJarsOfTheClassPathAsZipFileDoes() throws IOException {
        int read = 0;
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator, -1)) {
            Path path = Path.of(entry);
            if (entry.endsWith(".jar") && Files.isRegularFile(path)) {
                assertScanMatchesZipFile(path);
                read++;
            }
        }
        assertTrue(read >= 5, "Only " + read + " jars on the class path");
    }

    @Test
    void readsAJarWithDataBeforeItsFirstEntry() throws IOException {
        Path jar = jar("plain.jar", serviceEntries());
        Path prepended = tempDir.resolve("a dir").resolve("launcher.jar");
        byte[] script = "#!/bin/sh\nexec java -jar \"$0\" \"$@\"\n".getBytes(StandardCharsets.US_ASCII);
        Files.write(prepended, concat(script, Files.readAllBytes(jar)));
        assertNotNull(scan(prepended, ALL_NAMES));
        assertScanMatchesZipFile(prepended);
        assertSameServices(jar, prepended);
    }

    @Test
    void readsAJarWithAComment() throws IOException {
        for (String comment : List.of("a comment", "c".repeat(65_535))) {
            Path jar = zip("commented " + comment.length() + ".jar", zip -> {
                zip.setComment(comment);
                for (String name : serviceEntries()) {
                    put(zip, name);
                }
            });
            assertNotNull(scan(jar, ALL_NAMES), "comment of " + comment.length());
            assertScanMatchesZipFile(jar);
            assertSameAsZipFileSystem(jar);
        }
    }

    @Test
    void leavesAJarWithAnEndSignatureInItsCommentToZipFile() throws IOException {
        Path jar = zip("signature in comment.jar", zip -> {
            zip.setComment("PK\u0005\u0006 looks like an end record");
            for (String name : serviceEntries()) {
                put(zip, name);
            }
        });
        assertNull(scan(jar, ALL_NAMES));
        assertSameAsZipFileSystem(jar);
    }

    @Test
    void leavesAZip64JarToZipFile() throws IOException {
        // more than 65,534 entries make ZipOutputStream write the ZIP64 end records
        Path jar = zip("zip64.jar", zip -> {
            for (String name : serviceEntries()) {
                put(zip, name);
            }
            for (int i = 0; i < 65_600; i++) {
                put(zip, "c/" + i);
            }
        });
        byte[] bytes = Files.readAllBytes(jar);
        assertEquals(0x07064b50, littleEndian(bytes).getInt(bytes.length - 22 - 20), "no ZIP64 locator");
        assertNull(scan(jar, ALL_NAMES));
        assertSameAsZipFileSystem(jar);
    }

    @Test
    void leavesADamagedJarToZipFile() throws IOException {
        Path jar = jar("intact.jar", serviceEntries());
        byte[] intact = Files.readAllBytes(jar);
        ByteBuffer view = littleEndian(intact);
        int end = intact.length - 22;
        int cenStart = end - view.getInt(end + 12);
        int secondHeader = cenStart + 46 + Short.toUnsignedInt(view.getShort(cenStart + 28));
        int lastHeader = lastCentralHeader(intact, cenStart, end);

        // damage that ZipFile also rejects
        assertNull(scan(damaged("cen signature.jar", intact, b -> b.put(secondHeader, (byte) 0)), ALL_NAMES));
        assertNull(scan(damaged("name past the directory.jar", intact, b -> b.putShort(lastHeader + 28, (short) 0x7FFF)), ALL_NAMES));
        assertNull(scan(damaged("directory before the file.jar", intact, b -> b.putInt(end + 12, 0x7FFFFFFF)), ALL_NAMES));
        assertNull(scan(damaged("offset past the directory.jar", intact, b -> b.putInt(end + 16, 0x7FFFFFFF)), ALL_NAMES));
        assertNull(scan(write("truncated.jar", Arrays.copyOf(intact, intact.length - 1)), ALL_NAMES));
        assertNull(scan(write("too short.jar", Arrays.copyOf(intact, 21)), ALL_NAMES));
        assertNull(scan(write("empty.jar", new byte[0]), ALL_NAMES));
        assertNull(scan(write("text.jar", "not a zip file".repeat(10).getBytes(StandardCharsets.US_ASCII)), ALL_NAMES));
        assertNull(scan(tempDir.resolve("missing.jar"), ALL_NAMES));

        // damage that ZipFile accepts: the services are then the same as before, listed by ZipFile
        Path wrongCount = damaged("wrong count.jar", intact, b -> b.putShort(end + 10, (short) (b.getShort(end + 10) - 1)));
        Path padded = write("padded.jar", concat(intact, new byte[16]));
        Path commented = zip("commented.jar", zip -> {
            zip.setComment("0123456789");
            for (String name : serviceEntries()) {
                put(zip, name);
            }
        });
        byte[] commentedBytes = Files.readAllBytes(commented);
        int commentedEnd = commentedBytes.length - 22 - 10;
        Path shorterComment = damaged("comment length.jar", commentedBytes, b -> b.putShort(commentedEnd + 20, (short) 5));
        for (Path readable : List.of(wrongCount, padded, shorterComment)) {
            assertNull(scan(readable, ALL_NAMES), readable.toString());
            try (ZipFile zipFile = new ZipFile(readable.toFile())) {
                assertEquals(serviceEntries().size(), zipFile.size(), readable.toString());
            }
            assertSameServices(jar, readable);
        }
    }

    @ParameterizedTest
    @EnumSource
    void leavesAJarThatZipFileRejectsToZipFile(Rejected rejected) throws IOException {
        // one entry outside META-INF/micronaut/ makes ZipFile reject the jar; a class loader may still return its URL
        // without having opened it, and the services are then those the zip file system gives, as before
        Path jar = rejectedJar(rejected);
        assertThrows(ZipException.class, () -> new ZipFile(jar.toFile()).close());
        assertNull(scan(jar, SERVICES_PREFIX));
        assertEquals(asLists(walkZipFileSystemIfItOpens(jar)), asLists(findAllThroughUnopenedUrl(jar)));
    }

    @Test
    void readsAJarWithTheExtraFieldsAndCommentsZipFileAccepts() throws IOException {
        Path jar = zip("accepted headers.jar", zip -> {
            // the jar tool gives its first entry an empty block 0xCAFE, and a modification time adds a block 0x5455
            ZipEntry manifest = new ZipEntry("META-INF/MANIFEST.MF");
            manifest.setExtra(extraBlock(0xCAFE, 0));
            manifest.setLastModifiedTime(FileTime.fromMillis(1_700_000_000_000L));
            zip.putNextEntry(manifest);
            zip.closeEntry();
            for (String name : serviceEntries()) {
                put(zip, name);
            }
            ZipEntry commented = new ZipEntry("c/Commented.class");
            commented.setComment("an entry comment");
            zip.putNextEntry(commented);
            zip.closeEntry();
            put(zip, "ä/Ö€😀.class");
        });
        try (ZipFile zipFile = new ZipFile(jar.toFile())) {
            assertEquals(13, zipFile.getEntry("META-INF/MANIFEST.MF").getExtra().length, "blocks 0x5455 and 0xCAFE");
        }
        assertNotNull(scan(jar, ALL_NAMES));
        assertScanMatchesZipFile(jar);
        assertSameAsZipFileSystem(jar);
    }

    @Test
    void createsNoNameForTheOtherEntriesOfAJar() throws IOException {
        assumeTrue(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean);
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assumeTrue(threads.isThreadAllocatedMemorySupported() && threads.isThreadAllocatedMemoryEnabled());
        int others = 50_000;
        Path jar = zip("many classes.jar", zip -> {
            put(zip, SERVICES);
            put(zip, BEANS + "a.$A$Definition");
            for (int i = 0; i < others; i++) {
                put(zip, "com/example/generated/Host$$Lambda" + i + ".class");
            }
            put(zip, BEANS + "b.$B$Definition");
        });
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
            // the first calls open the jar in the class loader and load the classes the scan uses
            for (int i = 0; i < 3; i++) {
                MicronautMetaServiceLoaderUtils.findAllMicronautMetaServices(classLoader);
            }
            long before = threads.getCurrentThreadAllocatedBytes();
            Map<String, Set<String>> services = MicronautMetaServiceLoaderUtils.findAllMicronautMetaServices(classLoader);
            long allocated = threads.getCurrentThreadAllocatedBytes() - before;

            assertEquals(Map.of("io.micronaut.inject.BeanDefinitionReference", List.of("b.$B$Definition", "a.$A$Definition")), asLists(services));
            // a name alone (a String and its bytes) takes 40 bytes or more, and listing a ZipFile also creates a ZipEntry
            assertTrue(allocated < others * 16L, allocated + " bytes allocated to list " + others + " other entries");
        }
    }

    private static List<String> serviceEntries() {
        return List.of(
            "META-INF/",
            SERVICES,
            BEANS,
            BEANS + "a.$A$Definition",
            "a/A.class",
            BEANS + "b.$B$Definition",
            INTROSPECTIONS + "a.$A$Introspection",
            "b/B.class"
        );
    }

    private static List<String> scan(Path zip, byte[] prefix) {
        return MicronautMetaServiceLoaderUtils.scanCentralDirectory(zip.toFile(), prefix, new byte[65_581]);
    }

    private static void assertScanMatchesZipFile(Path zip) throws IOException {
        List<String> all = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                all.add(entries.nextElement().getName());
            }
        }
        assertEquals(all, scan(zip, ALL_NAMES), zip.toString());
        assertEquals(all.stream().filter(n -> n.startsWith(SERVICES)).toList(), scan(zip, SERVICES_PREFIX), zip.toString());
    }

    private void assertSameAsZipFileSystem(Path jar) throws IOException {
        Map<String, Set<String>> expected = walkZipFileSystem(jar);
        assertEquals(asLists(expected), asLists(findAll(jar)));
    }

    private static void assertSameServices(Path expectedJar, Path jar) throws IOException {
        assertEquals(asLists(walkZipFileSystem(expectedJar)), asLists(findAll(jar)), jar.toString());
    }

    private static Map<String, Set<String>> findAll(Path jar) throws IOException {
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
            return MicronautMetaServiceLoaderUtils.findAllMicronautMetaServices(classLoader);
        }
    }

    /**
     * Finds the services through a class loader that returns the {@code jar:file:} URL of a jar without opening it.
     */
    private static Map<String, Set<String>> findAllThroughUnopenedUrl(Path jar) throws IOException {
        URL url = URI.create("jar:" + jar.toUri() + "!/" + SERVICES).toURL();
        ClassLoader classLoader = new ClassLoader(null) {
            @Override
            public Enumeration<URL> getResources(String name) {
                return SERVICES.equals(name) ? Collections.enumeration(List.of(url)) : Collections.emptyEnumeration();
            }
        };
        return MicronautMetaServiceLoaderUtils.findAllMicronautMetaServices(classLoader);
    }

    private static Map<String, Set<String>> walkZipFileSystemIfItOpens(Path jar) throws IOException {
        try {
            return walkZipFileSystem(jar);
        } catch (ZipException e) {
            // the zip file system rejects the jar too, which ends the scan before it finds a service
            return Map.of();
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
        return zip(name, zip -> {
            for (String entry : entries) {
                put(zip, entry);
            }
        });
    }

    private Path zip(String name, ZipContent content) throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve("a dir"));
        Path jar = dir.resolve(name);
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(jar)); ZipOutputStream zip = new ZipOutputStream(out)) {
            content.write(zip);
        }
        return jar;
    }

    private static void put(ZipOutputStream zip, String name) throws IOException {
        // stored, as an empty entry needs no compression, and writing a deflated one takes several times longer
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(0);
        entry.setCrc(0);
        zip.putNextEntry(entry);
        zip.closeEntry();
    }

    private Path write(String name, byte[] bytes) throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve("a dir"));
        return Files.write(dir.resolve(name), bytes);
    }

    private Path damaged(String name, byte[] intact, Damage damage) throws IOException {
        byte[] bytes = intact.clone();
        damage.apply(littleEndian(bytes));
        return write(name, bytes);
    }

    private static int lastCentralHeader(byte[] zip, int cenStart, int cenEnd) {
        ByteBuffer view = littleEndian(zip);
        int position = cenStart;
        int last = cenStart;
        while (position < cenEnd) {
            last = position;
            position += 46 + Short.toUnsignedInt(view.getShort(position + 28)) + Short.toUnsignedInt(view.getShort(position + 30))
                + Short.toUnsignedInt(view.getShort(position + 32));
        }
        return last;
    }

    private static byte[] extraField(int dataLength) {
        // an unknown header ID, which ZipEntry keeps as it is
        return extraBlock(0x6D6E, dataLength);
    }

    private static byte[] extraBlock(int tag, int dataLength) {
        byte[] extra = new byte[4 + dataLength];
        littleEndian(extra).putShort(0, (short) tag).putShort(2, (short) dataLength);
        return extra;
    }

    private static ByteBuffer littleEndian(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] bytes = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, bytes, first.length, second.length);
        return bytes;
    }

    /**
     * A central directory header that {@link ZipFile} rejects, for each check {@code ZipFile.Source.checkAndAddEntry}
     * makes of a header (JDK 25 and later).
     */
    enum Rejected {
        ENCRYPTED_ENTRY,
        BZIP2_METHOD,
        NAME_WITH_BYTE_FF,
        NAME_WITH_OVERLONG_SLASH,
        NAME_WITH_SURROGATE,
        COMMENT_WITH_BYTE_FF,
        EXTRA_BLOCK_PAST_THE_FIELD,
        ZIP64_BLOCK_WITHOUT_ZIP64_VALUES,
        ZIP64_SIZE_WITHOUT_EXTRA_FIELD,
        ZIP64_DISK_WITHOUT_EXTRA_FIELD,
        HEADER_OF_70046_BYTES
    }

    /**
     * Writes a jar with the services and, last, an entry outside {@code META-INF/micronaut/} whose central directory
     * header {@link ZipFile} rejects.
     */
    private Path rejectedJar(Rejected rejected) throws IOException {
        String name = rejected == Rejected.HEADER_OF_70046_BYTES ? "c/" + "n".repeat(59_998) : "com/example/Other.class";
        Path jar = zip(rejected + ".jar", zip -> {
            for (String service : serviceEntries()) {
                put(zip, service);
            }
            ZipEntry entry = new ZipEntry(name);
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(0);
            entry.setCrc(0);
            switch (rejected) {
                case COMMENT_WITH_BYTE_FF -> entry.setComment("comment");
                case EXTRA_BLOCK_PAST_THE_FIELD -> entry.setExtra(extraBlock(0xCAFE, 4));
                case ZIP64_BLOCK_WITHOUT_ZIP64_VALUES -> entry.setExtra(extraBlock(0xCAFE, 8));
                default -> {
                    // the other headers are changed in the bytes of the written jar, below
                }
            }
            zip.putNextEntry(entry);
            zip.closeEntry();
        });
        byte[] bytes = Files.readAllBytes(jar);
        ByteBuffer view = littleEndian(bytes);
        int end = bytes.length - 22;
        int header = lastCentralHeader(bytes, end - view.getInt(end + 12), end);
        int nameAt = header + 46;
        int extraAt = nameAt + name.length();
        // bytes 13 to 15 of the name are "the" of "Other"
        switch (rejected) {
            case ENCRYPTED_ENTRY -> view.putShort(header + 8, (short) (view.getShort(header + 8) | 1));
            case BZIP2_METHOD -> view.putShort(header + 10, (short) 12);
            case NAME_WITH_BYTE_FF -> view.put(nameAt + 13, (byte) 0xFF);
            case NAME_WITH_OVERLONG_SLASH -> view.put(nameAt + 13, (byte) 0xC0).put(nameAt + 14, (byte) 0xAF);
            case NAME_WITH_SURROGATE -> view.put(nameAt + 13, (byte) 0xED).put(nameAt + 14, (byte) 0xA0).put(nameAt + 15, (byte) 0x80);
            case COMMENT_WITH_BYTE_FF -> view.put(extraAt + 3, (byte) 0xFF);
            case EXTRA_BLOCK_PAST_THE_FIELD -> view.putShort(extraAt + 2, (short) 8);
            case ZIP64_BLOCK_WITHOUT_ZIP64_VALUES -> view.putShort(extraAt, (short) 0x0001);
            case ZIP64_SIZE_WITHOUT_EXTRA_FIELD -> view.putInt(header + 20, -1);
            case ZIP64_DISK_WITHOUT_EXTRA_FIELD -> view.putShort(header + 34, (short) -1);
            // ZipEntry refuses a header longer than 65535 bytes, so the comment is added to the written header
            case HEADER_OF_70046_BYTES -> bytes = addComment(bytes, header, 10_000);
        }
        return write(rejected + ".jar", bytes);
    }

    private static byte[] addComment(byte[] zip, int header, int length) {
        ByteBuffer view = littleEndian(zip);
        int at = header + 46 + Short.toUnsignedInt(view.getShort(header + 28)) + Short.toUnsignedInt(view.getShort(header + 30));
        byte[] bytes = new byte[zip.length + length];
        System.arraycopy(zip, 0, bytes, 0, at);
        Arrays.fill(bytes, at, at + length, (byte) 'c');
        System.arraycopy(zip, at, bytes, at + length, zip.length - at);
        ByteBuffer commented = littleEndian(bytes);
        commented.putShort(header + 32, (short) length);
        int end = bytes.length - 22;
        commented.putInt(end + 12, commented.getInt(end + 12) + length);
        return bytes;
    }

    @FunctionalInterface
    private interface ZipContent {
        void write(ZipOutputStream zip) throws IOException;
    }

    @FunctionalInterface
    private interface Damage {
        void apply(ByteBuffer zip);
    }
}
