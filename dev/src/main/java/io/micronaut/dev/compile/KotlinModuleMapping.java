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

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Merges the Kotlin module metadata, {@code META-INF/<module>.kotlin_module}, of an incremental compilation
 * with that of the previous output. The file lists the file facades of every package of the module, the
 * classes of the top-level declarations, and kotlinc writes it for the sources it compiled only: promoted
 * alone, it would hide the top-level declarations of every other source from the next compilation.
 *
 * <p>The file is a version header, a count of ints and the ints, then from metadata 1.4 a flags word,
 * followed by the protocol buffer {@code JvmModuleProtoBuf.Module}. The merge keeps the new file as it is and appends, as further
 * {@code PackageParts} messages, the parts of the previous file that the compilation did not replace and
 * whose class is still in the output: the reader joins the messages of one package. What else the module
 * holds, its metadata parts and the optional annotation classes of a multiplatform module, is the new
 * file's.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
final class KotlinModuleMapping {

    private static final int MODULE_PACKAGE_PARTS = 1;
    private static final int MODULE_JVM_PACKAGE_NAME = 3;
    private static final int PARTS_PACKAGE = 1;
    private static final int PARTS_SHORT_NAME = 2;
    private static final int PARTS_FACADE_ID = 3;
    private static final int PARTS_FACADE_NAME = 4;
    private static final int PARTS_JVM_SHORT_NAME = 5;
    private static final int PARTS_JVM_PACKAGE_ID = 6;
    private static final int PARTS_JVM_FACADE_ID = 7;
    private static final int WIRE_VARINT = 0;
    private static final int WIRE_FIXED64 = 1;
    private static final int WIRE_LENGTH = 2;
    private static final int WIRE_FIXED32 = 5;

    private KotlinModuleMapping() {
    }

    /**
     * Writes into the staging directory the module metadata of the output after promotion.
     *
     * @param moduleName The module name
     * @param classOutput The class output, holding the previous module metadata
     * @param staging The staging directory, holding the module metadata of the compiled sources if kotlinc wrote any
     * @param replaced Whether a part, by binary name, was produced by a source the compilation replaced or deleted
     * @throws IOException if a file cannot be read or written
     */
    static void mergeInto(String moduleName, Path classOutput, Path staging, Predicate<String> replaced) throws IOException {
        Path relative = Path.of("META-INF", moduleName + ".kotlin_module");
        Path previousFile = classOutput.resolve(relative);
        if (!Files.isRegularFile(previousFile)) {
            return;
        }
        Path stagedFile = staging.resolve(relative);
        byte[] previous = Files.readAllBytes(previousFile);
        byte[] staged = Files.isRegularFile(stagedFile) ? Files.readAllBytes(stagedFile) : null;
        Module old = Module.parse(previous);
        Module fresh = staged != null ? Module.parse(staged) : old.withoutParts();
        Set<String> present = new LinkedHashSet<>();
        for (Part part : fresh.parts) {
            present.add(part.internalName());
        }
        List<Part> kept = new ArrayList<>();
        boolean dropped = false;
        for (Part part : old.parts) {
            String internalName = part.internalName();
            if (present.contains(internalName)) {
                continue;
            }
            if (replaced.test(internalName.replace('/', '.')) || !Files.isRegularFile(classOutput.resolve(internalName + ".class"))) {
                dropped = true;
                continue;
            }
            kept.add(part);
        }
        if (staged == null && !dropped) {
            // nothing compiled wrote metadata and nothing listed went: the previous file stays as it is
            return;
        }
        Files.createDirectories(stagedFile.getParent());
        Files.write(stagedFile, fresh.withParts(kept));
    }

    /**
     * One part of a package: a file facade, or a part of a multi-file facade.
     *
     * @param packageName The Kotlin package, dotted
     * @param jvmPackage The JVM package, dotted, when {@code @JvmPackageName} gave one
     * @param shortName The class name in its JVM package
     * @param facade The multi-file facade it is a part of, by short name
     */
    private record Part(String packageName, @Nullable String jvmPackage, String shortName, @Nullable String facade) {

        String internalName() {
            String pkg = jvmPackage != null ? jvmPackage : packageName;
            return pkg.isEmpty() ? shortName : pkg.replace('.', '/') + "/" + shortName;
        }
    }

    /**
     * A parsed module file.
     *
     * @param header The version header
     * @param body The message as written
     * @param otherFields The fields of the message other than the package parts and the JVM package names, as written
     * @param jvmPackages The JVM package names it declares
     * @param parts Its parts
     */
    private record Module(byte[] header, byte[] body, byte[] otherFields, List<String> jvmPackages, List<Part> parts) {

        /**
         * @return This module with no package parts and no JVM package names: what else it holds stays
         */
        Module withoutParts() {
            return new Module(header, otherFields, otherFields, List.of(), List.of());
        }

