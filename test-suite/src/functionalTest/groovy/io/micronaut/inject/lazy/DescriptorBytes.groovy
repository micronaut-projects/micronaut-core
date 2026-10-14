package io.micronaut.inject.lazy

import groovy.transform.CompileStatic

/**
 * Reads and writes the bytes of a bean definition descriptor as the format of
 * {@code io.micronaut.inject.writer.BeanDefinitionDescriptor} lays them out, without the classes of the processor,
 * so that a test can damage or edit a descriptor the processor wrote: change a flag or the bits of an annotation,
 * cut the fields appended after the conditions, or append bytes.
 *
 * <p>The sections a test does not edit are kept as they were read, byte for byte.</p>
 */
@CompileStatic
class DescriptorBytes {

    static final int MAGIC = 0x4D4E4244
    static final int HEADER_LENGTH = 10

    int magic = MAGIC
    int version = 1
    int flags
    String beanType
    List<String> exposedTypes = []
    List<String> indexes = []
    /** The membership bits of each annotation, in the order read. */
    Map<String, Integer> annotations = new LinkedHashMap<>()
    /** The repeatable containers, the non-binding members, the qualifiers and the conditions, as read. */
    byte[] middle = new byte[0]
    /** What follows the conditions: the fields appended to the first version of the format, and anything after. */
    byte[] appended = new byte[0]

    static DescriptorBytes read(byte[] content) {
        def input = new DataInputStream(new ByteArrayInputStream(content))
        def bytes = new DescriptorBytes()
        bytes.magic = input.readInt()
        bytes.version = input.readUnsignedShort()
        input.readInt()
        bytes.flags = input.readInt()
        bytes.beanType = input.readUTF()
        bytes.exposedTypes = readStrings(input)
        bytes.indexes = readStrings(input)
        int count = input.readUnsignedShort()
        for (int i = 0; i < count; i++) {
            bytes.annotations.put(input.readUTF(), input.readUnsignedByte())
        }
        int middleStart = content.length - input.available()
        count = input.readUnsignedShort()
        for (int i = 0; i < count; i++) {
            input.readUTF()
            input.readUTF()
        }
        readStrings(input)
        count = input.readUnsignedShort()
        for (int i = 0; i < count; i++) {
            skipAnnotation(input)
        }
        count = input.readUnsignedShort()
        for (int i = 0; i < count; i++) {
            skipCondition(input)
        }
        int middleEnd = content.length - input.available()
        bytes.middle = Arrays.copyOfRange(content, middleStart, middleEnd)
        bytes.appended = Arrays.copyOfRange(content, middleEnd, content.length)
        return bytes
    }

    /**
     * @return The fields appended after the conditions, if there are any: the definition name, the order, the
     * writer and the revision; the qualifier defaults that follow are kept as bytes
     */
    Map<String, Object> appendedFields() {
        if (appended.length == 0) {
            return null
        }
        def input = new DataInputStream(new ByteArrayInputStream(appended))
        def fields = [definitionName: input.readUTF(), order: input.readInt(), writer: input.readUTF(), revision: input.readUnsignedShort()] as Map<String, Object>
        fields.qualifierDefaults = Arrays.copyOfRange(appended, appended.length - input.available(), appended.length)
        return fields
    }

    /**
     * Replaces the appended fields, keeping the qualifier defaults there were, or with none.
     */
    DescriptorBytes withAppended(String definitionName, int order, String writer, int revision) {
        byte[] qualifierDefaults = appended.length == 0 ? [0, 0] as byte[] : (byte[]) appendedFields().qualifierDefaults
        def out = new ByteArrayOutputStream()
        def data = new DataOutputStream(out)
        data.writeUTF(definitionName)
        data.writeInt(order)
        data.writeUTF(writer)
        data.writeShort(revision)
        data.write(qualifierDefaults)
        data.flush()
        appended = out.toByteArray()
        return this
    }

