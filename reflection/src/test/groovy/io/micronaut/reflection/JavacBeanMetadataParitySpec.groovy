package io.micronaut.reflection

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.Argument

/**
 * The whole annotation metadata of one bean, as the processor generates it and as reflection builds it for the
 * very same compiled class. The bean is compiled here with javac and the processors, so the comparison is of
 * the two descriptions alone: no fixture compiled elsewhere, and no runtime extension the processor did not
 * see.
 *
 * <p>Every metadata is rendered whole - each annotation it carries with its values, its defaults and its
 * retained stereotype tree, the annotations it declares itself, and the stereotypes it carries - so that a
 * difference anywhere in the description fails here, not only in the members a test happens to ask about.</p>
 */
class JavacBeanMetadataParitySpec extends AbstractTypeElementSpec {

    void "the whole metadata of a bean is the same, generated or reflected"() {
        given:
        BeanIntrospection<?> generated = buildBeanIntrospection("test.Order", '''
package test;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Retainable;
import java.lang.annotation.*;
import java.util.List;
import java.util.Map;

@Introspected(members = true)
@Audited(scope = "orders", least = 7)
@Marked(name = "type", count = 2, kind = Kind.HIGH, nested = @Note(text = "on-type"))
class Order extends Base {

    @Marked(name = "field")
    @Marked(name = "second")
    private String reference;

    private List<@Sized(least = 5) String> lines;

    private Map<String, List<Integer>> totals;

    private final int quantity;

    Order(@Marked(name = "argument") int quantity) {
        this.quantity = quantity;
    }

    @Marked(name = "getter")
    public String getReference() {
        return reference;
    }

    @Marked(name = "setter")
    public void setReference(String reference) {
        this.reference = reference;
    }

    public List<String> getLines() {
        return lines;
    }

    public void setLines(List<String> lines) {
        this.lines = lines;
    }

    public Map<String, List<Integer>> getTotals() {
        return totals;
    }

    public void setTotals(Map<String, List<Integer>> totals) {
        this.totals = totals;
    }

    public int getQuantity() {
        return quantity;
    }

    @Executable
    @Marked(name = "method")
    @Audited(scope = "method")
    public String describe(@Marked(name = "parameter") String prefix, int count) {
        return prefix + reference + count;
    }
}

@Introspected
class Base {

    @Marked(name = "inherited")
    private String note;

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retainable
@interface Contract {
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE})
@Contract
@interface Limit {
    int min() default 0;
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.TYPE_USE})
@Limit(min = 3)
@interface Sized {
    @AliasFor(annotation = Limit.class, member = "min", applyDefault = true)
    int least() default 3;
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.METHOD})
@Sized(least = 1)
@interface Audited {
    String scope();

    @AliasFor(annotation = Sized.class, member = "least", applyDefault = true)
    int least() default 1;
}

@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Markers.class)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@interface Marked {
    String name();

    int count() default 1;

    Kind kind() default Kind.LOW;

    Class<?> type() default Object.class;

    Note nested() default @Note(text = "none");
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@interface Markers {
    Marked[] value();
}

@Retention(RetentionPolicy.RUNTIME)
@interface Note {
    String text() default "none";
}

enum Kind {
    LOW, HIGH
}
''')
        BeanIntrospection<?> reflective = ReflectionBeanIntrospection.of(generated.beanType)
        def differences = []
        def compare = { String what, Object expected, Object actual ->
            if (expected != actual) {
                differences << "$what\\n  generated:  $expected\\n  reflective: $actual"
            }
        }

        when: "the type, every property with its argument and its members, the constructor and every method"
        compare("the type", render(generated.annotationMetadata), render(reflective.annotationMetadata))
        generated.beanProperties.each { property ->
            def other = reflective.getRequiredProperty(property.name, property.type)
            compare("the property '$property.name'", render(property.annotationMetadata), render(other.annotationMetadata))
            compare("the argument of the property '$property.name'",
                    render(property.asArgument().annotationMetadata), render(other.asArgument().annotationMetadata))
            compare("the type arguments of the property '$property.name'",
                    renderAll(property.asArgument().typeParameters), renderAll(other.asArgument().typeParameters))
            property.members.each { member ->
                def otherMember = other.members.find { it.name == member.name && it.elementType == member.elementType }
                if (otherMember == null) {
                    differences << "the member '$member.name' of the property '$property.name' has no reflective counterpart"
                    return
                }
                compare("the member '$member.name' of the property '$property.name'",
                        render(member.annotationMetadata), render(otherMember.annotationMetadata))
                compare("the argument of the member '$member.name' of the property '$property.name'",
                        render(member.asArgument().annotationMetadata), render(otherMember.asArgument().annotationMetadata))
            }
        }
        compare("the constructor arguments",
                renderAll(generated.constructorArguments), renderAll(reflective.constructorArguments))
        generated.beanMethods.each { method ->
            def other = reflective.beanMethods.find { it.name == method.name && it.arguments.length == method.arguments.length }
            if (other == null) {
                differences << "the method '$method.name' has no reflective counterpart"
                return
            }
            compare("the method '$method.name'", render(method.annotationMetadata), render(other.annotationMetadata))
            compare("the arguments of the method '$method.name'", renderAll(method.arguments), renderAll(other.arguments))
            compare("the return type of the method '$method.name'",
                    render(method.returnType.asArgument().annotationMetadata),
                    render(other.returnType.asArgument().annotationMetadata))
        }

        then:
        differences.join("\\n\\n") == ""

        and: "the comparison is of a description that carries something"
        generated.beanProperties.size() == 5
        !generated.getRequiredProperty("reference", String).members.isEmpty()
        generated.annotationMetadata.hasStereotype("test.Contract")

        and: "a type-use annotation is described down to its own retained tree"
        generated.getRequiredProperty("lines", List).asArgument().typeParameters[0].annotationMetadata.intValue("test.Limit", "min").asInt == 5

        and: "an alias overriding a member two levels down carries the annotation it reaches into the tree, and reaches every level"
        tree(generated.annotationMetadata.getAnnotation("test.Audited")) ==
                tree(reflective.annotationMetadata.getAnnotation("test.Audited"))
        generated.annotationMetadata.getAnnotation("test.Audited").stereotypes*.annotationName ==
                ["test.Limit", "test.Sized"]
        generated.annotationMetadata.intValue("test.Limit", "min").asInt == 7
        reflective.annotationMetadata.intValue("test.Limit", "min").asInt == 7
    }

