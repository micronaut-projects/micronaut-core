/*
 * Copyright 2017-2018 original authors
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
import io.micronaut.core.annotation.AnnotationMetadata

import javax.inject.Qualifier
import javax.inject.Singleton
import java.lang.annotation.Documented
import java.lang.annotation.Retention

/**
 * @author graemerocher
 * @since 1.0
 */
class AnnotationMetadataWriterSpec extends AbstractBeanDefinitionSpec {

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
        metadata.getValue(Requirements).get()[1].values.get('classes') == ['test.Test'] as Object[]

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


}
