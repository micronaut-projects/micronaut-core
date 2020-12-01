package io.micronaut.graal.reflect

import groovy.json.JsonSlurper
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class GraalTypeElementVisitorSpec extends AbstractTypeElementSpec {

    void "test write reflect.json for @Introspected"() {

        given:
        Reader reader = readGenerated("native-image/test/test/reflect-config.json", 'test.Test', '''
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
        Reader reader = readGenerated("native-image/test/test/reflect-config.json", 'test.Test', '''
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
        Reader reader = readGenerated("native-image/test/test/reflect-config.json", 'test.Test', '''
package test;

import io.micronaut.core.annotation.*;

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

    void "test write reflect.json for @TypeHint with classes and type names"() {

        given:
        Reader reader = readGenerated("native-image/test/test/reflect-config.json", 'test.Test', '''
package test;

import io.micronaut.core.annotation.*;

@TypeHint(value = Bar.class, typeNames = "java.lang.String")
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
        json?.find { it.name == 'java.lang.String'}

        cleanup:
        reader.close()
    }

    void "test write reflect.json for @TypeHint with access type"() {

        given:
        Reader reader = readGenerated("native-image/test/test/reflect-config.json", 'test.Test', '''
package test;

import io.micronaut.core.annotation.*;

@TypeHint(value=Bar.class, accessType = TypeHint.AccessType.ALL_PUBLIC_METHODS)
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

        cleanup:
        reader.close()
    }

    void "test write reflect.json for @ReflectiveAccess with access type"() {

        given:
        Reader reader = readGenerated("native-image/test/test/reflect-config.json", 'test.Test', '''
package test;

import io.micronaut.core.annotation.*;

class Test {
    
    @ReflectiveAccess
    private String name;
    
    @ReflectiveAccess
    public String getFoo() {
        return name;
    }
}


''')

        when:
        def json = new JsonSlurper().parse(reader)
        json = json.sort { it.name }
        def entry = json?.find { it.name == 'test.Test'}
        then:
        entry
        entry.name == 'test.Test'
        entry.fields
        entry.fields[0].name == 'name'
        entry.methods
        entry.methods[0].name == 'getFoo'

        cleanup:
        reader.close()
    }

    void "test write reflect.json for @ReflectiveAccess with inheritance"() {

        given:
        Reader reader = readGenerated("native-image/test/test/reflect-config.json", 'test.HTTPCheck', '''
package test;

import io.micronaut.core.annotation.*;

@Introspected
class HTTPCheck extends NewCheck {

    private String interval;

    @ReflectiveAccess
    protected void setInterval(String interval) {
        this.interval = interval;
    }
}
abstract class NewCheck {
    
    private String status;
     
    @ReflectiveAccess
    protected void setStatus(String status) {
        this.status = status;
    }
}


''')

        when:
        def json = new JsonSlurper().parse(reader)
        json = json.sort { it.name }
        def entry = json?.find { it.name == 'test.HTTPCheck'}
        then:
        entry
        entry.name == 'test.HTTPCheck'
        entry.methods.size() == 1
        entry.methods[0].name == 'setInterval'

        cleanup:
        reader.close()
    }

}
