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
package io.micronaut.inject.writer;

import io.micronaut.context.BeanDefinitionDescriptorFormat;
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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import static io.micronaut.context.BeanDefinitionDescriptorFormat.CONDITION_CLASSES;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.CONDITION_CONFIGURATION;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.CONDITION_ENTITIES;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.CONDITION_ENVIRONMENTS;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.CONDITION_MISSING_CLASSES;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.CONDITION_MISSING_PROPERTY;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.CONDITION_NOT_ENVIRONMENTS;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.CONDITION_NOT_OS;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.CONDITION_OS;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.CONDITION_PROPERTY;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.CONDITION_RESOURCES;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.CONDITION_SDK;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.HEADER_LENGTH;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.VALUE_ANNOTATION;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.VALUE_ARRAY;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.VALUE_BOOLEAN;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.VALUE_BYTE;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.VALUE_CHAR;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.VALUE_CLASS;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.VALUE_DOUBLE;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.VALUE_EMPTY_ARRAY;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.VALUE_FLOAT;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.VALUE_INT;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.VALUE_LONG;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.VALUE_SHORT;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.VALUE_STRING;
import static io.micronaut.context.BeanDefinitionDescriptorFormat.VALUE_WRAPPER_ARRAY;

/**
 * What the reference of a bean definition answers before the definition is loaded, written as the content of its
 * {@code META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference/<definition name>} entry.
 *
 * <p>The entry is empty for a definition that has no descriptor: one compiled before descriptors existed, or one
 * this format cannot describe. A context reads the content only when lazy bean definitions are switched on
 * ({@code micronaut.bean-definitions.lazy}); otherwise the entry names alone are what the definitions are found
 * by.</p>
 *
 * <p>The content is a header and a payload, written as {@link java.io.DataOutput} writes them:</p>
 * <pre>
 * int    magic    0x4D4E4244, "MNBD"
 * ushort version  1
 * int    length   the bytes that follow; the entry is exactly 10 + length bytes
 *
 * int    flags
 * utf    beanType
 * ushort n, utf[n]                              exposedTypes, sorted
 * ushort n, utf[n]                              indexes
 * ushort n, (utf name, ubyte membership)[n]     annotations, sorted by name
 * ushort n, (utf annotation, utf container)[n]  repeatableContainers, sorted by annotation
 * ushort n, utf[n]                              nonBindingMembers
 * ushort n, annotation[n]                       qualifiers
 * ushort n, condition[n]                        preLoadConditions
 * utf    definitionName                         appended: the name of the definition class, as the entry is named
 * int    order                                  appended
 * utf    writer                                 appended: the Micronaut version of the processor, empty if unknown
 * ushort revision                               appended: {@link #REVISION}
 * ushort n, annotation[n]                       appended: qualifierDefaults
 *
 * annotation = utf name, ushort n, (utf member, value)[n]
 * value      = ubyte kind, then: 1 utf | 2 boolean | 3 byte | 4 char | 5 short | 6 int | 7 long | 8 float | 9 double
 *              | 10 utf (class name) | 11 annotation | 12 ubyte kind of the elements (1 to 11), ushort n, element[n]
 *              | 13 the same for the kinds 2 to 9, for an array of the wrappers of a primitive type
 *              | 14 nothing, for an array of objects that has no element
 * condition  = ubyte kind, then the components of the condition record
 * </pre>
 *
 * <p>Types are named in two ways, and a reader needs both. The bean type and the exposed types are named by the
 * name of the class, with {@code []} appended for each dimension of an array. A class that is the value of an
 * annotation member or of a condition is named as {@link Class#getName()} names it, {@code [I} or
 * {@code [Ljava.lang.String;} for an array, because the definition holds that value by a class literal.</p>
 *
 * <p>The qualifiers carry the members that are declared. The default values of their other members are in
 * {@code qualifierDefaults}, one annotation for each qualifier annotation whose defaults the definition registers when
 * it is loaded, holding those defaults as its values. A default that is an annotation with defaults of its own, which
 * the definition registers too, makes the definition undescribable.</p>
 *
 * <p>A reader uses a descriptor only when the magic and the version are the ones it knows and the entry is as long
 * as the header says. A later addition appends to the payload and keeps the version: the fields are read in order
 * and the bytes after the last one known are ignored. A change to what is already there takes a new version. The
 * fields from {@code definitionName} on were appended this way, before any release wrote a descriptor; a runtime
 * that reads descriptors requires them, and treats a descriptor without them as none. A runtime also treats a
 * descriptor of a revision later than the ones it knows as none.</p>
 *
 * <p>A change to what a field answers, such as a flag computed differently, another membership rule, or a condition
 * moved between the conditions checked before and after loading, keeps the format but must increase
 * {@link #REVISION}, so that a runtime can refuse the descriptors of a revision found to be wrong after its release
 * and load those definitions as it would without a descriptor.</p>
 *
 * <p>{@link #read(byte[])} gives {@code null} for an empty entry and for every entry it does not know, and a reader
 * that gets {@code null} loads the definition as a runtime without descriptors does. So does a reader that finds no
 * entry for a reference at all, as for the references a custom {@code BeanDefinitionsProvider} or the static
 * service loaders of Micronaut AOT give.</p>
 *
 * @param flags                The answers of the reference, a combination of the {@code FLAG_} constants
 * @param beanType             The name of the bean type, with {@code []} appended for each dimension of an array
 * @param exposedTypes         The names of the types the bean is exposed as
 * @param indexes              The names of the types the bean is indexed by
 * @param annotations          The names of the annotations and stereotypes of the bean, each with the combination
 *                             of the {@code MEMBERSHIP_} constants its annotation metadata answers for it
 * @param repeatableContainers The containers of the repeatable annotations the definition registers when it is
 *                             loaded, by the name of the annotation
 * @param nonBindingMembers    The members of the qualifiers that are not compared
 * @param qualifiers           The qualifier annotations declared on the bean, with the values of their members
 * @param preLoadConditions    The conditions that can be checked before the definition is loaded, in the order they
 *                             are checked
 * @param definitionName       The name of the definition class, which is the name of its entry
 * @param order                What {@link io.micronaut.core.order.Ordered#getOrder()} answers
 * @param writer               The Micronaut version of the processor that wrote the descriptor, empty if unknown
 * @param revision             The revision of what the fields answer, {@link #REVISION} when written
 * @param qualifierDefaults    The default values the definition registers for its qualifier annotations: an
 *                             annotation for each one that has defaults, with the defaults as its values
 * @author Álvaro Sánchez-Mariscal
 * @since 5.3.0
 */
