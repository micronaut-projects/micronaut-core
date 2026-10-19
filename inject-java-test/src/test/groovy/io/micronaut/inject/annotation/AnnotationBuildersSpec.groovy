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
package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationBuilder
import io.micronaut.core.annotation.AnnotationBuilderRegistry
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.AnnotationValueProvider
import io.micronaut.core.convert.ConversionService
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class AnnotationBuildersSpec extends AbstractTypeElementSpec {

    void setup() {
        RegisteringVisitor.REGISTERED.clear()
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new RegisterAnnotationsVisitor(), new RegisteringVisitor()]
    }

    private static final String SOURCE = '''
package test;

import io.micronaut.core.annotation.RegisterAnnotations;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@RegisterAnnotations({Holder.Sample.class, Holder.Tag.class})
public class Holder {

    public enum Shade { LIGHT, DARK }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Tag {
        String value() default "none";
        int weight() default 1;
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Sample {
        String name();
        int count() default 3;
        long big() default 4L;
        boolean flag() default true;
        double ratio() default 0.5;
        float fraction() default 1.5f;
        byte small() default 1;
        short tiny() default 2;
        char letter() default 'x';
        Class<?> type() default Object.class;
        Class<?>[] types() default {String.class};
        Shade shade() default Shade.DARK;
        Shade[] shades() default {Shade.LIGHT};
        String[] names() default {"a", "b"};
        int[] numbers() default {1, 2};
        Tag tag() default @Tag("default");
        Tag[] tags() default {@Tag("one")};
    }
}

@Holder.Sample(name = "jvm", count = 9)
class Annotated {
}
'''

    /**
     * The generated builder, loaded by its name: the class loader of a test cannot list the service entries.
     */
    private static AnnotationBuilder builder(ClassLoader classLoader, String holder, String annotation) {
        String name = holder + '$' + AnnotationBuilderRegistry.mangle(annotation) + AnnotationBuilderRegistry.BUILDER_SUFFIX
        return (AnnotationBuilder) classLoader.loadClass(name).getDeclaredConstructor().newInstance()
    }

    def "generates the builders and the implementations"() {
        given:
        def classLoader = buildClassLoader("test.Holder", SOURCE)
        def builder = builder(classLoader, "test.Holder", "test.Holder\$Sample")
        def sampleType = classLoader.loadClass("test.Holder\$Sample")
        def shade = classLoader.loadClass("test.Holder\$Shade")

        when:
        def sample = builder.build([name: "n", count: "7", shade: "LIGHT", type: "java.lang.Long",
                                    names: "p,q", numbers: [3, "4"], shades: ["DARK", "LIGHT"]])

        then:
        builder.annotationType() == sampleType
        sample.name() == "n"
        sample.count() == 7
        sample.big() == 4L
        sample.flag()
        sample.ratio() == 0.5d
        sample.fraction() == 1.5f
        sample.small() == (byte) 1
        sample.tiny() == (short) 2
        sample.letter() == (char) 'x'
        sample.type() == Long
        sample.types() == [String] as Class[]
        sample.shade().name() == "LIGHT"
        sample.shades()*.name() == ["DARK", "LIGHT"]
        sample.names() == ["p", "q"] as String[]
        sample.numbers() == [3, 4] as int[]
        shade.isInstance(sample.shade())
        sample.annotationType() == sampleType
    }

    def "arrays are copied and the annotation value has the members"() {
        given:
        def classLoader = buildClassLoader("test.Holder", SOURCE)
        def builder = builder(classLoader, "test.Holder", "test.Holder\$Sample")

        when:
        def sample = builder.build([name: "n"])
        sample.names()[0] = "changed"
        AnnotationValue<?> value = ((AnnotationValueProvider) sample).annotationValue()

        then:
        sample.names() == ["a", "b"] as String[]
        value.annotationName == "test.Holder\$Sample"
        value.stringValue("name").get() == "n"
        value.intValue("count").asInt == 3
        value.stringValues("names") == ["a", "b"] as String[]
        value.annotationClassValue("type").get().name == "java.lang.Object"
        value.stringValue("shade").get() == "DARK"
        value.toString() == sample.toString()
    }

    def "equals and hash code agree with the annotation of the JVM"() {
        given:
        def classLoader = buildClassLoader("test.Holder", SOURCE)
        def builder = builder(classLoader, "test.Holder", "test.Holder\$Sample")
        def sampleType = classLoader.loadClass("test.Holder\$Sample")
        def jvm = classLoader.loadClass("test.Annotated").getAnnotation(sampleType)

        when:
        def built = builder.build([name: "jvm", count: 9])
        def other = builder.build([name: "other"])

        then:
        built == jvm
        jvm == built
        built != other
        other != built
        built == built
        built != "not an annotation"
        built.hashCode() == jvm.hashCode()
        built.hashCode() == builder.build([name: "jvm", count: 9]).hashCode()
    }

    def "reads the members of an annotation as typed values"() {
        given:
        def classLoader = buildClassLoader("test.Holder", SOURCE)
        def builder = builder(classLoader, "test.Holder", "test.Holder\$Sample")
        def sampleType = classLoader.loadClass("test.Holder\$Sample")
        def tagType = classLoader.loadClass("test.Holder\$Tag")
        def shade = classLoader.loadClass("test.Holder\$Shade")
        def jvm = classLoader.loadClass("test.Annotated").getAnnotation(sampleType)
        def tag = AnnotationBuildersSpec.builder(classLoader, "test.Holder", "test.Holder\$Tag")

        when:
        // the nested annotations are given as instances: the registry does not see the builders of the test
        Map<String, Object> members = builder.toMap(builder.build([name: "n", shades: ["DARK"], tags: [tag.build([value: "t1"]), tag.build([value: "t2"])]]))
        Map<String, Object> fromJvm = builder.toMap(jvm)

        then:
        members.keySet() as List == ["name", "count", "big", "flag", "ratio", "fraction", "small", "tiny", "letter", "type",
                                     "types", "shade", "shades", "names", "numbers", "tag", "tags"]
        members.name == "n"
        members.count == 3
        members.count.class == Integer
        members.big.class == Long
        members.letter == (char) 'x'
        members.type == Object
        members.types.class == Class[]
        shade.isInstance(members.shade)
        members.shade.name() == "DARK"
        members.shades.class == shade.arrayType()
        members.shades*.name() == ["DARK"]
        members.names.class == String[]
        members.numbers.class == int[]
        tagType.isInstance(members.tag)
        members.tag.value() == "default"
        members.tags.class == tagType.arrayType()
        members.tags*.value() == ["t1", "t2"]
        fromJvm.name == "jvm"
        fromJvm.count == 9
        fromJvm.tag == members.tag
        builder.build(fromJvm) == jvm
    }

    def "reads the members of an annotation value with the defaults"() {
        given:
        def classLoader = buildClassLoader("test.Holder", SOURCE)
        def builder = builder(classLoader, "test.Holder", "test.Holder\$Sample")
        def value = AnnotationValue.builder("test.Holder\$Sample")
            .member("name", "v")
            .member("shade", "LIGHT")
            .member("tag", AnnotationValue.builder("test.Holder\$Tag").member("value", "nested").build())
            .build()

        when:
        Map<String, Object> members = builder.toMap(value, ConversionService.SHARED)

        then:
        members.name == "v"
        members.count == 3
        members.shade.name() == "LIGHT"
        members.tag.value() == "nested"
        members.tag.weight() == 1
        members.tags*.value() == ["one"]
    }

    def "the arrays of the members are copies"() {
        given:
        def classLoader = buildClassLoader("test.Holder", SOURCE)
        def builder = builder(classLoader, "test.Holder", "test.Holder\$Sample")
        def sample = builder.build([name: "n"])

        when:
        Map<String, Object> members = builder.toMap(sample)
        members.names[0] = "changed"
        members.numbers[0] = 42
        members.tags[0] = null
        Map<String, Object> again = builder.toMap(sample)

        then:
        sample.names() == ["a", "b"] as String[]
        sample.numbers() == [1, 2] as int[]
        sample.tags()*.value() == ["one"]
        again.names == ["a", "b"] as String[]
        !again.names.is(members.names)
    }

    def "the annotation types of the members get a builder"() {
        given:
        def classLoader = buildClassLoader("nested.Holder", '''
package nested;

import io.micronaut.core.annotation.RegisterAnnotations;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@RegisterAnnotations(Holder.Outer.class)
public class Holder {

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Leaf {
        int value() default 7;
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Inner {
        String name();
        Leaf[] leaves() default {@Leaf};
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Outer {
        Inner[] value();
    }
}
''')

        when:
        def outer = builder(classLoader, "nested.Holder", "nested.Holder\$Outer")
        def inner = builder(classLoader, "nested.Holder", "nested.Holder\$Inner")
        def leaf = builder(classLoader, "nested.Holder", "nested.Holder\$Leaf")
        def built = outer.build([value: [inner.build([name: "a", leaves: [leaf.build([value: 1]), leaf.build([value: 2])]]), inner.build([name: "b"])]])
        Map<String, Object> members = outer.toMap(built)

        then:
        built.value()*.name() == ["a", "b"]
        built.value()[0].leaves()*.value() == [1, 2]
        built.value()[1].leaves()*.value() == [7]
        members.value.class == inner.annotationType().arrayType()
        inner.toMap(members.value[0]).leaves.class == leaf.annotationType().arrayType()
        leaf.toMap(members.value[1].leaves()[0]) == [value: 7]
    }

    def "a visitor registers a builder of an annotation declared in the compilation"() {
        given:
        def classLoader = buildClassLoader("visitortest.First", '''
package visitortest;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@interface WantsBuilder {
}

@First.Custom("first")
public class First {
    @WantsBuilder
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Custom {
        String value() default "none";
        int weight() default 1;
    }
}

@First.Custom("second")
class Second {
    @First.Custom("field")
    String field;

    @First.Custom("method")
    void method() {
    }
}
''')

        when:
        def first = builder(classLoader, "visitortest.First", "visitortest.First\$Custom")
        def second = builder(classLoader, "visitortest.Second", "visitortest.First\$Custom")
        def jvm = classLoader.loadClass("visitortest.First").getAnnotation(first.annotationType())

        then: "a builder per owner, however many times its elements register it"
        RegisteringVisitor.REGISTERED.count { it == "visitortest.Second:visitortest.First\$Custom" } == 3
        first.build([value: "first"]) == jvm
        second.build([:]).value() == "none"
        first.toMap(jvm) == [value: "first", weight: 1]
        !hasBuilder(classLoader, "visitortest.First", "visitortest.WantsBuilder")
    }

    def "a visitor registers a builder of an annotation of a library and of its members"() {
        given:
        def classLoader = buildClassLoader("visitortest.Library", '''
package visitortest;

@jakarta.inject.Named("library")
@io.micronaut.context.annotation.Requirements({
    @io.micronaut.context.annotation.Requires(property = "a"),
    @io.micronaut.context.annotation.Requires(env = "b")
})
public class Library {
}
''')

        when:
        def named = builder(classLoader, "visitortest.Library", "jakarta.inject.Named")
        def requirements = builder(classLoader, "visitortest.Library", "io.micronaut.context.annotation.Requirements")
        def requires = builder(classLoader, "visitortest.Library", "io.micronaut.context.annotation.Requires")
        def jvm = classLoader.loadClass("visitortest.Library").getAnnotation(jakarta.inject.Named)
        def built = requirements.build([value: [requires.build([property: "a"]), requires.build([env: ["b"]])]])

        then:
        named.build([value: "library"]) == jvm
        named.toMap(jvm) == [value: "library"]
        requires.annotationType().name == "io.micronaut.context.annotation.Requires"
        built.value()*.property() == ["a", ""]
        requirements.toMap(built).value.class == requires.annotationType().arrayType()
    }

    def "an annotation that is not public cannot have a builder"() {
        when:
        buildClassLoader("visitortest.HiddenHolder", '''
package visitortest;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@interface WantsBuilder {
}

@WantsBuilder
@Retention(RetentionPolicy.RUNTIME)
@interface Hidden {
}

@Hidden
public class HiddenHolder {
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("The annotation [visitortest.Hidden] cannot have a builder: the type visitortest.Hidden is not public")
    }

    def "a public annotation with a member of a type that is not public cannot have a builder"() {
        when:
        buildClassLoader("visitortest.Exposed", '''
package visitortest;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@interface WantsBuilder {
}

enum Level { LOW }

@WantsBuilder
@Retention(RetentionPolicy.RUNTIME)
public @interface Exposed {
    Level level() default Level.LOW;
}

@Exposed
class Holder {
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("The annotation [visitortest.Exposed] cannot have a builder: the type visitortest.Level is not public")
    }

    def "a registration needs an originating element"() {
        when:
        buildClassLoader("noorigin.Holder", '''
package noorigin;

@jakarta.inject.Named("x")
public class Holder {
}
''')

        then:
        RegisteringVisitor.REGISTERED.contains("no origin:The builder of the annotation [jakarta.inject.Named] needs an originating element")
    }

    private static boolean hasBuilder(ClassLoader classLoader, String holder, String annotation) {
        try {
            builder(classLoader, holder, annotation)
            return true
        } catch (ClassNotFoundException ignored) {
            return false
        }
    }

    /**
     * Registers the builders of the annotations of the {@code visitortest} package whose type is annotated with
     * {@code WantsBuilder}, and of two annotations of libraries used there; every element registers twice, the
     * second time with the origin repeated.
     */
    static class RegisteringVisitor implements TypeElementVisitor<Object, Object> {

        static final List<String> REGISTERED = Collections.synchronizedList(new ArrayList<>())
        static final Set<String> LIBRARY = Set.of("jakarta.inject.Named", "io.micronaut.context.annotation.Requirements")

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.packageName == "noorigin") {
                try {
                    context.registerAnnotationBuilder(context.getClassElement("jakarta.inject.Named").get())
                } catch (IllegalArgumentException e) {
                    REGISTERED.add("no origin:" + e.message)
                }
                return
            }
            register(element, element, context)
        }

        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            register(element, element.owningType, context)
        }

        @Override
        void visitField(FieldElement element, VisitorContext context) {
            register(element, element.owningType, context)
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        private static void register(Element element, ClassElement owner, VisitorContext context) {
            if (owner.packageName != "visitortest") {
                return
            }
            for (String name : element.declaredAnnotationNames) {
                ClassElement annotationType = context.getClassElement(name).orElse(null)
                if (annotationType != null && (name in LIBRARY || annotationType.hasDeclaredAnnotation("visitortest.WantsBuilder"))) {
                    context.registerAnnotationBuilder(annotationType, element)
                    context.registerAnnotationBuilder(annotationType, element, element)
                    REGISTERED.add(owner.name + ":" + name)
                }
            }
        }
    }
}
