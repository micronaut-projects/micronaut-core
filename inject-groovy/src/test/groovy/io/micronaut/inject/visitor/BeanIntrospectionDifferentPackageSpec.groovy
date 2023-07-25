package test

import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospection
import spock.lang.Issue
import spock.lang.Specification

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

// intentionally use the wrong package

class BeanIntrospectionDifferentPackageSpec extends Specification {
    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/9221")
    void "test still compiles"() {
        given:
        def introspection = BeanIntrospection.getIntrospection(WrongPackageTest)

        expect:
        introspection != null
    }


}

@MyStereotype
class WrongPackageTest {}

@Introspected
@Retention(RetentionPolicy.RUNTIME)
@interface MyStereotype {}
