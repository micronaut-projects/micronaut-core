package io.micronaut.inject.configproperties

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory

class VisibilityIssuesSpec extends AbstractBeanDefinitionSpec {

    void "test extending a class with protected method in a different package fails compilation"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition("io.micronaut.inject.configproperties.ChildConfigProperties", """
            package io.micronaut.inject.configproperties;
            
            import io.micronaut.context.annotation.ConfigurationProperties;
            import io.micronaut.inject.configproperties.other.ParentConfigProperties;
            
            @ConfigurationProperties("child")
            class ChildConfigProperties extends ParentConfigProperties {
                
                Integer age
            }
        """)

        when:
        def context = ApplicationContext.run('parent.name': 'Sally', 'parent.child.age': 22)
        def instance = ((BeanFactory)beanDefinition).build(context, beanDefinition)

        then:
        instance.getName() == "Sally"
        instance.getAge() == 22

        cleanup:
        context.close()
    }

    void "test extending a class with protected field in a different package fails compilation"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition("io.micronaut.inject.configproperties.ChildConfigProperties", """
            package io.micronaut.inject.configproperties;
            
            import io.micronaut.context.annotation.ConfigurationProperties;
            import io.micronaut.inject.configproperties.other.ParentConfigProperties;
            
            @ConfigurationProperties("child")
            class ChildConfigProperties extends ParentConfigProperties {
                
                protected void setName(String name) {
                    super.setName(name)
                }
 
            }
        """)

        when:
        //not configured with parent.child.name because non public methods are ignored
        def context = ApplicationContext.run('parent.nationality': 'Italian', 'parent.name': 'Sally')
        def instance = ((BeanFactory)beanDefinition).build(context, beanDefinition)

        then:
        instance.nationality == "Italian"
        instance.getName() == 'Sally'

        cleanup:
        context.close()
    }

}
