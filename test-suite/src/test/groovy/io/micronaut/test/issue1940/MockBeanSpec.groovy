package io.micronaut.test.issue1940


import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Issue
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Named
import java.util.function.Supplier

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

    void "test mock bean with named bean"() {
        expect:
        testService.greetFromSupplier() == 'bar'
    }

    @MockBean(TestApi.class)
    TestApi testApi() {
        Mock(TestApi.class)
    }

    @MockBean(named = "my-str-supplier")
    @Named("my-str-supplier")
    Supplier<String> supplier() {
        return () -> "bar";
    }
}
