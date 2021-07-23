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
package io.micronaut.inject.annotation

import io.micronaut.aop.Around
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.http.annotation.Header
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.runtime.context.scope.Refreshable
import io.micronaut.runtime.context.scope.ScopedProxy
import spock.lang.Unroll

import jakarta.inject.Qualifier
import jakarta.inject.Scope
import jakarta.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class JavaAnnotationMetadataBuilderSpec extends AbstractTypeElementSpec {

    @Unroll
    void 'test hasPropertyExpressions() returns the correct value for metadata that has expressions for annotation #annotation'() {
        given:
        AnnotationMetadata annotationMetadata = buildMethodAnnotationMetadata("""\
package propertyexpr;

import io.micronaut.inject.annotation.repeatable.*;
import io.micronaut.context.annotation.*;

@Prototype
class Test {
    $annotation  
    void test() {}
}
""", 'test')
        def loadedMetadata = writeAndLoadMetadata("propertyexpr.Test", annotationMetadata)

        expect:
        annotationMetadata.hasPropertyExpressions() == hasPropertyExpressions
        loadedMetadata.hasPropertyExpressions() == hasPropertyExpressions

        where:
        annotation                                                                   | hasPropertyExpressions
        '@Value("foo.bar")'                                                          | false
        '@Value("${foo.bar}")'                                                       | true
        '@Value("foo.bar")'                                                          | false
        '@Property(name="${foo.bar}")'                                               | true
        '@Property(name="foo.bar")'                                                  | false
        '@Property(name="${foo.bar}") @Property(name="foo.bar")'                     | true
        '@Property(name="foo.bar") @Property(name="foo.another")'                    | false
        '@PropertySource({@Property(name="${foo.bar}"), @Property(name="foo.bar")})' | true
        '@PropertySource({@Property(name="foo.bar"), @Property(name="foo.another")})' | false
    }

    void "test build repeated annotation values"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test', '''\
package test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import io.micronaut.inject.annotation.*;

@jakarta.inject.Singleton
class Test {

    @io.micronaut.context.annotation.Executable
    void test(@io.micronaut.http.annotation.Header(name="foo",value="bar") String foo) {}
}

''')

        expect:
        definition != null
        definition.getRequiredMethod("test", String).arguments[0].isAnnotationPresent(Header)
    }

    void "test self referencing annotation"() {
        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''\
package test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import io.micronaut.inject.annotation.*;

@TestAnnotation
class Test {
}

@Target({ElementType.PACKAGE, ElementType.TYPE, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.METHOD})
@TestAnnotation
@interface TestAnnotation { }
''')

        expect:
        metadata != null
        metadata.hasAnnotation('test.TestAnnotation')
    }

    void "test self referencing annotation - 2"() {
        given:
        AnnotationMetadata metadata = buildBeanDefinition('test.Test', '''\
package test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import io.micronaut.inject.annotation.*;

@TestAnnotation
@jakarta.inject.Singleton
class Test {
}

@Target({ElementType.PACKAGE, ElementType.TYPE, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.METHOD})
@TestAnnotation
@interface TestAnnotation { }
''')

        expect:
        metadata != null
        metadata.hasAnnotation('test.TestAnnotation')
    }

    void "test find closest stereotype"() {
        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''\
package test;

import io.micronaut.inject.annotation.*;
@ScopeTwo
class Test {
}
''')

        expect:
        metadata != null
        metadata.getAnnotationNameByStereotype(AnnotationUtil.SCOPE).get() == ScopeTwo.name
    }


    void "test annotation names by stereotype"() {
        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''\
package test;

import io.micronaut.runtime.context.scope.*;
@Refreshable
class Test {
}
''')

        expect:
        metadata != null
        !metadata.getAnnotationNamesByStereotype(Around).contains(Refreshable.name)
        metadata.getAnnotationNamesByStereotype(Around).contains(ScopedProxy.name)
    }

    void "test multiple alias definitions with value"() {
        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''\
package test;

import io.micronaut.inject.annotation.*;

@MultipleAlias("test")
class Test {
}
''')
        expect:
        metadata != null
        metadata.hasDeclaredStereotype(ConfigurationReader)
        metadata.getValue(ConfigurationReader, String).get() == 'test'
        metadata.hasDeclaredAnnotation(MultipleAlias)
        metadata.getValue(MultipleAlias, String).get() == 'test'
        metadata.getValue(MultipleAlias, "id", String).get() == 'test'
    }

    void "test alias for has correct value for aliased member"() {
        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''\
package test;

import io.micronaut.inject.annotation.*;

@MyStereotype("test")
class Test {
}
''')
        expect:
        metadata != null
        metadata.hasDeclaredStereotype(ConfigurationReader)
        metadata.getValue(ConfigurationReader, String).get() == 'test'
    }

    void "test read annotation with annotation value"() {
        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''\
package test;

import io.micronaut.inject.annotation.*;

@TopLevel(nested=@Nested(num=10))
class Test {
}
''')
        expect:
        metadata != null
        metadata.hasAnnotation(TopLevel)
        metadata.getValue(TopLevel, "nested").isPresent()
        metadata.getValue(TopLevel, "nested", Nested).isPresent()
        metadata.getValue(TopLevel, "nested", Nested).get().num() == 10
    }

    void "test read external constants"() {
        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''\
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.AnnotationMetadata;
@Requires(property=AnnotationMetadata.VALUE_MEMBER)
class Test {
}
''')
        expect:
        metadata != null
        metadata.getValue(Requires, "property").isPresent()
        metadata.getValue(Requires, "property").get() == 'value'
    }

    void "test read constants defined in class"() {
        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''\
package test;

import io.micronaut.context.annotation.*;

@Requires(property=Test.TEST)
class Test {
    public static final String TEST = "blah";
}
''')

        expect:
        metadata != null
        metadata.getValue(Requires, "property").isPresent()
        metadata.getValue(Requires, "property").get() == 'blah'

    }

    void "test build repeatable annotations"() {
        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''\
package test;

import io.micronaut.context.annotation.*;

@Requires(property="blah")
@Requires(classes=Test.class)
class Test {
}
''')

        expect:
        metadata != null
        metadata.hasDeclaredAnnotation(Requirements)
        metadata.getValue(Requirements).get().size() == 2
        metadata.getValue(Requirements).get()[0] instanceof io.micronaut.core.annotation.AnnotationValue
        metadata.getValue(Requirements).get()[0].values.get('property') == 'blah'
        metadata.getValue(Requirements).get()[1] instanceof io.micronaut.core.annotation.AnnotationValue
        metadata.getValue(Requirements).get()[1].values.get('classes') == [new AnnotationClassValue('test.Test')] as AnnotationClassValue[]
    }

    void "test parse first level stereotype data"() {

        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''\
package test;

@io.micronaut.context.annotation.Primary
class Test {
}
''')

        expect:
        metadata != null
        metadata.hasDeclaredAnnotation(Primary)
        !metadata.hasDeclaredAnnotation(AnnotationUtil.SINGLETON)
        metadata.hasAnnotation(Primary)
        metadata.hasStereotype(AnnotationUtil.QUALIFIER)
        !metadata.hasStereotype(AnnotationUtil.SINGLETON)
    }

    void "test parse first level stereotype data singleton"() {

        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''\
package test;

@jakarta.inject.Singleton
class AImpl implements A {

}

interface A {

}


''')

        expect:
        metadata != null
        !metadata.hasDeclaredAnnotation(AnnotationUtil.SCOPE)
        metadata.hasDeclaredAnnotation(AnnotationUtil.SINGLETON)
        metadata.hasSimpleDeclaredAnnotation("Singleton")
        metadata.hasSimpleAnnotation("Singleton")
        metadata.hasStereotype(AnnotationUtil.SINGLETON)
        metadata.hasStereotype(AnnotationUtil.SCOPE)
        metadata.getAnnotationNameByStereotype(AnnotationUtil.SINGLETON).get() == 'javax.inject.Singleton'
    }

    void "test parse inherited stereotype data attributes"() {

        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''\
package test;

@io.micronaut.inject.annotation.Trace(type = Test.class, types = {Test.class}, something = true)
class Test {
}
''')

        expect:
        metadata != null
        metadata.hasAnnotation(Trace)
        metadata.getValue(Trace, "type").isPresent()
        metadata.getValue(Trace, "type").get() == new AnnotationClassValue('test.Test')
        metadata.getValue(Trace, "types").get() == [new AnnotationClassValue('test.Test')] as AnnotationClassValue[]
        metadata.hasStereotype(Trace)
        metadata.hasDeclaredAnnotation(Trace)
        metadata.hasStereotype(Around)
        metadata.hasStereotype(SomeOther)
        metadata.hasStereotype(AnnotationUtil.SCOPE)
        !metadata.hasDeclaredAnnotation(AnnotationUtil.SCOPE)
        !metadata.hasDeclaredAnnotation(Around)
        metadata.getValue(Around, 'hotswap').isPresent()
        metadata.isTrue(Around, 'hotswap')
        metadata.getValue(Around, 'proxyTarget').isPresent()
        metadata.getValue(Around, 'lazy').isPresent()
        metadata.isTrue(Around, 'proxyTarget')
        metadata.isFalse(Around, 'lazy')
    }

    void "test parse interface inherited stereotype data attributes"() {

        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''\
package test;


class Test implements ITest{
}

@io.micronaut.inject.annotation.Trace(type = Test.class, types = {Test.class}, something = true)
interface ITest {

}
''')

        expect:
        metadata != null
        metadata.hasAnnotation(Trace)
        metadata.getValue(Trace, "type").isPresent()
        metadata.getValue(Trace, "type").get() == new AnnotationClassValue('test.Test')
        metadata.getValue(Trace, "types").get() == [new AnnotationClassValue('test.Test')] as AnnotationClassValue[]
        metadata.hasStereotype(Trace)
        !metadata.hasDeclaredAnnotation(Trace)
        metadata.hasStereotype(Around)
        metadata.hasStereotype(SomeOther)
        metadata.hasStereotype(AnnotationUtil.SCOPE)
        !metadata.hasDeclaredAnnotation(AnnotationUtil.SCOPE)
        !metadata.hasDeclaredAnnotation(Around)
        metadata.getValue(Around, 'hotswap').isPresent()
        metadata.isTrue(Around, 'hotswap')
        metadata.getValue(Around, 'proxyTarget').isPresent()
        metadata.getValue(Around, 'lazy').isPresent()
        metadata.isTrue(Around, 'proxyTarget')
        metadata.isFalse(Around, 'lazy')
    }


    void "test parse super class inherited stereotype data attributes"() {

        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''\
package test;


class Test extends SuperTest{
}

@io.micronaut.inject.annotation.Trace(type = SuperTest.class, types = {SuperTest.class}, something = true)
class SuperTest {

}
''')

        expect:
        metadata != null
        metadata.hasAnnotation(Trace)
        metadata.getValue(Trace, "type").isPresent()
        metadata.getValue(Trace, "type").get() == new AnnotationClassValue('test.SuperTest')
        metadata.getValue(Trace, "types").get() == [new AnnotationClassValue('test.SuperTest')] as AnnotationClassValue[]

        metadata.hasStereotype(Trace)
        !metadata.hasDeclaredAnnotation(Trace)
        metadata.hasStereotype(Around)
        metadata.hasStereotype(SomeOther)
        metadata.hasStereotype(AnnotationUtil.SCOPE)
        !metadata.hasDeclaredAnnotation(AnnotationUtil.SCOPE)
        !metadata.hasDeclaredAnnotation(Around)
        metadata.getValue(Around, 'hotswap').isPresent()
        metadata.isTrue(Around, 'hotswap')
        metadata.getValue(Around, 'proxyTarget').isPresent()
        metadata.getValue(Around, 'lazy').isPresent()
        metadata.isTrue(Around, 'proxyTarget')
        metadata.isFalse(Around, 'lazy')
    }


    void "test override super class inherited stereotype data attributes"() {

        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''\
package test;

@io.micronaut.inject.annotation.Trace(type = Test.class, types = {Test.class}, something = false)
class Test extends SuperTest{
}

@io.micronaut.inject.annotation.Trace(type = SuperTest.class, types = {SuperTest.class}, something = true)
class SuperTest {

}
''')

        expect:
        metadata != null
        metadata.hasAnnotation(Trace)
        metadata.getValue(Trace, "type").isPresent()

        metadata.getValue(Trace, "type").get() == new AnnotationClassValue('test.Test')
        metadata.getValue(Trace, "types").get() == [new AnnotationClassValue('test.Test')] as AnnotationClassValue[]

        metadata.hasStereotype(Trace)
        metadata.hasDeclaredAnnotation(Trace)
        metadata.hasStereotype(Around)
        metadata.hasStereotype(SomeOther)
        metadata.hasStereotype(AnnotationUtil.SCOPE)
        !metadata.hasDeclaredAnnotation(AnnotationUtil.SCOPE)
        !metadata.hasDeclaredAnnotation(Around)
        metadata.getValue(Around, 'hotswap').isPresent()
        metadata.isFalse(Around, 'hotswap')
        metadata.getValue(Around, 'proxyTarget').isPresent()
        metadata.getValue(Around, 'lazy').isPresent()
        metadata.isTrue(Around, 'proxyTarget')
        metadata.isFalse(Around, 'lazy')
    }

    void "test parse super class inherited interface stereotype data attributes"() {

        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''\
package test;


class Test extends SuperTest{
}

class SuperTest implements ITest {}

@io.micronaut.inject.annotation.Trace(type = Test.class, types = {Test.class}, something = true)
interface ITest {

}
''')

        expect:
        metadata != null
        metadata.hasAnnotation(Trace)
        metadata.getValue(Trace, "type").isPresent()
        metadata.getValue(Trace, "type").get() == new AnnotationClassValue('test.Test')
        metadata.getValue(Trace, "types").get() == [new AnnotationClassValue('test.Test')] as AnnotationClassValue[]
        metadata.hasStereotype(Trace)
        !metadata.hasDeclaredAnnotation(Trace)
        metadata.hasStereotype(Around)
        metadata.hasStereotype(SomeOther)
        metadata.hasStereotype(AnnotationUtil.SCOPE)
        !metadata.hasDeclaredAnnotation(AnnotationUtil.SCOPE)
        !metadata.hasDeclaredAnnotation(Around)
        metadata.getValue(Around, 'hotswap').isPresent()
        metadata.isTrue(Around, 'hotswap')
        metadata.getValue(Around, 'proxyTarget').isPresent()
        metadata.getValue(Around, 'lazy').isPresent()
        metadata.isTrue(Around, 'proxyTarget')
        metadata.isFalse(Around, 'lazy')
        metadata.getAnnotationNamesByStereotype(Around.name) == [Trace.name, SomeOther.name]
    }


    void "test parse first level method stereotype data"() {

        given:
        AnnotationMetadata metadata = buildMethodAnnotationMetadata('''\
package test;


class Test {
    @io.micronaut.context.annotation.Primary
    void testMethod() {}
}
''', 'testMethod')

        expect:
        metadata != null
        metadata.hasDeclaredAnnotation(Primary)
        !metadata.hasDeclaredAnnotation(AnnotationUtil.SINGLETON)
        metadata.hasAnnotation(Primary)
        metadata.hasStereotype(AnnotationUtil.QUALIFIER)
        !metadata.hasStereotype(AnnotationUtil.SINGLETON)
    }

    void "test parse inherited from class method stereotype data"() {

        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Test', '''\
package test;


@io.micronaut.context.annotation.Primary
class Test {
    @io.micronaut.context.annotation.Executable
    void testMethod() {}
}
''')
        def metadata = beanDefinition.getRequiredMethod("testMethod").getAnnotationMetadata()

        expect:
        metadata != null
        !metadata.hasDeclaredAnnotation(Primary)
        !metadata.hasDeclaredAnnotation(AnnotationUtil.SINGLETON)
        metadata.hasAnnotation(Primary)
        metadata.hasStereotype(AnnotationUtil.QUALIFIER)
        !metadata.hasStereotype(AnnotationUtil.SINGLETON)
    }

    void "test parse inherited from interface method stereotype data"() {

        given:
        AnnotationMetadata metadata = buildMethodAnnotationMetadata('''\
package test;

import java.lang.annotation.*;

class Test implements ITest {
    @Override
    public void testMethod() {}
}

interface ITest {
    @MyAnn
    void testMethod(); 
}

@io.micronaut.context.annotation.Primary
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@interface MyAnn {}
''', 'testMethod')

        expect:
        metadata != null
        !metadata.hasDeclaredAnnotation(Primary)
        !metadata.hasDeclaredAnnotation(AnnotationUtil.SINGLETON)
        !metadata.hasAnnotation(Primary)
        metadata.hasStereotype(Primary)
        metadata.hasStereotype(AnnotationUtil.QUALIFIER)
        !metadata.hasStereotype(AnnotationUtil.SINGLETON)
    }

    void "test a circular annotation is read correctly"() {
        given:
        AnnotationMetadata metadata = buildMethodAnnotationMetadata('''\
package test;

class Test {

    @io.micronaut.inject.annotation.Circular
    void testMethod() {}
}
''', 'testMethod')


        expect:
        metadata != null
        metadata.hasAnnotation(Circular)
    }

}
