package io.micronaut.validation.routes

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class NullableParameterRuleSpec extends AbstractTypeElementSpec {

    void "test nullable parameter"() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import javax.annotation.Nullable;

@Controller("/foo")
class Foo {

    @Get("{/abc}")
    String abc(@Nullable String abc) {
        return "";
    }
}

""")

        then:
        noExceptionThrown()

        when:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import java.util.Optional;

@Controller("/foo")
class Foo {

    @Get("{/abc}")
    String abc(Optional<String> abc) {
        return "";
    }
}

""")

        then:
        noExceptionThrown()

        when:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@Controller("/foo")
class Foo {

    @Get("{/abc}")
    String abc(String abc) {
        return "";
    }
}

""")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("The uri variable [abc] is optional, but the corresponding method argument [java.lang.String abc] is not defined as an Optional or annotated with the javax.annotation.Nullable annotation.")
    }

    void "test query optional parameter"() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@Controller("/foo")
class Foo {

    @Get("/{?abc}")
    String abc(String abc) {
        return "";
    }
}

""")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("The uri variable [abc] is optional, but the corresponding method argument [java.lang.String abc] is not defined as an Optional or annotated with the javax.annotation.Nullable annotation.")
    }

    void "test ampersand optional parameter"() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@Controller("/foo")
class Foo {

    @Get("/{&abc}")
    String abc(String abc) {
        return "";
    }
}

""")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("The uri variable [abc] is optional, but the corresponding method argument [java.lang.String abc] is not defined as an Optional or annotated with the javax.annotation.Nullable annotation.")
    }

    void "test required argument doesn't fail compilation"() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;
import javax.annotation.Nullable;

@Controller("/foo")
class Foo {

    @Get("/{abc}")
    String abc(String abc) {
        return "";
    }
}

""")

        then:
        noExceptionThrown()
    }

}
