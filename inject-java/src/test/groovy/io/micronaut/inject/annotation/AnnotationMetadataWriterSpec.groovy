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

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Around
import io.micronaut.aop.introduction.StubIntroducer
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Type
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.TypeHint
import io.micronaut.inject.BeanDefinition
import io.micronaut.retry.annotation.Recoverable
import spock.lang.Unroll

import java.lang.annotation.Documented
import java.lang.annotation.Retention
/**
 * @author Graeme Rocher
 * @since 1.0
 */
class AnnotationMetadataWriterSpec extends AbstractTypeElementSpec {

    void "test inner annotations in metadata"() {
        given:
        def annotationMetadata = buildTypeAnnotationMetadata("""
package inneranntest;

import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.*;
import io.micronaut.inject.annotation.Outer;

@Outer.Inner
class Test {

}
""")

        annotationMetadata = writeAndLoadMetadata('annmetadatatest.Test', annotationMetadata)

        expect:
        annotationMetadata.hasAnnotation(Outer.Inner)
        annotationMetadata.hasDeclaredAnnotation(Outer.Inner)
    }

    @Unroll
    void "test read/write annotation array type #type"() {
        given:
        def annotationMetadata = buildTypeAnnotationMetadata("""
package annmetadatatest;

import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.*;

@TestAnn(${code})
class Test {
}

@interface TestAnn {
    ${method}[] value();
}
""")
        annotationMetadata = writeAndLoadMetadata('annmetadatatest.Test', annotationMetadata)
        AnnotationValue<?> av = annotationMetadata.getAnnotation("annmetadatatest.TestAnn")

        expect:
        av != null
        av."${method.toLowerCase()}Values"(AnnotationMetadata.VALUE_MEMBER) == val

        where:
        code                            | val                          | method    | type
        "{true, false}"                 | [true, false] as boolean[]   | "boolean" | boolean[].class
        "{1, 2}"                        | [1, 2] as byte[]             | "byte"    | byte[].class
        "{'a' , 'b'}"                   | ['a', 'b'] as char[]         | "char"    | char[].class
        "{1.1d, 2.2d}"                  | [1.1d, 2.2d] as double[]     | "double"  | double[].class
        "{1.1f, 2.2f}"                  | [1.1f, 2.2f] as float[]      | "float"   | float[].class
        "{10, 20}"                      | [10, 20] as int[]            | "int"     | int[].class
        "{30, 40}"                      | [30, 40] as long[]           | "long"    | long[].class
        "{5, 10}"                       | [5, 10] as short[]           | "short"   | short[].class
        '{"one", "two"}'                | ["one", "two"] as String[]   | "String"  | String[].class
        '{String.class, Integer.class}' | [String, Integer] as Class[] | "Class"   | Class[].class
    }

