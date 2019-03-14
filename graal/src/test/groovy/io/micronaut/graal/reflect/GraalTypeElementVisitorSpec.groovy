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

        then:
        json.size() == 1
        json[0].name == 'test.Test'
        json[0].allPublicMethods
        json[0].allDeclaredConstructors

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

        then:
        json.size() == 2
        json[0].name == 'test.Bar'
        json[0].allPublicMethods
        json[0].allDeclaredConstructors
        json[1].name == 'test.Test'

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

        then:
        json.size() == 1
        json[0].name == 'test.Bar'
        !json[0].allPublicMethods
        json[0].allDeclaredConstructors

        cleanup:
        reader.close()
    }
}
