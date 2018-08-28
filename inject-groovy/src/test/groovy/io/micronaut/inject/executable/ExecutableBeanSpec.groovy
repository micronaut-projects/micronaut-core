package io.micronaut.inject.executable

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.inject.BeanDefinition

import java.util.function.Function

class ExecutableBeanSpec extends AbstractBeanDefinitionSpec {

    void "test executable method return types"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.ExecutableBean1','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@javax.inject.Singleton
@Executable
class ExecutableBean1 {

    public int round(float num) {
        return Math.round(num);
    }
}
''')
        expect:
        definition != null
        definition.findMethod("round", float.class).get().returnType.type == int.class

    }
}
