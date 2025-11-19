package io.micronaut.inject.requires

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.condition.OperatingSystem
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference
import spock.util.environment.RestoreSystemProperties

class RequiresSpec extends AbstractTypeElementSpec{

    void "test requires eachbean simple"() {
        given:
            ApplicationContext context = buildContext( '''
package test;

import io.micronaut.context.annotation.*;

@Requires(property = "myconf")
@ConfigurationProperties("myconf")
class MyConfiguration {
}

@EachBean(MyConfiguration.class)
class MyClient {
}

@EachBean(MyClient.class)
class MyBean {
}
''')
        when:"the bean doesn't exist"
            getBean(context, 'test.MyBean')

        then:
            def e = thrown(NoSuchBeanException)
            def lines = e.message.readLines()
            lines[0] == "No bean of type [test.MyBean] exists. "
            lines[1] == "* [MyBean] requires the presence of a bean of type [test.MyClient]."
            lines[2] == " * [MyClient] requires the presence of a bean of type [test.MyConfiguration]."
            lines[3] == "  * [MyConfiguration] is disabled because:"
            lines[4] == "   - Required property [myconf] not present"
        cleanup:
            context.close()
    }

    void "test requires eachbean multiple"() {
        given:
            ApplicationContext context = buildContext( '''
package test;

import io.micronaut.context.annotation.*;

@Requires(property = "myconf")
@Requires(missingProperty = "myconf2.multiple")
@ConfigurationProperties("myconf")
class MyDefaultConfiguration implements MyConfiguration {
}

@EachProperty(value = "myconf2.multiple", primary = "default")
class MyMultiConfiguration implements MyConfiguration {
}

interface MyConfiguration {
}

@EachBean(MyConfiguration.class)
class MyClient {
}

@EachBean(MyClient.class)
class MyBean {
}
''')
        when:"the bean doesn't exist"
            getBean(context, 'test.MyBean')

        then:
            def e = thrown(NoSuchBeanException)
            def lines = e.message.readLines()
            lines[0] == "No bean of type [test.MyBean] exists. "
            lines[1] == "* [MyBean] requires the presence of a bean of type [test.MyClient]."
            lines[2] == " * [MyClient] requires the presence of a bean of type [test.MyConfiguration]."
            lines[3] == "  * [MyMultiConfiguration] a candidate of [MyConfiguration] is disabled because:"
            lines[4] == "   - Configuration requires entries under the prefix: [myconf2.multiple.default]"
            lines[5] == "  * [MyDefaultConfiguration] a candidate of [MyConfiguration] is disabled because:"
            lines[6] == "   - Required property [myconf] not present"
            lines.size() == 7
        cleanup:
            context.close()
    }

    void "test requires eachbean multiple 2"() {
        given:
            ApplicationContext context = buildContext( '''
package test;

import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;

@Requires(property = "myconf.helper", pattern = "abc")
@Singleton
class MyHelper {
}

@Requires(property = "myconf")
@Requires(missingProperty = "myconf2.multiple")
@ConfigurationProperties("myconf")
class MyDefaultConfiguration implements MyConfiguration {
}

@Requires(bean = MyHelper.class)
@EachProperty(value = "myconf2.multiple", primary = "default")
class MyMultiConfiguration implements MyConfiguration {
}

interface MyConfiguration {
}

@EachBean(MyConfiguration.class)
class MyClient {
}

@EachBean(MyClient.class)
class MyBean {
}
''')
        when:"the bean doesn't exist"
            getBean(context, 'test.MyBean')

        then:
            def e = thrown(NoSuchBeanException)
            def lines = e.message.readLines()
            lines[0] == "No bean of type [test.MyBean] exists. "
            lines[1] == "* [MyBean] requires the presence of a bean of type [test.MyClient]."
            lines[2] == " * [MyClient] requires the presence of a bean of type [test.MyConfiguration]."
            lines[3] == "  * [MyDefaultConfiguration] a candidate of [MyConfiguration] is disabled because:"
            lines[4] == "   - Required property [myconf] not present"
            lines[5] == "  * [MyMultiConfiguration] a candidate of [MyConfiguration] is disabled because:"
            lines[6] == "   - No bean of type [test.MyHelper] present within context"
            lines[7] == "   * [MyHelper] is disabled because:"
            lines[8] == "    - Required property [myconf.helper] not present"

            lines.size() == 9
        cleanup:
            context.close()
    }

    void "test requires java sdk - success"() {
        given:
        ApplicationContext context = buildContext( '''
package test;

import io.micronaut.context.annotation.*;

@Requires(sdk=Requires.Sdk.JAVA, value="8")
@jakarta.inject.Singleton
class MyBean {
}
''')
        expect:"the bean exists"
        getBean(context, 'test.MyBean')

        cleanup:
        context.close()
    }

