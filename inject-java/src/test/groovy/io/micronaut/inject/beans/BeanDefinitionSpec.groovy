package io.micronaut.inject.beans

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Mapper
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.Order
import io.micronaut.core.reflect.ClassUtils
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.core.type.TypeInformation
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Issue
import test.another.BeanWithPackagePrivate

class BeanDefinitionSpec extends AbstractTypeElementSpec {

    void "test bean with unresolved type"() {
        when:
        buildBeanDefinition('test.Unresolved', '''
package test;

import jakarta.inject.Singleton;

@Singleton
class Test {
    Test(Unresolved unresolved) {}
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('cannot find symbol')

    }

    void "test getTypeString for format #format"() {
        given:
        def definition = buildBeanDefinition('typestring.Test', '''
package typestring;

import io.micronaut.context.annotation.*;
import jakarta.inject.*;

@Singleton
class Test  {
}

''')

        expect:
        definition.asArgument().getTypeString(format) == result

        where:
        format                                    | result
        TypeInformation.TypeFormat.SIMPLE         | "Test"
        TypeInformation.TypeFormat.QUALIFIED      | "typestring.Test"
        TypeInformation.TypeFormat.SHORTENED      | "t.Test"
        TypeInformation.TypeFormat.ANSI_SIMPLE    | "\u001B[0;36mTest\u001B[0m"
        TypeInformation.TypeFormat.ANSI_QUALIFIED | "\u001B[0;36mtypestring.Test\u001B[0m"
        TypeInformation.TypeFormat.ANSI_SHORTENED | "\u001B[0;36mt.Test\u001B[0m"

    }

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
        BeanDefinition definition = buildBeanDefinition('test', 'Test', '''
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
        param3.getType() == String.class
    }

    void "test annotation metadata present on deep type parameters of definition"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test', 'Test', '''
package test;
import jakarta.validation.constraints.*;
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
        param.getAnnotationMetadata().getAnnotationNames().contains('io.micronaut.validation.annotation.ValidatedElement')
        param1.getAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.Size$List')
        param2.getAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.NotEmpty$List')
        param3.getAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.NotNull$List')
    }

    void "test isTypeVariable"() {
        given:
        ApplicationContext context = buildContext('''
package test;
import jakarta.validation.constraints.*;
import java.util.*;
import io.micronaut.core.annotation.*;
import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
class Test implements Serde<Object> {
}

interface Serde<T> extends Serializer<T>, Deserializer<T> {
}

interface Serializer<T> {
}

interface Deserializer<T> {
}

@jakarta.inject.Singleton
@Order(-100)
class ArrayListTest<E> implements Serde<ArrayList<E>> {
}

@jakarta.inject.Singleton
class SetTest<E> implements Serde<HashSet<E>> {
}

        ''')

        BeanDefinition<?> definition = getBeanDefinition(context, 'test.Test')


        when: "Micronaut Serialization use-case"
        def serdeTypeParam = definition.getTypeArguments("test.Serde")[0]
        def serializerTypeParam = definition.getTypeArguments("test.Serializer")[0]
        def deserializerTypeParam = definition.getTypeArguments("test.Deserializer")[0]
        def listDeser = context.getBean(Argument.of(context.classLoader.loadClass('test.Deserializer'), Argument.listOf(String)))
        def collectionDeser = context.getBean(Argument.of(context.classLoader.loadClass('test.Deserializer'), Argument.of(Collection.class, String)))

        then: "The first is a placeholder"
        listDeser.getClass().name == 'test.ArrayListTest'
        listDeser.is(collectionDeser)
        !serdeTypeParam.isTypeVariable() //
        !(serdeTypeParam instanceof GenericPlaceholder)
        and: "threat resolved placeholder as not a type variable"
        !serializerTypeParam.isTypeVariable()
        !(serializerTypeParam instanceof GenericPlaceholder)
        !deserializerTypeParam.isTypeVariable()
        !(deserializerTypeParam instanceof GenericPlaceholder)
    }

    void "test isTypeVariable array"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test', 'Test', '''
package test;
import jakarta.validation.constraints.*;
import java.util.List;

@jakarta.inject.Singleton
public class Test implements Serde<String[]> {
}

interface Serde<T> extends Serializer<T>, Deserializer<T> {
}

interface Serializer<T> {
}

interface Deserializer<T> {
}


        ''')

        when: "Micronaut Serialization use-case"
        def serdeTypeParam = definition.getTypeArguments("test.Serde")[0]
        def serializerTypeParam = definition.getTypeArguments("test.Serializer")[0]
        def deserializerTypeParam = definition.getTypeArguments("test.Deserializer")[0]
        // Arrays are not resolved as JavaClassElements or placeholders
        then: "The first is a placeholder"
        serdeTypeParam.simpleName == "String[]"
        !serdeTypeParam.isTypeVariable()
        !(serdeTypeParam instanceof GenericPlaceholder)
        and: "threat resolved placeholder as not a type variable"
        serializerTypeParam.simpleName == "String[]"
        !serializerTypeParam.isTypeVariable()
        !(serializerTypeParam instanceof GenericPlaceholder)
        deserializerTypeParam.simpleName == "String[]"
        !deserializerTypeParam.isTypeVariable()
        !(deserializerTypeParam instanceof GenericPlaceholder)
    }

    void "test intercepted type arguments"() {
        given:
        BeanDefinition definition = buildSimpleInterceptedBeanDefinition('test.AImplementationLong', '''
package test;

import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.context.annotation.Prototype;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;

@MyIntroductionBean
@Secondary
class AImplementationLong implements AInterface<Long, Long> {

