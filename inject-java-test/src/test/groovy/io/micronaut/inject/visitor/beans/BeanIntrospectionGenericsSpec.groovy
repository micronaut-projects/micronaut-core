package io.micronaut.inject.visitor.beans

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.GenericPlaceholder
import spock.lang.Issue

class BeanIntrospectionGenericsSpec
        extends AbstractTypeElementSpec {

    void "test generic placeholder for bean properties"() {
        given:
        def introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.Introspected;
import java.util.List;

@Introspected
class Test<T extends CharSequence> {
    private T property;
    private T[] array;
    private List<T> list;
    public void setProperty(T property) {
        this.property = property;
    }
    public T getProperty() {
        return property;
    }
    public void setArray(T[] array) {
        this.array = array;
    }
    public T[] getArray() {
        return array;
    }
    public void setList(List<T> list) {
        this.list = list;
    }
    public List<T> getList() {
        return list;
    }
}
''')
        when:"A simple property is retrieved"
        def property = introspection.getRequiredProperty("property", CharSequence)
        def argument = property.asArgument()

        then:"it is a generic placeholder"
        argument instanceof GenericPlaceholder
        argument.variableName == 'T'
        argument.name == 'property'
        argument.type == CharSequence

        when:"An array property is retrieved"
        def array = introspection.getRequiredProperty("array", CharSequence[].class)
        argument = array.asArgument()

        then:"it is a generic placeholder"
        argument instanceof GenericPlaceholder
        argument.variableName == 'T'
        argument.name == 'array'
        argument.type == CharSequence[].class

        when:"A list property is retrieved"
        def list = introspection.getRequiredProperty("list", List.class)
        argument = list.asArgument().getFirstTypeVariable().orElse(null)

        then:"it is a generic placeholder"
        argument instanceof GenericPlaceholder
        argument.name == 'E'
        argument.variableName == 'T'
        argument.type == CharSequence.class
    }
}
