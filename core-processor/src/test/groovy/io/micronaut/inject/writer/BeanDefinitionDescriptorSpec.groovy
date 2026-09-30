package io.micronaut.inject.writer

import io.micronaut.context.annotation.Requires
import io.micronaut.context.condition.Condition
import io.micronaut.context.condition.ConditionContext
import io.micronaut.context.conditions.MatchesAbsenceOfClassesCondition
import io.micronaut.context.conditions.MatchesConditionUtils
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
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.expressions.EvaluatedExpressionReference
import spock.lang.Specification

import java.lang.annotation.RetentionPolicy
import java.nio.ByteBuffer

import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_CONTEXT_SCOPE
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_PARALLEL
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_POST_LOAD_CONDITIONS
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.FLAG_SINGLETON
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.MEMBERSHIP_ANNOTATION
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.MEMBERSHIP_DECLARED_ANNOTATION
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.MEMBERSHIP_DECLARED_STEREOTYPE
import static io.micronaut.inject.writer.BeanDefinitionDescriptor.MEMBERSHIP_STEREOTYPE

class BeanDefinitionDescriptorSpec extends Specification {

    private static final List<Condition> EVERY_CONDITION = [
        new MatchesPresenceOfClassesCondition([new AnnotationClassValue<>("a.Present"), new AnnotationClassValue<>(String)] as AnnotationClassValue[]),
        new MatchesAbsenceOfClassesCondition([new AnnotationClassValue<>("a.Absent")] as AnnotationClassValue[]),
        new MatchesEnvironmentCondition(["test", "dev"] as String[]),
        new MatchesNotEnvironmentCondition(["prod"] as String[]),
        new MatchesPresenceOfEntitiesCondition([new AnnotationClassValue<>("a.Entity")] as AnnotationClassValue[]),
        new MatchesPropertyCondition("a.property", null, null, MatchesPropertyCondition.Condition.CONTAINS),
        new MatchesPropertyCondition("a.property", "value", "default", MatchesPropertyCondition.Condition.EQUALS),
        new MatchesPropertyCondition("a.property", "other", null, MatchesPropertyCondition.Condition.NOT_EQUALS),
        new MatchesPropertyCondition("a.property", "v.*", "", MatchesPropertyCondition.Condition.PATTERN),
        new MatchesMissingPropertyCondition("a.missing"),
        new MatchesConfigurationCondition("a.configuration", "1.2.3"),
        new MatchesConfigurationCondition("a.configuration", null),
        new MatchesSdkCondition(Requires.Sdk.JAVA, "21"),
        new MatchesPresenceOfResourcesCondition(["classpath:a.txt", "file:b.txt"] as String[]),
        new MatchesCurrentOsCondition(EnumSet.of(Requires.Family.LINUX, Requires.Family.MAC_OS)),
        new MatchesCurrentNotOsCondition(EnumSet.of(Requires.Family.WINDOWS))
    ]

