package io.micronaut.validation.properties

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class MixedCasePropertySpec extends AbstractTypeElementSpec {

    void "test wrong property name in @Property"() {
        when:
        buildTypeElement("""
package test;

import io.micronaut.context.annotation.Property;
import javax.inject.Singleton;

@Singleton
class MyService {

    @Property(name = "fooBar")
    private String property;
}

""")
        then:
        def e = thrown(RuntimeException)
        e.message.contains("Value 'fooBar' is not valid. Please use kebab-case notation.")
    }

    void "test wrong property name in @Value"() {
        when:
            buildTypeElement("""
package test;

import io.micronaut.context.annotation.Value;
import javax.inject.Singleton;

@Singleton
class MyService {

    @Value(\"\${fooBar:baz}\")
    private String property;
}

""")
        then:
            def e = thrown(RuntimeException)
            e.message.contains("Value 'fooBar' is not valid. Please use kebab-case notation.")
    }

    void "test wrong property name in @Controller"() {
        when:
            buildTypeElement("""
package test;

import io.micronaut.http.annotation.Controller;

@Controller(value = \"\${controllerPath}\")
class MyController {

}

""")
        then:
            def e = thrown(RuntimeException)
            e.message.contains("Value 'controllerPath' is not valid. Please use kebab-case notation.")
    }

    void "test wrong property name in @Controller with 'produces' property"() {
        when:
            buildTypeElement("""
package test;

import io.micronaut.http.annotation.Controller;

@Controller(value = \"\${controller-path}\", produces = {\"\${app.produces1}\", \"\${app.myWrongValue}\"})
class MyController {

}

""")
        then:
            def e = thrown(RuntimeException)
            e.message.contains("Value 'app.myWrongValue' is not valid. Please use kebab-case notation.")
    }

    void "test wrong property name in @Named in a constructor"() {
        when:
            buildTypeElement("""
package test;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import javax.inject.Named;

@Controller()
class VehicleController {

    private final Engine engine;

    public VehicleController(@Named(\"\${vehicleCylinders}\") Engine engine) {
        this.engine = engine;
    }
}

interface Engine {
    int getCylinders();
}

""")
        then:
            def e = thrown(RuntimeException)
            e.message.contains("Value 'vehicleCylinders' is not valid. Please use kebab-case notation.")
    }
}
