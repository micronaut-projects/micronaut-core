package io.micronaut.inject.beans

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.Order
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Issue

class BeanDefinitionSpec extends AbstractTypeElementSpec {

    void "test limit the exposed bean types"() {
        given:
        def definition = buildBeanDefinition('limittypes.Test', '''
package limittypes;

import io.micronaut.context.annotation.*;
import jakarta.inject.*;

@Singleton
@Bean(typed = Runnable.class)
class Test implements Runnable {
    public void run() {}
}

''')
        expect:
        definition.exposedTypes == [Runnable] as Set
    }

    void "test limit the exposed bean types - reference"() {
        given:
        def reference = buildBeanDefinitionReference('limittypes.Test', '''
package limittypes;

import io.micronaut.context.annotation.*;
import jakarta.inject.*;

@Singleton
@Bean(typed = Runnable.class)
class Test implements Runnable {
    public void run() {}
}

''')
        expect:
        reference.exposedTypes == [Runnable] as Set
    }

    void "test fail compilation on invalid exposed bean type"() {
        when:
        buildBeanDefinition('limittypes.Test', '''
package limittypes;

import io.micronaut.context.annotation.*;
import jakarta.inject.*;

@Singleton
@Bean(typed = Runnable.class)
class Test {

}

''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains("Bean defines an exposed type [java.lang.Runnable] that is not implemented by the bean type")
    }

    void "test exposed types on factory with AOP"() {
        when:
        buildBeanDefinition('limittypes.Test$Method0', '''
package limittypes;

import io.micronaut.aop.Logged;
import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;

@Factory
class Test {

    @Singleton
    @Bean(typed = {X.class})
    @Logged
    Y method() {
        return new Y();
    }
}

interface X {

}
class Y implements X {

}

''')

        then:
        noExceptionThrown()
    }

    void "test fail compilation on exposed subclass of bean type"() {
        when:
        buildBeanDefinition('limittypes.Test', '''
package limittypes;

import io.micronaut.context.annotation.*;
import jakarta.inject.*;

@Singleton
@Bean(typed = X.class)
class Test {

}

class X extends Test {}

''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains("Bean defines an exposed type [limittypes.X] that is not implemented by the bean type")
    }

    void "test fail compilation on exposed subclass of bean type with factory"() {
        when:
        buildBeanDefinition('limittypes.Test$Method0', '''
package limittypes;

import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;

@Factory
class Test {

    @Singleton
    @Bean(typed = {X.class, Y.class})
    X method() {
        return new Y();
    }
}

interface X {

}
class Y implements X {

}

''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Bean defines an exposed type [limittypes.Y] that is not implemented by the bean type")
    }

    void "test declared generics from definition"() {
        when:
        def definition = buildBeanDefinition('limittypes.Test', '''
package limittypes;

import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;

@Singleton
class Test<K, V> {
}


''')

        then:
        definition.getGenericBeanType().getTypeString(true) == 'Test<Object, Object>'
    }

    void "test declared generics from reference"() {
        when:
        def ref = buildBeanDefinitionReference('limittypes.Test', '''
package limittypes;

import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;

@Singleton
class Test<K, V> {
}


''')

        then:
        ref.getGenericBeanType().getTypeString(true) == 'Test<Object, Object>'
    }

    void "test declared generics from reference with inheritance"() {
        when:
        def ref = buildBeanDefinitionReference('test.DefaultKafkaConsumerConfiguration', '''
package test;

import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;

@Singleton
@Requires(beans = KafkaDefaultConfiguration.class)
class DefaultKafkaConsumerConfiguration<K, V> extends AbstractKafkaConsumerConfiguration<K, V> {
}

abstract class AbstractKafkaConsumerConfiguration<K, V> extends AbstractKafkaConfiguration<K, V> { }

abstract class AbstractKafkaConfiguration<K, V> {}

class KafkaDefaultConfiguration {}
''')

        then:
        ref.getGenericBeanType().getTypeString(true) == 'DefaultKafkaConsumerConfiguration<Object, Object>'
    }

    void "test generics from factory"() {
        when:
        def ref = buildBeanDefinitionReference('limittypes.Test$Method0', '''
package limittypes;

import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;

@Factory
class Test {

    @Singleton
    X<Y> method() {
        return new Y();
    }
}

interface X<T> {

}
class Y implements X<Y> {

}

''')

        then:
        ref.getGenericBeanType().getTypeString(true) == 'X<Y>'
    }

    void "test exposed bean types with factory invalid type"() {
        when:
        buildBeanDefinition('limittypes.Test$Method0', '''
package limittypes;

import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;

@Factory
class Test {

    @Singleton
    @Bean(typed = {Z.class})
    X method() {
        return new Y();
    }
}

interface Z { }
interface X { }
class Y implements X { }
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Bean defines an exposed type [limittypes.Z] that is not implemented by the bean type")
    }

    void 'test order annotation'() {
        given:
        def definition = buildBeanDefinition('test.TestOrder', '''
package test;

import io.micronaut.core.annotation.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;

@Requires(property = "spec.name", value = "BeanDefinitionDelegateSpec")
@Singleton
@Order(value = 10)
class TestOrder {

}
''')
        expect:

        definition.intValue(Order).getAsInt() == 10
    }

    void 'test qualifier for named only'() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

@jakarta.inject.Named("foo")
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

@jakarta.inject.Singleton
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
    @AliasFor(annotation = jakarta.inject.Named.class, member = "value")
    String named() default "";
}
''')
        expect:
        definition.getDeclaredQualifier() == Qualifiers.byName("foo")
        definition.getAnnotationNameByStereotype(AnnotationUtil.QUALIFIER).get() == AnnotationUtil.NAMED
    }

    void 'test qualifier annotation'() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

import io.micronaut.context.annotation.*;

@MyQualifier
class Test {

}

@jakarta.inject.Qualifier
@interface MyQualifier {

    @AliasFor(annotation = Replaces.class, member = "named")
    @AliasFor(annotation = jakarta.inject.Named.class, member = "value")
    String named() default "";
}
''')
        expect:
        definition.getDeclaredQualifier() == Qualifiers.byAnnotation(definition.getAnnotationMetadata(), "test.MyQualifier")
        definition.getAnnotationNameByStereotype(AnnotationUtil.QUALIFIER).get() == "test.MyQualifier"
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/5001")
    void "test building a bean with generics that dont have a type"() {
        when:
        def definition = buildBeanDefinition('test.NumberThingManager', '''
package test;

import jakarta.inject.Singleton;

interface Thing<T> {}

interface NumberThing<T extends Number & Comparable<T>> extends Thing<T> {}

class AbstractThingManager<T extends Thing<?>> {}

@Singleton
public class NumberThingManager extends AbstractThingManager<NumberThing<?>> {}
''')

        then:
        noExceptionThrown()
        definition != null
        definition.getTypeArguments("test.AbstractThingManager")[0].getTypeVariables().get("T").getType() == Number.class
    }