    void "a descriptor is read back as it was written"() {
        given:
        def descriptor = new BeanDefinitionDescriptor(
            FLAG_CONTEXT_SCOPE | FLAG_SINGLETON | FLAG_POST_LOAD_CONDITIONS,
            "a.Bean[]",
            ["a.Bean", "a.Api", "int"],
            ["a.Indexed", "a.Api"],
            new TreeMap<String, Integer>([
                "a.Ann"       : MEMBERSHIP_DECLARED_ANNOTATION | MEMBERSHIP_ANNOTATION | MEMBERSHIP_DECLARED_STEREOTYPE | MEMBERSHIP_STEREOTYPE,
                "a.Inherited" : MEMBERSHIP_ANNOTATION | MEMBERSHIP_STEREOTYPE,
                "a.Stereotype": MEMBERSHIP_STEREOTYPE
            ]),
            new TreeMap<String, String>(["a.Repeatable": "a.Repeatables"]),
            ["comment"],
            [
                new AnnotationValue<>("jakarta.inject.Named", [value: "one"] as Map<CharSequence, Object>),
                new AnnotationValue<>("a.Marker")
            ],
            EVERY_CONDITION
        )

        when:
        byte[] content = descriptor.toByteArray()
        def read = BeanDefinitionDescriptor.read(content)

        then:
        read == descriptor
        read.beanType() == "a.Bean[]"
        read.is(FLAG_CONTEXT_SCOPE)
        read.is(FLAG_SINGLETON)
        read.is(FLAG_POST_LOAD_CONDITIONS)
        !read.is(FLAG_PARALLEL)
        read.exposedTypes() == ["a.Api", "a.Bean", "int"]
        read.indexes() == ["a.Indexed", "a.Api"]
        read.has("a.Ann", MEMBERSHIP_DECLARED_ANNOTATION)
        read.has("a.Inherited", MEMBERSHIP_ANNOTATION)
        !read.has("a.Inherited", MEMBERSHIP_DECLARED_ANNOTATION)
        !read.has("a.Stereotype", MEMBERSHIP_ANNOTATION)
        read.has("a.Stereotype", MEMBERSHIP_STEREOTYPE)
        !read.has("a.Unknown", MEMBERSHIP_STEREOTYPE)
        read.repeatableContainers() == ["a.Repeatable": "a.Repeatables"]
        read.nonBindingMembers() == ["comment"]
        read.qualifiers()*.annotationName == ["jakarta.inject.Named", "a.Marker"]
        read.qualifiers()[0].stringValue().get() == "one"
        read.preLoadConditions() == EVERY_CONDITION

        and: "the header says what follows"
        def header = ByteBuffer.wrap(content)
        header.getInt() == BeanDefinitionDescriptor.MAGIC
        header.getShort() == (short) BeanDefinitionDescriptor.VERSION
        header.getInt() == content.length - 10
    }

    void "the conditions that are checked before a definition is loaded are all described"() {
        given: "a requirement with every member that gives a condition"
        def requirement = AnnotationValue.builder(Requires)
            .member("classes", new AnnotationClassValue<>("a.Present"))
            .member("missing", new AnnotationClassValue<>("a.Absent"))
            .member("env", "test")
            .member("notEnv", "prod")
            .member("entities", new AnnotationClassValue<>("a.Entity"))
            .member("property", "a.property")
            .member("missingProperty", "a.missing")
            .member("configuration", "a.configuration")
            .member("sdk", "JAVA")
            .member("version", "21")
            .member("resources", "classpath:a.txt")
            .member("os", "LINUX")
            .member("notOs", "WINDOWS")
            .member("beans", new AnnotationClassValue<>("a.Bean"))
            .member("missingBeans", new AnnotationClassValue<>("a.MissingBean"))
            .member("condition", new AnnotationClassValue<>("a.Condition"))
            .build()
        List<Condition> preConditions = []
        List<Condition> postConditions = []
        MatchesConditionUtils.createConditions(requirement, preConditions, postConditions)

        when:
        def read = BeanDefinitionDescriptor.read(describe([], preConditions).toByteArray())

        then: "a condition added to the ones checked before loading has to be given a form too"
        preConditions*.getClass().toSet().size() == 12
        read.preLoadConditions() == preConditions
    }

