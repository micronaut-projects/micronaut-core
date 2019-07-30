package io.micronaut.test.issue1940


import io.micronaut.test.annotation.MicronautTest
import io.micronaut.test.annotation.MockBean
import spock.lang.Issue
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@Issue('https://github.com/micronaut-projects/micronaut-test/issues/45')
class MockBeanSpec extends Specification {
    @Inject TestService testService
    @Inject TestApi testApi

    void "testOne"() {
        when:
        String result = testService.greeting("john")

        then:
        "Hello, John!" == result
        1 * testApi.greeting("john") >> "Hello, John!"
    }

    void "testTwo"() {
        when:
        String result = testService.greeting("jady")

        then:
        "Hello, Jady!" == result
        1 * testApi.greeting("jady") >> "Hello, Jady!"
    }

    @MockBean(TestApi.class)
    TestApi testApi() {
        Mock(TestApi.class)
    }
}
