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
package io.micronaut.context;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.conditions.MatchesAbsenceOfClassesCondition;
import io.micronaut.context.conditions.MatchesConfigurationCondition;
import io.micronaut.context.conditions.MatchesCurrentNotOsCondition;
import io.micronaut.context.conditions.MatchesCurrentOsCondition;
import io.micronaut.context.conditions.MatchesEnvironmentCondition;
import io.micronaut.context.conditions.MatchesMissingPropertyCondition;
import io.micronaut.context.conditions.MatchesNotEnvironmentCondition;
import io.micronaut.context.conditions.MatchesPresenceOfClassesCondition;
import io.micronaut.context.conditions.MatchesPresenceOfEntitiesCondition;
import io.micronaut.context.conditions.MatchesPresenceOfResourcesCondition;
import io.micronaut.context.conditions.MatchesPropertyCondition;
import io.micronaut.context.conditions.MatchesSdkCondition;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanDefinitionReference;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * Reads the descriptor that the processors write into the {@code META-INF/micronaut} entry of a bean definition,
 * in the format of {@code io.micronaut.inject.writer.BeanDefinitionDescriptor} in {@code micronaut-core-processor}:
 *
 * <pre>
 * int    magic    0x4D4E4244, "MNBD"
 * ushort version  1
 * int    length   the bytes that follow
 * int flags, utf beanType, utf[] exposedTypes, utf[] indexes, (utf, ubyte)[] annotations,
 * (utf, utf)[] repeatableContainers, utf[] nonBindingMembers, annotation[] qualifiers, condition[] preLoadConditions,
 * utf definitionName, int order, utf writer, ushort revision, annotation[] qualifierDefaults
 * </pre>
 *
 * <p>Reading never throws. An entry gives no data, and its definition is loaded as without descriptors, when it is
 * empty, is not a descriptor of this version, is shorter or longer than its header says, ends before the last field
 * this reader knows, names another definition than the entry does, names no exposed type, is of a revision this
 * reader does not know, or holds a kind of value or condition this reader does not know. Bytes after the last field
 * are ignored, as the format requires.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 5.3.0
 */
final class BeanDefinitionDescriptorReader {

    /**
     * The directory of the entries of the bean definitions.
     */
    static final String ENTRIES = "META-INF/micronaut/" + BeanDefinitionReference.class.getName() + "/";

    /**
     * Read when an entry is empty, or its content is not a descriptor this reader uses.
     */
    static final Object UNUSABLE = new Object();

    /**
     * Read when the class loader has no entry by the name.
     */
    static final Object MISSING = new Object();

    /**
     * The latest revision of what the fields answer that this reader knows, the {@code REVISION} of the writer: a
     * descriptor of a later revision answers in a way this runtime does not, so it is not used.
     */
    static final int REVISION = 1;

    private static final int MAGIC = 0x4D4E4244;
    private static final int VERSION = 1;
    private static final int HEADER_LENGTH = 10;
    /**
     * The longest payload read: a header that announces more is damaged, and is not given a buffer of that size.
     */
    private static final int MAX_LENGTH = 1 << 20;

    private static final int VALUE_STRING = 1;
    private static final int VALUE_BOOLEAN = 2;
    private static final int VALUE_BYTE = 3;
    private static final int VALUE_CHAR = 4;
    private static final int VALUE_SHORT = 5;
    private static final int VALUE_INT = 6;
    private static final int VALUE_LONG = 7;
    private static final int VALUE_FLOAT = 8;
    private static final int VALUE_DOUBLE = 9;
    private static final int VALUE_CLASS = 10;
    private static final int VALUE_ANNOTATION = 11;
    private static final int VALUE_ARRAY = 12;
    private static final int VALUE_WRAPPER_ARRAY = 13;
    private static final int VALUE_EMPTY_ARRAY = 14;

    private static final int CONDITION_CLASSES = 1;
    private static final int CONDITION_MISSING_CLASSES = 2;
    private static final int CONDITION_ENVIRONMENTS = 3;
    private static final int CONDITION_NOT_ENVIRONMENTS = 4;
    private static final int CONDITION_ENTITIES = 5;
    private static final int CONDITION_PROPERTY = 6;
    private static final int CONDITION_MISSING_PROPERTY = 7;
    private static final int CONDITION_CONFIGURATION = 8;
    private static final int CONDITION_SDK = 9;
    private static final int CONDITION_RESOURCES = 10;
    private static final int CONDITION_OS = 11;
    private static final int CONDITION_NOT_OS = 12;

