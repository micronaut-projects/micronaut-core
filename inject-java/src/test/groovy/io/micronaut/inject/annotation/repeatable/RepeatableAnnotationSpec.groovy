package io.micronaut.inject.annotation.repeatable

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.convert.value.ConvertibleValues
import io.micronaut.inject.AbstractTypeElementSpec
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
@javax.inject.Singleton
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



    void "test repeatable annotation resolve all values with single @Requires"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.inject.annotation.repeatable.*;
import io.micronaut.context.annotation.*;

@OneRequires
@Requires(property="bar")
@javax.inject.Singleton
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
@javax.inject.Singleton
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
@javax.inject.Singleton
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
        requirements.size() == 3
        requires.size() == 3
    }

    void "test repeatable annotation resolve inherited from meta annotations - reverse"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.inject.annotation.repeatable.*;
import io.micronaut.context.annotation.*;

@TwoRequires
@OneRequires
@javax.inject.Singleton
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
        requirements.size() == 3
        requires.size() == 3
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
@javax.inject.Singleton
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
@javax.inject.Singleton
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
@javax.inject.Singleton
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
@javax.inject.Singleton
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
@javax.inject.Singleton
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
}
