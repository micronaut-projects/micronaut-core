package io.micronaut.context

import io.micronaut.context.annotation.Requires
import io.micronaut.context.condition.Condition
import io.micronaut.context.conditions.MatchesAbsenceOfClassesCondition
import io.micronaut.context.conditions.MatchesConfigurationCondition
import io.micronaut.context.conditions.MatchesCurrentNotOsCondition
import io.micronaut.context.conditions.MatchesCurrentOsCondition
import io.micronaut.context.conditions.MatchesEnvironmentCondition
import io.micronaut.context.conditions.MatchesMissingPropertyCondition
import io.micronaut.context.conditions.MatchesNotEnvironmentCondition
import io.micronaut.context.conditions.MatchesPresenceOfClassesCondition
import io.micronaut.context.conditions.MatchesPresenceOfEntitiesCondition
import io.micronaut.context.conditions.MatchesPresenceOfResourcesCondition
import io.micronaut.context.conditions.MatchesPropertyCondition
import io.micronaut.context.conditions.MatchesSdkCondition
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.io.service.MicronautMetaServiceLoaderUtils
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.writer.BeanDefinitionDescriptor
import spock.lang.Specification

import java.nio.ByteBuffer
import java.util.function.UnaryOperator

/**
 * The reader of the runtime against the writer of the processors: what one writes, the other reads, for every kind
 * of value and condition and for every descriptor this module's test classes have; and no damage to a descriptor
 * makes the reader throw.
 */
class BeanDefinitionDescriptorReaderSpec extends Specification {

    private static final String ENTRIES = 'META-INF/micronaut/' + BeanDefinitionReference.name + '/'
    private static final UnaryOperator<String> SAME = UnaryOperator.identity()

    private static final List<Condition> EVERY_CONDITION = [
        new MatchesPresenceOfClassesCondition([new AnnotationClassValue<>("a.Present"), new AnnotationClassValue<>(String), new AnnotationClassValue<>('[I')] as AnnotationClassValue[]),
        new MatchesAbsenceOfClassesCondition([new AnnotationClassValue<>("a.Absent")] as AnnotationClassValue[]),
        new MatchesEnvironmentCondition(["test", "dev"] as String[]),
        new MatchesNotEnvironmentCondition(["prod"] as String[]),
        new MatchesPresenceOfEntitiesCondition([new AnnotationClassValue<>("a.Entity")] as AnnotationClassValue[]),
        new MatchesPropertyCondition("a.property", null, null, MatchesPropertyCondition.Condition.CONTAINS),
        new MatchesPropertyCondition("a.property", "value", "default", MatchesPropertyCondition.Condition.EQUALS),
        new MatchesMissingPropertyCondition("a.missing"),
        new MatchesConfigurationCondition("a.configuration", "1.2.3"),
        new MatchesConfigurationCondition("a.configuration", null),
        new MatchesSdkCondition(Requires.Sdk.JAVA, "21"),
        new MatchesPresenceOfResourcesCondition(["classpath:a.txt"] as String[]),
        new MatchesCurrentOsCondition(EnumSet.of(Requires.Family.LINUX, Requires.Family.MAC_OS)),
        new MatchesCurrentNotOsCondition(EnumSet.of(Requires.Family.WINDOWS))
    ]

    void "the reader reads what the writer writes, for every kind of value and condition"() {
        given:
        Map<CharSequence, Object> values = [
            string     : "one",
            bool       : true,
            aByte      : (byte) 1,
            aChar      : (char) 'c',
            aShort     : (short) 2,
            anInt      : 3,
            aLong      : 4L,
            aFloat     : 5.5f,
            aDouble    : 6.5d,
            type       : new AnnotationClassValue<>("a.Type"),
            nested     : new AnnotationValue<>("a.Nested", [value: "n"] as Map<CharSequence, Object>),
            strings    : ["x", "y"] as String[],
            ints       : [1, 2] as int[],
            types      : [new AnnotationClassValue<>("a.One")] as AnnotationClassValue[],
            nesteds    : [new AnnotationValue<>("a.Nested")] as AnnotationValue[],
            wrappers   : [1, 2] as Integer[],
            empty      : new Object[0],
            '$nonBinding': ["comment"] as String[]
        ]
        def descriptor = new BeanDefinitionDescriptor(
            (1 << 4) | (1 << 10),
            "a.Bean[]",
            ["a.Bean", "a.Api", "int[]"],
            ["a.Indexed"],
            new TreeMap<String, Integer>(["a.Ann": 15, "a.Stereotype": 8]),
            new TreeMap<String, String>(["a.Repeatable": "a.Repeatables"]),
            ["comment"],
            [new AnnotationValue<>("a.Qualifier", values), new AnnotationValue<>("jakarta.inject.Named", [value: "one"] as Map<CharSequence, Object>)],
            EVERY_CONDITION,
            'a.$Bean$Definition',
            -7,
            "5.3.0",
            BeanDefinitionDescriptor.REVISION
        )

        when:
        BeanDefinitionDescriptorData data = BeanDefinitionDescriptorReader.read(descriptor.toByteArray(), 'a.$Bean$Definition', SAME)

        then: "the types are named as Class.getName() names them"
        data != null
        data.flags == ((1 << 4) | (1 << 10))
        data.beanTypeName == '[La.Bean;'
        data.exposedTypeNames as List == ['a.Api', 'a.Bean', '[I']
        data.indexNames as List == ['a.Indexed']
        data.annotationNames as List == ['a.Ann', 'a.Stereotype']
        data.membership('a.Ann') == 15
        data.membership('a.Stereotype') == 8
        data.membership('a.Unknown') == 0
        data.repeatableContainers == ['a.Repeatable': 'a.Repeatables']
        data.nonBindingMembers as List == ['comment']
        shape(data.qualifiers as List) == shape(descriptor.qualifiers())
        data.conditions as List == EVERY_CONDITION
        data.definitionName == 'a.$Bean$Definition'
        data.order == -7
        data.writer == "5.3.0"
        data.revision == BeanDefinitionDescriptor.REVISION

        and: "an entry of another name, or without an exposed type, is not used"
        BeanDefinitionDescriptorReader.read(descriptor.toByteArray(), 'a.$Other$Definition', SAME) == null
        BeanDefinitionDescriptorReader.read(copy(descriptor, []).toByteArray(), 'a.$Bean$Definition', SAME) == null
    }

