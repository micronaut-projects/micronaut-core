package io.micronaut.kotlin.processing.inject.configproperties

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory
import spock.lang.Specification
import static io.micronaut.kotlin.processing.KotlinCompiler.*

class VisibilityIssuesSpec extends Specification {

    void "test extending a class with protected method in a different package fails compilation"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition("io.micronaut.inject.configproperties.ChildConfigProperties", """
package io.micronaut.inject.configproperties

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.kotlin.processing.inject.configproperties.other.ParentConfigProperties;

@ConfigurationProperties("child")
class ChildConfigProperties: ParentConfigProperties() {
    var age: Int? = null
}
""")

        when:
        def context = ApplicationContext.run(
                'parent.child.age': 22,
                'parent.name': 'Sally',
                'parent.engine.manufacturer': 'Chevy')
        def instance = ((BeanFactory)beanDefinition).build(context, beanDefinition)

        then:
        beanDefinition.injectedMethods.size() == 3
        beanDefinition.injectedMethods[0].name == "setAge"
        beanDefinition.injectedMethods[1].name == "setName"
        beanDefinition.injectedMethods[2].name == "setNationality"
        instance.getName() == null //methods that require reflection are not injected
        instance.getAge() == 22
        instance.getBuilder().build().getManufacturer() == 'Chevy'

        cleanup:
        context.close()
    }

    void "test extending a class with protected field in a different package fails compilation"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition("io.micronaut.inject.configproperties.ChildConfigProperties", """
package io.micronaut.inject.configproperties

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.kotlin.processing.inject.configproperties.other.ParentConfigProperties

@ConfigurationProperties("child")
open class ChildConfigProperties: ParentConfigProperties() {
    override var name: String? = null
}
""")

        when:
        def context = ApplicationContext.run('parent.nationality': 'Italian', 'parent.child.name': 'Sally')
        def instance = ((BeanFactory)beanDefinition).build(context, beanDefinition)

        then:
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.injectedMethods[0].name == "setName"
        beanDefinition.injectedMethods[1].name == "setNationality"
        instance.getName() == "Sally"
        instance.getNationality() == null //methods that require reflection are not injected

        cleanup:
        context.close()
    }

}