    void "test requires java sdk - failure"() {
        given:
        ApplicationContext context = buildContext( '''
package test;

import io.micronaut.context.annotation.*;

@Requires(sdk=Requires.Sdk.JAVA, version="800")
@jakarta.inject.Singleton
class MyBean {
}
''')
        when:"the bean doesn't exist"
        getBean(context, 'test.MyBean')

        then:
        def e = thrown(NoSuchBeanException)
        def lines = e.message.readLines()
        lines[0] == 'No bean of type [test.MyBean] exists. '
        lines[1] == '* [MyBean] is disabled because:'
        lines[2] == " - Java major version [${Runtime.version().feature()}] must be at least 800"

        cleanup:
        context.close()
    }

    void "test requires property equals - error"() {
        given:
        ApplicationContext context = buildContext( '''
package test;

import io.micronaut.context.annotation.*;

@Requires(property="foo", value="bar")
@jakarta.inject.Singleton
class MyBean {
}
''')

        when:"the bean doesn't exist"
        getBean(context, 'test.MyBean')

        then:
        def e = thrown(NoSuchBeanException)
        def lines = e.message.readLines()
        lines[0] == 'No bean of type [test.MyBean] exists. '
        lines[1] == '* [MyBean] is disabled because:'
        lines[2] == ' - Required property [foo] with value [bar] not present'

        cleanup:
        context.close()
    }

    void "test requires property not equals"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(property="foo", notEquals="bar")
@jakarta.inject.Singleton
class MyBean {
}
''')

        ApplicationContext applicationContext = ApplicationContext.builder().build()
        applicationContext.environment.addPropertySource(PropertySource.of("foo":"test"))
        applicationContext.start()

        then:
        beanDefinition.isEnabled(applicationContext)

        when:
        applicationContext.close()
        applicationContext = ApplicationContext.builder().build()
        applicationContext.environment.addPropertySource(PropertySource.of("foo":"bar"))
        applicationContext.start()

        then:
        !beanDefinition.isEnabled(applicationContext)

        when:
        applicationContext.close()
        applicationContext = ApplicationContext.builder().build()
        applicationContext.start()

        then:
        beanDefinition.isEnabled(applicationContext)

        cleanup:
        applicationContext.close()
    }

    void "test requires classes with classes present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(classes=String.class)
@jakarta.inject.Singleton
class MyBean {
}
''')

        then:
        beanDefinition.isEnabled(BeanContext.build())
    }

    void "test requires classes with classes not present"() {
        when:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''
package test;

import io.micronaut.context.annotation.*;

@Requires(classes=String.class)
@jakarta.inject.Singleton
class MyBean {
}
''')

        then:
        metadata.getAnnotationValuesByType(Requires)
    }

    void "test meta requires condition not satisfied"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.inject.requires.*;

@MetaRequires
@jakarta.inject.Singleton
class MyBean {
}
''')

        then:
        !beanDefinition.isEnabled(BeanContext.build())
    }

    void "test meta requires condition satisfied"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.inject.requires.*;

@MetaRequires
@jakarta.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .builder('foo.bar':"test")
                .build()
        context.start()

        context.registerSingleton(String.class, "foo")

        then:
        beanDefinition.isEnabled(context)

        cleanup:
        context.close()
    }

    void "test requires missing beans with no bean present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(missingBeans=String.class)
@jakarta.inject.Singleton
class MyBean {
}
''')

        then:
        beanDefinition.isEnabled(BeanContext.build())
    }

    void "test requires missing beans with bean present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(missingBeans=String.class)
@jakarta.inject.Singleton
class MyBean {
}
''')

        ApplicationContext context = ApplicationContext.run()
        context.registerSingleton(String.class, "foo")

        then:
        !beanDefinition.isEnabled(context)

        cleanup:
        context.close()
    }

    void "test requires missing classes as string class name when class present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(missingClasses = "java.lang.String")
@jakarta.inject.Singleton
class MyBean {
}
''')

        then:
        !beanDefinition.isEnabled(BeanContext.build())
    }

    void "test requires missing classes as string class names when classes not present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(missingClasses = {"org.hibernate.reactive.provider.ReactiveServiceRegistryBuilder"})
@jakarta.inject.Singleton
class MyBean {
}
''')

        then:
        beanDefinition.isEnabled(BeanContext.build())
    }

    void "test requires missing classes as class when class present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(missing = String.class)
@jakarta.inject.Singleton
class MyBean {
}
''')

        then:
        !beanDefinition.isEnabled(BeanContext.build())
    }

    void "test requires beans with no bean present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(beans=String.class)
@jakarta.inject.Singleton
class MyBean {
}
''')

        then:
        !beanDefinition.isEnabled(BeanContext.build())
    }

    void "test requires beans with bean present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(beans=String.class)
@jakarta.inject.Singleton
class MyBean {
}
''')

        ApplicationContext context = ApplicationContext.run()
        context.registerSingleton(String.class, "foo")

        then:
        beanDefinition.isEnabled(context)

        cleanup:
        context.close()
    }


    void "test requires class with inner class"() {
        when:
        BeanDefinitionReference beanDefinition = buildBeanDefinitionReference('test.MyBean', '''
package test;

import io.micronaut.inject.requires.*;
import io.micronaut.context.annotation.*;

