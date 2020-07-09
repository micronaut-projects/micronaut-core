package io.micronaut.validation.executable

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import spock.lang.Shared

class NullablePrimitiveSpec extends AbstractTypeElementSpec {

    @Shared
    String errorMsg = "Primitive types can not be null"

    void 'test @Nullable is not allowed on primitive parameters'() {
        when:
        buildTypeElement("""
package test;

@javax.inject.Singleton
class Foo {
    void bar(@javax.annotation.Nullable boolean boolPrimitive) {
    }
}
""")
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains(errorMsg)


        when:
        buildTypeElement("""
package test;

import io.micronaut.http.annotation.*;

@Controller("/foo")
class Foo {

    @Get()
    String abc(@edu.umd.cs.findbugs.annotations.Nullable boolean boolPrimitive) {
        return "";
    }
}

""")
        then:
        ex = thrown(RuntimeException)
        ex.message.contains(errorMsg)
    }

    void 'test @Nullable is allowed on non-primitive parameters'() {
        when:
        buildTypeElement("""
package test;

@javax.inject.Singleton
class Foo {
    void bar(@javax.annotation.Nullable Boolean boolType) {
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

    @Get()
    String abc(@edu.umd.cs.findbugs.annotations.Nullable Boolean boolPrimitive) {
        return "";
    }
}

""")
        then:
        noExceptionThrown()
    }

}
