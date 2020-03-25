package io.micronaut.inject.requires

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.condition.OperatingSystem
import io.micronaut.context.env.PropertySource
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference
import spock.util.environment.RestoreSystemProperties

class RequiresSpec extends AbstractTypeElementSpec{

    void "test requires property not equals"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(property="foo", notEquals="bar")
@javax.inject.Singleton
class MyBean {
}
''')

        ApplicationContext applicationContext = ApplicationContext.builder().build()
        applicationContext.environment.addPropertySource(PropertySource.of("foo":"test"))
        applicationContext.environment.start()

        then:
        beanDefinition.isEnabled(applicationContext)

        when:
        applicationContext.close()
        applicationContext = ApplicationContext.builder().build()
        applicationContext.environment.addPropertySource(PropertySource.of("foo":"bar"))
        applicationContext.environment.start()

        then:
        !beanDefinition.isEnabled(applicationContext)

        when:
        applicationContext.close()
        applicationContext = ApplicationContext.builder().build()
        applicationContext.environment.start()

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
@javax.inject.Singleton
class MyBean {
}
''')

        then:
        beanDefinition.isEnabled(Mock(BeanContext))
    }

    void "test requires classes with classes not present"() {
        when:
        AnnotationMetadata metadata = buildTypeAnnotationMetadata('''
package test;

import io.micronaut.context.annotation.*;

@Requires(classes=String.class)
@javax.inject.Singleton
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
@javax.inject.Singleton
class MyBean {
}
''')

        then:
        !beanDefinition.isEnabled(new DefaultBeanContext())
    }

    void "test meta requires condition satisfied"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.inject.requires.*;

@MetaRequires
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .builder('foo.bar':"test")
                .build()
        context.environment.start()

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
@javax.inject.Singleton
class MyBean {
}
''')

        then:
        beanDefinition.isEnabled(new DefaultBeanContext())
    }

    void "test requires missing beans with bean present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(missingBeans=String.class)
@javax.inject.Singleton
class MyBean {
}
''')

        def context = new DefaultBeanContext()
        context.registerSingleton(String.class, "foo")

        then:
        !beanDefinition.isEnabled(context)

        cleanup:
        context.close()
    }

    void "test requires beans with no bean present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(beans=String.class)
@javax.inject.Singleton
class MyBean {
}
''')

        then:
        !beanDefinition.isEnabled(new DefaultBeanContext())
    }

    void "test requires beans with bean present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.*;

@Requires(beans=String.class)
@javax.inject.Singleton
class MyBean {
}
''')

        def context = new DefaultBeanContext()
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
@javax.inject.Singleton
class MyBean {
}
''')

        def context = new DefaultBeanContext()
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
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext.builder("foo").build()

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
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext.builder().build()

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
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext.builder("foo").build()

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
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext.builder("foo").build()

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
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext.builder().build()

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
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext.builder().build()

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
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .builder('foo.bar':true)
                .build()

        context.environment.start()


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
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .builder()
                .build()

        context.environment.start()


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
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .builder('foo.bar':"test")
                .build()

        context.environment.start()

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
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .builder()
                .build()

        context.environment.start()


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
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .builder('foo.bar':"blah")
                .build()

        context.environment.start()


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
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .builder('foo.bar':"10")
                .build()

        context.environment.start()

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
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .builder()
                .build()

        context.environment.start()


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
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .builder('foo.bar':"blah")
                .build()

        context.environment.start()


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
@javax.inject.Singleton
class MyBean {
}
''')
        OperatingSystem.instance = new OperatingSystem(Requires.Family.LINUX)
        def context = ApplicationContext
                .builder()
                .build()

        context.environment.start()

        then:
        !beanDefinition.isEnabled(context)

        when:
        context.close()
        OperatingSystem.instance = new OperatingSystem(Requires.Family.WINDOWS)
        context = ApplicationContext
                .builder()
                .build()

        context.environment.start()

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
@javax.inject.Singleton
class MyBean {
}
''')
        OperatingSystem.instance = new OperatingSystem(Requires.Family.WINDOWS)
        def context = ApplicationContext
                .builder()
                .build()

        context.environment.start()

        then:
        !beanDefinition.isEnabled(context)

        when:
        context.close()
        OperatingSystem.instance = new OperatingSystem(Requires.Family.LINUX)
        context = ApplicationContext
                .builder()
                .build()

        context.environment.start()

        then:
        beanDefinition.isEnabled(context)

        cleanup:
        OperatingSystem.instance = null
        context.close()
    }
}
