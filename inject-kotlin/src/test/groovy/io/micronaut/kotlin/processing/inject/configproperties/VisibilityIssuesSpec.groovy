package io.micronaut.kotlin.processing.inject.configproperties

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.InstantiatableBeanDefinition
import spock.lang.Specification
import static io.micronaut.annotation.processing.test.KotlinCompiler.*

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
        def instance = ((InstantiatableBeanDefinition)beanDefinition).instantiate(context)

        then:
        beanDefinition.injectedMethods.size() == 3
        beanDefinition.injectedMethods.find {it.name == "setAge" }
        beanDefinition.injectedMethods.find {it.name == "setName" }
        beanDefinition.injectedMethods.find {it.name == "setNationality" }
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
        def instance = ((InstantiatableBeanDefinition)beanDefinition).instantiate(context)

        then:
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.injectedMethods.find {it.name == "setName" }
        beanDefinition.injectedMethods.find {it.name == "setNationality" }
        instance.getName() == "Sally"
        instance.getNationality() == null //methods that require reflection are not injected

        cleanup:
        context.close()
    }

}
