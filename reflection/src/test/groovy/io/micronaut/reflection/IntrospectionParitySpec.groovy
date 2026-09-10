package io.micronaut.reflection

import io.micronaut.core.beans.BeanIntrospection
import spock.lang.Specification

/**
 * A reflective description stands in for the one the processor generates, so a type that gains or loses its
 * generated introspection has to describe the same bean either way. These compare the two descriptions of one
 * type: {@link ParityBean} is a Groovy fixture, which this module's Groovy sources are processed as, so a
 * generated introspection exists for it alongside the reflective one.
 *
 * <p>Groovy adds a {@code metaClass} property and marks the accessors it synthesises with
 * {@code groovy.transform.Generated}. Where a comparison is affected by that it says so.</p>
 */
class IntrospectionParitySpec extends Specification {

    private static final String META_CLASS = "metaClass"

    private BeanIntrospection<ParityBean> generated = BeanIntrospection.getIntrospection(ParityBean)
    private BeanIntrospection<ParityBean> reflective = ReflectionBeanIntrospection.of(ParityBean)

    void "both descriptions report the same bean type and annotations"() {
        expect:
        reflective.beanType == generated.beanType
        reflective.annotationMetadata.annotationNames == generated.annotationMetadata.annotationNames
    }

    void "both describe the same properties"() {
        expect: "Groovy's metaClass aside, the properties are the same"
        names(reflective) == names(generated)
    }

    void "a property is the same type, and readable and writable the same way"() {
        expect:
        generated.beanProperties.every { property ->
            def other = reflective.getProperty(property.name).orElse(null)
            other != null &&
                other.type == property.type &&
                other.asArgument().typeParameters*.type == property.asArgument().typeParameters*.type &&
                other.readOnly == property.readOnly &&
                other.writeOnly == property.writeOnly
        }
    }

    void "a property reads and writes the same value through either description"() {
        given:
        def bean = new ParityBean()

        when:
        generated.getRequiredProperty("name", String).set(bean, "written")
        generated.getRequiredProperty("count", int).set(bean, 3)

        then: "the reflective description reads what the generated one wrote"
        reflective.getRequiredProperty("name", String).get(bean) == "written"
        reflective.getRequiredProperty("derived", String).get(bean) == "written3"

        when:
        reflective.getRequiredProperty("name", String).set(bean, "rewritten")

        then: "and the generated one reads what the reflective one wrote"
        generated.getRequiredProperty("name", String).get(bean) == "rewritten"
    }

    void "both select the same constructor"() {
        expect:
        reflective.constructorArguments*.type == generated.constructorArguments*.type
        reflective.constructorArguments*.name == generated.constructorArguments*.name
    }

    void "both instantiate the same bean"() {
        expect:
        reflective.instantiate().class == generated.instantiate().class
    }

    /**
     * The one place the two descriptions are meant to differ.
     *
     * <p>{@link BeanIntrospection#getBeanMethods()} is defined to report the {@code @Executable} methods alone,
     * which is what the processor generates. A type the processor never saw carries no {@code @Executable} to
     * go by, so a reflective description reports every public method instead: it is the only way a
     * specification reaches the methods of such a type, and
     * {@link SupplementedBeanIntrospection} completes a generated description from it for the same reason.</p>
     *
     * <p>Reporting the {@code @Executable} ones alone was tried: the Jakarta Validation TCK run over an archive
     * compiled without the annotation processor goes from no failures to 60, because method validation reaches
     * a method through this collection. So the divergence is deliberate.</p>
     */
    void "a reflective description reports every public method, where a generated one reports the executable ones"() {
        expect: "everything the generated description reports is reported reflectively too"
        reflective.beanMethods*.name.toSet().containsAll(generated.beanMethods*.name.toSet())

        and: "along with the accessors and the rest of the public methods, which it alone reports"
        reflective.beanMethods*.name.containsAll(["getName", "setName", "getDerived"])
        !generated.beanMethods*.name.contains("getName")

        and: "and the executable method itself is reported by both"
        reflective.beanMethods*.name.contains("describe")
        generated.beanMethods*.name.contains("describe")
    }

    /**
     * The accessors Groovy synthesises carry {@code groovy.transform.Generated}, which is a compiler artifact
     * like the {@code kotlin.Metadata} the annotation reader already leaves out.
     */
    void "a property carries the same annotations in both descriptions"() {
        expect:
        generated.beanProperties.every { property ->
            reflective.getRequiredProperty(property.name, property.type).annotationMetadata.annotationNames ==
                property.annotationMetadata.annotationNames
        }
    }

    private static List<String> names(BeanIntrospection<?> introspection) {
        introspection.beanProperties*.name.findAll { it != META_CLASS }.sort()
    }
}