    void "the values of a qualifier are read back with the types they have"() {
        given:
        Map<CharSequence, Object> values = [
            string     : "text",
            bool       : true,
            aByte      : (byte) 1,
            aChar      : (char) 'c',
            aShort     : (short) 2,
            anInt      : 3,
            aLong      : 4L,
            aFloat     : 5.5f,
            aDouble    : 6.5d,
            type       : new AnnotationClassValue<>("a.Type"),
            annotation : new AnnotationValue<>("a.Nested", [value: 1] as Map<CharSequence, Object>),
            strings    : ["a", "b"] as String[],
            noStrings  : [] as String[],
            noObjects  : [] as Object[],
            booleans   : [true, false] as boolean[],
            bytes      : [1, 2] as byte[],
            chars      : ['a', 'b'] as char[],
            shorts     : [1, 2] as short[],
            ints       : [1, 2] as int[],
            longs      : [1L, 2L] as long[],
            floats     : [1.5f, 2.5f] as float[],
            doubles    : [1.5d, 2.5d] as double[],
            Booleans   : [true, false] as Boolean[],
            Bytes      : [1, 2] as Byte[],
            Characters : ['a', 'b'] as Character[],
            Shorts     : [1, 2] as Short[],
            Integers   : [1, 2] as Integer[],
            Longs      : [1L, 2L] as Long[],
            Floats     : [1.5f, 2.5f] as Float[],
            Doubles    : [1.5d, 2.5d] as Double[],
            types      : [new AnnotationClassValue<>("a.One"), new AnnotationClassValue<>("a.Two")] as AnnotationClassValue[],
            annotations: [new AnnotationValue<>("a.Nested", [value: ["x"] as String[]] as Map<CharSequence, Object>)] as AnnotationValue[]
        ]

        when:
        def read = BeanDefinitionDescriptor.read(describe([new AnnotationValue<>("a.Qualifier", values)], []).toByteArray())
        Map<CharSequence, Object> readValues = read.qualifiers()[0].values

        then:
        readValues.keySet().toList() == values.keySet().toList()
        values.each { member, value ->
            assert readValues[member].getClass() == value.getClass()
            assert readValues[member] == value
        }
    }

    void "a class is described by the name the class literal of the definition has: #name"() {
        given:
        def classValue = new AnnotationClassValue<>(name)

        when:
        def read = BeanDefinitionDescriptor.read(describe(
            [qualifier(classValue), qualifier([classValue] as AnnotationClassValue[])],
            [new MatchesPresenceOfClassesCondition([classValue] as AnnotationClassValue[])]
        ).toByteArray())

        then:
        read.qualifiers()[0].values.value == new AnnotationClassValue<>(described)
        read.qualifiers()[1].values.value == [new AnnotationClassValue<>(described)] as AnnotationClassValue[]
        read.preLoadConditions() == [new MatchesPresenceOfClassesCondition([new AnnotationClassValue<>(described)] as AnnotationClassValue[])]

        and: "which is the name the class has once it is loaded"
        loaded == null || loaded.name == described

        where:
        name                   | described              | loaded
        "a.Type"               | "a.Type"               | null
        "java.lang.String"     | "java.lang.String"     | String
        "[I"                   | "[I"                   | int[]
        "[[Ljava.lang.String;" | "[[Ljava.lang.String;" | String[][]
        "boolean[]"            | "[Z"                   | boolean[]
        "byte[]"               | "[B"                   | byte[]
        "char[]"               | "[C"                   | char[]
        "short[]"              | "[S"                   | short[]
        "int[]"                | "[I"                   | int[]
        "long[]"               | "[J"                   | long[]
        "float[]"              | "[F"                   | float[]
        "double[]"             | "[D"                   | double[]
        "int[][]"              | "[[I"                  | int[][]
        "java.lang.Object[]"   | "[Ljava.lang.Object;"  | Object[]
        "java.lang.String[][]" | "[[Ljava.lang.String;" | String[][]
        "a.Type\$Inner[]"      | "[La.Type\$Inner;"     | null
    }

    void "an enum constant is described by its name, as the generated metadata holds it"() {
        when:
        def read = BeanDefinitionDescriptor.read(describe([new AnnotationValue<>("a.Qualifier", [
            constant : RetentionPolicy.RUNTIME,
            constants: [RetentionPolicy.SOURCE, RetentionPolicy.CLASS] as RetentionPolicy[]
        ] as Map<CharSequence, Object>)], []).toByteArray())

        then:
        read.qualifiers()[0].values.constant == "RUNTIME"
        read.qualifiers()[0].values.constants == ["SOURCE", "CLASS"] as String[]
    }

    void "the reserved stereotypes member of an annotation is not one of its values"() {
        given:
        def qualifier = new AnnotationValue<>("a.Qualifier", [value: "x", (AnnotationUtil.STEREOTYPES_MEMBER): [new AnnotationValue<>("a.Stereotype")] as AnnotationValue[]] as Map<CharSequence, Object>)

        when:
        def read = BeanDefinitionDescriptor.read(describe([qualifier], []).toByteArray())

        then:
        read.qualifiers()[0].values == [value: "x"]
    }