        static Module parse(byte[] bytes) throws IOException {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            if (bytes.length < 4) {
                throw new IOException("Not a Kotlin module file");
            }
            int count = buffer.getInt();
            if (count < 0 || count > (bytes.length - 4) / 4) {
                throw new IOException("Not a Kotlin module file");
            }
            int headerLength = 4 + 4 * count;
            // from metadata 1.4 on, a flags word follows the version
            int major = count > 0 ? buffer.getInt(4) : 0;
            int minor = count > 1 ? buffer.getInt(8) : 0;
            if (major > 1 || major == 1 && minor >= 4) {
                headerLength += 4;
            }
            if (headerLength > bytes.length) {
                throw new IOException("Not a Kotlin module file");
            }
            byte[] header = java.util.Arrays.copyOfRange(bytes, 0, headerLength);
            byte[] body = java.util.Arrays.copyOfRange(bytes, headerLength, bytes.length);
            List<String> jvmPackages = new ArrayList<>();
            List<byte[]> packageParts = new ArrayList<>();
            ByteArrayOutputStream otherFields = new ByteArrayOutputStream();
            Reader reader = new Reader(body);
            while (reader.hasMore()) {
                int start = reader.position;
                long tag = reader.varint();
                int field = (int) (tag >>> 3);
                int wire = (int) (tag & 7);
                if (field == MODULE_PACKAGE_PARTS && wire == WIRE_LENGTH) {
                    packageParts.add(reader.bytes());
                } else if (field == MODULE_JVM_PACKAGE_NAME && wire == WIRE_LENGTH) {
                    jvmPackages.add(new String(reader.bytes(), StandardCharsets.UTF_8));
                } else {
                    reader.skip(wire);
                    // the metadata parts, the annotations and their tables, kept as written
                    otherFields.write(body, start, reader.position - start);
                }
            }
            List<Part> parts = new ArrayList<>();
            for (byte[] message : packageParts) {
                parsePackageParts(message, jvmPackages, parts);
            }
            return new Module(header, body, otherFields.toByteArray(), jvmPackages, parts);
        }

        private static void parsePackageParts(byte[] message, List<String> jvmPackages, List<Part> parts) throws IOException {
            String packageName = "";
            List<String> shortNames = new ArrayList<>();
            List<Integer> facadeIds = new ArrayList<>();
            List<String> facadeNames = new ArrayList<>();
            List<String> jvmShortNames = new ArrayList<>();
            List<Integer> jvmPackageIds = new ArrayList<>();
            List<Integer> jvmFacadeIds = new ArrayList<>();
            Reader reader = new Reader(message);
            while (reader.hasMore()) {
                long tag = reader.varint();
                int field = (int) (tag >>> 3);
                int wire = (int) (tag & 7);
                switch (field) {
                    case PARTS_PACKAGE -> packageName = reader.string(wire);
                    case PARTS_SHORT_NAME -> shortNames.add(reader.string(wire));
                    case PARTS_FACADE_ID -> reader.ints(wire, facadeIds);
                    case PARTS_FACADE_NAME -> facadeNames.add(reader.string(wire));
                    case PARTS_JVM_SHORT_NAME -> jvmShortNames.add(reader.string(wire));
                    case PARTS_JVM_PACKAGE_ID -> reader.ints(wire, jvmPackageIds);
                    case PARTS_JVM_FACADE_ID -> reader.ints(wire, jvmFacadeIds);
                    default -> reader.skip(wire);
                }
            }
            for (int i = 0; i < shortNames.size(); i++) {
                parts.add(new Part(packageName, null, shortNames.get(i), facade(facadeIds, facadeNames, i)));
            }
            for (int i = 0; i < jvmShortNames.size(); i++) {
                // as kotlinc reads them: a missing package id is the last one given
                Integer packageId = i < jvmPackageIds.size() ? jvmPackageIds.get(i) : (jvmPackageIds.isEmpty() ? null : jvmPackageIds.get(jvmPackageIds.size() - 1));
                if (packageId == null || packageId < 0 || packageId >= jvmPackages.size()) {
                    continue;
                }
                parts.add(new Part(packageName, jvmPackages.get(packageId), jvmShortNames.get(i), facade(jvmFacadeIds, facadeNames, i)));
            }
        }

        @Nullable
        private static String facade(List<Integer> ids, List<String> names, int index) {
            if (index >= ids.size()) {
                return null;
            }
            // an id counts from one, zero being no facade
            int id = ids.get(index) - 1;
            return id >= 0 && id < names.size() ? names.get(id) : null;
        }

