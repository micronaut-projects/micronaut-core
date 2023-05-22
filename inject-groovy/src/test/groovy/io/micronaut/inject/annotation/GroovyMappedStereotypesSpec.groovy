package io.micronaut.inject.annotation

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.Introspected
import io.micronaut.inject.visitor.VisitorContext
import spock.lang.Issue

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

class GroovyMappedStereotypesSpec extends AbstractBeanDefinitionSpec {
    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/9221')
    void "test groovy mapped stereotypes include the right annotation metadata"() {
        given:
        def introspection = buildBeanIntrospection('mappedstereotype.Test', '''
package mappedstereotype

import io.micronaut.inject.annotation.Embeddable

@Embeddable
class Test {}
''')
        expect:
        introspection.getAnnotationMetadata().@allStereotypes[Introspected.name] != null
        introspection.getAnnotationMetadata().@allStereotypes[Introspected.name].isEmpty() // annotation defaults are not there
        introspection.hasStereotype(Introspected)
        introspection.stringValue(Introspected, "targetPackage").isEmpty() // annotation defaults are not there
        introspection.booleanValue(TestSerializable).orElse(false)
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Introspected
@interface Embeddable {
}

@Retention(RetentionPolicy.RUNTIME)
@Introspected
@interface TestSerializable {
    boolean value() default false
}

class EmbeddableMapper implements TypedAnnotationMapper<Embeddable> {

    @Override
    List<AnnotationValue<?>> map(AnnotationValue<Embeddable> annotation, VisitorContext visitorContext) {
        [AnnotationValue.builder(TestSerializable).value(true).build()]
    }

    @Override
    Class<Embeddable> annotationType() {
        return Embeddable
    }
}
