/*
 * Copyright 2017 original authors
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
package org.particleframework.inject.requires

import org.particleframework.context.ApplicationContext
import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.context.env.PropertySource
import org.particleframework.inject.AbstractTypeElementSpec
import org.particleframework.inject.BeanDefinition
import org.particleframework.inject.BeanFactory

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class RequiresSpec extends AbstractTypeElementSpec{

    void "test requires classes with classes present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import org.particleframework.context.annotation.*;

@Requires(classes=String.class)
@javax.inject.Singleton
class MyBean {
}
''')

        then:
        beanDefinition.isEnabled(Mock(BeanContext))
    }

    void "test meta requires condition not satisfied"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import org.particleframework.context.annotation.*;
import org.particleframework.inject.requires.*;

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

import org.particleframework.context.annotation.*;
import org.particleframework.inject.requires.*;

@MetaRequires
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .build()
                .environment( {
            it.addPropertySource(PropertySource.of('foo.bar':"test"))
            it.start()
        })

        context.registerSingleton(String.class, "foo")

        then:
        beanDefinition.isEnabled(context)
    }

    void "test requires beans with no bean present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import org.particleframework.context.annotation.*;

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

import org.particleframework.context.annotation.*;

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

    void "test requires environment with environment present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import org.particleframework.context.annotation.*;

@Requires(env="foo")
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext.build("foo")

        then:
        beanDefinition.isEnabled(context)
    }

    void "test requires environment with environment not present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import org.particleframework.context.annotation.*;

@Requires(env="foo")
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext.build()

        then:
        !beanDefinition.isEnabled(context)
    }

    void "test requires not environment with environment present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import org.particleframework.context.annotation.*;

@Requires(notEnv="foo")
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext.build("foo")

        then:
        !beanDefinition.isEnabled(context)
    }

    void "test requires not environment with environment not present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import org.particleframework.context.annotation.*;

@Requires(notEnv="foo")
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext.build()

        then:
        beanDefinition.isEnabled(context)
    }

    void "test requires property with property present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import org.particleframework.context.annotation.*;

@Requires(property="foo.bar")
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .build()
                .environment( {
            it.addPropertySource(PropertySource.of('foo.bar':true))
            it.start()
        })


        then:
        beanDefinition.isEnabled(context)
    }

    void "test requires property with property not present"() {
        setup:
        System.setProperty("foo.bar","")
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import org.particleframework.context.annotation.*;

@Requires(property="mybean.foo.bar")
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .build()
                .environment( {
            it.start()
        })


        then:
        !beanDefinition.isEnabled(context)
    }



    void "test requires property and value with property present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import org.particleframework.context.annotation.*;

@Requires(property="foo.bar", value="test")
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .build()
                .environment( {
            it.addPropertySource(PropertySource.of('foo.bar':"test"))
            it.start()
        })


        then:
        beanDefinition.isEnabled(context)
    }

    void "test requires property and value with property not present"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import org.particleframework.context.annotation.*;

@Requires(property="foo.bar", value="test")
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .build()
                .environment( {
            it.start()
        })


        then:
        !beanDefinition.isEnabled(context)
    }

    void "test requires property and value with property not equal"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean', '''
package test;

import org.particleframework.context.annotation.*;

@Requires(property="foo.bar", value="test")
@javax.inject.Singleton
class MyBean {
}
''')

        def context = ApplicationContext
                .build()
                .environment( {
            it.addPropertySource(PropertySource.of('foo.bar':"blah"))
            it.start()
        })


        then:
        !beanDefinition.isEnabled(context)
    }
}
