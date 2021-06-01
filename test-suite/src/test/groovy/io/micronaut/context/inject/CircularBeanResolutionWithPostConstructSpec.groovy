package io.micronaut.context.inject

import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Issue
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest
class CircularBeanResolutionWithPostConstructSpec extends Specification {

    @Inject
    ExampleRepo exampleRepo

    @Inject
    EventManager eventManager

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/2140')
    def "A bean that resolves another bean of the same type within a @PostConstruct method doesn't cause a recursive update error"() {

        when: "calling the search"
        def result = exampleRepo.find()

        then: "should get a list with the sign up object"
        result == null
    }

    @MockBean(EventManagerImpl)
    EventManager eventManager() {
        Mock(EventManager) {
            0 * eventManager.register(*_)
        }
    }
}
