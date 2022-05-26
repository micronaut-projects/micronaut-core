/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.validation.properties

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import spock.lang.Ignore

@Ignore("Mixed case properties are now allowed")
class MixedCasePropertySpec extends AbstractTypeElementSpec {

    void "test wrong property name in @Property"() {
        when:
        buildTypeElement("""
package test;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;

@Singleton
class MyService {

    @Property(name = "fooBar")
    private String property;
}

""")
        then:
        def e = thrown(RuntimeException)
        e.message.contains("Value 'fooBar' is not valid property placeholder. Please use kebab-case notation, for example 'foo-bar'.")
    }

    void "test wrong property name in @Value"() {
        when:
        buildTypeElement("""
package test;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
class MyService {

    @Value(\"Hello \${userName:John}\")
    private String property;
}

""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Value 'userName' is not valid property placeholder. Please use kebab-case notation, for example 'user-name'.")
    }

    void "test more than one property in @Value"() {
        when:
        buildTypeElement("""
package test;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
class MyService {

    @Value(\"Hello \${user-name:John} \${lastName:Doe}\")
    private String property;
}

""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Value 'lastName' is not valid property placeholder. Please use kebab-case notation, for example 'last-name'.")
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
        e.message.contains("Value 'controllerPath' is not valid property placeholder. Please use kebab-case notation, for example 'controller-path'.")
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
        e.message.contains("Value 'app.myWrongValue' is not valid property placeholder. Please use kebab-case notation, for example 'app.my-wrong-value'.")
    }

    void "test wrong property name in @Named in a constructor"() {
        when:
        buildTypeElement("""
package test;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Named;

@Controller()
class VehicleController {

    private final Engine engine;

    public VehicleController(@Value(\"\${vehicleCylinders}\") Engine engine) {
        this.engine = engine;
    }
}

interface Engine {
    int getCylinders();
}

""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Value 'vehicleCylinders' is not valid property placeholder. Please use kebab-case notation, for example 'vehicle-cylinders'.")
    }

    void "test that environment-style variables are supported"() {
        when:
        buildTypeElement("""
package test;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
class MyService {

    @Value(\"Hello \${USER_NAME}\")
    private String msg;
}

""")

        then:
        notThrown(Exception)
    }

    void "test that with defaults the last value is not checked"() {
        when:
        buildTypeElement("""
package test;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
class MyService {

    @Value(\"\${some-value:another-thing:someValue2:doesntMaTTeR}\")
    private String property;
}

""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Value 'someValue2' is not valid property placeholder. Please use kebab-case notation, for example 'some-value2'.")
    }

    void "test that escaping : works with right property name"() {
        when:
        buildTypeElement("""
package test;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
class MyService {

    @Value(\"\${server-address:`http://localhost:8080`}\")
    private String serverAddress;
}

""")

        then:
        notThrown(Exception)
    }

    void "test that escaping : works with wrong property name"() {
        when:
        buildTypeElement("""
package test;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
class MyService {

    @Value(\"\${serverAddress:`http://localhost:8080`}\")
    private String serverAddress;
}

""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Value 'serverAddress' is not valid property placeholder. Please use kebab-case notation, for example 'server-address'.")
    }

    void "test that escaping : works with more than one property"() {
        when:
        buildTypeElement("""
package test;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
class MyService {

    @Value(\"\${some-value:anotherThing:`this:goes:together`}\")
    private String foo;
}

""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Value 'some-value:anotherThing' is not valid property placeholder. Please use kebab-case notation, for example 'some-value:another-thing'.")
    }

    void "test that escaping : works with more than one property when all property names are correct"() {
        when:
        buildTypeElement("""
package test;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
class MyService {

    @Value(\"\${some-value:another-thing:foo-bar:`this:goes:together`}\")
    private String foo;
}

""")

        then:
        notThrown(Exception)
    }

    void "test that escaping : works with the last property name"() {
        when:
        buildTypeElement("""
package test;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
class MyService {

    @Value(\"\${some-value:another-thing:fooBar:`this:goes:together`}\")
    private String foo;
}

""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Value 'some-value:another-thing:fooBar' is not valid property placeholder. Please use kebab-case notation, for example 'some-value:another-thing:foo-bar'.")
    }

    void "test that escaping : works with more than one property when all property names are correct and default value is not checked"() {
        when:
        buildTypeElement("""
package test;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
class MyService {

    @Value(\"\${some-value:another-thing:foo-bar:`this:goes:together:AndDoesNtMATtteR`}\")
    private String foo;
}

""")

        then:
        notThrown(Exception)
    }
}