@Internal
record BeanDefinitionDescriptor(int flags,
                                String beanType,
                                List<String> exposedTypes,
                                List<String> indexes,
                                SortedMap<String, Integer> annotations,
                                SortedMap<String, String> repeatableContainers,
                                List<String> nonBindingMembers,
                                List<AnnotationValue<?>> qualifiers,
                                List<Condition> preLoadConditions,
                                String definitionName,
                                int order,
                                String writer,
                                int revision,
                                List<AnnotationValue<?>> qualifierDefaults) {

    /**
     * The first four bytes of a descriptor: {@code MNBD}.
     */
    static final int MAGIC = BeanDefinitionDescriptorFormat.MAGIC;

    /**
     * The version of the format.
     */
    static final int VERSION = BeanDefinitionDescriptorFormat.VERSION;

    /**
     * The revision of what the fields answer. Increase it whenever a field answers differently for the same
     * definition, so that a runtime can tell the descriptors of a revision found to be wrong from the others.
     */
    static final int REVISION = 1;

    /**
     * {@link io.micronaut.inject.BeanDefinitionReference#isContextScope()}.
     */
    static final int FLAG_CONTEXT_SCOPE = BeanDefinitionDescriptorFormat.FLAG_CONTEXT_SCOPE;

    /**
     * {@link io.micronaut.inject.BeanDefinitionReference#isParallel()}.
     */
    static final int FLAG_PARALLEL = BeanDefinitionDescriptorFormat.FLAG_PARALLEL;

    /**
     * {@link io.micronaut.inject.BeanDefinitionReference#isProxiedBean()}.
     */
    static final int FLAG_PROXIED_BEAN = BeanDefinitionDescriptorFormat.FLAG_PROXIED_BEAN;

    /**
     * {@link io.micronaut.inject.BeanDefinitionReference#isProxyTarget()}.
     */
    static final int FLAG_PROXY_TARGET = BeanDefinitionDescriptorFormat.FLAG_PROXY_TARGET;

    /**
     * {@link io.micronaut.inject.BeanDefinitionReference#isSingleton()}.
     */
    static final int FLAG_SINGLETON = BeanDefinitionDescriptorFormat.FLAG_SINGLETON;

    /**
     * {@link io.micronaut.inject.BeanType#isPrimary()}.
     */
    static final int FLAG_PRIMARY = BeanDefinitionDescriptorFormat.FLAG_PRIMARY;

    /**
     * {@link io.micronaut.inject.BeanDefinitionReference#isConfigurationProperties()}.
     */
    static final int FLAG_CONFIGURATION_PROPERTIES = BeanDefinitionDescriptorFormat.FLAG_CONFIGURATION_PROPERTIES;

    /**
     * {@link io.micronaut.inject.BeanType#isContainerType()}.
     */
    static final int FLAG_CONTAINER_TYPE = BeanDefinitionDescriptorFormat.FLAG_CONTAINER_TYPE;

    /**
     * {@link io.micronaut.inject.BeanType#requiresMethodProcessing()}.
     */
    static final int FLAG_REQUIRES_METHOD_PROCESSING = BeanDefinitionDescriptorFormat.FLAG_REQUIRES_METHOD_PROCESSING;

    /**
     * The bean has conditions that can only be checked once the definition is loaded.
     */
    static final int FLAG_POST_LOAD_CONDITIONS = BeanDefinitionDescriptorFormat.FLAG_POST_LOAD_CONDITIONS;

    /**
     * The indexes are the ones the bean declares with {@link io.micronaut.core.annotation.Indexed}, which a definition
     * answers all or none of: none when one of the types does not load. Without the flag, the indexes are the ones of
     * the {@link io.micronaut.core.annotation.Indexes} of the annotation metadata, of a factory for instance, which
     * are read as class values.
     */
    static final int FLAG_DECLARED_INDEXES = BeanDefinitionDescriptorFormat.FLAG_DECLARED_INDEXES;

    /**
     * The annotation metadata of the bean has property expressions, so that a definition answers it through a
     * metadata that resolves them, which knows no repeatable annotation by name:
     * {@link io.micronaut.core.annotation.AnnotationMetadata#findRepeatableAnnotation(String)} answers empty.
     */
    static final int FLAG_PROPERTY_EXPRESSIONS = BeanDefinitionDescriptorFormat.FLAG_PROPERTY_EXPRESSIONS;

    /**
     * {@link io.micronaut.core.annotation.AnnotationMetadata#hasDeclaredAnnotation(String)}.
     */
    static final int MEMBERSHIP_DECLARED_ANNOTATION = BeanDefinitionDescriptorFormat.MEMBERSHIP_DECLARED_ANNOTATION;

    /**
     * {@link io.micronaut.core.annotation.AnnotationMetadata#hasAnnotation(String)}.
     */
    static final int MEMBERSHIP_ANNOTATION = BeanDefinitionDescriptorFormat.MEMBERSHIP_ANNOTATION;

    /**
     * {@link io.micronaut.core.annotation.AnnotationMetadata#hasDeclaredStereotype(String)}.
     */
    static final int MEMBERSHIP_DECLARED_STEREOTYPE = BeanDefinitionDescriptorFormat.MEMBERSHIP_DECLARED_STEREOTYPE;

    /**
     * {@link io.micronaut.core.annotation.AnnotationMetadata#hasStereotype(String)}.
     */
    static final int MEMBERSHIP_STEREOTYPE = BeanDefinitionDescriptorFormat.MEMBERSHIP_STEREOTYPE;

    private static final int MAX_COUNT = 0xFFFF;

    /**
     * Copies the collections: the exposed types are a set and are kept sorted, as the names of the maps are, so
     * that the same definition always gives the same bytes.
     */
    BeanDefinitionDescriptor {
        exposedTypes = List.copyOf(new TreeSet<>(exposedTypes));
        indexes = List.copyOf(indexes);
        annotations = Collections.unmodifiableSortedMap(new TreeMap<>(annotations));
        repeatableContainers = Collections.unmodifiableSortedMap(new TreeMap<>(repeatableContainers));
        nonBindingMembers = List.copyOf(nonBindingMembers);
        qualifiers = List.copyOf(qualifiers);
        preLoadConditions = List.copyOf(preLoadConditions);
        qualifierDefaults = List.copyOf(qualifierDefaults);
    }

    /**
     * Whether the reference answers true for a flag.
     *
     * @param flag One of the {@code FLAG_} constants
     * @return Whether the reference answers true for it
     */
    boolean is(int flag) {
        return (flags & flag) != 0;
    }

    /**
     * Whether the annotation metadata of the bean answers true for an annotation.
     *
     * @param annotation The name of an annotation
     * @param membership One of the {@code MEMBERSHIP_} constants
     * @return Whether the annotation metadata of the bean answers true for it
     */
    boolean has(String annotation, int membership) {
        Integer memberships = annotations.get(annotation);
        return memberships != null && (memberships & membership) != 0;
    }

    /**
     * Writes the descriptor.
     *
     * @return The content of the entry, or {@code null} if the format cannot describe the definition: the entry
     * is then left empty
     */
    byte @Nullable [] toByteArray() {
        var payload = new ByteArrayOutputStream(512);
        try (var out = new DataOutputStream(payload)) {
            writePayload(out);
        } catch (UTFDataFormatException | UndescribableException e) {
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ByteBuffer.allocate(HEADER_LENGTH + payload.size())
            .putInt(MAGIC)
            .putShort((short) VERSION)
            .putInt(payload.size())
            .put(payload.toByteArray())
            .array();
    }

    /**
     * Reads a descriptor.
     *
     * @param content The content of the entry
     * @return The descriptor, or {@code null} if the entry has none that this version reads: it is empty, it is
     * not a descriptor, it is of another version, or it has been cut or added to
     */
    static @Nullable BeanDefinitionDescriptor read(byte[] content) {
        if (content.length < HEADER_LENGTH) {
            return null;
        }
        try (var in = new DataInputStream(new ByteArrayInputStream(content))) {
            if (in.readInt() != MAGIC || in.readUnsignedShort() != VERSION || in.readInt() != content.length - HEADER_LENGTH) {
                return null;
            }
            return readPayload(in);
        } catch (IOException e) {
            return null;
        }
    }

    private void writePayload(DataOutputStream out) throws IOException {
        out.writeInt(flags);
        out.writeUTF(beanType);
        writeStrings(out, exposedTypes);
        writeStrings(out, indexes);
        writeCount(out, annotations.size());
        for (Map.Entry<String, Integer> annotation : annotations.entrySet()) {
            out.writeUTF(annotation.getKey());
            out.writeByte(annotation.getValue());
        }
        writeCount(out, repeatableContainers.size());
        for (Map.Entry<String, String> repeatable : repeatableContainers.entrySet()) {
            out.writeUTF(repeatable.getKey());
            out.writeUTF(repeatable.getValue());
        }
        writeStrings(out, nonBindingMembers);
        writeCount(out, qualifiers.size());
        for (AnnotationValue<?> qualifier : qualifiers) {
            writeAnnotation(out, qualifier);
        }
        writeCount(out, preLoadConditions.size());
        for (Condition condition : preLoadConditions) {
            writeCondition(out, condition);
        }
        out.writeUTF(definitionName);
        out.writeInt(order);
        out.writeUTF(writer);
        out.writeShort(revision);
        writeCount(out, qualifierDefaults.size());
        for (AnnotationValue<?> defaults : qualifierDefaults) {
            for (Object value : defaults.getValues().values()) {
                if (value instanceof AnnotationValue<?> annotation && CollectionUtils.isNotEmpty(annotation.getDefaultValues())) {
                    // the definition registers the defaults of such a value too, which these bytes do not give
                    throw new UndescribableException();
                }
            }
            writeAnnotation(out, defaults);
        }
    }

    private static BeanDefinitionDescriptor readPayload(DataInputStream in) throws IOException {
        int flags = in.readInt();
        String beanType = in.readUTF();
        List<String> exposedTypes = readStrings(in);
        List<String> indexes = readStrings(in);
        int count = in.readUnsignedShort();
        SortedMap<String, Integer> annotations = new TreeMap<>();
        for (int i = 0; i < count; i++) {
            annotations.put(in.readUTF(), in.readUnsignedByte());
        }
        count = in.readUnsignedShort();
        SortedMap<String, String> repeatableContainers = new TreeMap<>();
        for (int i = 0; i < count; i++) {
            repeatableContainers.put(in.readUTF(), in.readUTF());
        }
        List<String> nonBindingMembers = readStrings(in);
        count = in.readUnsignedShort();
        List<AnnotationValue<?>> qualifiers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            qualifiers.add(readAnnotation(in));
        }
        count = in.readUnsignedShort();
        List<Condition> preLoadConditions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            preLoadConditions.add(readCondition(in));
        }
        String definitionName = in.readUTF();
        int order = in.readInt();
        String writer = in.readUTF();
        int revision = in.readUnsignedShort();
        count = in.readUnsignedShort();
        List<AnnotationValue<?>> qualifierDefaults = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            qualifierDefaults.add(readAnnotation(in));
        }
        return new BeanDefinitionDescriptor(flags, beanType, exposedTypes, indexes, annotations, repeatableContainers, nonBindingMembers, qualifiers, preLoadConditions,
            definitionName, order, writer, revision, qualifierDefaults);
    }

    private static void writeCount(DataOutputStream out, int count) throws IOException {
        if (count > MAX_COUNT) {
            throw new UndescribableException();
        }
        out.writeShort(count);
    }

    private static void writeStrings(DataOutputStream out, Collection<String> strings) throws IOException {
        writeCount(out, strings.size());
        for (String string : strings) {
            out.writeUTF(string);
        }
    }

    private static void writeStrings(DataOutputStream out, String[] strings) throws IOException {
        writeCount(out, strings.length);
        for (String string : strings) {
            out.writeUTF(element(string));
        }
    }

    private static List<String> readStrings(DataInputStream in) throws IOException {
        return List.of(readStringArray(in, in.readUnsignedShort()));
    }

    private static String[] readStringArray(DataInputStream in, int length) throws IOException {
        String[] strings = new String[length];
        for (int i = 0; i < length; i++) {
            strings[i] = in.readUTF();
        }
        return strings;
    }

    private static void writeOptionalString(DataOutputStream out, @Nullable String string) throws IOException {
        out.writeBoolean(string != null);
        if (string != null) {
            out.writeUTF(string);
        }
    }

    private static @Nullable String readOptionalString(DataInputStream in) throws IOException {
        return in.readBoolean() ? in.readUTF() : null;
    }

    private static void writeClasses(DataOutputStream out, AnnotationClassValue<?>[] classes) throws IOException {
        writeCount(out, classes.length);
        for (AnnotationClassValue<?> classValue : classes) {
            writeClass(out, element(classValue));
        }
    }

    private static void writeClass(DataOutputStream out, AnnotationClassValue<?> classValue) throws IOException {
        if (classValue.isInstantiated()) {
            // an instance is a value of its own, which a name does not give back
            throw new UndescribableException();
        }
        out.writeUTF(classLiteralName(classValue.getName()));
    }

    /**
     * The name of the class of a class value as the definition holds it. The definition takes the value from a
     * class literal, so that the name is the one {@link Class#getName()} gives. A processor that names an array as
     * the source does, {@code int[]}, names it differently from the class it generates, which holds {@code [I}.
     *
     * @param name The name a processor gives the class
     * @return The name of the class
     */
    private static String classLiteralName(String name) {
        if (!name.endsWith("[]")) {
            return name;
        }
        int dimensions = 0;
        String component = name;
        while (component.endsWith("[]")) {
            dimensions++;
            component = component.substring(0, component.length() - 2);
        }
        if (component.isEmpty() || component.charAt(0) == '[') {
            // not a name a class literal is written from
            throw new UndescribableException();
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

    private static AnnotationClassValue<?>[] readClasses(DataInputStream in, int length) throws IOException {
        AnnotationClassValue<?>[] classes = new AnnotationClassValue<?>[length];
        for (int i = 0; i < length; i++) {
            classes[i] = new AnnotationClassValue<>(in.readUTF());
        }
        return classes;
    }

    private static void writeAnnotation(DataOutputStream out, AnnotationValue<?> annotation) throws IOException {
        out.writeUTF(annotation.getAnnotationName());
        Map<CharSequence, Object> values = annotation.getValues();
        writeCount(out, values.size());
        for (Map.Entry<CharSequence, Object> member : values.entrySet()) {
            out.writeUTF(member.getKey().toString());
            writeValue(out, member.getValue());
        }
    }

    private static AnnotationValue<?> readAnnotation(DataInputStream in) throws IOException {
        String name = in.readUTF();
        int count = in.readUnsignedShort();
        Map<CharSequence, Object> values = LinkedHashMap.newLinkedHashMap(count);
        for (int i = 0; i < count; i++) {
            values.put(in.readUTF(), readValue(in));
        }
        return new AnnotationValue<>(name, values);
    }

    @SuppressWarnings({"rawtypes", "java:S3776"}) // an array of a generic type is matched by its raw type; one case for each type of value
    private static void writeValue(DataOutputStream out, @Nullable Object value) throws IOException {
        if (value == null) {
            throw new UndescribableException();
        }
        switch (value) {
            case String string -> {
                out.writeByte(VALUE_STRING);
                out.writeUTF(string);
            }
            // the generated metadata holds an enum constant by its name
            case Enum<?> constant -> {
                out.writeByte(VALUE_STRING);
                out.writeUTF(constant.name());
            }
            case Boolean _, Byte _, Character _, Short _, Integer _, Long _, Float _, Double _ -> {
                int kind = primitiveKind(value.getClass());
                out.writeByte(kind);
                writePrimitive(out, kind, value);
            }
            case AnnotationClassValue<?> classValue -> {
                out.writeByte(VALUE_CLASS);
                writeClass(out, classValue);
            }
            case AnnotationValue<?> annotation -> {
                out.writeByte(VALUE_ANNOTATION);
                writeAnnotation(out, annotation);
            }
            case String[] strings -> {
                out.writeByte(VALUE_ARRAY);
                out.writeByte(VALUE_STRING);
                writeStrings(out, strings);
            }
            case Enum[] constants -> {
                writeArray(out, VALUE_STRING, constants.length);
                for (Enum<?> constant : constants) {
                    out.writeUTF(element(constant).name());
                }
            }
            case boolean[] booleans -> {
                writeArray(out, VALUE_BOOLEAN, booleans.length);
                for (boolean bool : booleans) {
                    out.writeBoolean(bool);
                }
            }
            case byte[] bytes -> {
                writeArray(out, VALUE_BYTE, bytes.length);
                out.write(bytes);
            }
            case char[] chars -> {
                writeArray(out, VALUE_CHAR, chars.length);
                for (char character : chars) {
                    out.writeChar(character);
                }
            }
            case short[] shorts -> {
                writeArray(out, VALUE_SHORT, shorts.length);
                for (short number : shorts) {
                    out.writeShort(number);
                }
            }
            case int[] ints -> {
                writeArray(out, VALUE_INT, ints.length);
                for (int number : ints) {
                    out.writeInt(number);
                }
            }
            case long[] longs -> {
                writeArray(out, VALUE_LONG, longs.length);
                for (long number : longs) {
                    out.writeLong(number);
                }
            }
            case float[] floats -> {
                writeArray(out, VALUE_FLOAT, floats.length);
                for (float number : floats) {
                    out.writeFloat(number);
                }
            }
            case double[] doubles -> {
                writeArray(out, VALUE_DOUBLE, doubles.length);
                for (double number : doubles) {
                    out.writeDouble(number);
                }
            }
            case AnnotationClassValue[] classes -> {
                out.writeByte(VALUE_ARRAY);
                out.writeByte(VALUE_CLASS);
                writeClasses(out, classes);
            }
            case AnnotationValue[] annotations -> {
                writeArray(out, VALUE_ANNOTATION, annotations.length);
                for (AnnotationValue<?> annotation : annotations) {
                    writeAnnotation(out, element(annotation));
                }
            }
            // the elements of an array of a primitive type are wrapped by the processors of some languages
            case Object[] wrappers when primitiveKind(wrappers.getClass().getComponentType()) != 0 -> {
                int kind = primitiveKind(wrappers.getClass().getComponentType());
                out.writeByte(VALUE_WRAPPER_ARRAY);
                out.writeByte(kind);
                writeCount(out, wrappers.length);
                for (Object wrapper : wrappers) {
                    writePrimitive(out, kind, element(wrapper));
                }
            }
            // an empty array of a type the processor does not know is one of objects, in the generated metadata too
            case Object[] objects when objects.length == 0 && objects.getClass() == Object[].class -> out.writeByte(VALUE_EMPTY_ARRAY);
            // an expression, a collection, an array of another type: the generated metadata holds a value that a
            // reader of these bytes could not give back
            default -> throw new UndescribableException();
        }
    }

    private static void writeArray(DataOutputStream out, int kind, int length) throws IOException {
        out.writeByte(VALUE_ARRAY);
        out.writeByte(kind);
        writeCount(out, length);
    }

    private static <T> T element(@Nullable T element) {
        if (element == null) {
            throw new UndescribableException();
        }
        return element;
    }

    /**
     * @return The kind of value of a primitive type, by its wrapper, or 0 if the type is not one
     */
    private static int primitiveKind(Class<?> wrapper) {
        if (wrapper == Boolean.class) {
            return VALUE_BOOLEAN;
        } else if (wrapper == Byte.class) {
            return VALUE_BYTE;
        } else if (wrapper == Character.class) {
            return VALUE_CHAR;
        } else if (wrapper == Short.class) {
            return VALUE_SHORT;
        } else if (wrapper == Integer.class) {
            return VALUE_INT;
        } else if (wrapper == Long.class) {
            return VALUE_LONG;
        } else if (wrapper == Float.class) {
            return VALUE_FLOAT;
        } else if (wrapper == Double.class) {
            return VALUE_DOUBLE;
        }
        return 0;
    }

    private static void writePrimitive(DataOutputStream out, int kind, Object value) throws IOException {
        switch (kind) {
            case VALUE_BOOLEAN -> out.writeBoolean((Boolean) value);
            case VALUE_BYTE -> out.writeByte((Byte) value);
            case VALUE_CHAR -> out.writeChar((Character) value);
            case VALUE_SHORT -> out.writeShort((Short) value);
            case VALUE_INT -> out.writeInt((Integer) value);
            case VALUE_LONG -> out.writeLong((Long) value);
            case VALUE_FLOAT -> out.writeFloat((Float) value);
            case VALUE_DOUBLE -> out.writeDouble((Double) value);
            default -> throw new UndescribableException();
        }
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

    private static Object readValue(DataInputStream in) throws IOException {
        int kind = in.readUnsignedByte();
        return switch (kind) {
            case VALUE_STRING -> in.readUTF();
            case VALUE_CLASS -> new AnnotationClassValue<>(in.readUTF());
            case VALUE_ANNOTATION -> readAnnotation(in);
            case VALUE_ARRAY -> readArray(in);
            case VALUE_WRAPPER_ARRAY -> readWrapperArray(in);
            case VALUE_EMPTY_ARRAY -> new Object[0];
            default -> readPrimitive(in, kind);
        };
    }

    private static Object[] readWrapperArray(DataInputStream in) throws IOException {
        int kind = in.readUnsignedByte();
        int length = in.readUnsignedShort();
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
    private static Object readArray(DataInputStream in) throws IOException {
        int kind = in.readUnsignedByte();
        int length = in.readUnsignedShort();
        return switch (kind) {
            case VALUE_STRING -> readStringArray(in, length);
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
            case VALUE_CLASS -> readClasses(in, length);
            case VALUE_ANNOTATION -> {
                AnnotationValue<?>[] annotations = new AnnotationValue<?>[length];
                for (int i = 0; i < length; i++) {
                    annotations[i] = readAnnotation(in);
                }
                yield annotations;
            }
            default -> throw new IOException("Unknown kind of array: " + kind);
        };
    }

    private static void writeCondition(DataOutputStream out, Condition condition) throws IOException {
        switch (condition) {
            case MatchesPresenceOfClassesCondition classes -> {
                out.writeByte(CONDITION_CLASSES);
                writeClasses(out, classes.classes());
            }
            case MatchesAbsenceOfClassesCondition classes -> {
                out.writeByte(CONDITION_MISSING_CLASSES);
                writeClasses(out, classes.classes());
            }
            case MatchesEnvironmentCondition environments -> {
                out.writeByte(CONDITION_ENVIRONMENTS);
                writeStrings(out, environments.env());
            }
            case MatchesNotEnvironmentCondition environments -> {
                out.writeByte(CONDITION_NOT_ENVIRONMENTS);
                writeStrings(out, environments.env());
            }
            case MatchesPresenceOfEntitiesCondition entities -> {
                out.writeByte(CONDITION_ENTITIES);
                writeClasses(out, entities.classes());
            }
            case MatchesPropertyCondition property -> {
                out.writeByte(CONDITION_PROPERTY);
                out.writeUTF(property.property());
                writeOptionalString(out, property.value());
                writeOptionalString(out, property.defaultValue());
                out.writeUTF(property.condition().name());
            }
            case MatchesMissingPropertyCondition property -> {
                out.writeByte(CONDITION_MISSING_PROPERTY);
                out.writeUTF(property.property());
            }
            case MatchesConfigurationCondition configuration -> {
                out.writeByte(CONDITION_CONFIGURATION);
                out.writeUTF(configuration.configurationName());
                writeOptionalString(out, configuration.minimumVersion());
            }
            case MatchesSdkCondition sdk -> {
                out.writeByte(CONDITION_SDK);
                out.writeUTF(sdk.sdk().name());
                out.writeUTF(sdk.version());
            }
            case MatchesPresenceOfResourcesCondition resources -> {
                out.writeByte(CONDITION_RESOURCES);
                writeStrings(out, resources.resourcePaths());
            }
            case MatchesCurrentOsCondition os -> {
                out.writeByte(CONDITION_OS);
                writeFamilies(out, os.os());
            }
            case MatchesCurrentNotOsCondition os -> {
                out.writeByte(CONDITION_NOT_OS);
                writeFamilies(out, os.notOs());
            }
            // a condition added after this version of the format
            default -> throw new UndescribableException();
        }
    }

    private static Condition readCondition(DataInputStream in) throws IOException {
        int kind = in.readUnsignedByte();
        return switch (kind) {
            case CONDITION_CLASSES -> new MatchesPresenceOfClassesCondition(readClasses(in, in.readUnsignedShort()));
            case CONDITION_MISSING_CLASSES -> new MatchesAbsenceOfClassesCondition(readClasses(in, in.readUnsignedShort()));
            case CONDITION_ENVIRONMENTS -> new MatchesEnvironmentCondition(readStringArray(in, in.readUnsignedShort()));
            case CONDITION_NOT_ENVIRONMENTS -> new MatchesNotEnvironmentCondition(readStringArray(in, in.readUnsignedShort()));
            case CONDITION_ENTITIES -> new MatchesPresenceOfEntitiesCondition(readClasses(in, in.readUnsignedShort()));
            case CONDITION_PROPERTY -> new MatchesPropertyCondition(
                in.readUTF(),
                readOptionalString(in),
                readOptionalString(in),
                constant(MatchesPropertyCondition.Condition.values(), in.readUTF())
            );
            case CONDITION_MISSING_PROPERTY -> new MatchesMissingPropertyCondition(in.readUTF());
            case CONDITION_CONFIGURATION -> new MatchesConfigurationCondition(in.readUTF(), readOptionalString(in));
            case CONDITION_SDK -> new MatchesSdkCondition(constant(Requires.Sdk.values(), in.readUTF()), in.readUTF());
            case CONDITION_RESOURCES -> new MatchesPresenceOfResourcesCondition(readStringArray(in, in.readUnsignedShort()));
            case CONDITION_OS -> new MatchesCurrentOsCondition(readFamilies(in));
            case CONDITION_NOT_OS -> new MatchesCurrentNotOsCondition(readFamilies(in));
            default -> throw new IOException("Unknown kind of condition: " + kind);
        };
    }

    private static void writeFamilies(DataOutputStream out, Set<Requires.Family> families) throws IOException {
        // in the order of the constants, whatever the set is
        var sorted = new TreeSet<>(families);
        writeCount(out, sorted.size());
        for (Requires.Family family : sorted) {
            out.writeUTF(family.name());
        }
    }

    private static Set<Requires.Family> readFamilies(DataInputStream in) throws IOException {
        Requires.Family[] families = new Requires.Family[in.readUnsignedShort()];
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
     * Thrown for what the format has no faithful form for.
     */
    private static final class UndescribableException extends RuntimeException {

        UndescribableException() {
            super(null, null, false, false);
        }
    }
}
