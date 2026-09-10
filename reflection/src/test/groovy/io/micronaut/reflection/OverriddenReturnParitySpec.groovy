package io.micronaut.reflection

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospector
import spock.lang.PendingFeature

/**
 * The annotations of a method that overrides a declaration, of its return value and of its parameters, described
 * either way, when the annotation is one whose target includes {@code TYPE_USE}.
 *
 * <p>Written before the return type of a method, such an annotation is both an annotation of the method and an
 * annotation of the type it returns (JLS 9.7.4): javac records it on the method and on the return type, and so
 * does the class file, of a Java and of a Groovy class alike. The processor generates a bean method whose return
 * value carries what the return type carries, and a reflective description reads the same back from
 * {@link java.lang.reflect.Method#getAnnotatedReturnType()}. There is no overlap to remove: an annotation
 * targeting the method alone is on the method alone, one targeting a type use alone is on the return value alone,
 * and one targeting both is on both.</p>
 *
 * <p>Each case states what the processors answer today before comparing the two, so that a description built
 * reflectively is held to what a generated one actually says rather than to what it is assumed to say, and so
 * that a change on the generated side is seen here rather than silently followed. The Java processor is the
 * reference; the one gap is on the Groovy side, and it is written down as pending.</p>
 */
class OverriddenReturnParitySpec extends AbstractTypeElementSpec {

    BeanIntrospection<OverriddenReturn> generated = BeanIntrospector.SHARED.getIntrospection(OverriddenReturn)
    BeanIntrospection<OverriddenReturn> reflective = ReflectionBeanIntrospection.of(OverriddenReturn)

    private static List<String> tags(AnnotationMetadata metadata) {
        return metadata.getAnnotationValuesByType(Tag)*.stringValue()*.orElse(null).toSorted()
    }

    private static AnnotationMetadata methodOf(BeanIntrospection<?> introspection, String name) {
        return introspection.getBeanMethods().find { it.name == name }.getAnnotationMetadata()
    }

    private static AnnotationMetadata returnOf(BeanIntrospection<?> introspection, String name) {
        return introspection.getBeanMethods().find { it.name == name }.getReturnType().getAnnotationMetadata()
    }

    private static AnnotationMetadata returnElementOf(BeanIntrospection<?> introspection, String name) {
        return introspection.getBeanMethods().find { it.name == name }.getReturnType().asArgument()
            .getFirstTypeVariable().get().getAnnotationMetadata()
    }

    private static AnnotationMetadata parameterOf(BeanIntrospection<?> introspection, String name) {
        return introspection.getBeanMethods().find { it.name == name }.getArguments()[0].getAnnotationMetadata()
    }

    private static AnnotationMetadata parameterElementOf(BeanIntrospection<?> introspection, String name) {
        return introspection.getBeanMethods().find { it.name == name }.getArguments()[0]
            .getFirstTypeVariable().get().getAnnotationMetadata()
    }

    // --- the Groovy processor, over the fixture next to this spec

    void "the method #method carries what the override declares, described either way"() {
        expect: "what a generated description answers today: the annotation of the override, and not the one the"
        and: "interface or the super class declares on the method it overrides"
        tags(methodOf(generated, method)) == ["from-impl"]

        and: "and a reflective description answers the same"
        tags(methodOf(reflective, method)) == tags(methodOf(generated, method))

        where:
        method << ["place", "describe"]
    }

    @PendingFeature(reason = "the Groovy processor reads the type annotations of a return type from its ClassNode alone, where the Groovy parser files an annotation written before the return type under the method, so the return value it generates misses an annotation that targets TYPE_USE too, one groovyc writes into the class file and javac's processor records")
    void "the return value of #method carries the annotation of the method too, its target including TYPE_USE, described either way"() {
        expect: "what the Java processor answers, and the class file groovyc writes: an annotation written before"
        and: "the return type with a target that includes TYPE_USE is on the method and on the value it returns"
        tags(returnOf(generated, method)) == ["from-impl"]

        and: "and a reflective description answers the same"
        tags(returnOf(reflective, method)) == tags(returnOf(generated, method))

        where:
        method << ["place", "describe"]
    }

    void "an annotation written on a nested type argument of the return type stays on that argument, described either way"() {
        expect: "what a generated description answers today: nothing on the return value itself,"
        tags(returnOf(generated, "nested")) == []

        and: "the annotation on the type argument it is written on"
        tags(returnElementOf(generated, "nested")) == ["nested"]

        and: "and a reflective description answers the same"
        tags(returnOf(reflective, "nested")) == tags(returnOf(generated, "nested"))
        tags(returnElementOf(reflective, "nested")) == tags(returnElementOf(generated, "nested"))
    }

    void "an annotation written on a parameter is on the parameter, described either way"() {
        expect: "what a generated description answers today: the annotation once on the parameter, although the"
        and: "class file carries it as an annotation of the parameter and as one of its type"
        tags(parameterOf(generated, "take")) == ["param"]

        and: "and a reflective description answers the same, not the annotation twice over"
        tags(parameterOf(reflective, "take")) == tags(parameterOf(generated, "take"))
    }