    void "the reader agrees with the processor on every descriptor of the test classes of this module"() {
        given:
        ClassLoader loader = getClass().classLoader
        List<String> names = MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(loader, BeanDefinitionReference.name).toList()
        int compared = 0
        List<String> disagreeing = []

        when:
        for (String name : names) {
            byte[] content = content(loader, name)
            if (content == null || content.length == 0) {
                continue
            }
            def written = BeanDefinitionDescriptor.read(content)
            BeanDefinitionDescriptorData read = BeanDefinitionDescriptorReader.read(content, name, SAME)
            if (written == null || written.exposedTypes().isEmpty()) {
                if (read != null) {
                    disagreeing << name + ": read what the processor does not"
                }
                continue
            }
            compared++
            if (read == null || !agree(written, read)) {
                disagreeing << name
            }
        }

        then:
        compared > 100
        disagreeing.isEmpty()
    }

    void "no truncation and no changed byte of a real descriptor makes the reader throw"() {
        given: "the largest descriptors of the test classes"
        ClassLoader loader = getClass().classLoader
        List<byte[]> samples = MicronautMetaServiceLoaderUtils.findMicronautMetaServiceEntries(loader, BeanDefinitionReference.name)
            .collect { content(loader, it) }
            .findAll { it != null && it.length > 0 }
            .sort { -it.length }
            .take(20)
        int read = 0

        when:
        for (byte[] content : samples) {
            for (int length = 0; length < content.length; length++) {
                byte[] cut = Arrays.copyOf(content, length)
                BeanDefinitionDescriptorReader.read(cut, 'x', SAME)
                if (length >= 10) {
                    // with a header that agrees, so the payload is read up to where it ends
                    BeanDefinitionDescriptorReader.read(withLength(cut), 'x', SAME)
                }
                read += 2
            }
            for (int i = 10; i < content.length; i++) {
                for (int flip : [0x01, 0x80, 0xFF]) {
                    byte[] changed = content.clone()
                    changed[i] = (byte) (changed[i] ^ flip)
                    BeanDefinitionDescriptorReader.read(changed, 'x', SAME)
                    read++
                }
            }
        }

        then: "nothing was thrown"
        samples.size() == 20
        read > 10000
    }

    private static byte[] withLength(byte[] content) {
        def buffer = ByteBuffer.wrap(content.clone())
        buffer.putInt(6, content.length - 10)
        return buffer.array()
    }

    private static byte[] content(ClassLoader loader, String name) {
        URL entry = loader.getResource(ENTRIES + name)
        return entry == null ? null : entry.bytes
    }

    // the record of the processor is not visible from this package: it is only ever named dynamically
    private static Object copy(descriptor, List<String> exposedTypes) {
        new BeanDefinitionDescriptor(descriptor.flags(), descriptor.beanType(), exposedTypes, descriptor.indexes(), descriptor.annotations(),
            descriptor.repeatableContainers(), descriptor.nonBindingMembers(), descriptor.qualifiers(), descriptor.preLoadConditions(),
            descriptor.definitionName(), descriptor.order(), descriptor.writer(), descriptor.revision())
    }

    private static boolean agree(written, BeanDefinitionDescriptorData read) {
        read.flags == written.flags() &&
            read.beanTypeName == BeanDefinitionDescriptorReader.className(written.beanType()) &&
            read.exposedTypeNames as List == written.exposedTypes().collect { BeanDefinitionDescriptorReader.className(it) } &&
            read.indexNames as List == written.indexes().collect { BeanDefinitionDescriptorReader.className(it) } &&
            read.annotationNames as List == written.annotations().keySet().toList() &&
            written.annotations().every { name, bits -> read.membership(name) == bits } &&
            read.repeatableContainers == written.repeatableContainers() &&
            read.nonBindingMembers as List == written.nonBindingMembers() &&
            shape(read.qualifiers as List) == shape(written.qualifiers()) &&
            read.conditions as List == written.preLoadConditions() &&
            read.definitionName == written.definitionName() &&
            read.order == written.order() &&
            read.writer == written.writer() &&
            read.revision == written.revision()
    }

    /**
     * Annotation values by content, with each value of the type it has.
     */
    private static Object shape(Object value) {
        if (value instanceof List) {
            return value.collect { shape(it) }
        }
        if (value instanceof AnnotationValue) {
            return [annotation: value.annotationName, values: value.values.collectEntries { k, v -> [(k.toString()): shape(v)] }]
        }
        if (value instanceof AnnotationClassValue) {
            return [type: value.name]
        }
        if (value != null && value.getClass().isArray()) {
            return [array: value.getClass().componentType.name, elements: (0..<java.lang.reflect.Array.getLength(value)).collect { shape(java.lang.reflect.Array.get(value, it)) }]
        }
        return [type: value?.getClass()?.name, value: value]
    }
}
