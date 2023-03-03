package io.micronaut.test.replacesbug

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.annotation.MockBean
import jakarta.inject.Singleton
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest
class MathInnerServiceSpec extends Specification {

    @Inject
    MathService mathService

    void "should compute use inner mock"() {
        when:
        def result = mathService.compute(10)

        then:
        result == 50
    }

    @MockBean(MathService)
    static class MyMock implements MathService {

        @Override
        Integer compute(Integer num) {
            return 50
        }
    }
}

interface MathService {

    Integer compute(Integer num);
}

@Singleton
class MathServiceImpl implements MathService {

    @Override
    Integer compute(Integer num) {
        return num * 4 // should never be called
    }
}
