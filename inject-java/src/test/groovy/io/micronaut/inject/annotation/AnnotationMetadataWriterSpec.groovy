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

import io.micrometer.core.annotation.Timed
import io.micronaut.aop.Around
import io.micronaut.aop.introduction.StubIntroducer
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Type
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.retry.annotation.Recoverable

import javax.inject.Qualifier
import javax.inject.Scope
import javax.inject.Singleton
import java.lang.annotation.Documented
import java.lang.annotation.Retention

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class AnnotationMetadataWriterSpec extends AbstractTypeElementSpec {

    void "test javax nullable on field"() {
        given:
        AnnotationMetadata metadata = buildMethodAnnotationMetadata('''\
package test;

import edu.umd.cs.findbugs.annotations.Nullable;

class Test {
    @Nullable
    void testMethod() {}
}
''', 'testMethod')



        expect:
        metadata != null
        metadata.declaredAnnotationNames.size() == 1
        metadata.declaredStereotypes == null
        metadata.annotationNames.size() == 1
    }

    void "test write annotation metadata with primitive arrays"() {
        given:
        AnnotationMetadata toWrite = new DefaultAnnotationMetadata(
                [
                        "io.micrometer.core.annotation.Timed": [
                                percentiles: [1.1d] as double[]
                        ]

                ], null, null, [
                "io.micrometer.core.annotation.Timed": [
                        percentiles: [1.1d] as double[]
                ]

        ], null
        )
        when:
        def className = "test"
        AnnotationMetadata metadata = writeAndLoadMetadata(className, toWrite)

        then:
        metadata != null
        metadata.getValue(Timed.name, "percentiles", double[].class).get() == [1.1d] as double[]
    }


    void "test annotation metadata with instantiated member"() {
        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata('''\
package test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.micronaut.inject.annotation.*;
import io.micronaut.core.annotation.*;

@MyAnn(ToInstantiate.class)
class Test {
}

class SomeType {}

@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE})
@interface MyAnn {
    @InstantiatedMember
    Class value();
}

''')
        when:
        def className = "test"
        AnnotationMetadata metadata = writeAndLoadMetadata(className, toWrite)

        then:
        metadata != null
        metadata.getValue("test.MyAnn", ToInstantiate).get() instanceof ToInstantiate
    }

    void "test annotation metadata with primitive arrays"() {
        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata('''\
package test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.micronaut.inject.annotation.*;

@MyAnn(doubleArray={1.1d})
@io.micrometer.core.annotation.Timed(percentiles={1.1d})
class Test {
}


@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE})
@interface MyAnn {
    double[] doubleArray() default {};
    int[] intArray() default {};
    short[] shortArray() default {};
    boolean[] booleanArray() default {};
}

''')
        when:
        def className = "test"
        AnnotationMetadata metadata = writeAndLoadMetadata(className, toWrite)

        then:
        metadata != null
        metadata.getValue(Timed, "percentiles", double[].class).get() == [1.1d] as double[]
        metadata.doubleValue(Timed,"percentiles").asDouble == 1.1d
        metadata.getValue("test.MyAnn", "doubleArray", double[].class).get() == [1.1d] as double[]
    }

    void "test annotation metadata inherited stereotypes"() {
        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata('''\
package test;

import io.micronaut.inject.annotation.*;

@MyStereotype
class Test {
}
''')
        when:
        def className = "test"
        AnnotationMetadata metadata = writeAndLoadMetadata(className, toWrite)

        then:
        metadata != null
        metadata.hasAnnotation(MyStereotype)
        // the value of @Type should be the the one declared on the the @MyStereotype annotation not the one declared on @Recoverable
        metadata.getValue(Type.class, String.class).get() == StubIntroducer.class.getName()
        metadata.stringValue(Type.class).get() == StubIntroducer.class.getName()
        // the stereotypes should include meta annotation stereotypes
        metadata.getAnnotationNamesByStereotype(Around.class).contains(Recoverable.class.getName())
    }
    
    void "test read annotation with annotation value"() {
        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata('''\
package test;

import io.micronaut.inject.annotation.*;

@TopLevel(nested=@Nested(num=10))
class Test {
}
''')
        when:
        def className = "test"
        AnnotationMetadata metadata = writeAndLoadMetadata(className, toWrite)

        then:
        metadata != null
        metadata.hasAnnotation(TopLevel)
        metadata.getValue(TopLevel, "nested").isPresent()
        metadata.getValue(TopLevel, "nested", Nested).isPresent()
        metadata.getValue(TopLevel, "nested", Nested).get().num() == 10

        when:
        TopLevel topLevel = metadata.synthesize(TopLevel)

        then:
        topLevel.nested().num() == 10
    }

    void "test read enum constants"() {
        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata('''\
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.AnnotationMetadata;
@Requires(sdk=Requires.Sdk.JAVA, version="1.8")
class Test {
}
''')

        when:
        def className = "test"
        AnnotationMetadata metadata = writeAndLoadMetadata(className, toWrite)

        then:
        metadata != null
        metadata.enumValue(Requires, "sdk", Requires.Sdk).get() == Requires.Sdk.JAVA
        metadata.getValue(Requires, "sdk", Requires.Sdk).get() == Requires.Sdk.JAVA
        metadata.getValue(Requires, "version").get() == "1.8"
    }

    void "test read enum constants with custom toString()"() {
        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata('''\
package test;

import io.micronaut.inject.annotation.*;

@EnumAnn(EnumAnn.MyEnum.TWO)
class Test {
}
''')

        when:
        def className = "test"
        AnnotationMetadata metadata = writeAndLoadMetadata(className, toWrite)

        then:
        metadata != null
        metadata.synthesize(EnumAnn).value() == EnumAnn.MyEnum.TWO
        metadata.enumValue(EnumAnn, EnumAnn.MyEnum).get() == EnumAnn.MyEnum.TWO
        metadata.enumValue(EnumAnn, "value", EnumAnn.MyEnum).get() == EnumAnn.MyEnum.TWO
        metadata.enumValue(EnumAnn.name, EnumAnn.MyEnum).get() == EnumAnn.MyEnum.TWO
        metadata.enumValue(EnumAnn.name, "value", EnumAnn.MyEnum).get() == EnumAnn.MyEnum.TWO
    }

    void "test read external constants"() {
        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata('''\
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.AnnotationMetadata;
@Requires(property=AnnotationMetadata.VALUE_MEMBER)
class Test {
}
''')

        when:
        def className = "test"
        AnnotationMetadata metadata = writeAndLoadMetadata(className, toWrite)

        then:
        metadata != null
        metadata.getValue(Requires, "property").isPresent()
        metadata.getValue(Requires, "property").get() == 'value'
    }

    void "test read constants defined in class"() {
        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata('''\
package test;

import io.micronaut.context.annotation.*;

@Requires(property=Test.TEST)
class Test {
    public static final String TEST = "blah";
}
''')

        when:
        def className = "test"
        AnnotationMetadata metadata = writeAndLoadMetadata(className, toWrite)

        then:
        metadata != null
        metadata.getValue(Requires, "property").isPresent()
        metadata.getValue(Requires, "property").get() == 'blah'

    }

    void "test build repeatable annotations"() {
        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata('''\
package test;

import io.micronaut.context.annotation.*;

@Requires(property="blah")
@Requires(classes=Test.class)
class Test {
}
''')

        when:
        def className = "test"
        AnnotationMetadata metadata = writeAndLoadMetadata(className, toWrite)

        then:
        metadata != null
        metadata.hasDeclaredAnnotation(Requirements)
        metadata.getValue(Requirements).get().size() == 2
        metadata.getValue(Requirements).get()[0] instanceof io.micronaut.core.annotation.AnnotationValue
        metadata.getValue(Requirements).get()[0].values.get('property') == 'blah'
        metadata.getValue(Requirements).get()[1] instanceof io.micronaut.core.annotation.AnnotationValue
        metadata.getValue(Requirements).get()[1].values.get('classes') == [new AnnotationClassValue<>('test.Test')] as AnnotationClassValue[]

        when:
        Requires[] requires = metadata.synthesize(Requirements).value()

        then:
        requires.size() == 2
        requires[0].property() == 'blah'

        when:
        requires = metadata.synthesizeAnnotationsByType(Requires)

        then:
        requires.size() == 2
        requires[0].property() == 'blah'

    }

    void "test write first level stereotype data"() {

        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata('''\
package test;

@io.micronaut.context.annotation.Primary
class Test {
}
''')


        when:
        def className = "test"
        AnnotationMetadata metadata = writeAndLoadMetadata(className, toWrite)

        then:
        metadata.synthesize(Primary) instanceof Primary
        metadata.synthesizeDeclared().size() == 1
        metadata != null
        metadata.hasDeclaredAnnotation(Primary)
        !metadata.hasDeclaredAnnotation(Singleton)
        metadata.hasAnnotation(Primary)
        !metadata.hasStereotype(Documented) // ignore internal annotations
        !metadata.hasStereotype(Retention) // ignore internal annotations
        metadata.hasStereotype(Qualifier)
        !metadata.hasStereotype(Singleton)
    }

    void "test write annotation metadata default values"() {

        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata('''\
package test;

@io.micronaut.inject.annotation.Trace(type = Test.class, types = {Test.class})
class Test {
}
''')

        when:
        def className = "test"
        AnnotationMetadata metadata = writeAndLoadMetadata(className, toWrite)

        then:
        metadata != null
        metadata.hasAnnotation(Trace)
        metadata.isFalse(Around, 'hotswap')
        metadata.isFalse(Trace, 'something')
        metadata.getValue(SomeOther, "testDefault", String).get() == 'foo'
        !metadata.isPresent(SomeOther, "testDefault")
    }

    void "test write inherited stereotype data attributes"() {

        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata('''\
package test;

@io.micronaut.inject.annotation.Trace(type = Test.class, types = {Test.class}, something = true)
class Test {
}
''')

        when:
        def className = "test"
        AnnotationMetadata metadata = writeAndLoadMetadata(className, toWrite)

        then:
        metadata != null
        metadata.hasAnnotation(Trace)
        metadata.getValue(Trace, "type").isPresent()
        metadata.getValue(Trace, "type").get() == new AnnotationClassValue('test.Test')
        metadata.getValue(Trace, "types").get() == [new AnnotationClassValue<>('test.Test')] as AnnotationClassValue[]
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

    void "test write super class inherited interface stereotype data attributes"() {

        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata('''\
package test;


class Test extends SuperTest{
}

class SuperTest implements ITest {}

@io.micronaut.inject.annotation.Trace(type = Test.class, types = {Test.class}, something = true)
interface ITest {

}
''')

        when:
        def className = "test"
        AnnotationMetadata metadata = writeAndLoadMetadata(className, toWrite)

        then:
        metadata != null
        metadata.hasAnnotation(Trace)
        metadata.getValue(Trace, "type").isPresent()
        metadata.getValue(Trace, "type").get() == new AnnotationClassValue('test.Test')
        metadata.getValue(Trace, "types").get() == [new AnnotationClassValue<>('test.Test')] as AnnotationClassValue[]
        metadata.hasStereotype(Trace)
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
        metadata.getAnnotationNamesByStereotype(Around.name) == [Trace.name, SomeOther.name]
    }

    void "test repeatable annotations are combined"() {
        AnnotationMetadata toWrite = buildMethodAnnotationMetadata('''\
package test;

import io.micronaut.inject.annotation.repeatable.*;
import io.micronaut.context.annotation.*;

@Property(name="prop1", value="value1")
@Property(name="prop2", value="value2")
@Property(name="prop3", value="value3")
@javax.inject.Singleton
class Test {

    @Property(name="prop2", value="value2")    
    @Property(name="prop3", value="value33")    
    @Property(name="prop4", value="value4")    
    void someMethod() {}
}
''', 'someMethod')

        when:
        def className = "test"
        AnnotationMetadata metadata = writeAndLoadMetadata(className, toWrite)

        then:
        List<AnnotationValue<Property>> properties = metadata.getAnnotationValuesByType(Property)

        then:
        properties.size() == 5
        properties[0].get("name", String).get() == "prop2"
        properties[1].get("name", String).get() == "prop3"
        properties[1].getValue(String).get() == "value33"
        properties[2].get("name", String).get() == "prop4"
        properties[3].get("name", String).get() == "prop1"
        properties[4].get("name", String).get() == "prop3"
        properties[4].getValue(String).get() == "value3"
    }

}