    void "test javax nullable on field"() {
        given:
        AnnotationMetadata metadata = buildMethodAnnotationMetadata('''\
package test;

import io.micronaut.core.annotation.Nullable;

class Test {
    @Nullable
    void testMethod() {}
}
''', 'testMethod')


        expect:
        metadata != null
        metadata.declaredAnnotationNames.size() == 1
        metadata.declaredStereotypeAnnotationNames.size() == 0
        metadata.annotationNames.size() == 1
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
        metadata.getValue("test.MyAnn", "doubleArray", double[].class).get() == [1.1d] as double[]
        metadata.doubleValue("test.MyAnn", "doubleArray").asDouble == 1.1d
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
        !metadata.hasDeclaredAnnotation(AnnotationUtil.SINGLETON)
        metadata.hasAnnotation(Primary)
        !metadata.hasStereotype(Documented) // ignore internal annotations
        !metadata.hasStereotype(Retention) // ignore internal annotations
        metadata.hasStereotype(AnnotationUtil.QUALIFIER)
        !metadata.hasStereotype(AnnotationUtil.SINGLETON)
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

    void "test repeatable annotations are combined"() {
        BeanDefinition definition = buildBeanDefinition('test.Test', '''\
package test;

import io.micronaut.inject.annotation.repeatable.*;
import io.micronaut.context.annotation.*;

@Property(name="prop1", value="value1")
@Property(name="prop2", value="value2")
@Property(name="prop3", value="value3")
@jakarta.inject.Singleton
class Test {

    @Property(name="prop2", value="value2")
    @Property(name="prop3", value="value33")
    @Property(name="prop4", value="value4")
    @io.micronaut.context.annotation.Executable
    void someMethod() {}
}
''')

        when:
        AnnotationMetadata metadata = definition.getRequiredMethod("someMethod").getAnnotationMetadata()

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

    void "test repeatable annotations are combined, lookup by name"() {
        BeanDefinition definition = buildBeanDefinition('test.Test', '''\
package test;

import io.micronaut.inject.annotation.repeatable.*;
import io.micronaut.context.annotation.*;

@Property(name="prop1", value="value1")
@Property(name="prop2", value="value2")
@Property(name="prop3", value="value3")
@jakarta.inject.Singleton
class Test {

    @Property(name="prop2", value="value2")
    @Property(name="prop3", value="value33")
    @Property(name="prop4", value="value4")
    @io.micronaut.context.annotation.Executable
    void someMethod() {}
}
''')

        when:
        AnnotationMetadata metadata = definition.getRequiredMethod("someMethod").getAnnotationMetadata()

        then:
        List<AnnotationValue<?>> properties = metadata.getAnnotationValuesByName(Property.name)

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

    void "test declared repeatable annotations are combined, lookup by name"() {
        BeanDefinition definition = buildBeanDefinition('test.Test', '''\
package test;

import io.micronaut.inject.annotation.repeatable.*;
import io.micronaut.context.annotation.*;

@Property(name="prop1", value="value1")
@Property(name="prop2", value="value2")
@Property(name="prop3", value="value3")
@jakarta.inject.Singleton
class Test {

    @Property(name="prop2", value="value2")
    @Property(name="prop3", value="value33")
    @Property(name="prop4", value="value4")
    @io.micronaut.context.annotation.Executable
    void someMethod() {}
}
''')

        when:
        AnnotationMetadata metadata = definition.getRequiredMethod("someMethod").getAnnotationMetadata()

        then:
        List<AnnotationValue<?>> properties = metadata.getDeclaredAnnotationValuesByName(Property.name)

        then:
        properties.size() == 3
        properties[0].get("name", String).get() == "prop2"
        properties[1].get("name", String).get() == "prop3"
        properties[1].getValue(String).get() == "value33"
        properties[2].get("name", String).get() == "prop4"
    }

    void "test read beandef annotation with a default annotation value"() {
        when:
            BeanDefinition definition = buildBeanDefinition('test.Test', '''\
package test;

import io.micronaut.inject.annotation.*;
import jakarta.inject.Singleton;

@Singleton
@TopLevel2
class Test {
}
''')
            AnnotationMetadata metadata = definition.getAnnotationMetadata()

        then:
            AnnotationValue nestedAnnotation = metadata.getAnnotation(TopLevel2).getDefaultValues().get("nested")
            nestedAnnotation.annotationName == "io.micronaut.inject.annotation.Nested"
            nestedAnnotation.getDefaultValues().get("num") == 10

    }

    void "test annotation metadata string value array types"() {
        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''
package test;

import io.micronaut.core.annotation.TypeHint;
import java.util.UUID;

@TypeHint({UUID[].class, UUID.class})
class Test {

}
''')

        expect:
        metadata.stringValues(TypeHint).size() == 2
        metadata.stringValues(TypeHint)[0] == '[Ljava.util.UUID;'
        metadata.stringValues(TypeHint)[1] == 'java.util.UUID'

        when:
        def className = "test"
        metadata = writeAndLoadMetadata(className, metadata)

        then:
        metadata.stringValues(TypeHint).size() == 2
        metadata.stringValues(TypeHint)[0] == '[Ljava.util.UUID;'
        metadata.stringValues(TypeHint)[1] == 'java.util.UUID'
        metadata.classValues(TypeHint)[0] == UUID[].class
        metadata.classValues(TypeHint)[1] == UUID.class
    }

    void "test defaults"() {
        given:
            AnnotationMetadata toWrite = buildTypeAnnotationMetadata('''\
package test;

@io.micronaut.inject.annotation.MyAnnotation2(intArray3 = 1, stringArray4 = "X", boolArray4 = false, myEnumArray4 = io.micronaut.inject.annotation.MyEnum2.FOO)
class Test {
}

''')
        when:
            AnnotationMetadata metadata = writeAndLoadMetadata("test", toWrite)
            def defaults = metadata.getDefaultValues("io.micronaut.inject.annotation.MyAnnotation2")
            def av = metadata.getAnnotation("io.micronaut.inject.annotation.MyAnnotation2")

        then:
            defaults["num"] == 10
            defaults["bool"] == false
            defaults["intArray1"] == new int[] {}
            defaults["intArray2"] == new int[] {1, 2, 3}
            defaults["intArray3"] == null
            defaults["stringArray1"] == new String[] {}
            defaults["stringArray2"] == new String[] {""}
            defaults["stringArray3"] == new String[] {"A"}
            defaults["stringArray4"] == null
            defaults["boolArray1"] == new boolean[] {}
            defaults["boolArray2"] == new boolean[] {true}
            defaults["boolArray3"] == new boolean[] {false}
            defaults["boolArray4"] == null
            defaults["myEnumArray1"] == new String[] {}
            defaults["myEnumArray2"] == new String[] {"ABC"}
            defaults["myEnumArray3"] == new String[] {"FOO", "BAR"}
            defaults["myEnumArray4"] == null
            defaults["classesArray1"] == new AnnotationClassValue[0]
            defaults["classesArray2"] == new AnnotationClassValue[] {new AnnotationClassValue(String)}
            defaults["ann"] == AnnotationValue.builder(MyAnnotation3).value("foo").build()
            defaults["annotationsArray1"] == new AnnotationValue[0]
            defaults["annotationsArray2"] == new AnnotationValue[] { AnnotationValue.builder(MyAnnotation3).value("foo").build(), AnnotationValue.builder(MyAnnotation3).value("bar").build() }

            av.getRequiredValue("num", Integer.class) == 10
            av.getRequiredValue("bool", Boolean.class) == false
            av.getRequiredValue("intArray1", int[].class) == new int[] {}
            av.getRequiredValue("intArray2", int[].class) == new int[] {1, 2, 3}
            av.getRequiredValue("stringArray1", String[].class) == new String[] {}
            av.getRequiredValue("stringArray2", String[].class) == new String[] {""}
            av.getRequiredValue("stringArray3", String[].class) == new String[] {"A"}
            av.getRequiredValue("myEnumArray1", String[].class) == new String[] {}
            av.getRequiredValue("myEnumArray2", String[].class) == new String[] {"ABC"}
            av.getRequiredValue("myEnumArray3", String[].class) == new String[] {"FOO", "BAR"}
    }

    void "test aliases"() {
        given:
            AnnotationMetadata toWrite = buildTypeAnnotationMetadata('''\
package test;

@io.micronaut.inject.annotation.MyAnnotation2Aliases(
        intArray1Alias = {},
        intArray2Alias = {1, 2, 3},
        stringArray1Alias = {},
        stringArray2Alias = "",
        stringArray3Alias = "A",
        myEnumArray1Alias = {},
        myEnumArray2Alias = io.micronaut.inject.annotation.MyEnum2.ABC,
        myEnumArray3Alias = {io.micronaut.inject.annotation.MyEnum2.FOO, io.micronaut.inject.annotation.MyEnum2.BAR},
        classesArray1Alias = {},
        classesArray2Alias = {String.class},
        annAlias = @io.micronaut.inject.annotation.MyAnnotation3("foo"),
        annotationsArray1Alias = {},
        annotationsArray2Alias = {
                @io.micronaut.inject.annotation.MyAnnotation3("foo"),
                @io.micronaut.inject.annotation.MyAnnotation3("bar")
        }
)class Test {
}

''')
        when:
            AnnotationMetadata metadata = writeAndLoadMetadata("test", toWrite)
            def values = metadata.getValues("io.micronaut.inject.annotation.MyAnnotation2Aliases")
            def av = metadata.getAnnotation("io.micronaut.inject.annotation.MyAnnotation2Aliases")

        then:
            values["intArray1"] == new int[] {}
            values["intArray2"] == new int[] {1, 2, 3}
            values["stringArray1"] == new String[] {}
            values["stringArray2"] == new String[] {""}
            values["stringArray3"] == new String[] {"A"}
            values["myEnumArray1"] == new String[] {}
            values["myEnumArray2"] == new String[] {"ABC"}
            values["myEnumArray3"] == new String[] {"FOO", "BAR"}
            values["classesArray1"] == new AnnotationClassValue[0]
            values["classesArray2"] == new AnnotationClassValue[] {new AnnotationClassValue(String)}
            values["ann"] == AnnotationValue.builder(MyAnnotation3).value("foo").build()
            values["annotationsArray1"] == new AnnotationValue[0]
            values["annotationsArray2"] == new AnnotationValue[] { AnnotationValue.builder(MyAnnotation3).value("foo").build(), AnnotationValue.builder(MyAnnotation3).value("bar").build() }
    }

}
