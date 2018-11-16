package io.micronaut.inject.configproperties

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory

class VisibilityIssuesSpec extends AbstractTypeElementSpec {

    void "test extending a class with protected method in a different package fails compilation"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition("io.micronaut.inject.configproperties.ChildConfigProperties", """
            package io.micronaut.inject.configproperties;
            
            import io.micronaut.context.annotation.ConfigurationProperties;
            import io.micronaut.inject.configproperties.other.ParentConfigProperties;
            
            @ConfigurationProperties("child")
            public class ChildConfigProperties extends ParentConfigProperties {
                
                private Integer age;
            
                public Integer getAge() {
                    return age;
                }
            
                public void setAge(Integer age) {
                    this.age = age;
                }
            }
        """)

        when:
        def context = ApplicationContext.run('parent.name': 'Sally')
        def instance = ((BeanFactory)beanDefinition).build(context, beanDefinition)

        then:
        instance.getName() == "Sally"

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
            public class ChildConfigProperties extends ParentConfigProperties {
                
                protected void setName(String name) {
                    super.setName(name);
                }
 
            }
        """)

        when:
        def context = ApplicationContext.run('parent.nationality': 'Italian')
        def instance = ((BeanFactory)beanDefinition).build(context, beanDefinition)

        then:
        instance.nationality == "Italian"

        cleanup:
        context.close()
    }

}
