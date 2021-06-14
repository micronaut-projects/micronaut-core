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
package io.micronaut.inject.annotation

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValueProvider
import io.micronaut.inject.BeanConfiguration
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import jakarta.inject.Named
import spock.lang.Issue

import jakarta.inject.Scope
import jakarta.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class BeanDefinitionAnnotationMetadataSpec extends AbstractTypeElementSpec {

    void "test synthesize annotation from different source annotation"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import jakarta.inject.*;

@Singleton
@Named("test")
class Test {

}
''')


        expect:
        def ann = definition.synthesize(Named, "javax.inject.Named")
        ann.value() == 'test'
        definition.synthesizeDeclared(Named, "javax.inject.Named").value() == 'test'
        ann instanceof AnnotationValueProvider
        ann.annotationValue()
    }

    void "test bean definition computed state"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.context.annotation.Primary;

@jakarta.inject.Singleton
@Primary
class Test {

}
''')
        expect:
        definition != null
        definition.isSingleton()
        !definition.isIterable()
        definition.isPrimary()
        !definition.isProvided()
        definition.getScopeName().get() == AnnotationUtil.SINGLETON
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/1607')
    void "test recursive generics"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.inject.annotation.RecursiveGenerics;

@jakarta.inject.Singleton
class Test extends RecursiveGenerics<Test> {

}
''')
        expect:
        definition != null
        definition.getTypeArguments(RecursiveGenerics).size() == 1
        definition.getTypeArguments(RecursiveGenerics).get(0).type.name == 'test.Test'
    }

    void "test alias for existing member values within annotation values"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
@TestCachePut("test")
@TestCachePut("blah")
class Test {

}
''')
        expect:
        definition != null
        definition.synthesize(TestCachePuts.class).value()[0].value() == (['test'] as String[])
        definition.synthesize(TestCachePuts.class).value()[0].cacheNames() == (['test'] as String[])
        definition.synthesize(TestCachePuts.class).value()[1].value() == (['blah'] as String[])
        definition.synthesize(TestCachePuts.class).value()[1].cacheNames() == (['blah'] as String[])
    }

    void "test alias for existing member values"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
@TestCachePut("test")
class Test {

}
''')
        expect:
        definition != null
        definition.synthesize(TestCachePut.class).value() == (['test'] as String[])
        definition.synthesize(TestCachePut.class).cacheNames() == (['test'] as String[])
    }

    void "test repeated annotation values"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
@Requires(property="foo", value="bar")
@Requires(property="baz", value="stuff")
class Test {

    @Executable
    void sometMethod() {}
}
''')
        expect:
        definition != null
        definition.synthesize(Requirements.class).value()[0].property() == 'foo'
        definition.synthesize(Requirements.class).value()[0].value() == 'bar'
        definition.synthesize(Requirements.class).value()[1].property() == 'baz'
        definition.synthesize(Requirements.class).value()[1].value() == 'stuff'
    }

    void "test basic method annotation metadata"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test','''\
package test;

import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
class Test {

    @Executable
    void sometMethod() {}
}
''')
        def method = definition.findMethod('sometMethod').get()

        expect:
        definition != null
        definition.hasDeclaredAnnotation(AnnotationUtil.SINGLETON)
        method.annotationMetadata.hasAnnotation(AnnotationUtil.SINGLETON)
        method.annotationMetadata.hasDeclaredAnnotation(Executable)
    }

    void "test build configuration"() {
        given:
        BeanConfiguration configuration = buildBeanConfiguration("test", '''
@Configuration
@Requires(property="foo")
package test;
import io.micronaut.context.annotation.*;

''')
        expect:
        configuration != null
        configuration.getAnnotationMetadata().hasStereotype(Requires)
    }

    void "test build bean basic definition"() {
        given:
        BeanDefinition definition = buildBeanDefinition("test.Test", '''
package test;

@jakarta.inject.Singleton
class Test {

}
''')
        expect:
        definition != null
        definition.hasDeclaredAnnotation(AnnotationUtil.SINGLETON)
        definition.hasDeclaredStereotype(AnnotationUtil.SCOPE)
        definition.hasStereotype(AnnotationUtil.SCOPE)
        !definition.hasStereotype(Primary)
    }

    void "test factory bean definition"() {
        given:
        ClassLoader classLoader = buildClassLoader("test.Test", '''
package test;

import io.micronaut.context.annotation.*;
import java.util.concurrent.*;

@Factory
class Test {

    @EachBean(Test.class)
    @Bean(preDestroy = "shutdown")
    public ExecutorService executorService(Test test) {
        return null;
    }
}

''')
        BeanDefinition definition = classLoader.loadClass('test.$Test$ExecutorService0Definition').newInstance()
        expect:
        definition != null
        definition.hasStereotype(Factory) // inherits the factory annotations as stereotypes
        !definition.hasDeclaredAnnotation(Factory)
        !definition.hasDeclaredAnnotation(AnnotationUtil.SINGLETON)
        definition.hasDeclaredAnnotation(Bean)
        definition.hasDeclaredAnnotation(EachBean)
    }

    void "test factory bean definition inherits returned objects metadata"() {
        given:
        ClassLoader classLoader = buildClassLoader("test.Test", '''
package test;

import io.micronaut.context.annotation.*;
import java.util.concurrent.*;
import jakarta.inject.*;

@Factory
class Test {

    @Bean
    public Foo foo() {
        return null;
    }
}

@Singleton
interface Foo {

}

''')
        BeanDefinition definition = classLoader.loadClass('test.$Test$Foo0Definition').newInstance()
        expect:
        definition != null
        definition.hasStereotype(AnnotationUtil.SINGLETON)
        definition.hasDeclaredAnnotation(Bean)
    }


    void "test factory bean definition inherits returned objects metadata with inheritance"() {
        given:
        ClassLoader classLoader = buildClassLoader("test.Test", '''
package test;

import io.micronaut.context.annotation.*;
import java.util.concurrent.*;
import jakarta.inject.*;

@Factory
class Test {

    @Bean
    public Foo foo() {
        return null;
    }
}


interface Foo extends Bar {

}

@Singleton
interface Bar {
}


''')
        BeanDefinition definition = classLoader.loadClass('test.$Test$Foo0Definition').newInstance()
        expect:
        definition != null
        definition.hasStereotype(AnnotationUtil.SINGLETON)
        definition.hasDeclaredAnnotation(Bean)
    }
}