    void "a definition that cannot be described faithfully has no descriptor: #reason"() {
        expect:
        descriptor.toByteArray() == null

        where:
        reason                                 | descriptor
        "an expression"                        | describe([qualifier(new EvaluatedExpressionReference("#{1}", "a.Qualifier", "value", "a.\$Expr"))], [])
        "a collection"                         | describe([qualifier(["a", "b"])], [])
        "an array of objects"                  | describe([qualifier(["a", 1] as Object[])], [])
        "an empty array of another type"       | describe([qualifier([] as BigInteger[])], [])
        "an array of an array by its name"     | describe([qualifier(new AnnotationClassValue<>("[I[]"))], [])
        "an array of numbers"                  | describe([qualifier([1G, 2G] as BigInteger[])], [])
        "an array of wrappers with no element" | describe([qualifier([1, null] as Integer[])], [])
        "a number that is not of a primitive"  | describe([qualifier(1G)], [])
        "an array of arrays"                   | describe([qualifier([["a"] as String[]] as String[][])], [])
        "an array with no element"             | describe([qualifier([null] as String[])], [])
        "a class"                              | describe([qualifier(String)], [])
        "an instance as a class value"         | describe([qualifier(new AnnotationClassValue<>(new Object()))], [])
        "an instance in an array"              | describe([qualifier([new AnnotationClassValue<>(new Object())] as AnnotationClassValue[])], [])
        "an expression in a nested annotation" | describe([qualifier(new AnnotationValue<>("a.Nested", [value: new EvaluatedExpressionReference("#{1}", "a.Nested", "value", "a.\$Expr")] as Map<CharSequence, Object>))], [])
        "a condition of another kind"          | describe([], [{ ConditionContext context -> true } as Condition])
        "an instance in a condition"           | describe([], [new MatchesPresenceOfClassesCondition([new AnnotationClassValue<>(new Object())] as AnnotationClassValue[])])
        "a string of more than 65535 bytes"    | describe([qualifier("x" * 70_000)], [])
        "more than 65535 elements"             | describe([qualifier(new int[70_000])], [])
    }

    void "an entry that is not a descriptor of this version is read as none: #reason"() {
        expect:
        BeanDefinitionDescriptor.read(content as byte[]) == null

        where:
        reason                         | content
        "empty"                        | new byte[0]
        "shorter than a header"        | valid()[0..8]
        "another magic"                | valid().tap { it[0] = (byte) 0 }
        "another version"              | valid().tap { it[5] = (byte) 2 }
        "cut"                          | valid()[0..-2]
        "added to"                     | valid().toList() + [(byte) 0]
        "two descriptors"              | valid().toList() + valid().toList()
        "a payload that ends early"    | withPayload(new byte[3])
        "an unknown condition"         | withPayload(payloadEnding(1, 99))
        "an unknown value"             | withPayload(payloadEndingWithQualifierValue(99))
        "an unknown array"             | withPayload(payloadEndingWithQualifierValue(12, 99, 0, 0))
        "an unknown array of wrappers" | withPayload(payloadEndingWithQualifierValue(13, 1, 0, 0))
        "a kind after the last one"    | withPayload(payloadEndingWithQualifierValue(15))
        "an array of arrays"           | withPayload(payloadEndingWithQualifierValue(12, 12, 0, 1, 1, 0, 0))
        "an unknown constant"          | withPayload(payloadEnding(1, 11, 0, 1, 0, 3, (int) 'Z', (int) 'O', (int) 'S'))
        "text"                         | "a.b.\$C\$Definition".bytes
    }

    void "what a later writer appends to the payload is ignored"() {
        given:
        def descriptor = describe([new AnnotationValue<>("a.Qualifier")], EVERY_CONDITION)
        byte[] content = descriptor.toByteArray()
        byte[] payload = (content[10..-1] + [(byte) 1, (byte) 2, (byte) 3, (byte) 4]) as byte[]

        expect:
        BeanDefinitionDescriptor.read(withPayload(payload)) == descriptor
    }