    private static final String[] NO_STRINGS = new String[0];

    /**
     * What was read from the entries of one class loader, kept while the loader lives and the service entries cached
     * for it are not invalidated: the bootstrap context and the contexts of a test suite read each entry once.
     */
    private static final AtomicReference<@Nullable Cache> CACHE = new AtomicReference<>();

    private BeanDefinitionDescriptorReader() {
    }

    /**
     * What the entry of a definition holds.
     *
     * @param classLoader The class loader that finds the entry
     * @param name        The name of the entry, the name of the definition class
     * @return The data of its descriptor, {@link #UNUSABLE}, or {@link #MISSING}
     */
    static Object find(ClassLoader classLoader, String name) {
        Cache current = cache(classLoader);
        Object found = current.entries.get(name);
        if (found == null) {
            found = readEntry(classLoader, name, current);
            Object raced = current.entries.putIfAbsent(name, found);
            if (raced != null) {
                found = raced;
            }
        }
        return found;
    }

    /**
     * The cache of a class loader. The readers that ask first, on several threads at once, all get the cache one of
     * them published.
     */
    private static Cache cache(ClassLoader classLoader) {
        long generation = MicronautMetaServiceLoaderUtils.generation();
        while (true) {
            Cache current = CACHE.get();
            if (current != null && current.classLoader.get() == classLoader && current.generation == generation) {
                return current;
            }
            Cache candidate = new Cache(new WeakReference<>(classLoader), generation, new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
            if (CACHE.compareAndSet(current, candidate)) {
                return candidate;
            }
        }
    }

    private static Object readEntry(ClassLoader classLoader, String name, Cache current) {
        byte[] content;
        try (InputStream in = classLoader.getResourceAsStream(ENTRIES + name)) {
            if (in == null) {
                return MISSING;
            }
            content = readContent(in);
        } catch (IOException | RuntimeException e) {
            return UNUSABLE;
        }
        if (content == null) {
            return UNUSABLE;
        }
        BeanDefinitionDescriptorData data = read(content, name, current::intern);
        return data != null ? data : UNUSABLE;
    }

    /**
     * Reads the header of an entry and then exactly the payload it announces, into one array of that size, rather
     * than through the buffer of {@link InputStream#readAllBytes()}.
     *
     * @return The content, or {@code null} if it is not a descriptor of this version or not as long as it says
     */
    private static byte @Nullable [] readContent(InputStream in) throws IOException {
        byte[] header = new byte[HEADER_LENGTH];
        if (in.readNBytes(header, 0, HEADER_LENGTH) != HEADER_LENGTH) {
            return null;
        }
        ByteBuffer fields = ByteBuffer.wrap(header);
        int length = fields.getInt(6);
        if (fields.getInt(0) != MAGIC || Short.toUnsignedInt(fields.getShort(4)) != VERSION || length < 0 || length > MAX_LENGTH) {
            return null;
        }
        byte[] content = Arrays.copyOf(header, HEADER_LENGTH + length);
        if (in.readNBytes(content, HEADER_LENGTH, length) != length || in.read() != -1) {
            // shorter or longer than the header says
            return null;
        }
        return content;
    }

    /**
     * Reads a descriptor.
     *
     * @param content The content of the entry
     * @param name    The name of the entry, which the descriptor must give as the name of its definition
     * @param intern  Gives the one instance of each name that the references share
     * @return The data, or {@code null} if the content is not a descriptor this reader uses
     */
    @Nullable
    static BeanDefinitionDescriptorData read(byte[] content, String name, UnaryOperator<String> intern) {
        if (content.length < HEADER_LENGTH) {
            return null;
        }
        try (var in = new DataInputStream(new ByteArrayInputStream(content))) {
            if (in.readInt() != MAGIC || in.readUnsignedShort() != VERSION || in.readInt() != content.length - HEADER_LENGTH) {
                return null;
            }
            return readPayload(in, name, intern);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    @SuppressWarnings("java:S3776") // the fields in the order of the format
    @Nullable
    private static BeanDefinitionDescriptorData readPayload(DataInputStream in, String name, UnaryOperator<String> intern) throws IOException {
        int flags = in.readInt();
        String beanType = intern.apply(className(in.readUTF()));
        String[] exposedTypes = readTypeNames(in, intern);
        String[] indexes = readTypeNames(in, intern);
        int count = count(in);
        String[] annotationNames = new String[count];
        byte[] memberships = new byte[count];
        for (int i = 0; i < count; i++) {
            annotationNames[i] = intern.apply(in.readUTF());
            memberships[i] = (byte) in.readUnsignedByte();
            if (i > 0 && annotationNames[i - 1].compareTo(annotationNames[i]) >= 0) {
                // the writer sorts them, which the binary search relies on
                throw new IOException("Annotations out of order");
            }
        }
        count = count(in);
        Map<String, String> repeatableContainers = count == 0 ? Collections.emptyMap() : new LinkedHashMap<>(count);
        for (int i = 0; i < count; i++) {
            repeatableContainers.put(intern.apply(in.readUTF()), intern.apply(in.readUTF()));
        }
        String[] nonBindingMembers = readStrings(in, count(in), intern);
        count = count(in);
        AnnotationValue<?>[] qualifiers = new AnnotationValue<?>[count];
        for (int i = 0; i < count; i++) {
            qualifiers[i] = readAnnotation(in, intern);
        }
        count = count(in);
        Condition[] conditions = new Condition[count];
        for (int i = 0; i < count; i++) {
            conditions[i] = readCondition(in, intern);
        }
        String definitionName = in.readUTF();
        int order = in.readInt();
        String writer = intern.apply(in.readUTF());
        int revision = in.readUnsignedShort();
        count = count(in);
        AnnotationValue<?>[] qualifierDefaults = new AnnotationValue<?>[count];
        for (int i = 0; i < count; i++) {
            qualifierDefaults[i] = readAnnotation(in, intern);
        }
        if (!definitionName.equals(name) || exposedTypes.length == 0 || revision < 1 || revision > REVISION) {
            // content copied from another entry, a bean the index would find under no type, or a revision that
            // answers in a way this reader does not know
            return null;
        }
        return new BeanDefinitionDescriptorData(
            flags,
            beanType,
            exposedTypes,
            indexes,
            annotationNames,
            memberships,
            Collections.unmodifiableMap(repeatableContainers),
            nonBindingMembers,
            qualifiers,
            conditions,
            name,
            order,
            writer,
            revision,
            qualifierDefaults
        );
    }

    /**
     * The name {@link Class#getName()} gives a type the descriptor names by its class name with {@code []} for each
     * dimension of an array.
     *
     * @param name The name in the descriptor
     * @return The name of the class
     */
    static String className(String name) {
        if (!name.endsWith("[]")) {
            return name;
        }
        int dimensions = 0;
        String component = name;
        while (component.endsWith("[]")) {
            dimensions++;
            component = component.substring(0, component.length() - 2);
        }
        String descriptor = switch (component) {
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "short" -> "S";
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            case "double" -> "D";
            default -> "L" + component + ";";
        };
        return "[".repeat(dimensions) + descriptor;
    }

    private static int count(DataInputStream in) throws IOException {
        int count = in.readUnsignedShort();
        // every element takes a byte at least
        if (count > in.available()) {
            throw new IOException("Count beyond the content");
        }
        return count;
    }

    private static String[] readTypeNames(DataInputStream in, UnaryOperator<String> intern) throws IOException {
        int count = count(in);
        String[] names = new String[count];
        for (int i = 0; i < count; i++) {
            names[i] = intern.apply(className(in.readUTF()));
        }
        return names;
    }

    private static String[] readStrings(DataInputStream in, int count, UnaryOperator<String> intern) throws IOException {
        if (count == 0) {
            return NO_STRINGS;
        }
        String[] strings = new String[count];
        for (int i = 0; i < count; i++) {
            strings[i] = intern.apply(in.readUTF());
        }
        return strings;
    }

    private static @Nullable String readOptionalString(DataInputStream in) throws IOException {
        return in.readBoolean() ? in.readUTF() : null;
    }

    private static AnnotationClassValue<?>[] readClasses(DataInputStream in, int count, UnaryOperator<String> intern) throws IOException {
        AnnotationClassValue<?>[] classes = new AnnotationClassValue<?>[count];
        for (int i = 0; i < count; i++) {
            classes[i] = new AnnotationClassValue<>(intern.apply(in.readUTF()));
        }
        return classes;
    }

    private static AnnotationValue<?> readAnnotation(DataInputStream in, UnaryOperator<String> intern) throws IOException {
        String annotation = intern.apply(in.readUTF());
        int count = count(in);
        Map<CharSequence, Object> values = LinkedHashMap.newLinkedHashMap(count);
        for (int i = 0; i < count; i++) {
            values.put(intern.apply(in.readUTF()), readValue(in, intern));
        }
        return new AnnotationValue<>(annotation, values);
    }

    private static Object readValue(DataInputStream in, UnaryOperator<String> intern) throws IOException {
        int kind = in.readUnsignedByte();
        return switch (kind) {
            case VALUE_STRING -> in.readUTF();
            case VALUE_CLASS -> new AnnotationClassValue<>(intern.apply(in.readUTF()));
            case VALUE_ANNOTATION -> readAnnotation(in, intern);
            case VALUE_ARRAY -> readArray(in, intern);
            case VALUE_WRAPPER_ARRAY -> readWrapperArray(in);
            case VALUE_EMPTY_ARRAY -> new Object[0];
            default -> readPrimitive(in, kind);
        };
    }

    private static Object readPrimitive(DataInputStream in, int kind) throws IOException {
        return switch (kind) {
            case VALUE_BOOLEAN -> in.readBoolean();
            case VALUE_BYTE -> in.readByte();
            case VALUE_CHAR -> in.readChar();
            case VALUE_SHORT -> in.readShort();
            case VALUE_INT -> in.readInt();
            case VALUE_LONG -> in.readLong();
            case VALUE_FLOAT -> in.readFloat();
            case VALUE_DOUBLE -> in.readDouble();
            default -> throw new IOException("Unknown kind of value: " + kind);
        };
    }

    private static Object[] readWrapperArray(DataInputStream in) throws IOException {
        int kind = in.readUnsignedByte();
        int length = count(in);
        Object[] wrappers = switch (kind) {
            case VALUE_BOOLEAN -> new Boolean[length];
            case VALUE_BYTE -> new Byte[length];
            case VALUE_CHAR -> new Character[length];
            case VALUE_SHORT -> new Short[length];
            case VALUE_INT -> new Integer[length];
            case VALUE_LONG -> new Long[length];
            case VALUE_FLOAT -> new Float[length];
            case VALUE_DOUBLE -> new Double[length];
            default -> throw new IOException("Unknown kind of array: " + kind);
        };
        for (int i = 0; i < length; i++) {
            wrappers[i] = readPrimitive(in, kind);
        }
        return wrappers;
    }

    @SuppressWarnings("java:S3776") // one loop for each type of array
    private static Object readArray(DataInputStream in, UnaryOperator<String> intern) throws IOException {
        int kind = in.readUnsignedByte();
        int length = count(in);
        return switch (kind) {
            case VALUE_STRING -> {
                String[] strings = new String[length];
                for (int i = 0; i < length; i++) {
                    strings[i] = in.readUTF();
                }
                yield strings;
            }
            case VALUE_BOOLEAN -> {
                boolean[] booleans = new boolean[length];
                for (int i = 0; i < length; i++) {
                    booleans[i] = in.readBoolean();
                }
                yield booleans;
            }
            case VALUE_BYTE -> {
                byte[] bytes = new byte[length];
                in.readFully(bytes);
                yield bytes;
            }
            case VALUE_CHAR -> {
                char[] chars = new char[length];
                for (int i = 0; i < length; i++) {
                    chars[i] = in.readChar();
                }
                yield chars;
            }
            case VALUE_SHORT -> {
                short[] shorts = new short[length];
                for (int i = 0; i < length; i++) {
                    shorts[i] = in.readShort();
                }
                yield shorts;
            }
            case VALUE_INT -> {
                int[] ints = new int[length];
                for (int i = 0; i < length; i++) {
                    ints[i] = in.readInt();
                }
                yield ints;
            }
            case VALUE_LONG -> {
                long[] longs = new long[length];
                for (int i = 0; i < length; i++) {
                    longs[i] = in.readLong();
                }
                yield longs;
            }
            case VALUE_FLOAT -> {
                float[] floats = new float[length];
                for (int i = 0; i < length; i++) {
                    floats[i] = in.readFloat();
                }
                yield floats;
            }
            case VALUE_DOUBLE -> {
                double[] doubles = new double[length];
                for (int i = 0; i < length; i++) {
                    doubles[i] = in.readDouble();
                }
                yield doubles;
            }
            case VALUE_CLASS -> readClasses(in, length, intern);
            case VALUE_ANNOTATION -> {
                AnnotationValue<?>[] annotations = new AnnotationValue<?>[length];
                for (int i = 0; i < length; i++) {
                    annotations[i] = readAnnotation(in, intern);
                }
                yield annotations;
            }
            default -> throw new IOException("Unknown kind of array: " + kind);
        };
    }

    private static Condition readCondition(DataInputStream in, UnaryOperator<String> intern) throws IOException {
        int kind = in.readUnsignedByte();
        return switch (kind) {
            case CONDITION_CLASSES -> new MatchesPresenceOfClassesCondition(readClasses(in, count(in), intern));
            case CONDITION_MISSING_CLASSES -> new MatchesAbsenceOfClassesCondition(readClasses(in, count(in), intern));
            case CONDITION_ENVIRONMENTS -> new MatchesEnvironmentCondition(readStrings(in, count(in), intern));
            case CONDITION_NOT_ENVIRONMENTS -> new MatchesNotEnvironmentCondition(readStrings(in, count(in), intern));
            case CONDITION_ENTITIES -> new MatchesPresenceOfEntitiesCondition(readClasses(in, count(in), intern));
            case CONDITION_PROPERTY -> new MatchesPropertyCondition(
                in.readUTF(),
                readOptionalString(in),
                readOptionalString(in),
                constant(MatchesPropertyCondition.Condition.values(), in.readUTF())
            );
            case CONDITION_MISSING_PROPERTY -> new MatchesMissingPropertyCondition(in.readUTF());
            case CONDITION_CONFIGURATION -> new MatchesConfigurationCondition(in.readUTF(), readOptionalString(in));
            case CONDITION_SDK -> new MatchesSdkCondition(constant(Requires.Sdk.values(), in.readUTF()), in.readUTF());
            case CONDITION_RESOURCES -> new MatchesPresenceOfResourcesCondition(readStrings(in, count(in), intern));
            case CONDITION_OS -> new MatchesCurrentOsCondition(readFamilies(in));
            case CONDITION_NOT_OS -> new MatchesCurrentNotOsCondition(readFamilies(in));
            default -> throw new IOException("Unknown kind of condition: " + kind);
        };
    }

    private static Set<Requires.Family> readFamilies(DataInputStream in) throws IOException {
        Requires.Family[] families = new Requires.Family[count(in)];
        for (int i = 0; i < families.length; i++) {
            families[i] = constant(Requires.Family.values(), in.readUTF());
        }
        return families.length == 0 ? Set.of() : CollectionUtils.enumSet(families);
    }

    private static <E extends Enum<E>> E constant(E[] constants, String name) throws IOException {
        for (E constant : constants) {
            if (constant.name().equals(name)) {
                return constant;
            }
        }
        throw new IOException("Unknown constant: " + name);
    }

    /**
     * What was read from the entries of a class loader.
     *
     * @param classLoader The class loader, weakly
     * @param generation  The generation of the service entries cached by {@link MicronautMetaServiceLoaderUtils}
     * @param entries     What each entry holds, by its name: data, {@link #UNUSABLE} or {@link #MISSING}
     * @param names       The one instance of each name read
     */
    private record Cache(WeakReference<ClassLoader> classLoader,
                         long generation,
                         Map<String, Object> entries,
                         Map<String, String> names) {

        String intern(String name) {
            String interned = names.putIfAbsent(name, name);
            return interned != null ? interned : name;
        }
    }
}
