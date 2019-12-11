/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.requires

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.PropertySource
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference
import spock.util.environment.RestoreSystemProperties

/**
 * @author Graeme Rocher
 * @since 1.0
 */
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

        ApplicationContext applicationContext = ApplicationContext.build().build()
        applicationContext.environment.addPropertySource(PropertySource.of("foo":"test"))
        applicationContext.environment.start()

        then:
        beanDefinition.isEnabled(applicationContext)

        when:
        applicationContext = ApplicationContext.build().build()
        applicationContext.environment.addPropertySource(PropertySource.of("foo":"bar"))
        applicationContext.environment.start()

        then:
        !beanDefinition.isEnabled(applicationContext)

        when:
        applicationContext = ApplicationContext.build().build()
        applicationContext.environment.start()

        then:
        beanDefinition.isEnabled(applicationContext)
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
                .build('foo.bar':"test")
                .build()
        context.environment.start()

        context.registerSingleton(String.class, "foo")

        then:
        beanDefinition.isEnabled(context)
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

        def context = ApplicationContext.build("foo").build()

        then:
        beanDefinition.isEnabled(context)
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

        def context = ApplicationContext.build().build()

        then:
        !beanDefinition.isEnabled(context)
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

        def context = ApplicationContext.build("foo").build()

        then:
        !beanDefinition.isEnabled(context)
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

        def context = ApplicationContext.build("foo").build()

        then:
        !beanDefinition.isEnabled(context)
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

        def context = ApplicationContext.build().build()

        then:
        beanDefinition.isEnabled(context)
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

        def context = ApplicationContext.build().build()

        then:
        beanDefinition.isEnabled(context)
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
                .build('foo.bar':true)
                .build()

        context.environment.start()


        then:
        beanDefinition.isEnabled(context)
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
                .build()
                .build()

        context.environment.start()


        then:
        !beanDefinition.isEnabled(context)
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
                .build('foo.bar':"test")
                .build()

        context.environment.start()

        then:
        beanDefinition.isEnabled(context)
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
                .build()
                .build()

        context.environment.start()


        then:
        !beanDefinition.isEnabled(context)
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
                .build('foo.bar':"blah")
                .build()

        context.environment.start()


        then:
        !beanDefinition.isEnabled(context)
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
                .build('foo.bar':"10")
                .build()

        context.environment.start()

        then:
        beanDefinition.isEnabled(context)
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
                .build()
                .build()

        context.environment.start()


        then:
        !beanDefinition.isEnabled(context)
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
                .build('foo.bar':"blah")
                .build()

        context.environment.start()


        then:
        !beanDefinition.isEnabled(context)
    }
}