    void "the same definition always gives the same bytes"() {
        given: "collections whose order of iteration is not the order of the names"
        def one = new BeanDefinitionDescriptor(0, "a.Bean", ["c", "a", "b"], [], new TreeMap<String, Integer>([x: 1, y: 2]), new TreeMap<String, String>([r: "c", q: "d"]), [], [], [
            new MatchesCurrentOsCondition(new LinkedHashSet<>([Requires.Family.WINDOWS, Requires.Family.LINUX]))
        ])
        def other = new BeanDefinitionDescriptor(0, "a.Bean", ["b", "c", "a", "a"], [], new TreeMap<String, Integer>([y: 2, x: 1]), new TreeMap<String, String>([q: "d", r: "c"]), [], [], [
            new MatchesCurrentOsCondition(EnumSet.of(Requires.Family.LINUX, Requires.Family.WINDOWS))
        ])

        expect:
        one.toByteArray() == other.toByteArray()
        one.exposedTypes() == ["a", "b", "c"]
    }

    void "the #collection of a descriptor cannot be changed"() {
        given:
        def read = BeanDefinitionDescriptor.read(valid())

        when:
        change.call(read)

        then:
        thrown(UnsupportedOperationException)

        where:
        collection             | change
        "exposedTypes"         | { BeanDefinitionDescriptor d -> d.exposedTypes().add("x") }
        "indexes"              | { BeanDefinitionDescriptor d -> d.indexes().add("x") }
        "annotations"          | { BeanDefinitionDescriptor d -> d.annotations().put("x", 1) }
        "repeatableContainers" | { BeanDefinitionDescriptor d -> d.repeatableContainers().put("x", "y") }
        "nonBindingMembers"    | { BeanDefinitionDescriptor d -> d.nonBindingMembers().add("x") }
        "qualifiers"           | { BeanDefinitionDescriptor d -> d.qualifiers().clear() }
        "preLoadConditions"    | { BeanDefinitionDescriptor d -> d.preLoadConditions().clear() }
    }

    private static BeanDefinitionDescriptor describe(List<AnnotationValue<?>> qualifiers, List<Condition> conditions) {
        new BeanDefinitionDescriptor(0, "a.Bean", ["a.Bean"], [], new TreeMap<String, Integer>(), new TreeMap<String, String>(), [], qualifiers, conditions)
    }

    private static AnnotationValue<?> qualifier(Object value) {
        new AnnotationValue<>("a.Qualifier", [value: value] as Map<CharSequence, Object>)
    }

    private static byte[] valid() {
        describe([], []).toByteArray()
    }

    private static byte[] withPayload(byte[] payload) {
        def out = new ByteArrayOutputStream()
        new DataOutputStream(out).with {
            writeInt(BeanDefinitionDescriptor.MAGIC)
            writeShort(BeanDefinitionDescriptor.VERSION)
            writeInt(payload.length)
            write(payload)
        }
        out.toByteArray()
    }

    /**
     * A payload with no types, annotations or qualifiers, whose conditions are the bytes given.
     */
    private static byte[] payloadEnding(int conditionCount, int... conditions) {
        def out = new ByteArrayOutputStream()
        new DataOutputStream(out).with {
            writeInt(0)
            writeUTF("a.Bean")
            6.times { writeShort(0) }
            writeShort(conditionCount)
            conditions.each { writeByte(it) }
        }
        out.toByteArray()
    }

    /**
     * A payload with one qualifier, whose one member has the value the bytes given are.
     */
    private static byte[] payloadEndingWithQualifierValue(int... value) {
        def out = new ByteArrayOutputStream()
        new DataOutputStream(out).with {
            writeInt(0)
            writeUTF("a.Bean")
            5.times { writeShort(0) }
            writeShort(1)
            writeUTF("a.Qualifier")
            writeShort(1)
            writeUTF("value")
            value.each { writeByte(it) }
            writeShort(0)
        }
        out.toByteArray()
    }
}