    /**
     * The one thing the two descriptions do not agree on: a member of a <em>type-use</em> annotation written
     * with the very value that is its default.
     *
     * <p>An annotation instance answers every one of its members, so a member equal to its default is dropped,
     * as the processors record only what the source writes. Which members the source wrote is read back from
     * the class file for an annotation written on a class, a field, a method or a parameter; the ones written
     * on a type are held in another attribute, which is not read, so such a member is dropped where the
     * processor keeps it. Writing a member with its own default is rare, and keeping every member instead
     * would add one to each of the many type-use annotations written bare.</p>
     */
    void "a type-use member written with its default is the one thing described differently"() {
        given:
        BeanIntrospection<?> generated = buildBeanIntrospection("test.Defaulted", '''
package test;

import io.micronaut.core.annotation.Introspected;
import java.lang.annotation.*;
import java.util.List;

@Introspected
class Defaulted {
    private List<@Bounded(least = 2) String> written;

    public List<String> getWritten() {
        return written;
    }

    public void setWritten(List<String> written) {
        this.written = written;
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE_USE})
@interface Bounded {
    int least() default 2;
}
''')
        BeanIntrospection<?> reflective = ReflectionBeanIntrospection.of(generated.beanType)
        def argument = { BeanIntrospection<?> introspection ->
            introspection.getRequiredProperty("written", List).asArgument().typeParameters[0].annotationMetadata
        }

        expect: "both carry the annotation"
        argument(generated).hasAnnotation("test.Bounded")
        argument(reflective).hasAnnotation("test.Bounded")

        and: "the processor kept the member the source wrote, reflection dropped it as equal to its default"
        argument(generated).getValues("test.Bounded").containsKey("least")
        argument(generated).intValue("test.Bounded", "least").asInt == 2
        !argument(reflective).getValues("test.Bounded").containsKey("least")
        argument(reflective).intValue("test.Bounded", "least").empty

        and: "a member the source wrote with another value is kept either way, so only the default is at stake"
        argument(reflective).hasAnnotation("test.Bounded") == argument(generated).hasAnnotation("test.Bounded")
    }

    /**
     * The arguments rendered in order, by what they carry rather than by their name: the sources are compiled
     * here without {@code -parameters}, so the class file holds no name for a parameter and reflection can
     * only call it {@code arg0}, where the processor read the name off the source.
     */
    private static List<String> renderAll(Argument<?>[] arguments) {
        return arguments.collect { render(it.annotationMetadata) }
    }

    /**
     * A metadata rendered whole: the annotations it carries, each with its values, its defaults and its
     * retained stereotype tree, the ones it declares itself and the stereotypes it carries, in a stable order.
     */
    private static String render(AnnotationMetadata metadata) {
        def lines = []
        metadata.annotationNames.toSorted().each { lines << "annotation " + tree(metadata.getAnnotation(it)) }
        metadata.declaredAnnotationNames.toSorted().each { lines << "declared " + tree(metadata.getDeclaredAnnotation(it)) }
        metadata.stereotypeAnnotationNames.toSorted().each { lines << "stereotype " + tree(metadata.getAnnotation(it)) }
        metadata.declaredStereotypeAnnotationNames.toSorted().each { lines << "declared-stereotype $it" }
        metadata.annotationNames.toSorted().each { lines << "defaults $it " + new TreeMap<>(metadata.getDefaultValues(it).collectEntries { k, v -> [(k.toString()): canonical(v)] }) }
        return lines.join("\n")
    }

    private static String tree(AnnotationValue<?> value) {
        if (value == null) {
            return "<absent>"
        }
        def stereotypes = value.stereotypes?.collect { tree(it) } ?: []
        return value.annotationName +
                new TreeMap<>(value.values.findAll { it.key.toString() != AnnotationUtil.STEREOTYPES_MEMBER }
                        .collectEntries { k, v -> [(k.toString()): canonical(v)] }) +
                (stereotypes.isEmpty() ? "" : stereotypes.toString())
    }

    private static Object canonical(Object value) {
        if (value instanceof java.lang.annotation.Annotation) {
            return canonical(AnnotationValue.of(value))
        }
        if (value instanceof AnnotationValue) {
            return tree(value)
        }
        if (value?.getClass()?.isArray()) {
            return (value as Object[]).toList().collect { canonical(it) }
        }
        if (value instanceof Collection) {
            return value.collect { canonical(it) }
        }
        if (value instanceof Map) {
            return new TreeMap<>(value.collectEntries { k, v -> [(k.toString()): canonical(v)] })
        }
        return String.valueOf(value)
    }
}
