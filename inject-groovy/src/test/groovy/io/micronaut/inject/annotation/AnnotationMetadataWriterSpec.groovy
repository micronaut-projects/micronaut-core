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

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.TypeHint

import jakarta.inject.Named
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import java.lang.annotation.Documented
import java.lang.annotation.Retention

/**
 * @author graemerocher
 * @since 1.0
 */
class AnnotationMetadataWriterSpec extends AbstractBeanDefinitionSpec {

    void "test inner annotations in metadata"() {
        given:
        def annotationMetadata = buildTypeAnnotationMetadata("inneranntest.Test","""
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

    void "test annotation metadata with primitive arrays"() {
        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata('annmetawriter1.Test','''\
package annmetawriter1;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.micronaut.inject.annotation.*;

@MyAnn(doubleArray=[1.1d])
class Test {
}


@Documented
@Retention(RUNTIME)
@Target([ElementType.TYPE])
@interface MyAnn {
    double[] doubleArray() default [];
    int[] intArray() default [];
    short[] shortArray() default [];
    boolean[] booleanArray() default [];
}

''')
        when:
        def className = "test"
        AnnotationMetadata metadata = writeAndLoadMetadata(className, toWrite)

        then:
        metadata != null
        metadata.getValue("annmetawriter1.MyAnn","doubleArray", Double[].class).get() == [1.1d] as Double[]
        metadata.getValue("annmetawriter1.MyAnn","doubleArray", double[].class).get() == [1.1d] as double[]
    }

    void "test read annotation on alias with primitive type"() {
        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata("annmetawriter2.Test",'''\
package annmetawriter2;

import io.micronaut.inject.annotation.MultipleAlias;

@MultipleAlias(primitiveAlias=10, bool=true)
class Test {
}
''')
        when:
        def className = "test"
        AnnotationMetadata metadata = writeAndLoadMetadata(className, toWrite)

        then:
        metadata != null
        metadata.hasAnnotation(MultipleAlias)
        metadata.getValue(Nested, "num", Integer).get() == 10
        metadata.getValue(Nested, "bool", Boolean).get()
    }

    void "test read annotation with annotation value"() {
        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata("annmetawriter3.Test",'''\
package annmetawriter3;

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
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata("annmetawriter4.Test", '''
package annmetawriter4;

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
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata("annmetawriter5.Test",'''
package annmetawriter5;

import io.micronaut.inject.annotation.*;

@EnumAnn(value = EnumAnn.MyEnum.TWO)
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

    void "test read enum constants array"() {
        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata("annmetawriter6.Test",'''
package annmetawriter6;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


@EnumAnn(value = [MyEnum.ONE, MyEnum.TWO])
class Test {
}

@Retention(RUNTIME)
@Target([ElementType.TYPE])
public @interface EnumAnn {
    MyEnum[] value();
}

enum MyEnum {
    ONE,
    TWO;
}
''')

        when:
        def className = "test"
        AnnotationMetadata metadata = writeAndLoadMetadata(className, toWrite)

        then:
        metadata.getValues("annmetawriter6.EnumAnn", Object.class).get("value").get() == ["ONE", "TWO"]
    }

    void "test read enum constants array with custom tostring"() {
        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata("annmetawriter7.Test",'''
package annmetawriter7;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.RetentionPolicy.RUNTIME;


@EnumAnn(value = [MyEnum.ONE, MyEnum.TWO])
class Test {
}

@Retention(RUNTIME)
@Target([ElementType.TYPE])
public @interface EnumAnn {
    MyEnum[] value();
}

enum MyEnum {
    ONE,
    TWO;

    @Override
    public String toString() {
        return this == ONE ? "1" : "2";
    }
}
''')

        when:
        def className = "test"
        AnnotationMetadata metadata = writeAndLoadMetadata(className, toWrite)

        then:
        metadata.getValues("annmetawriter7.EnumAnn", Object.class).get("value").get() == ["ONE", "TWO"]
    }

    void "test read external constants"() {
        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata("annmetawriter8.Test",'''\
package annmetawriter8;

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
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata("annmetawriter9.Test",'''\
package annmetawriter9;

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
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata("annmetawriter10.Test",'''\
package annmetawriter10;

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
        metadata.getValue(Requirements).get()[1].values.get('classes') == [new AnnotationClassValue('annmetawriter10.Test')] as AnnotationClassValue[]

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
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata("annmetawriter11.Test",'''\
package annmetawriter11;

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


    void "test basic argument metadata"() {
        given:
        AnnotationMetadata metadata = buildParameterAnnotationMetadata("annmetawriter12.Test", '''
package annmetawriter12;

@jakarta.inject.Singleton
class Test {

    void test(@jakarta.inject.Named("foo") String id) {

    }
}
''', 'test', 'id')

        expect:
        metadata != null
        !metadata.empty
        metadata.hasDeclaredAnnotation(Named)
        metadata.getValue(Named).get() == "foo"
    }

    void "test argument metadata inheritance"() {
        given:
        AnnotationMetadata metadata = buildParameterAnnotationMetadata("annmetawriter13.Test", '''
package annmetawriter13;

import java.lang.annotation.*;

@jakarta.inject.Singleton
class Test implements TestApi {

    @jakarta.annotation.PostConstruct
    @java.lang.Override
    public void test(String id) {

    }
}

interface TestApi {

    void test(@MyAnn String id);

}

@Inherited
@Retention(RetentionPolicy.RUNTIME)
@jakarta.inject.Named("foo")
@interface MyAnn {}
''', 'test', 'id')

        expect:
        metadata != null
        !metadata.empty
        !metadata.hasDeclaredAnnotation(Named)
        metadata.hasStereotype(Named)
        metadata.getValue(Named).get() == "foo"
    }

    void "test annotation metadata string value array types"() {
        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('annmetawriter14.Test', """
package annmetawriter14;

import io.micronaut.core.annotation.TypeHint;
import java.util.UUID;

@TypeHint([UUID[].class, UUID.class])
class Test {

}
""")

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
    }

    void "test defaults"() {
        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata("test.Test",'''\
package test;

@io.micronaut.inject.annotation.MyAnnotation
class Test {
}

''')
        when:
        AnnotationMetadata metadata = writeAndLoadMetadata("test", toWrite)
        def defaults = metadata.getDefaultValues("io.micronaut.inject.annotation.MyAnnotation")
        def av = metadata.getAnnotation("io.micronaut.inject.annotation.MyAnnotation")

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
        defaults["classesArray2"] == new AnnotationClassValue[] {new AnnotationClassValue(String.class)}
        defaults["classesArray3"] == new AnnotationClassValue[] {new AnnotationClassValue(String[].class)}
        defaults["classesArray4"] == new AnnotationClassValue[] {new AnnotationClassValue(String[][].class)}

            defaults["annotationsArray1"] == new AnnotationValue[0]
//   Default annotation values are crashing Groovy compiler
//        defaults["annotationsArray2"] == new AnnotationValue[] { AnnotationValue.builder(MyAnnotation3).value("foo").build(), AnnotationValue.builder(MyAnnotation3).value("bar").build() }
//        defaults["ann"] == AnnotationValue.builder(MyAnnotation3).value("foo").build()

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
            AnnotationMetadata toWrite = buildTypeAnnotationMetadata("test.Test", '''\
package test;

@io.micronaut.inject.annotation.MyAnnotation2Aliases(
        intArray1Alias = [],
        intArray2Alias = [1, 2, 3],
        stringArray1Alias = [],
        stringArray2Alias = "",
        stringArray3Alias = "A",
        myEnumArray1Alias = [],
        myEnumArray2Alias = io.micronaut.inject.annotation.MyEnum2.ABC,
        myEnumArray3Alias = [io.micronaut.inject.annotation.MyEnum2.FOO, io.micronaut.inject.annotation.MyEnum2.BAR],
        classesArray1Alias = [],
        classesArray2Alias = [String.class],
//        ann = @io.micronaut.inject.annotation.MyAnnotation3("foo"),
        annotationsArray1Alias = [],
        annotationsArray2Alias = [
                @io.micronaut.inject.annotation.MyAnnotation3("foo"),
                @io.micronaut.inject.annotation.MyAnnotation3("bar")
        ]
        )
class Test {
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
//   Default annotation values are crashing Groovy compiler
//            values["ann"] == AnnotationValue.builder(MyAnnotation3).value("foo").build()
            values["annotationsArray1"] == new AnnotationValue[0]
            values["annotationsArray2"] == new AnnotationValue[] { AnnotationValue.builder(MyAnnotation3).value("foo").build(), AnnotationValue.builder(MyAnnotation3).value("bar").build() }
    }

    void "test synthesize"() {
        when:
            def annotationValue = AnnotationValue.builder(jakarta.inject.Named.class).value("Denis").build()
        then:
            AnnotationMetadataSupport.buildAnnotation(jakarta.inject.Named.class, annotationValue).value() == "Denis"
    }
}