    // --- the Java processor, over the same declarations compiled in memory

    private BeanIntrospection<?> javac() {
        return buildBeanIntrospection("test.OverriddenReturn", '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.reflection.Tag;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

interface ReturnDeclarer {

    @Tag("from-interface")
    String place();
}

abstract class AbstractReturnDeclarer {

    @Tag("from-super")
    public abstract String describe();
}

@Introspected
class OverriddenReturn extends AbstractReturnDeclarer implements ReturnDeclarer {

    @Executable
    @Override
    @Tag("from-impl")
    public String place() {
        return "";
    }

    @Executable
    @Override
    @Tag("from-impl")
    public String describe() {
        return "";
    }

    @Executable
    public @TypeUseOnly("type-use-only") String typeUseOnly() {
        return "";
    }

    @Executable
    @MethodOnly("method-only")
    public String methodOnly() {
        return "";
    }

    @Executable
    public List<@Tag("nested") String> nested() {
        return null;
    }

    @Executable
    public void take(@Tag("param") String value) {
    }

    @Executable
    public void takeNested(List<@Tag("nested") String> value) {
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface TypeUseOnly {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface MethodOnly {
    String value();
}
''')
    }

    void "compiled by javac, the method #method and its return value both carry what the override declares, described either way"() {
        given:
        BeanIntrospection<?> generated = javac()
        BeanIntrospection<?> reflective = ReflectionBeanIntrospection.of(generated.beanType)

        expect: "what a generated description answers today: the annotation of the override on the method,"
        tags(methodOf(generated, method)) == ["from-impl"]

        and: "and, its target including TYPE_USE, on the value the method returns"
        tags(returnOf(generated, method)) == ["from-impl"]

        and: "and a reflective description answers the same"
        tags(methodOf(reflective, method)) == tags(methodOf(generated, method))
        tags(returnOf(reflective, method)) == tags(returnOf(generated, method))

        where:
        method << ["place", "describe"]
    }

    void "compiled by javac, an annotation goes where its target says, described either way"() {
        given:
        BeanIntrospection<?> generated = javac()
        BeanIntrospection<?> reflective = ReflectionBeanIntrospection.of(generated.beanType)

        expect: "what a generated description answers today: one targeting a type use alone is on the return value alone,"
        methodOf(generated, "typeUseOnly").stringValue("test.TypeUseOnly").isEmpty()
        returnOf(generated, "typeUseOnly").stringValue("test.TypeUseOnly").get() == "type-use-only"

        and: "one targeting the method alone is on the method alone"
        methodOf(generated, "methodOnly").stringValue("test.MethodOnly").get() == "method-only"
        returnOf(generated, "methodOnly").stringValue("test.MethodOnly").isEmpty()

        and: "and a reflective description answers the same"
        methodOf(reflective, "typeUseOnly").stringValue("test.TypeUseOnly") == methodOf(generated, "typeUseOnly").stringValue("test.TypeUseOnly")
        returnOf(reflective, "typeUseOnly").stringValue("test.TypeUseOnly") == returnOf(generated, "typeUseOnly").stringValue("test.TypeUseOnly")
        methodOf(reflective, "methodOnly").stringValue("test.MethodOnly") == methodOf(generated, "methodOnly").stringValue("test.MethodOnly")
        returnOf(reflective, "methodOnly").stringValue("test.MethodOnly") == returnOf(generated, "methodOnly").stringValue("test.MethodOnly")
    }

    void "compiled by javac, an annotation on a nested type argument stays on that argument, described either way"() {
        given:
        BeanIntrospection<?> generated = javac()
        BeanIntrospection<?> reflective = ReflectionBeanIntrospection.of(generated.beanType)

        expect: "what a generated description answers today: nothing on the return value or the parameter itself,"
        tags(returnOf(generated, "nested")) == []
        tags(parameterOf(generated, "takeNested")) == []

        and: "the annotation on the type argument it is written on"
        tags(returnElementOf(generated, "nested")) == ["nested"]
        tags(parameterElementOf(generated, "takeNested")) == ["nested"]

        and: "and a reflective description answers the same"
        tags(returnOf(reflective, "nested")) == tags(returnOf(generated, "nested"))
        tags(returnElementOf(reflective, "nested")) == tags(returnElementOf(generated, "nested"))
        tags(parameterOf(reflective, "takeNested")) == tags(parameterOf(generated, "takeNested"))
        tags(parameterElementOf(reflective, "takeNested")) == tags(parameterElementOf(generated, "takeNested"))
    }

    void "compiled by javac, an annotation written on a parameter is on the parameter once, described either way"() {
        given:
        BeanIntrospection<?> generated = javac()
        BeanIntrospection<?> reflective = ReflectionBeanIntrospection.of(generated.beanType)

        expect: "what a generated description answers today: the annotation once on the parameter, although the"
        and: "class file carries it as an annotation of the parameter and as one of its type"
        tags(parameterOf(generated, "take")) == ["param"]

        and: "and a reflective description answers the same, not the annotation twice over"
        tags(parameterOf(reflective, "take")) == tags(parameterOf(generated, "take"))
    }
}
