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
package io.micronaut.inject.annotation.repeatable

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.convert.value.ConvertibleValues
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition


class RepeatableAnnotationSpec extends AbstractTypeElementSpec {

    void "test repeatable annotation properties with alias"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.inject.annotation.repeatable.*;
import io.micronaut.context.annotation.*;

@OneRequires(properties = @Property(name="prop1", value="value1"))
@SomeOther(properties = @Property(name="prop2", value="value2"))
@jakarta.inject.Singleton
class Test {

    @OneRequires(properties = @Property(name="prop2", value="value2"))    
    void someMethod() {}
}
''')

        when:
        SomeOther someOther= definition.synthesize(SomeOther)
        OneRequires oneRequires = definition.synthesize(OneRequires)

        then:
        oneRequires.properties().size() == 1
        oneRequires.properties()[0].name() == 'prop1'
        oneRequires.properties()[0].value() == 'value1'
        someOther.properties().size() == 1
        someOther.properties()[0].name() == 'prop2'
        someOther.properties()[0].value() == 'value2'
    }


    void "test repeatable annotations are combined"() {
        AnnotationMetadata metadata = buildBeanDefinition('test.Test','''\
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
    @Executable
    void someMethod() {}
}
''').getRequiredMethod("someMethod").getAnnotationMetadata()

        when:
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


    void "test repeatable annotation resolve all values with single @Requires"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.inject.annotation.repeatable.*;
import io.micronaut.context.annotation.*;

@OneRequires
@Requires(property="bar")
@jakarta.inject.Singleton
class Test {

}
''')
        when:
        List<ConvertibleValues> requirements = definition.getAnnotationValuesByType(Requires.class)
        Requires[] requires = definition.synthesizeAnnotationsByType(Requires)

        then:
        definition.getAnnotationMetadata().hasAnnotation(Requires.class)
        definition.getAnnotationMetadata().hasAnnotation(Requirements.class)

        requirements != null
        requirements.size() == 2
        requires.size() == 2
    }

    void "test repeatable annotation resolve all values with single @Requires - reverse"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.inject.annotation.repeatable.*;
import io.micronaut.context.annotation.*;

@Requires(property="bar")
@OneRequires
@jakarta.inject.Singleton
class Test {

}
''')
        when:
        List<ConvertibleValues> requirements = definition.getAnnotationValuesByType(Requires.class)
        Requires[] requires = definition.synthesizeAnnotationsByType(Requires)

        then:
        definition.getAnnotationMetadata().hasAnnotation(Requires.class)
        definition.getAnnotationMetadata().hasAnnotation(Requirements.class)

        requirements != null
        requirements.size() == 2
        requires.size() == 2
    }

    void "test repeatable annotation resolve inherited from meta annotations"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.inject.annotation.repeatable.*;
import io.micronaut.context.annotation.*;

@OneRequires
@TwoRequires
@jakarta.inject.Singleton
class Test {

}
''')
        when:
        List<ConvertibleValues> requirements = definition.getAnnotationValuesByType(Requires.class)
        Requires[] requires = definition.synthesizeAnnotationsByType(Requires)

        then:
        definition.getAnnotationMetadata().hasStereotype(Requires.class)
        definition.getAnnotationMetadata().hasStereotype(Requirements.class)
        !definition.getAnnotationMetadata().hasAnnotation(Requires.class)
        !definition.getAnnotationMetadata().hasAnnotation(Requirements.class)

        requirements != null
        requirements.size() == 2
        requires.size() == 2
    }

    void "test repeatable annotation resolve inherited from meta annotations - reverse"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.inject.annotation.repeatable.*;
import io.micronaut.context.annotation.*;

