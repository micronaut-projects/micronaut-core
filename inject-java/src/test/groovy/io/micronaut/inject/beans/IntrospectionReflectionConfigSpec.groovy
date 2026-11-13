package io.micronaut.inject.beans

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.ReflectionConfig
import io.micronaut.core.graal.GraalReflectionConfigurer
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.beans.visitor.IntrospectionReflectionConfig

class IntrospectionReflectionConfigSpec extends AbstractTypeElementSpec {

    void "the private fields an introspection reads reflectively are configured for a native image"() {
        given:
        def classLoader = buildClassLoader('test.Secret', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected(accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY)
public class Secret {
    private String code;
    public int open;
}
''')

        when:
        def configurer = (GraalReflectionConfigurer) classLoader.loadClass('test.$Secret$Introspection' + GraalReflectionConfigurer.CLASS_SUFFIX)
            .getDeclaredConstructor().newInstance()
        def configs = configurer.annotationMetadata.getAnnotationValuesByType(ReflectionConfig)

        then:
        configs.size() == 1
        configs[0].stringValue('type').get() == 'test.Secret'
        configs[0].getAnnotations('fields')*.stringValue('name')*.get() == ['code']

        and: "the configurer is a service"
        classLoader.getResources('META-INF/micronaut/' + GraalReflectionConfigurer.name + '/test.$Secret$Introspection' + GraalReflectionConfigurer.CLASS_SUFFIX).hasMoreElements()
    }

    void "every property member reporting reflection required is configured"() {
        given:
        def introspection = buildBeanIntrospection('test.Child', '''
package test;

import io.micronaut.core.annotation.Introspected;

class Parent {
    private String inherited;
    public String getInherited() { return inherited; }
}

@Introspected(members = true)
public class Child extends Parent {
    private String own;
    public String getOwn() { return own; }
}
''')
        def classLoader = introspection.beanType.classLoader

        when:
        def configurer = (GraalReflectionConfigurer) classLoader.loadClass('test.$Child$Introspection' + GraalReflectionConfigurer.CLASS_SUFFIX)
            .getDeclaredConstructor().newInstance()
        def configured = configurer.annotationMetadata.getAnnotationValuesByType(ReflectionConfig).collectMany { config ->
            config.getAnnotations('fields').collect { config.stringValue('type').get() + '.' + it.stringValue('name').get() }
        } as Set
        def required = introspection.beanProperties.collectMany { it.members }
            .findAll { it.reflectionRequired }
            .collect { it.declaringType.name + '.' + it.name } as Set

        then:
        !required.isEmpty()
        configured.containsAll(required)
    }

    void "a parameter type is named the way Class.forName loads it"() {
        expect:
        buildClassElement('''
package test;

class Arrays {
    public void take(String plain, String[] names, int[][] grid) {}
}
''') { ClassElement type ->
            type.getEnclosedElement(ElementQuery.ALL_METHODS.named('take')).get().parameters
                .collect { IntrospectionReflectionConfig.binaryName(it.type) }
        } == ['java.lang.String', '[Ljava.lang.String;', '[[I']
    }

    void "an introspection dispatching directly writes no configuration"() {
        given:
        def classLoader = buildClassLoader('test.Open', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class Open {
    private String name;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
''')

        when:
        classLoader.loadClass('test.$Open$Introspection' + GraalReflectionConfigurer.CLASS_SUFFIX)

        then:
        thrown(ClassNotFoundException)
    }
}
