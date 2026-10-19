/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.dev.compile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The merge of the Kotlin module metadata, on module files written here in the wire format kotlinc writes.
 */
class KotlinModuleMappingTest {

    private static final String MODULE = "main";

    @TempDir
    Path directory;

    @Test
    void withoutAPreviousFileThereIsNothingToMerge() throws IOException {
        Path classes = Files.createDirectories(directory.resolve("classes"));
        Path staging = Files.createDirectories(directory.resolve("staging"));
        KotlinModuleMapping.mergeInto(MODULE, classes, staging, name -> false);
        assertFalse(Files.exists(staging.resolve("META-INF")));
    }

    @Test
    void thePartsOfThePreviousFileWhoseClassIsLeftAreKept() throws IOException {
        Path classes = directory.resolve("classes");
        Path staging = Files.createDirectories(directory.resolve("staging"));
        writeClass(classes, "app/AKt");
        writeClass(classes, "app/PartKt");
        writeClass(classes, "jvm/pkg/JKt");
        // BKt's class is gone
        write(classes, previous());

        KotlinModuleMapping.mergeInto(MODULE, classes, staging, name -> false);

        String merged = text(staging);
        assertTrue(merged.contains("AKt") && merged.contains("PartKt") && merged.contains("Facade"), merged);
        assertTrue(merged.contains("JKt") && merged.contains("jvm.pkg"), merged);
        assertFalse(merged.contains("BKt"), merged);
        // what else the module holds is kept as written
        assertTrue(merged.contains("metadata-part"), merged);
    }

    @Test
    void theCompiledFileIsKeptAndThePartsItDoesNotListAreAppended() throws IOException {
        Path classes = directory.resolve("classes");
        Path staging = directory.resolve("staging");
        writeClass(classes, "app/AKt");
        writeClass(classes, "app/BKt");
        writeClass(classes, "app/PartKt");
        writeClass(classes, "jvm/pkg/JKt");
        write(classes, previous());
        // the compiled file lists AKt and declares the JVM package
        byte[] compiled = module(header(1, 4, 0), concat(string(3, "jvm.pkg"), bytes(1, concat(string(1, "app"), string(2, "AKt")))));
        write(staging, compiled);

        KotlinModuleMapping.mergeInto(MODULE, classes, staging, name -> name.equals("app.PartKt"));

        byte[] merged = Files.readAllBytes(file(staging));
        assertArrayEquals(compiled, Arrays.copyOf(merged, compiled.length));
        String appended = new String(merged, compiled.length, merged.length - compiled.length, StandardCharsets.UTF_8);
        assertTrue(appended.contains("BKt") && appended.contains("JKt"), appended);
        assertFalse(appended.contains("AKt") || appended.contains("PartKt"), appended);
        // the JVM package the compiled file declares is not declared again
        assertFalse(appended.contains("jvm.pkg"), appended);
    }

    @Test
    void whenNothingWentAndNothingWasCompiledThePreviousFileStays() throws IOException {
        Path classes = directory.resolve("classes");
        Path staging = Files.createDirectories(directory.resolve("staging"));
        for (String name : new String[] {"app/AKt", "app/BKt", "app/PartKt", "jvm/pkg/JKt"}) {
            writeClass(classes, name);
        }
        write(classes, previous());
        KotlinModuleMapping.mergeInto(MODULE, classes, staging, name -> false);
        assertFalse(Files.exists(file(staging)));
    }

    @Test
    void whatIsNoModuleFileIsRefused() throws IOException {
        byte[] header = header(1);
        assertRefused(new byte[] {0, 0});
        assertRefused(ByteBuffer.allocate(8).putInt(100).putInt(1).array());
        // a version of 1.4 announces a flags word that is missing
        assertRefused(Arrays.copyOf(header(1, 4, 0), 16));
        // a truncated and a malformed varint
        assertRefused(module(header, new byte[] {(byte) 0x80}));
        byte[] endless = new byte[11];
        Arrays.fill(endless, (byte) 0x80);
        assertRefused(module(header, endless));
        // a length past the end
        assertRefused(module(header, new byte[] {0x0A, 5, 1}));
        // an unknown wire type, and a fixed64 past the end
        assertRefused(module(header, new byte[] {0x4B}));
        assertRefused(module(header, new byte[] {0x51, 1, 2}));
        // a package name that is no string, and a facade id that is no int
        assertRefused(module(header, bytes(1, new byte[] {0x08, 1})));
        assertRefused(module(header, bytes(1, new byte[] {0x1D, 0, 0, 0, 0})));
    }

    private void assertRefused(byte[] contents) throws IOException {
        Path classes = directory.resolve("refused");
        write(classes, contents);
        Path staging = Files.createDirectories(directory.resolve("refused-staging"));
        assertThrows(IOException.class, () -> KotlinModuleMapping.mergeInto(MODULE, classes, staging, name -> false));
    }

    /**
     * Package app: AKt, BKt, PartKt of the multi-file facade Facade, and JKt in the JVM package jvm.pkg, with fields
     * of every wire type the reader skips.
     */
    private static byte[] previous() {
        byte[] parts = concat(
            string(1, "app"),
            string(2, "AKt"), string(2, "BKt"), string(2, "PartKt"),
            packed(3, 0, 0, 1),
            string(4, "Facade"),
            string(5, "JKt"),
            new byte[] {0x30, 0},
            packed(7, 1),
            new byte[] {0x48, 1});
        byte[] body = concat(
            string(3, "jvm.pkg"),
            bytes(1, parts),
            string(2, "metadata-part"),
            new byte[] {0x48, 7},
            new byte[] {0x51, 0, 0, 0, 0, 0, 0, 0, 0},
            new byte[] {0x5D, 0, 0, 0, 0});
        return module(header(1, 4, 0), body);
    }

    private static byte[] header(int... version) {
        boolean flags = version.length > 1 && (version[0] > 1 || version[0] == 1 && version[1] >= 4);
        ByteBuffer buffer = ByteBuffer.allocate(4 + 4 * version.length + (flags ? 4 : 0));
        buffer.putInt(version.length);
        for (int v : version) {
            buffer.putInt(v);
        }
        return buffer.array();
    }

    private static byte[] module(byte[] header, byte[] body) {
        return concat(header, body);
    }

    private static byte[] string(int field, String value) {
        return bytes(field, value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] bytes(int field, byte[] value) {
        return concat(new byte[] {(byte) (field << 3 | 2), (byte) value.length}, value);
    }

    private static byte[] packed(int field, int... values) {
        byte[] encoded = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            encoded[i] = (byte) values[i];
        }
        return bytes(field, encoded);
    }

    private static byte[] concat(byte[]... arrays) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] array : arrays) {
            out.writeBytes(array);
        }
        return out.toByteArray();
    }

    private static Path file(Path root) {
        return root.resolve("META-INF").resolve(MODULE + ".kotlin_module");
    }

    private static void write(Path root, byte[] contents) throws IOException {
        Files.createDirectories(file(root).getParent());
        Files.write(file(root), contents);
    }

    private static void writeClass(Path root, String internalName) throws IOException {
        Path file = root.resolve(internalName + ".class");
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {0});
    }

    private static String text(Path staging) throws IOException {
        return new String(Files.readAllBytes(file(staging)), StandardCharsets.ISO_8859_1);
    }
}
