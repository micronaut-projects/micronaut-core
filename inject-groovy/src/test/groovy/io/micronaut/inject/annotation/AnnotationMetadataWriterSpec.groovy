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

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.TypeHint

import javax.inject.Named
import javax.inject.Qualifier
import javax.inject.Singleton
import java.lang.annotation.Documented
import java.lang.annotation.Retention

/**
 * @author graemerocher
 * @since 1.0
 */
class AnnotationMetadataWriterSpec extends AbstractBeanDefinitionSpec {

    void "test annotation metadata with primitive arrays"() {
        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata('test.Test','''\
package test;

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
        metadata.getValue("test.MyAnn","doubleArray", Double[].class).get() == [1.1d] as Double[]
        metadata.getValue("test.MyAnn","doubleArray", double[].class).get() == [1.1d] as double[]
    }

    void "test read annotation on alias with primitive type"() {
        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata("test.Test",'''\
package test;

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
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata("test.Test",'''\
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

    void "test read enum constants with custom toString()"() {
        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata('test.Test','''\
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
    }


    void "test read enum constants"() {
        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata("test.Test",'''\
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
        metadata.getValue(Requires, "sdk", Requires.Sdk).get() == Requires.Sdk.JAVA
        metadata.getValue(Requires, "version").get() == "1.8"
    }

    void "test read external constants"() {
        given:
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata("test.Test",'''\
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
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata("test.Test",'''\
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
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata("test.Test",'''\
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
        metadata.getValue(Requirements).get()[1].values.get('classes') == [new AnnotationClassValue('test.Test')] as AnnotationClassValue[]

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
        AnnotationMetadata toWrite = buildTypeAnnotationMetadata("test.Test",'''\
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


    void "test basic argument metadata"() {
        given:
        AnnotationMetadata metadata = buildFieldAnnotationMetadata("test.Test", '''
package test;

@javax.inject.Singleton
class Test {

    void test(@javax.inject.Named("foo") String id) {
    
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
        AnnotationMetadata metadata = buildFieldAnnotationMetadata("test.Test", '''
package test;

@javax.inject.Singleton
class Test implements TestApi {

    @javax.annotation.PostConstruct
    @java.lang.Override
    public void test(String id) {
    
    }
}

interface TestApi {

    void test(@javax.inject.Named("foo") String id);

}
''', 'test', 'id')

        expect:
        metadata != null
        !metadata.empty
        !metadata.hasDeclaredAnnotation(Named)
        metadata.hasAnnotation(Named)
        metadata.getValue(Named).get() == "foo"
    }

    void "test annotation metadata string value array types"() {
        given:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('test.Test', """
package test;

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
}
