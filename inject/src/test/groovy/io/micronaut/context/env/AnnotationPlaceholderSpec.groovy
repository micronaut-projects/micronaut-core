package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.BeanDefinition
import spock.lang.Specification
import jakarta.inject.Singleton

class AnnotationPlaceholderSpec extends Specification {

    void "test placeholder binding to a string array"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(['from.config': ['a', 'b', 'c'], 'more.values': ['d', 'e']])
        BeanDefinition beanDefinition = ctx.getBeanDefinition(Test)

        when:
        String[] values = beanDefinition.stringValues(StringArrayValue)

        then:
        values == ['a', 'b', 'c', 'd', 'e'] as String[]

        when:
        values = beanDefinition.getValue(StringArrayValue, String[].class).get()

        then:
        values == ['a', 'b', 'c', 'd', 'e'] as String[]
    }

    void "test nested annotation placeholder binding"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(['child.value1': 'newChildValue1', 'child.value2': 'newChildValue2', 'child.value3': 'newChildValue3'])
        BeanDefinition beanDefinition = ctx.getBeanDefinition(NestedTest)

        when:
        AnnotationValue parentValue = beanDefinition.findAnnotation(ParentValue.class).get()
        AnnotationValue childValue = parentValue.getAnnotation('childValue', ChildValue.class).get()

        then:
        childValue.stringValue('value').get() == 'newChildValue1'

        when:
        childValue = parentValue.getAnnotation('childValue').get()

        then:
        childValue.stringValue('value').get() == 'newChildValue1'

        when:
        List<AnnotationValue> childValues = parentValue.getAnnotations('childValues', ChildValue.class)

        then:
        childValues.size() == 2
        childValues.get(0).stringValue('value').get() == 'newChildValue2'
        childValues.get(1).stringValue('value').get() == 'newChildValue3'

        when:
        childValues = parentValue.getAnnotations('childValues')

        then:
        childValues.size() == 2
        childValues.get(0).stringValue('value').get() == 'newChildValue2'
        childValues.get(1).stringValue('value').get() == 'newChildValue3'
    }

    @Singleton
    @StringArrayValue(['${from.config}', '${more.values}'])
    static class Test {

    }

    @Singleton
    @ParentValue(value = "parentValue",
            childValue = @ChildValue(value = '${child.value1}'),
            childValues = [@ChildValue(value = '${child.value2}'), @ChildValue(value = '${child.value3}')])
    static class NestedTest {

    }
}
