package io.micronaut.inject.beans

import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.qualifiers.Qualifiers

import javax.inject.Named
import javax.inject.Qualifier

class BeanDefinitionSpec extends AbstractTypeElementSpec{

    void 'test qualifier for named only'() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

@javax.inject.Named("foo")
class Test {

}
''')
        expect:
        definition.getDeclaredQualifier() == Qualifiers.byName("foo")
    }

    void 'test no qualifier / only scope'() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

@javax.inject.Singleton
class Test {

}
''')
        expect:
        definition.getDeclaredQualifier() == null
    }

    void 'test named via alias'() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

import io.micronaut.context.annotation.*;

@MockBean(named="foo")
class Test {

}

@Bean
@interface MockBean {

    @AliasFor(annotation = Replaces.class, member = "named")
    @AliasFor(annotation = javax.inject.Named.class, member = "value")
    String named() default "";
}
''')
        expect:
        definition.getDeclaredQualifier() == Qualifiers.byName("foo")
        definition.getAnnotationNameByStereotype(Qualifier).get() == Named.name
    }

    void 'test qualifier annotation'() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

import io.micronaut.context.annotation.*;

@MyQualifier
class Test {

}

@javax.inject.Qualifier
@interface MyQualifier {

    @AliasFor(annotation = Replaces.class, member = "named")
    @AliasFor(annotation = javax.inject.Named.class, member = "value")
    String named() default "";
}
''')
        expect:
        definition.getDeclaredQualifier() == Qualifiers.byAnnotation(definition.getAnnotationMetadata(), "test.MyQualifier")
        definition.getAnnotationNameByStereotype(Qualifier).get() == "test.MyQualifier"
    }
}