@TwoRequires
@OneRequires
@jakarta.inject.Singleton
class Test {

}
''')
        when:
        List<ConvertibleValues> requirements = definition.getAnnotationValuesByType(Requires.class)
        Requires[] requires = definition.synthesizeAnnotationsByType(Requires)

        then:
        definition.getAnnotationMetadata().hasStereotype(Requires.class)
        definition.getAnnotationMetadata().hasStereotype(Requirements.class)
        !definition.getAnnotationMetadata().hasAnnotation(Requires.class)
        !definition.getAnnotationMetadata().hasAnnotation(Requirements.class)

        requirements != null
        requirements.size() == 2
        requires.size() == 2
    }


    void "test repeatable annotation resolve all values with multiple @Requires"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.inject.annotation.repeatable.*;
import io.micronaut.context.annotation.*;

@OneRequires
@Requires(property="bar")
@Requires(property="another")
@jakarta.inject.Singleton
class Test {

}
''')
        when:
        List<ConvertibleValues> requirements = definition.getAnnotationValuesByType(Requires.class)
        Requires[] requires = definition.synthesizeAnnotationsByType(Requires)

        then:
        definition.getAnnotationMetadata().hasAnnotation(Requires.class)
        definition.getAnnotationMetadata().hasAnnotation(Requirements.class)
        requirements != null
        requirements.size() == 3
        requires.size() == 3
    }

    void "test repeatable annotation resolve all values with multiple @Requires - reverse"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.inject.annotation.repeatable.*;
import io.micronaut.context.annotation.*;

@Requires(property="bar")
@Requires(property="another")
@OneRequires
@jakarta.inject.Singleton
class Test {

}
''')
        when:
        List<ConvertibleValues> requirements = definition.getAnnotationValuesByType(Requires.class)
        Requires[] requires = definition.synthesizeAnnotationsByType(Requires)

        then:
        definition.getAnnotationMetadata().hasAnnotation(Requires.class)
        definition.getAnnotationMetadata().hasAnnotation(Requirements.class)
        requirements != null
        requirements.size() == 3
        requires.size() == 3
    }

    void "test repeatable annotation resolve all values with multiple declared and inherited @Requires"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.inject.annotation.repeatable.*;
import io.micronaut.context.annotation.*;

@TwoRequires
@Requires(property="bar")
@Requires(property="another")
@jakarta.inject.Singleton
class Test {

}
''')
        when:
        List<ConvertibleValues> requirements = definition.getAnnotationValuesByType(Requires.class)
        Requires[] requires = definition.synthesizeAnnotationsByType(Requires)

        then:
        requirements != null
        requirements.size() == 4
        requires.size() == 4
    }



    void "test repeatable annotation resolve all values with multiple inherited @Requires"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.inject.annotation.repeatable.*;
import io.micronaut.context.annotation.*;

@TwoRequires
@Requires(property="bar")
@jakarta.inject.Singleton
class Test {

}
''')
        when:
        List<ConvertibleValues> requirements = definition.getAnnotationValuesByType(Requires.class)
        Requires[] requires = definition.synthesizeAnnotationsByType(Requires)

        then:
        requirements != null
        requirements.size() == 3
        requires.size() == 3
    }

    void "test repeatable annotation resolve all values with multiple inherited @Requires - reverse"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.inject.annotation.repeatable.*;
import io.micronaut.context.annotation.*;

@Requires(property="bar")
@TwoRequires
@jakarta.inject.Singleton
class Test {

}
''')
        when:
        List<ConvertibleValues> requirements = definition.getAnnotationValuesByType(Requires.class)
        Requires[] requires = definition.synthesizeAnnotationsByType(Requires)

        then:
        requirements != null
        requirements.size() == 3
        requires.size() == 3
    }

    void "test members in repeatable parent are retained"() {
        when:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.inject.annotation.repeatable.*;
import io.micronaut.context.annotation.*;

@Topics(connectionName = "test", value = {@Topic("hello"), @Topic("world")})
@jakarta.inject.Singleton
class Test {

}
''')

        then:
        definition.getValue(Topics, "connectionName", String).get() == "test"
        definition.getAnnotationValuesByType(Topic).size() == 2
        definition.getAnnotationValuesByType(Topic)[0].getValue(String).get() == "hello"
        definition.getAnnotationValuesByType(Topic)[1].getValue(String).get() == "world"
    }
}
