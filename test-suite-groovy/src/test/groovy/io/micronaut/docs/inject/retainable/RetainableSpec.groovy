package io.micronaut.docs.inject.retainable

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class RetainableSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    void "test each composed annotation reports the occurrence it introduced"() {
        given:
            def definition = context.getBeanDefinition(CodeValidator)

        expect:
            //tag::read[]
            def min = definition.getAnnotation(MinLength).getStereotypes().first()
            def max = definition.getAnnotation(MaxLength).getStereotypes().first()

            min.getAnnotationName() == Limit.name
            min.getValues() == [min: 3] // @Limit(min = 3)
            max.getValues() == [max: 9] // @Limit(max = 9)
            //end::read[]
    }

    void "test the flat index cannot attribute the occurrences"() {
        given:
            def definition = context.getBeanDefinition(CodeValidator)

        expect:
            definition.getAnnotationNamesByStereotype(Limit) as Set == [MinLength.name, MaxLength.name] as Set
    }
}