    void "test building a bean with generics wildcard extending"() {
        when:
        def definition = buildBeanDefinition('test.NumberThingManager', '''
package test;

import jakarta.inject.Singleton;

interface Thing<T> {}

interface NumberThing<T extends Number & Comparable<T>> extends Thing<T> {}

class AbstractThingManager<T extends Thing<?>> {}

@Singleton
public class NumberThingManager extends AbstractThingManager<NumberThing<? extends Double>> {}
''')

        then:
        noExceptionThrown()
        definition != null
        definition.getTypeArguments("test.AbstractThingManager")[0].getTypeVariables().get("T").getType() == Double.class
    }

    void "test a bean definition in a package with uppercase letters"() {
        when:
        def definition = buildBeanDefinition('test.A', 'TestBean', '''
package test.A;

@jakarta.inject.Singleton
class TestBean {

}
''')
        then:
        noExceptionThrown()
        definition != null
    }

    void "test deep type parameters are created in definition"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test','Test','''
package test;
import java.util.List;

@jakarta.inject.Singleton
public class Test {
    List<List<List<String>>> deepList;
    public Test(List<List<List<String>>> deepList) { this.deepList = deepList; }
}
        ''')

        expect:
        definition != null
        def constructor = definition.getConstructor()

        def param = constructor.getArguments()[0]
        param.getTypeParameters().length == 1
        def param1 = param.getTypeParameters()[0]
        param1.getTypeParameters().length == 1
        def param2 = param1.getTypeParameters()[0]
        param2.getTypeParameters().length == 1
        def param3 = param2.getTypeParameters()[0]
    }

    void "test annotation metadata present on deep type parameters of definition"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test','Test','''
package test;
import javax.validation.constraints.*;
import java.util.List;

@jakarta.inject.Singleton
public class Test {
    public Test(List<@Size(min=1) List<@NotEmpty List<@NotNull String>>> deepList) { }
}
        ''')

        when:
        definition != null
        def constructor = definition.getConstructor()
        def param = constructor.getArguments()[0]
        def param1 = param.getTypeParameters()[0]
        def param2 = param1.getTypeParameters()[0]
        def param3 = param2.getTypeParameters()[0]

        then:
        param.getAnnotationMetadata().getAnnotationNames().size() == 0
        param1.getAnnotationMetadata().getAnnotationNames().size() == 1
        param1.getAnnotationMetadata().getAnnotationNames().asList() == ['javax.validation.constraints.Size$List']
        param2.getAnnotationMetadata().getAnnotationNames().size() == 1
        param2.getAnnotationMetadata().getAnnotationNames().asList() == ['javax.validation.constraints.NotEmpty$List']
        param3.getAnnotationMetadata().getAnnotationNames().size() == 1
        param3.getAnnotationMetadata().getAnnotationNames().asList() == ['javax.validation.constraints.NotNull$List']
    }
}