    private Long l1,l2;

    @Override
    public void set(Long s, Long s2) {
        l1=s;
        l2=s2;
    }

    @Override
    public String get(Long s, Long s2) {
        return s.getClass().getName() +","+s.getClass().getName();
    }
}

@MyIntroductionBean
@Secondary
class AImplementationString implements AInterface<String, String> {

    private String s1,s2;

    @Override
    public void set(String s, String s2) {
        s1=s;
        this.s2=s2;
    }

    @Override
    public String get(String s, String s2) {
        return s.getClass().getName() +","+s.getClass().getName();
    }
}

interface AInterface<K, V> {

    void set(K k, V v);

    String get(K k, V v);
}

@Introduction
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Documented
@DefaultScope(Prototype.class)
@interface MyIntroductionBean {
}

''')

        when:
        def arguments = definition.getTypeArguments(ClassUtils.forName("test.AInterface", definition.getClass().classLoader).orElseThrow())
        then:
        arguments[0].type == Long
        arguments[1].type == Long
    }

    void "test package-private methods with different package are marked as overridden"() {
        when:
        def ctx = ApplicationContext.builder().build().start()
        BeanDefinition definition = buildBeanDefinition('test.another.Test', '''
package test.another;

import test.Middle;
import jakarta.inject.Singleton;

@Singleton
class Test extends Middle {
    public boolean root;
    void injectPackagePrivateMethod() {
        root = true;
    }
}

''')

        def bean1 = ctx.getBean(BeanWithPackagePrivate)
        def bean2 = ctx.getBean(definition)
        then: """By Java rules the base method is not overridden and should have been injected too, but it's not possible to invoked using the reflection,
so we mark it as overridden
"""
        !bean1.@root
        bean1.@middle
        !bean1.@base
        !bean2.@root
        bean2.@middle
        !bean2.@base
        cleanup:
        ctx.close()
    }

    void "test repeatable inner type annotation 1"() {
        when:
        def ctx = ApplicationContext.builder().properties(["repeatabletest": "true"]).build().start()
        def beanDef = ctx.getBeanDefinition(MapOfListsBean1)
        then:
        beanDef.getAnnotationMetadata().findRepeatableAnnotation(MyMin1).isPresent()

        cleanup:
        ctx.close()
    }

    void "test repeatable inner type annotation 2"() {
        when:
        def ctx = ApplicationContext.builder().properties(["repeatabletest": "true"]).build().start()
        def beanDef = ctx.getBeanDefinition(MapOfListsBean2)
        then:
        beanDef.getAnnotationMetadata().findRepeatableAnnotation(MyMin2).isPresent()

        cleanup:
        ctx.close()
    }

    void "test repeatable inner type annotation 3"() {
        when:
        def ctx = ApplicationContext.builder().properties(["repeatabletest": "true"]).build().start()
        def beanDef = ctx.getBeanDefinition(MapOfListsBean3)
        then:
        beanDef.getAnnotationMetadata().findRepeatableAnnotation(MyMin3).isPresent()

        cleanup:
        ctx.close()
    }

    void "test repeatable inner type annotation 4"() {
        when:
        def ctx = ApplicationContext.builder().properties(["repeatabletest": "true"]).build().start()
        def beanDef = ctx.getBeanDefinition(MapOfListsBean4)
        then:
        beanDef.getAnnotationMetadata().findRepeatableAnnotation(MyMin4).isPresent()

        cleanup:
        ctx.close()
    }

    void "test repeatable inner type annotation 5"() {
        when:
        def ctx = ApplicationContext.builder().properties(["repeatabletest": "true"]).build().start()
        def beanDef = ctx.getBeanDefinition(MapOfListsBean5)
        then:
        beanDef.getAnnotationMetadata().findRepeatableAnnotation(MyMin5).isPresent()

        cleanup:
        ctx.close()
    }

    void "test interface bean"() {
        given:
        def definition = buildBeanDefinition('test.MyEntityControllerInterface', '''
package test;

import io.micronaut.http.annotation.Controller;

@Controller
interface MyEntityControllerInterface {
}
''')

        expect:
        definition == null
    }

    void "test interface bean 2"() {
        given:
        def definition = buildBeanDefinition('test.MyEntityControllerInterface', '''
package test;

import jakarta.inject.Singleton;

@Singleton
interface MyEntityControllerInterface {
}
''')

        expect:
        definition == null
    }

    void "test mapped interface bean"() {
        given:
        def definition = buildSimpleInterceptedBeanDefinition('test.ProductMappers', '''
package test;

import io.micronaut.context.annotation.Mapper.Mapping;
import jakarta.inject.Singleton;
import io.micronaut.core.annotation.Introspected;

@Singleton
interface ProductMappers {
    @Mapping(
        to = "price",
        from = "#{product.price * 2}",
        format = "$#.00"
    )
    @Mapping(
        to = "distributor",
        from = "#{this.getDistributor()}"
    )
    ProductDTO toProductDTO(Product product);

    default String getDistributor() {
        return "Great Product Company";
    }
}

@Introspected
record Product(
    String name,
    double price,
    String manufacturer) {
}

@Introspected
record ProductDTO(String name, String price, String distributor) {
}
''')

        expect:
        definition.getExecutableMethods()[0].hasDeclaredAnnotation(Mapper)
        definition.getExecutableMethods()[0].hasDeclaredAnnotation(Mapper.Mapping)
    }
}