@Requires(beans=Outer.Inner.class)
@jakarta.inject.Singleton
class MyBean {
}
''')

        ApplicationContext context = ApplicationContext.run()
        context.registerSingleton(new Outer.Inner())

        then:
        beanDefinition.isEnabled(context)

        cleanup:
        context.close()
    }

    void "test requires environment with environment present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(env="foo")
@jakarta.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext.builder("foo").build()
        context.start()

        then:
        beanDefinition.isEnabled(context)

        cleanup:
        context.close()
    }

    void "test requires environment with environment not present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(env="foo")
@jakarta.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext.builder().build()
        context.start()

        then:
        !beanDefinition.isEnabled(context)

        cleanup:
        context.close()
    }

    void "test requires not environment with environment present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(notEnv="foo")
@jakarta.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext.builder("foo").build()
        context.start()

        then:
        !beanDefinition.isEnabled(context)

        cleanup:
        context.close()
    }

    void "test requires not multiple environment with environment present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(notEnv={"foo", "bar"})
@jakarta.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext.builder("foo").build()
        context.start()

        then:
        !beanDefinition.isEnabled(context)

        cleanup:
        context.close()
    }

    void "test requires not environment with environment not present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(notEnv="foo")
@jakarta.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext.builder().build()
        context.start()

        then:
        beanDefinition.isEnabled(context)

        cleanup:
        context.close()
    }

    void "test requires not multiple environments with environment not present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(notEnv={"foo", "bar"})
@jakarta.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext.builder().build()
        context.start()

        then:
        beanDefinition.isEnabled(context)

        cleanup:
        context.close()
    }

    void "test requires property with property present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(property="foo.bar")
@jakarta.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .builder('foo.bar':true)
                .build()

        context.start()

        then:
        beanDefinition.isEnabled(context)

        cleanup:
        context.close()
    }

    @RestoreSystemProperties
    void "test requires property with property not present"() {
        setup:
        System.setProperty("foo.bar","")
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(property="mybean.foo.bar")
@jakarta.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .builder()
                .build()

        context.start()

        then:
        !beanDefinition.isEnabled(context)

        cleanup:
        context.close()
    }

    void "test requires property and value with property present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(property="foo.bar", value="test")
@jakarta.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .builder('foo.bar':"test")
                .build()

        context.start()

        then:
        beanDefinition.isEnabled(context)

        cleanup:
        context.close()
    }

    void "test requires property and value with property not present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(property="foo.bar", value="test")
@jakarta.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .builder()
                .build()

        context.start()


        then:
        !beanDefinition.isEnabled(context)

        cleanup:
        context.close()
    }

    void "test requires property and value with property not equal"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(property="foo.bar", value="test")
@jakarta.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .builder('foo.bar':"blah")
                .build()

        context.start()

        then:
        !beanDefinition.isEnabled(context)

        cleanup:
        context.close()
    }

    //  *********************************************************************************

    void "test requires property and pattern with property present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(property="foo.bar", pattern="\\\\d+")
@jakarta.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .builder('foo.bar':"10")
                .build()

        context.start()

        then:
        beanDefinition.isEnabled(context)

        cleanup:
        context.close()
    }

    void "test requires property and pattern with property not present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(property="foo.bar", pattern="\\\\d+")
@jakarta.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .builder()
                .build()

        context.start()

        then:
        !beanDefinition.isEnabled(context)

        cleanup:
        context.close()
    }

    void "test requires property and pattern with property not matching"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(property="foo.bar", pattern="\\\\d+")
@jakarta.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .builder('foo.bar':"blah")
                .build()

        context.start()

        then:
        !beanDefinition.isEnabled(context)

        cleanup:
        context.close()
    }

    void "test requires OS"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(os = {Requires.Family.WINDOWS, Requires.Family.MAC_OS})
@jakarta.inject.Singleton
class MyBean {
}
''')
        OperatingSystem.instance = new OperatingSystem(Requires.Family.LINUX)
        def context = ApplicationContext
                .builder()
                .build()

        context.start()

        then:
        !beanDefinition.isEnabled(context)

        when:
        context.close()
        OperatingSystem.instance = new OperatingSystem(Requires.Family.WINDOWS)
        context = ApplicationContext
                .builder()
                .build()

        context.start()

        then:
        beanDefinition.isEnabled(context)

        cleanup:
        OperatingSystem.instance = null
        context.close()
    }

    void "test not OS"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(notOs = {Requires.Family.WINDOWS, Requires.Family.MAC_OS})
@jakarta.inject.Singleton
class MyBean {
}
''')
        OperatingSystem.instance = new OperatingSystem(Requires.Family.WINDOWS)
        def context = ApplicationContext
                .builder()
                .build()

        context.start()

        then:
        !beanDefinition.isEnabled(context)

        when:
        context.close()
        OperatingSystem.instance = new OperatingSystem(Requires.Family.LINUX)
        context = ApplicationContext
                .builder()
                .build()

        context.start()

        then:
        beanDefinition.isEnabled(context)

        cleanup:
        OperatingSystem.instance = null
        context.close()
    }
}
