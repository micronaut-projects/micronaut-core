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
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Which top-level classes of a class output directory reference which, read from the constant
 * pools of the class files, so that an incremental compilation recompiles the sources whose classes
 * link against a changed class.
 *
 * <p>The index over-approximates on purpose: every class name a constant pool mentions counts as a
 * reference, including ones that appear only in a descriptor. Recompiling a source that did not
 * strictly need it is cheap; missing one that did leaves the output inconsistent.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class ClassDependencyIndex {

    private static final Logger LOG = LoggerFactory.getLogger(ClassDependencyIndex.class);
    private static final int CLASS_MAGIC = 0xCAFEBABE;
    private static final int ACC_STATIC = 0x0008;
    private static final int ACC_FINAL = 0x0010;

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
        ClassDependencyIndex index = new ClassDependencyIndex();
        if (!Files.isDirectory(classOutput)) {
            return index;
        }
        try (Stream<Path> files = Files.walk(classOutput)) {
            files.filter(file -> file.getFileName().toString().endsWith(".class"))
                .forEach(file -> index.add(classOutput.relativize(file), file));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot scan " + classOutput, e);
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
     * Whether a top-level class, or a class nested in it, declares a compile-time constant: a static
     * final field with a constant value, which javac inlines into the classes that read it, leaving no
     * reference in their constant pools for the index to follow.
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
            ClassInfo info = read(new DataInputStream(in));
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

    static ClassInfo read(DataInputStream in) throws IOException {
        if (in.readInt() != CLASS_MAGIC) {
            return new ClassInfo(Set.of(), false);
        }
        in.readUnsignedShort(); // minor
        in.readUnsignedShort(); // major
        int count = in.readUnsignedShort();
        String[] utf8 = new String[count];
        Set<Integer> classIndexes = new HashSet<>();
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> {
                    int length = in.readUnsignedShort();
                    byte[] bytes = new byte[length];
                    in.readFully(bytes);
                    utf8[i] = new String(bytes, StandardCharsets.UTF_8);
                }
                case 3, 4 -> in.skipBytes(4);
                case 5, 6 -> {
                    in.skipBytes(8);
                    i++;
                }
                case 7 -> classIndexes.add(in.readUnsignedShort());
                case 8, 16, 19, 20 -> in.skipBytes(2);
                case 9, 10, 11, 12, 17, 18 -> in.skipBytes(4);
                case 15 -> in.skipBytes(3);
                default -> throw new IOException("Unknown constant pool tag " + tag);
            }
        }
        Set<String> names = new HashSet<>();
        for (int index : classIndexes) {
            String name = index < utf8.length ? utf8[index] : null;
            if (name != null && !name.startsWith("[")) {
                names.add(name.replace('/', '.'));
            }
        }
        for (String constant : utf8) {
            if (constant != null && constant.indexOf('L') >= 0 && constant.indexOf(';') > 0) {
                addDescriptorTypes(constant, names);
            }
        }
        boolean constants;
        try {
            constants = declaresConstants(in, utf8);
        } catch (IOException e) {
            // a class file cut short after its constant pool: the references are what matters
            constants = false;
        }
        return new ClassInfo(names, constants);
    }

    /**
     * Reads past the class header and interfaces to the fields, looking for a static final one with a
     * {@code ConstantValue} attribute.
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
                boolean staticFinal = (access & (ACC_STATIC | ACC_FINAL)) == (ACC_STATIC | ACC_FINAL);
                if (staticFinal && nameIndex < utf8.length && "ConstantValue".equals(utf8[nameIndex])) {
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
     * @param references The binary names the constant pool mentions: every {@code CONSTANT_Class} and every {@code L...;} type in a descriptor or signature
     * @param declaresConstants Whether a static final field carries a {@code ConstantValue}
     */
    record ClassInfo(Set<String> references, boolean declaresConstants) {
    }
}
