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

import io.micronaut.core.annotation.Experimental;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Which top-level classes of a class output directory reference which, read from the constant
 * pools of the class files, so that an incremental compilation recompiles the sources whose classes
 * link against a changed class.
 *
 * <p>The index over-approximates on purpose: every class name a constant pool mentions counts as a
 * reference, including ones that appear only in a descriptor, and so does every class whose code the
 * compiler inlined into the class, which its SMAP names. Recompiling a source that did not strictly
 * need it is cheap; missing one that did leaves the output inconsistent.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public final class ClassDependencyIndex {

    private static final Logger LOG = LoggerFactory.getLogger(ClassDependencyIndex.class);
    private static final int CLASS_MAGIC = 0xCAFEBABE;
    private static final int ACC_PRIVATE = 0x0002;
    private static final int ACC_STATIC = 0x0008;
    private static final int ACC_FINAL = 0x0010;
    private static final int BUFFER_SIZE = 16 * 1024;
    private static final String SMAP_HEADER = "SMAP\n";
    private static final String SOURCE_DEBUG_EXTENSION = "SourceDebugExtension";

    private final Map<String, Set<String>> referencesByClass = new HashMap<>();
    private final Map<String, Set<String>> dependentsByClass = new HashMap<>();
    private final Set<String> constantDeclarers = new HashSet<>();

    private ClassDependencyIndex() {
    }

    /**
     * Reads the class files under the given directory.
     *
     * @param classOutput The class output directory; a missing directory yields an empty index
     * @return The index
     * @throws UncheckedIOException if a class file cannot be read
     */
    public static ClassDependencyIndex scan(Path classOutput) {
        return scan(List.of(classOutput));
    }

    /**
     * Reads the class files under the given directories.
     *
     * @param classOutputs The directories; a missing directory adds nothing
     * @return The index
     * @throws UncheckedIOException if a class file cannot be read
     */
    public static ClassDependencyIndex scan(List<Path> classOutputs) {
        ClassDependencyIndex index = new ClassDependencyIndex();
        for (Path classOutput : classOutputs) {
            if (!Files.isDirectory(classOutput)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(classOutput)) {
                files.filter(file -> file.getFileName().toString().endsWith(".class"))
                    .forEach(file -> index.add(classOutput.relativize(file), file));
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot scan " + classOutput, e);
            }
        }
        return index;
    }

    /**
     * The top-level classes the index knows.
     *
     * @return The binary names
     */
    public Set<String> classes() {
        return Set.copyOf(referencesByClass.keySet());
    }

    /**
     * The top-level classes the given top-level class references.
     *
     * @param topLevelClass The binary name
     * @return The referenced classes, never including the class itself
     */
    public Set<String> referencesOf(String topLevelClass) {
        return Set.copyOf(referencesByClass.getOrDefault(topLevelClass, Set.of()));
    }

    /**
     * The top-level classes that reference the given one, directly.
     *
     * @param topLevelClass The binary name
     * @return The dependents
     */
    public Set<String> dependentsOf(String topLevelClass) {
        return Set.copyOf(dependentsByClass.getOrDefault(topLevelClass, Set.of()));
    }

    /**
     * The top-level classes that reference any of the given ones, directly or through other classes.
     *
     * @param topLevelClasses The binary names
     * @return The transitive dependents, not including the given classes unless they depend on each other
     */
    /**
     * Whether a top-level class, or a class nested in it, declares a compile-time constant another class can read: a
     * static final field with a constant value, which javac inlines into the classes that read it, leaving no reference
     * in their constant pools for the index to follow. A private one does not count: only the class and the classes
     * nested in it can read it, and they come from the same source, which compiles with it.
     *
     * @param topLevelClass The binary name
     * @return True if a change to the class can change the classes that read its constants without the index knowing
     */
    public boolean declaresConstants(String topLevelClass) {
        return constantDeclarers.contains(topLevelClass);
    }

    public Set<String> transitiveDependentsOf(Set<String> topLevelClasses) {
        Set<String> result = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(topLevelClasses);
        Set<String> visited = new HashSet<>(topLevelClasses);
        while (!queue.isEmpty()) {
            for (String dependent : dependentsByClass.getOrDefault(queue.poll(), Set.of())) {
                if (visited.add(dependent)) {
                    result.add(dependent);
                    queue.add(dependent);
                }
            }
        }
        return result;
    }

    /**
     * The top-level class a class file belongs to: {@code com/acme/Foo$Bar.class} belongs to {@code com.acme.Foo}.
     *
     * @param relativeClassFile The class file, relative to the output directory
     * @return The binary name of the top-level class
     */
    public static String topLevelClassOf(Path relativeClassFile) {
        String name = relativeClassFile.toString().replace(java.io.File.separatorChar, '.');
        name = name.substring(0, name.length() - ".class".length());
        return topLevelOf(name);
    }

    /**
     * The top-level class of a binary name: {@code com.acme.Foo$Bar} is {@code com.acme.Foo}. A generated
     * Micronaut class named after a type with a leading {@code $} ({@code com.acme.$Foo$Definition})
     * belongs to that type.
     *
     * @param binaryName The binary name
     * @return The top-level class
     */
    public static String topLevelOf(String binaryName) {
        int lastDot = binaryName.lastIndexOf('.');
        String packageName = lastDot < 0 ? "" : binaryName.substring(0, lastDot + 1);
        String simple = lastDot < 0 ? binaryName : binaryName.substring(lastDot + 1);
        if (simple.startsWith("$")) {
            simple = simple.substring(1);
        }
        int dollar = simple.indexOf('$');
        if (dollar > 0) {
            simple = simple.substring(0, dollar);
        }
        return packageName + simple;
    }

    private void add(Path relativeClassFile, Path classFile) {
        String owner = topLevelClassOf(relativeClassFile);
        Set<String> references = referencesByClass.computeIfAbsent(owner, k -> new HashSet<>());
        try (InputStream in = Files.newInputStream(classFile)) {
            ClassInfo info = readClassFile(in);
            if (info.declaresConstants()) {
                constantDeclarers.add(owner);
            }
            for (String referenced : info.references()) {
                String topLevel = topLevelOf(referenced);
                if (!topLevel.equals(owner)) {
                    references.add(topLevel);
                    dependentsByClass.computeIfAbsent(topLevel, k -> new HashSet<>()).add(owner);
                }
            }
        } catch (IOException | RuntimeException e) {
            // a class file being written, or not a class file at all: it references nothing the index can use
            LOG.debug("Skipping {} in the dependency index: {}", classFile, e.getMessage());
        }
    }

    /**
     * Reads what the index needs from a class file, through a buffer: {@link DataInputStream} reads its stream a byte or
     * a short at a time, which unbuffered is a read from the file system for each of the thousands of fields of a class file.
     *
     * @param in The class file
     * @return What the index needs
     * @throws IOException if the stream cannot be read
     */
    static ClassInfo readClassFile(InputStream in) throws IOException {
        return read(new DataInputStream(new BufferedInputStream(in, BUFFER_SIZE)));
    }

    static ClassInfo read(DataInputStream in) throws IOException {
        if (in.readInt() != CLASS_MAGIC) {
            return new ClassInfo(Set.of(), false);
        }
        in.readUnsignedShort(); // minor
        in.readUnsignedShort(); // major
        int count = in.readUnsignedShort();
        String[] utf8 = new String[count];
        Set<Integer> classIndexes = new HashSet<>();
        int i = 1;
        while (i < count) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> {
                    int length = in.readUnsignedShort();
                    byte[] bytes = new byte[length];
                    in.readFully(bytes);
                    utf8[i] = new String(bytes, StandardCharsets.UTF_8);
                }
                case 3, 4 -> in.skipBytes(4);
                case 5, 6 -> in.skipBytes(8);
                case 7 -> classIndexes.add(in.readUnsignedShort());
                case 8, 16, 19, 20 -> in.skipBytes(2);
                case 9, 10, 11, 12, 17, 18 -> in.skipBytes(4);
                case 15 -> in.skipBytes(3);
                default -> throw new IOException("Unknown constant pool tag " + tag);
            }
            // a long or a double takes two entries of the pool
            i += tag == 5 || tag == 6 ? 2 : 1;
        }
        Set<String> names = new HashSet<>();
        for (int index : classIndexes) {
            String name = index < utf8.length ? utf8[index] : null;
            if (name != null && !name.startsWith("[")) {
                names.add(name.replace('/', '.'));
            }
        }
        for (String constant : utf8) {
            if (constant != null && holdsTypeDescriptor(constant)) {
                addDescriptorTypes(constant, names);
            }
            if (constant != null && constant.startsWith(SMAP_HEADER)) {
                // kotlinc keeps the SMAP in the constant pool too, as the value of kotlin.jvm.internal.SourceDebugExtension
                addInlinedFrom(constant, names);
            }
        }
        boolean constants;
        try {
            constants = declaresConstants(in, utf8);
        } catch (IOException e) {
            // a class file cut short after its constant pool: the references are what matters
            return new ClassInfo(names, false);
        }
        try {
            readSourceDebugExtension(in, utf8, names);
        } catch (IOException e) {
            // cut short after its fields: the SMAP, if any, is in the constant pool too
            LOG.debug("Cannot read the attributes of a class file: {}", e.getMessage());
        }
        return new ClassInfo(names, constants);
    }

    /**
     * Reads past the methods to the attributes of the class, and adds the classes the SMAP of its
     * {@code SourceDebugExtension} attribute names.
     */
    private static void readSourceDebugExtension(DataInputStream in, String[] utf8, Set<String> names) throws IOException {
        int methods = in.readUnsignedShort();
        for (int i = 0; i < methods; i++) {
            in.skipBytes(6); // access flags, name, descriptor
            skipAttributes(in);
        }
        int attributes = in.readUnsignedShort();
        for (int i = 0; i < attributes; i++) {
            int nameIndex = in.readUnsignedShort();
            int length = in.readInt();
            if (nameIndex < utf8.length && SOURCE_DEBUG_EXTENSION.equals(utf8[nameIndex])) {
                byte[] smap = new byte[length];
                in.readFully(smap);
                String text = modifiedUtf8(smap);
                if (text.startsWith(SMAP_HEADER)) {
                    addInlinedFrom(text, names);
                }
            } else {
                in.skipNBytes(length);
            }
        }
    }

    /**
     * The attribute's bytes as the class file format writes text, in modified UTF-8: a character outside the basic plane
     * is two surrogates of three bytes each, which standard UTF-8 does not read, and NUL takes two bytes.
     */
    static String modifiedUtf8(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length);
        int i = 0;
        while (i < bytes.length) {
            int b = bytes[i] & 0xFF;
            if (b < 0x80) {
                text.append((char) b);
                i++;
            } else if ((b & 0xE0) == 0xC0 && i + 1 < bytes.length) {
                text.append((char) ((b & 0x1F) << 6 | bytes[i + 1] & 0x3F));
                i += 2;
            } else if ((b & 0xF0) == 0xE0 && i + 2 < bytes.length) {
                text.append((char) ((b & 0x0F) << 12 | (bytes[i + 1] & 0x3F) << 6 | bytes[i + 2] & 0x3F));
                i += 3;
            } else {
                // not modified UTF-8: what it names is not a class
                text.append('\uFFFD');
                i++;
            }
        }
        return text.toString();
    }

    private static void skipAttributes(DataInputStream in) throws IOException {
        int attributes = in.readUnsignedShort();
        for (int i = 0; i < attributes; i++) {
            in.readUnsignedShort(); // name
            in.skipNBytes(in.readInt());
        }
    }

    /**
     * Adds the classes whose code the compiler inlined into a class, which its SMAP (JSR-045) names: an inline function's
     * body is copied into its callers, which keep no other reference to the class declaring it, so without these a change
     * to the body would not compile them. Each file of an {@code *F} section with a path is a line {@code + <id> <name>}
     * followed by the path, the internal name of the class for Kotlin.
     *
     * @param smap The SMAP
     * @param names Where to add the binary names
     */
    static void addInlinedFrom(String smap, Set<String> names) {
        String[] lines = smap.split("\r?\n");
        boolean files = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("*")) {
                files = line.equals("*F");
            } else if (files && line.startsWith("+ ") && i + 1 < lines.length) {
                String path = lines[++i].trim();
                if (!path.isEmpty() && !path.startsWith("*")) {
                    names.add(path.replace('/', '.'));
                }
            }
        }
    }

    /**
     * Reads past the class header and interfaces to the fields, looking for a static final one that is not private
     * with a {@code ConstantValue} attribute.
     */
    private static boolean declaresConstants(DataInputStream in, String[] utf8) throws IOException {
        in.readUnsignedShort(); // access flags
        in.readUnsignedShort(); // this class
        in.readUnsignedShort(); // super class
        int interfaces = in.readUnsignedShort();
        in.skipBytes(interfaces * 2);
        int fields = in.readUnsignedShort();
        boolean constants = false;
        for (int i = 0; i < fields; i++) {
            int access = in.readUnsignedShort();
            in.readUnsignedShort(); // name
            in.readUnsignedShort(); // descriptor
            int attributes = in.readUnsignedShort();
            for (int j = 0; j < attributes; j++) {
                int nameIndex = in.readUnsignedShort();
                int length = in.readInt();
                in.skipBytes(length);
                boolean readable = (access & (ACC_STATIC | ACC_FINAL | ACC_PRIVATE)) == (ACC_STATIC | ACC_FINAL);
                if (readable && nameIndex < utf8.length && "ConstantValue".equals(utf8[nameIndex])) {
                    constants = true;
                }
            }
        }
        return constants;
    }

    /**
     * Adds every {@code L...;} type of a descriptor or signature, including the ones nested in a
     * generic argument: in {@code Ljava/util/List&lt;Lexample/Foo;&gt;;} both {@code java.util.List}
     * and {@code example.Foo}. A type name ends at the first {@code ;}, {@code <} or {@code .}
     * (an inner-class suffix in a signature) after its {@code L}.
     */
    /**
     * Whether a constant can hold an {@code L...;} type: a {@code ;} after an {@code L}.
     */
    private static boolean holdsTypeDescriptor(String constant) {
        int typeStart = constant.indexOf('L');
        return typeStart >= 0 && constant.indexOf(';', typeStart + 1) >= 0;
    }

    private static void addDescriptorTypes(String descriptor, Set<String> names) {
        int start = 0;
        while ((start = descriptor.indexOf('L', start)) >= 0) {
            int end = start + 1;
            while (end < descriptor.length()) {
                char c = descriptor.charAt(end);
                if (c == ';' || c == '<' || c == '.') {
                    break;
                }
                end++;
            }
            String name = descriptor.substring(start + 1, end);
            if (!name.isEmpty() && name.indexOf(' ') < 0 && name.indexOf('(') < 0 && name.indexOf(')') < 0) {
                names.add(name.replace('/', '.'));
            }
            start = end + 1;
        }
    }

    /**
     * What the index needs from a class file.
     *
     * @param references The binary names the constant pool mentions: every {@code CONSTANT_Class} and every {@code L...;} type in a descriptor or signature, and the classes the SMAP names
     * @param declaresConstants Whether a static final field that is not private carries a {@code ConstantValue}
     */
    record ClassInfo(Set<String> references, boolean declaresConstants) {
    }
}
