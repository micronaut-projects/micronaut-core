package io.micronaut.graal.reflect

import groovy.json.JsonSlurper
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class GraalTypeElementVisitorSpec extends AbstractTypeElementSpec {

    void "test write reflect.json for @Introspected"() {

        given:
        Reader reader = readGenerated("reflect.json", 'test.Test', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
class Test {
    
}

''')

        when:
        def json = new JsonSlurper().parse(reader)
        def entry = json?.find { it.name == 'test.Test'}

        then:
        entry
        entry.name == 'test.Test'
        entry.allPublicMethods
        entry.allDeclaredConstructors

        cleanup:
        reader.close()
    }



    void "test write reflect.json for @Introspected with classes"() {

        given:
        Reader reader = readGenerated("reflect.json", 'test.Test', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected(classes = Bar.class)
class Test {
    
}

class Bar {}

''')

        when:
        def json = new JsonSlurper().parse(reader)
        json = json.sort { it.name }
        def entry = json?.find { it.name == 'test.Bar'}
        then:
        entry
        entry.name == 'test.Bar'
        entry.allPublicMethods
        entry.allDeclaredConstructors
        json?.find { it.name == 'test.Test'}

        cleanup:
        reader.close()
    }

    void "test write reflect.json for @TypeHint with classes"() {

        given:
        Reader reader = readGenerated("reflect.json", 'test.Test', '''
package test;

import io.micronaut.core.annotation.TypeHint;

@TypeHint(Bar.class)
class Test {
    
}

class Bar {}

''')

        when:
        def json = new JsonSlurper().parse(reader)
        json = json.sort { it.name }
        def entry = json?.find { it.name == 'test.Bar'}
        then:
        entry
        entry.name == 'test.Bar'
        !entry.allPublicMethods
        entry.allDeclaredConstructors

        cleanup:
        reader.close()
    }

    void "test write reflect.json for controller methods"() {

        given:
        Reader reader = readGenerated("reflect.json", 'test.Test', '''
package test;

import io.micronaut.http.annotation.*;

@Controller("/")
class Test {

    @Get("/bar")
    Bar getBar() {
        return null;
    }
    
}

class Bar {}

''')

        when:
        def json = new JsonSlurper().parse(reader)
        json = json.sort { it.name }

        def entry = json?.find { it.name == 'test.Bar'}

        then:
        entry
        entry.name == 'test.Bar'
        entry.allPublicMethods
        entry.allDeclaredConstructors

        cleanup:
        reader.close()
    }
}