    /**
     * Replaces the revision of the appended fields.
     */
    DescriptorBytes withRevision(int revision) {
        def fields = appendedFields()
        return withAppended((String) fields.definitionName, (int) fields.order, (String) fields.writer, revision)
    }

    byte[] toByteArray() {
        def payload = new ByteArrayOutputStream()
        def out = new DataOutputStream(payload)
        out.writeInt(flags)
        out.writeUTF(beanType)
        writeStrings(out, exposedTypes)
        writeStrings(out, indexes)
        out.writeShort(annotations.size())
        for (Map.Entry<String, Integer> annotation : new TreeMap<>(annotations).entrySet()) {
            out.writeUTF(annotation.key)
            out.writeByte(annotation.value)
        }
        out.write(middle)
        out.write(appended)
        out.flush()
        def content = new ByteArrayOutputStream()
        def header = new DataOutputStream(content)
        header.writeInt(magic)
        header.writeShort(version)
        header.writeInt(payload.size())
        header.write(payload.toByteArray())
        header.flush()
        return content.toByteArray()
    }

    /**
     * A descriptor of a bean with no annotation values and no conditions, for a class the processor did not write a
     * descriptor for.
     */
    static DescriptorBytes of(String beanType, Map<String, Integer> annotations) {
        def bytes = new DescriptorBytes(beanType: beanType, exposedTypes: [beanType], annotations: new LinkedHashMap<>(annotations))
        def out = new ByteArrayOutputStream()
        def data = new DataOutputStream(out)
        data.writeShort(0) // repeatable containers
        data.writeShort(0) // non-binding members
        data.writeShort(0) // qualifiers
        data.writeShort(0) // conditions
        data.flush()
        bytes.middle = out.toByteArray()
        return bytes
    }

    private static List<String> readStrings(DataInputStream input) {
        int count = input.readUnsignedShort()
        List<String> strings = []
        for (int i = 0; i < count; i++) {
            strings << input.readUTF()
        }
        return strings
    }

    private static void writeStrings(DataOutputStream out, List<String> strings) {
        out.writeShort(strings.size())
        for (String string : strings) {
            out.writeUTF(string)
        }
    }

    private static void skipAnnotation(DataInputStream input) {
        input.readUTF()
        int members = input.readUnsignedShort()
        for (int i = 0; i < members; i++) {
            input.readUTF()
            skipValue(input, input.readUnsignedByte())
        }
    }

    private static void skipValue(DataInputStream input, int kind) {
        switch (kind) {
            case 1:
            case 10:
                input.readUTF(); break
            case 2:
            case 3:
                input.readByte(); break
            case 4:
            case 5:
                input.readShort(); break
            case 6:
            case 8:
                input.readInt(); break
            case 7:
            case 9:
                input.readLong(); break
            case 11:
                skipAnnotation(input); break
            case 12:
            case 13:
                int elementKind = input.readUnsignedByte()
                int length = input.readUnsignedShort()
                for (int i = 0; i < length; i++) {
                    skipValue(input, elementKind)
                }
                break
            case 14:
                break
            default:
                throw new IllegalArgumentException("Unknown kind of value: " + kind)
        }
    }

    private static void skipCondition(DataInputStream input) {
        int kind = input.readUnsignedByte()
        switch (kind) {
            case 1: case 2: case 3: case 4: case 5: case 10: case 11: case 12:
                readStrings(input); break
            case 6:
                input.readUTF()
                if (input.readBoolean()) {
                    input.readUTF()
                }
                if (input.readBoolean()) {
                    input.readUTF()
                }
                input.readUTF()
                break
            case 7:
                input.readUTF(); break
            case 8:
                input.readUTF()
                if (input.readBoolean()) {
                    input.readUTF()
                }
                break
            case 9:
                input.readUTF()
                input.readUTF()
                break
            default:
                throw new IllegalArgumentException("Unknown kind of condition: " + kind)
        }
    }
}
