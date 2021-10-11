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
package io.micronaut.ast.groovy.annotation

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.annotation.MultipleAlias
import io.micronaut.aop.Around
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.runtime.context.scope.Refreshable
import io.micronaut.runtime.context.scope.ScopedProxy
import spock.lang.Ignore

import javax.inject.Qualifier
import javax.inject.Scope
import javax.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class GroovyAnnotationMetadataBuilderSpec extends AbstractBeanDefinitionSpec {

    void "test multiple alias definitions with value"() {
        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('annbuild1.Multi', '''\
package annbuild1;

import io.micronaut.inject.annotation.*;

@MultipleAlias("test")
class Multi {
}
''', )
        expect:
        metadata != null
        metadata.hasDeclaredStereotype(ConfigurationReader)
        metadata.getValue(ConfigurationReader, String).get() == 'test'
        metadata.hasDeclaredAnnotation(MultipleAlias)
        metadata.getValue(MultipleAlias, String).get() == 'test'
        metadata.getValue(MultipleAlias, "id", String).get() == 'test'
    }
    void "test annotation names by stereotype"() {
        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('annbuild2.Test','''\
package annbuild2;

import io.micronaut.runtime.context.scope.*;
@Refreshable
class Test {
}
''')

        expect:
        metadata != null
        metadata.getAnnotationNamesByStereotype(Around).contains(ScopedProxy.name)
    }


    void "test read external constants"() {
        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('annbuild3.Test','''\
package annbuild3;

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


    void "test read lists simple"() {
        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('annbuild4.Test','''\
package annbuild4;
import io.micronaut.context.annotation.*;

@Requires(env=['foo'])
class Test {
}
''')

        expect:
        metadata != null
        metadata.getValue(Requires, "env").isPresent()
        metadata.getValue(Requires, "env").get() == ['foo']
    }


    void "test read constants"() {
        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('annbuild5.Test', '''\
package annbuild5;

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


    @Ignore // Support for closure values not yet supported
    void "test build annotation with closure value"() {
        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('annbuild6.Test','''\
package annbuild6;

import io.micronaut.context.annotation.*;

@Requires(condition= { true })
class Test {
}
''')

        expect:
        metadata != null
        metadata.hasDeclaredAnnotation(Requires)
        metadata.isPresent(Requires, 'condition')
        Closure.isAssignableFrom( metadata.synthesize(Requires).condition() )
    }

    void "test build repeatable annotations"() {
        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('annbuild7.Test','''\
package annbuild7;

import io.micronaut.context.annotation.*;

@Requirements([
@Requires(property="blah"),
@Requires(classes=Test.class) ])
class Test {
}
''')

        expect:
        metadata != null
        metadata.hasDeclaredAnnotation(Requirements)
        metadata.getValue(Requirements).get().size() == 2
        metadata.getValue(Requirements).get()[0] instanceof AnnotationValue
        metadata.getValue(Requirements).get()[0].values.get('property') == 'blah'
        metadata.getValue(Requirements).get()[1] instanceof AnnotationValue
        metadata.getValue(Requirements).get()[1].values.get('classes') == [new AnnotationClassValue('annbuild7.Test')] as AnnotationClassValue[]
    }

    void "test parse first level stereotype data"() {

        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata("annbuild8.Test",'''\
package annbuild8;

@io.micronaut.context.annotation.Primary
class Test {
}
''')

        expect:
        metadata != null
        metadata.hasDeclaredAnnotation(Primary)
        !metadata.hasDeclaredAnnotation(Singleton)
        metadata.hasAnnotation(Primary)
        metadata.hasStereotype(Qualifier)
        !metadata.hasStereotype(Singleton)
    }

    void "test parse inherited stereotype data attributes default values"() {

        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata("annbuild9.Test",'''\
package annbuild9;

@io.micronaut.ast.groovy.annotation.Trace(type = Test.class, types = [Test.class])
class Test {
}
''')

        expect:
        metadata != null
        metadata.hasAnnotation(Trace)
        metadata.isFalse(Around, 'hotswap')
        metadata.isFalse(Trace, 'something')
        !metadata.isPresent(Around, 'hotswap')
        !metadata.isPresent(Trace, 'something')
    }

    void "test parse inherited stereotype data attributes"() {

        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata("annbuild10.Test",'''\
package annbuild10;

@io.micronaut.ast.groovy.annotation.Trace(type = Test.class, types = [Test.class], something = true)
class Test {
}
''')

        expect:
        metadata != null
        metadata.hasAnnotation(Trace)
        metadata.getValue(Trace, "type").isPresent()
        metadata.getValue(Trace, "type").get() == new AnnotationClassValue('annbuild10.Test')
        metadata.getValue(Trace, "types").get() == [new AnnotationClassValue('annbuild10.Test')] as AnnotationClassValue[]
        metadata.hasStereotype(Trace)
        metadata.hasDeclaredAnnotation(Trace)
        metadata.hasStereotype(Around)
        metadata.hasStereotype(SomeOther)
        metadata.hasStereotype(Scope)
        !metadata.hasDeclaredAnnotation(Scope)
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
        AnnotationMetadata metadata = buildTypeAnnotationMetadata( "annbuild11.Test",'''\
package annbuild11;


class Test implements ITest{
}

@io.micronaut.ast.groovy.annotation.Trace(type = Test.class, types = [Test.class], something = true)
interface ITest {

}
''')

        expect:
        metadata != null
        metadata.hasAnnotation(Trace)
        metadata.getValue(Trace, "type").isPresent()
        metadata.getValue(Trace, "type").get() == new AnnotationClassValue('annbuild11.Test')
        metadata.getValue(Trace, "types").get() == [new AnnotationClassValue('annbuild11.Test')] as AnnotationClassValue[]
        metadata.hasStereotype(Trace)
        !metadata.hasDeclaredAnnotation(Trace)
        !metadata.hasDeclaredStereotype(Trace)
        metadata.hasStereotype(Around)
        metadata.hasStereotype(SomeOther)
        metadata.hasStereotype(Scope)
        !metadata.hasDeclaredAnnotation(Scope)
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
        AnnotationMetadata metadata = buildTypeAnnotationMetadata("annbuild12.Test",'''\
package annbuild12;


class Test extends SuperTest{
}

@io.micronaut.ast.groovy.annotation.Trace(type = SuperTest.class, types = [SuperTest.class], something = true)
class SuperTest {

}
''')

        expect:
        metadata != null
        metadata.hasAnnotation(Trace)
        metadata.getValue(Trace, "type").isPresent()
        metadata.getValue(Trace, "type").get() == new AnnotationClassValue('annbuild12.SuperTest')
        metadata.getValue(Trace, "types").get() == [new AnnotationClassValue('annbuild12.SuperTest')] as AnnotationClassValue[]
        metadata.hasStereotype(Trace)
        !metadata.hasDeclaredAnnotation(Trace)
        !metadata.hasDeclaredStereotype(Trace)
        metadata.hasStereotype(Around)
        metadata.hasStereotype(SomeOther)
        metadata.hasStereotype(Scope)
        !metadata.hasDeclaredAnnotation(Scope)
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
        AnnotationMetadata metadata = buildTypeAnnotationMetadata("annbuild13.Test",'''\
package annbuild13;

@io.micronaut.ast.groovy.annotation.Trace(type = Test.class, types = [Test.class], something = false)
class Test extends SuperTest{
}

@io.micronaut.ast.groovy.annotation.Trace(type = SuperTest.class, types = [SuperTest.class], something = true)
class SuperTest {

}
''')

        expect:
        metadata != null
        metadata.hasAnnotation(Trace)
        metadata.getValue(Trace, "type").isPresent()
        metadata.getValue(Trace, "type").get() == new AnnotationClassValue('annbuild13.Test')
        metadata.getValue(Trace, "types").get() == [new AnnotationClassValue('annbuild13.Test')] as AnnotationClassValue[]
        metadata.hasStereotype(Trace)
        metadata.hasDeclaredStereotype(Trace)
        metadata.hasDeclaredAnnotation(Trace)
        metadata.hasStereotype(Around)
        metadata.hasStereotype(SomeOther)
        metadata.hasStereotype(Scope)
        !metadata.hasDeclaredAnnotation(Scope)
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
        AnnotationMetadata metadata = buildTypeAnnotationMetadata("annbuild14.Test",'''\
package annbuild14;


class Test extends SuperTest{
}

class SuperTest implements ITest {}

@io.micronaut.ast.groovy.annotation.Trace(type = Test.class, types = [Test.class], something = true)
interface ITest {

}
''')

        expect:
        metadata != null
        metadata.hasAnnotation(Trace)
        metadata.getValue(Trace, "type").isPresent()
        metadata.getValue(Trace, "type").get() == new AnnotationClassValue('annbuild14.Test')
        metadata.getValue(Trace, "types").get() == [new AnnotationClassValue('annbuild14.Test')] as AnnotationClassValue[]
        metadata.hasStereotype(Trace)
        !metadata.hasDeclaredStereotype(Trace)
        !metadata.hasDeclaredAnnotation(Trace)
        metadata.hasStereotype(Around)
        metadata.hasStereotype(SomeOther)
        metadata.hasStereotype(Scope)
        !metadata.hasDeclaredAnnotation(Scope)
        !metadata.hasDeclaredAnnotation(Around)
        metadata.getValue(Around, 'hotswap').isPresent()
        metadata.isTrue(Around, 'hotswap')
        metadata.getValue(Around, 'proxyTarget').isPresent()
        metadata.getValue(Around, 'lazy').isPresent()
        metadata.isTrue(Around, 'proxyTarget')
        metadata.isFalse(Around, 'lazy')
    }


    void "test parse first level method stereotype data"() {

        given:
        AnnotationMetadata metadata = buildMethodAnnotationMetadata("annbuild15.Test",'''\
package annbuild15;


class Test {
    @io.micronaut.context.annotation.Primary
    void testMethod() {}
}
''', 'testMethod')

        expect:
        metadata != null
        metadata.hasDeclaredAnnotation(Primary)
        !metadata.hasDeclaredAnnotation(Singleton)
        metadata.hasAnnotation(Primary)
        metadata.hasStereotype(Qualifier)
        !metadata.hasStereotype(Singleton)
    }

    void "test parse inherited from class method stereotype data"() {

        given:
        AnnotationMetadata metadata = buildBeanDefinition("annbuild16.Test",'''\
package annbuild16;


@io.micronaut.context.annotation.Primary
@io.micronaut.context.annotation.Bean
class Test {
    @io.micronaut.context.annotation.Executable
    void testMethod() {}
}
''').getRequiredMethod("testMethod").getAnnotationMetadata()

        expect:
        metadata != null
        !metadata.hasDeclaredAnnotation(Primary)
        !metadata.hasDeclaredAnnotation(Singleton)
        metadata.hasAnnotation(Primary)
        metadata.hasStereotype(Qualifier)
        !metadata.hasStereotype(Singleton)
    }

    void "test parse inherited from interface method stereotype data"() {

        given:
        AnnotationMetadata metadata = buildMethodAnnotationMetadata("annbuild17.Test",'''\
package annbuild17;

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

    void "test array annotation value"() {
        given:
        AnnotationMetadata metadata = buildMethodAnnotationMetadata('annbuild18.Test', '''\
package annbuild18;

import io.micronaut.inject.annotation.*;
import io.micronaut.ast.groovy.annotation.*;
class Test {
    @Parent(child = @Child())
    void testMethod() {}
}
''', 'testMethod')

        expect:
        metadata != null
        metadata.hasDeclaredAnnotation(Parent)
        metadata.getValue(Parent, "child").get().getClass().isArray()
    }
}