        /**
         * This module with further parts appended, grouped by package, and the JVM package names they need.
         */
        byte[] withParts(List<Part> extra) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(header);
            out.write(body);
            List<String> addedJvmPackages = new ArrayList<>();
            Map<String, List<Part>> byPackage = new LinkedHashMap<>();
            for (Part part : extra) {
                byPackage.computeIfAbsent(part.packageName(), k -> new ArrayList<>()).add(part);
            }
            for (Map.Entry<String, List<Part>> entry : byPackage.entrySet()) {
                Writer message = new Writer();
                message.string(PARTS_PACKAGE, entry.getKey());
                List<String> facadeNames = new ArrayList<>();
                List<Integer> facadeIds = new ArrayList<>();
                List<Integer> jvmFacadeIds = new ArrayList<>();
                List<Integer> jvmPackageIds = new ArrayList<>();
                for (Part part : entry.getValue()) {
                    int facadeId = 0;
                    if (part.facade() != null) {
                        int index = facadeNames.indexOf(part.facade());
                        if (index < 0) {
                            facadeNames.add(part.facade());
                            index = facadeNames.size() - 1;
                        }
                        facadeId = index + 1;
                    }
                    if (part.jvmPackage() == null) {
                        message.string(PARTS_SHORT_NAME, part.shortName());
                        facadeIds.add(facadeId);
                    } else {
                        message.string(PARTS_JVM_SHORT_NAME, part.shortName());
                        jvmFacadeIds.add(facadeId);
                        int index = jvmPackages.indexOf(part.jvmPackage());
                        if (index < 0) {
                            int added = addedJvmPackages.indexOf(part.jvmPackage());
                            if (added < 0) {
                                addedJvmPackages.add(part.jvmPackage());
                                added = addedJvmPackages.size() - 1;
                            }
                            index = jvmPackages.size() + added;
                        }
                        jvmPackageIds.add(index);
                    }
                }
                message.packed(PARTS_FACADE_ID, facadeIds);
                for (String facadeName : facadeNames) {
                    message.string(PARTS_FACADE_NAME, facadeName);
                }
                message.packed(PARTS_JVM_PACKAGE_ID, jvmPackageIds);
                message.packed(PARTS_JVM_FACADE_ID, jvmFacadeIds);
                Writer module = new Writer();
                module.bytes(MODULE_PACKAGE_PARTS, message.toByteArray());
                out.write(module.toByteArray());
            }
            Writer module = new Writer();
            for (String jvmPackage : addedJvmPackages) {
                module.string(MODULE_JVM_PACKAGE_NAME, jvmPackage);
            }
            out.write(module.toByteArray());
            return out.toByteArray();
        }
    }

    /**
     * Reads the protocol buffer wire format.
     */
    private static final class Reader {
        private final byte[] bytes;
        private int position;

        Reader(byte[] bytes) {
            this.bytes = bytes;
        }

        boolean hasMore() {
            return position < bytes.length;
        }

        long varint() throws IOException {
            long value = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                if (position >= bytes.length) {
                    throw new IOException("Truncated Kotlin module file");
                }
                byte b = bytes[position++];
                value |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return value;
                }
            }
            throw new IOException("Malformed varint in a Kotlin module file");
        }

        byte[] bytes() throws IOException {
            long length = varint();
            if (length < 0 || length > bytes.length - position) {
                throw new IOException("Truncated Kotlin module file");
            }
            byte[] value = java.util.Arrays.copyOfRange(bytes, position, position + (int) length);
            position += (int) length;
            return value;
        }

        String string(int wire) throws IOException {
            if (wire != WIRE_LENGTH) {
                throw new IOException("Unexpected wire type " + wire + " for a string in a Kotlin module file");
            }
            return new String(bytes(), StandardCharsets.UTF_8);
        }

        void ints(int wire, List<Integer> into) throws IOException {
            if (wire == WIRE_VARINT) {
                into.add((int) varint());
            } else if (wire == WIRE_LENGTH) {
                Reader packed = new Reader(bytes());
                while (packed.hasMore()) {
                    into.add((int) packed.varint());
                }
            } else {
                throw new IOException("Unexpected wire type " + wire + " for an int in a Kotlin module file");
            }
        }

        void skip(int wire) throws IOException {
            switch (wire) {
                case WIRE_VARINT -> varint();
                case WIRE_FIXED64 -> position += 8;
                case WIRE_LENGTH -> bytes();
                case WIRE_FIXED32 -> position += 4;
                default -> throw new IOException("Unsupported wire type " + wire + " in a Kotlin module file");
            }
            if (position > bytes.length) {
                throw new IOException("Truncated Kotlin module file");
            }
        }
    }

    /**
     * Writes the protocol buffer wire format.
     */
    private static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        void string(int field, String value) {
            bytes(field, value.getBytes(StandardCharsets.UTF_8));
        }

        void bytes(int field, byte[] value) {
            varint(((long) field << 3) | WIRE_LENGTH);
            varint(value.length);
            out.writeBytes(value);
        }

        void packed(int field, List<Integer> values) {
            if (values.isEmpty()) {
                return;
            }
            Writer packed = new Writer();
            for (int value : values) {
                packed.varint(value);
            }
            bytes(field, packed.toByteArray());
        }

        void varint(long value) {
            long remaining = value;
            while ((remaining & ~0x7FL) != 0) {
                out.write((int) ((remaining & 0x7F) | 0x80));
                remaining >>>= 7;
            }
            out.write((int) remaining);
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }
    }
}
