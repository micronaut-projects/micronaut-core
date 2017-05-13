package org.particleframework.inject.ast

import org.particleframework.context.DefaultComponentDefinition
import org.particleframework.context.DefaultComponentDefinitionClass
import org.particleframework.inject.ComponentDefinition
import spock.lang.Specification

/**
 * Created by graemerocher on 11/05/2017.
 */
class ParseSpec extends Specification {

    void "test parse simple singleton"() {
        given:
        def gcl = new GroovyClassLoader()

        when:
        gcl.parseClass('''
@javax.inject.Singleton
class FooService {}
''')

        then:
        gcl.loadedClasses.size() == 3

        when:
        def cls = gcl.loadClass('FooServiceComponentDefinitionClass')

        then:
        DefaultComponentDefinitionClass.isAssignableFrom(cls)
        cls.genericSuperclass.typeName == 'org.particleframework.context.DefaultComponentDefinitionClass<FooService>'

        when:
        DefaultComponentDefinitionClass cdefc = cls.newInstance()
        ComponentDefinition cdef = cdefc.load()

        then:
        cdef.constructor.arguments.size() == 0

    }

    void "test parse singleton with array injection"() {
        given:
        def gcl = new GroovyClassLoader()

        when:
        gcl.parseClass('''
@javax.inject.Singleton
class FooService {}

class BarService {
    @javax.inject.Inject
    void setFooServices(FooService[] fooServices) {
    
    }
}
''')

        then:
        gcl.loadedClasses.size() == 6

        when:
        def cls = gcl.loadClass('FooServiceComponentDefinitionClass')

        then:
        DefaultComponentDefinitionClass.isAssignableFrom(cls)
        cls.genericSuperclass.typeName == 'org.particleframework.context.DefaultComponentDefinitionClass<FooService>'

        when:
        DefaultComponentDefinitionClass cdefc = cls.newInstance()
        ComponentDefinition cdef = cdefc.load()

        then:
        cdef.constructor.arguments.size() == 0

    }


    void "test parse singleton with array constructor injection"() {
        given:
        def gcl = new GroovyClassLoader()

        when:
        gcl.parseClass('''
@javax.inject.Singleton
class FooService {}

    class BarService {
        @javax.inject.Inject
        BarService(FooService[] fooServices) {

        }
    }
    ''')

        then:
        gcl.loadedClasses.size() == 6

        when:
        def cls = gcl.loadClass('FooServiceComponentDefinitionClass')

        then:
        DefaultComponentDefinitionClass.isAssignableFrom(cls)
        cls.genericSuperclass.typeName == 'org.particleframework.context.DefaultComponentDefinitionClass<FooService>'

        when:
        DefaultComponentDefinitionClass cdefc = cls.newInstance()
        ComponentDefinition cdef = cdefc.load()

        then:
        cdef.constructor.arguments.size() == 0

    }


    void "test parse singleton with collection constructor injection"() {
        given:
        def gcl = new GroovyClassLoader()

        when:
        gcl.parseClass('''
@javax.inject.Singleton
class FooService {}

class BarService {
    @javax.inject.Inject
    BarService(Collection<FooService> fooServices) {
    
    }
}
''')

        then:
        gcl.loadedClasses.size() == 6

        when:
        def cls = gcl.loadClass('FooServiceComponentDefinitionClass')

        then:
        DefaultComponentDefinitionClass.isAssignableFrom(cls)
        cls.genericSuperclass.typeName == 'org.particleframework.context.DefaultComponentDefinitionClass<FooService>'

        when:
        DefaultComponentDefinitionClass cdefc = cls.newInstance()
        ComponentDefinition cdef = cdefc.load()

        then:
        cdef.constructor.arguments.size() == 0

    }

    void "test parse simple singleton with constructor argument"() {
        given:
        def gcl = new GroovyClassLoader()

        when:
        gcl.parseClass('''
@javax.inject.Singleton
class FooService {
    FooService(URL url){}
}
''')

        then:
        gcl.loadedClasses.size() == 3

        when:
        def cls = gcl.loadClass('FooServiceComponentDefinition')

        then:
        DefaultComponentDefinition.isAssignableFrom(cls)
        cls.genericSuperclass.typeName == 'org.particleframework.context.DefaultComponentDefinition<FooService>'

        when:
        ComponentDefinition cdef = cls.newInstance()

        then:
        cdef.constructor.arguments.size() == 1

    }


    void "test parse singleton and property injected bean"() {
        given:
        def gcl = new GroovyClassLoader()

        when:
        gcl.parseClass('''
@javax.inject.Singleton
class FooService {}

class BarService {
    @javax.inject.Inject FooService fooService
    
    @javax.inject.Inject private FooService anotherFooService
}
''')

        then:
        gcl.loadedClasses.size() == 6

        when:
        def cls = gcl.loadClass('BarServiceComponentDefinition')

        then:
        DefaultComponentDefinition.isAssignableFrom(cls)
        cls.genericSuperclass.typeName == 'org.particleframework.context.DefaultComponentDefinition<BarService>'

        when:
        ComponentDefinition cdef = cls.newInstance()

        then:
        cdef.constructor.arguments.size() == 0
        cdef.requiredFields.size() == 1
        cdef.requiredFields.first().name == 'anotherFooService'
        cdef.requiredProperties.size() == 1
        cdef.requiredProperties.first().method.name == "setFooService"

    }
}
