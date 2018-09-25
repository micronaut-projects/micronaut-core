package io.micronaut.validation.routes

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class MissingParameterRuleSpec extends AbstractTypeElementSpec {

    void "test missing parameter"() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

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

        when:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@Controller("/foo")
class Foo {

    @Get("/{abc}")
    String abc() {
        return "";
    }
}

""")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("The route declares a uri variable named [abc], but no corresponding method argument is present")
    }


    void "test missing parameter with expression"() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@Controller("/\${version}/foo")
class Foo {

    @Get("/{abc}")
    String abc(String abc) {
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

    @Get("/{abc}")
    String abc() {
        return "";
    }
}

""")

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("The route declares a uri variable named [abc], but no corresponding method argument is present")
    }

    void "test validation can be turned off with a system property"() {
        setup:
        System.setProperty("micronaut.route.validation", "false")

        when:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@Controller("/foo")
class Foo {

    @Get("/{abc}")
    String abc() {
        return "";
    }
}

""")

        then:
        noExceptionThrown()

        cleanup:
        System.setProperty("micronaut.route.validation", "")
    }

    void "test property name change with bindable"() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@Controller("/foo")
class Foo {

    @Get("/{abc}")
    String abc(@QueryValue("abc") String def) {
        return "";
    }
}

""")

        then:
        noExceptionThrown()
    }

    void "test body bean properties are added to parameters"() {
        when:
        buildTypeElement("""

package test;

import io.micronaut.http.annotation.*;

@Controller("/foo")
class Foo {

    @Post("/{abc}")
    String abc(@Body Book book) {
        return "";
    }
}

class Book {
    
    private String abc;
    
    public String getAbc() {
        return this.abc;
    }
    
    public void setAbc(String abc) {
        this.abc = abc;
    }
}

""")

        then:
        noExceptionThrown()
    }
}
