package io.micronaut.inject.validation

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor
import io.micronaut.inject.writer.BeanDefinitionWriter

class ValidationAdviceSpec extends AbstractBeanDefinitionSpec {

    private static final String ANN_VALIDATED = 'io.micronaut.validation.Validated'
    private static final String INTERCEPTED_BEAN = 'validationadvice.$MyBean' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionVisitor.PROXY_SUFFIX

    void "test only the constrained method of an introduction interface is validated"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('validationadvice.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package validationadvice

import io.micronaut.aop.introduction.*
import jakarta.inject.Singleton
import jakarta.validation.constraints.NotBlank

@Stub
@Singleton
interface MyBean {

    String constrained(@NotBlank String name)

    String notConstrained(String name)
}
''')

        then:
        beanDefinition.getRequiredMethod("constrained", String).hasAnnotation(ANN_VALIDATED)
        !beanDefinition.getRequiredMethod("notConstrained", String).hasAnnotation(ANN_VALIDATED)
    }

    void "test only the constrained method of an introduction abstract class is validated"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('validationadvice.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package validationadvice

import io.micronaut.aop.introduction.*
import jakarta.inject.Singleton
import jakarta.validation.constraints.NotBlank

@Stub
@Singleton
abstract class MyBean {

    abstract String constrained(@NotBlank String name)

    abstract String notConstrained(String name)
}
''')

        then:
        beanDefinition.getRequiredMethod("constrained", String).hasAnnotation(ANN_VALIDATED)
        !beanDefinition.getRequiredMethod("notConstrained", String).hasAnnotation(ANN_VALIDATED)
    }

    void "test a constrained method inherited from a super interface is validated"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('validationadvice.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package validationadvice

import io.micronaut.aop.introduction.*
import jakarta.inject.Singleton
import jakarta.validation.constraints.NotBlank

interface Parent {

    String constrained(@NotBlank String name)

    String notConstrained(String name)
}

@Stub
@Singleton
interface MyBean extends Parent {
}
''')

        then:
        beanDefinition.getRequiredMethod("constrained", String).hasAnnotation(ANN_VALIDATED)
        !beanDefinition.getRequiredMethod("notConstrained", String).hasAnnotation(ANN_VALIDATED)
    }

    void "test only the setter of a constrained groovy property is validated"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition(INTERCEPTED_BEAN, '''
package validationadvice

import jakarta.inject.Singleton
import jakarta.validation.constraints.NotNull

@Singleton
class MyBean {

    @NotNull
    String name
}
''')

        then:
        beanDefinition.getRequiredMethod("setName", String).hasAnnotation(ANN_VALIDATED)
        beanDefinition.executableMethods*.name == ['setName']
    }

    void "test a constrained @Value groovy property is validated at injection without a proxy"() {
        when:
        ClassLoader classLoader = buildClassLoader('''
package validationadvice

import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import jakarta.validation.constraints.Max

@Singleton
class MyBean {

    @Max(20L)
    @Value('${my.number}')
    Integer number
}
''')

        then:
        classLoader.loadClass('validationadvice.$MyBean$Definition')

        when:
        classLoader.loadClass('validationadvice.$MyBean$Definition' + BeanDefinitionVisitor.PROXY_SUFFIX)

        then:
        thrown(ClassNotFoundException)
    }

    void "test a constrained groovy method does not validate the getter of a constrained @Value property"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition(INTERCEPTED_BEAN, '''
package validationadvice

import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import jakarta.validation.constraints.Max

@Singleton
class MyBean {

    @Max(20L)
    @Value('${my.number}')
    Integer number

    void update(@Max(20L) Integer number) {
        this.number = number
    }
}
''')

        then:
        beanDefinition.getRequiredMethod("update", Integer).hasAnnotation(ANN_VALIDATED)
        beanDefinition.executableMethods*.name == ['update']
    }

    void "test a declared setter of a constrained groovy property stays the declared method"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition(INTERCEPTED_BEAN, '''
package validationadvice

import jakarta.inject.Singleton
import jakarta.validation.constraints.NotNull

@Singleton
class MyBean {

    @NotNull
    String name

    protected void setName(@NotNull String name) {
        this.name = name
    }
}
''')

        then:
        def setter = beanDefinition.getRequiredMethod("setName", String)
        setter.hasAnnotation(ANN_VALIDATED)
        beanDefinition.executableMethods*.name == ['setName']
    }
}
