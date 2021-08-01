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
package io.micronaut.inject.provider

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.BeanContextConfiguration
import io.micronaut.context.DefaultBeanContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.annotation.TestCachePuts

class BeanProviderSpec extends AbstractTypeElementSpec {

     void "test bean definition reference references correct bean type for jakarta.inject.Provider"() {
        given:
        BeanDefinitionReference definition = buildBeanDefinitionReference('test.Test','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
class Test implements jakarta.inject.Provider<Foo>{

    public Foo get() {
        return new Foo();
    }
}

class Foo {}
''')
        expect:
        definition != null
        definition.getBeanType().name == 'test.Test'
    }

    void "test inject bean with jakarta.inject.Provider"() {
        given:
        BeanDefinitionReference ref = buildBeanDefinitionReference('test.Test','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;


@jakarta.inject.Singleton
class Test {
    jakarta.inject.Provider<Foo> provider;
    Test(jakarta.inject.Provider<Foo> provider) {
        this.provider = provider;
    }
    public Foo get() {
        return provider.get();
    }
}

@jakarta.inject.Singleton
class Foo {}
''')
        def definition = ref.load()

        expect:
        ref != null
        ref.getBeanType().name == 'test.Test'
        definition.constructor.arguments[0].typeParameters.length == 1
        definition.constructor.arguments[0].typeParameters.length == 1
        definition.constructor.arguments[0].typeParameters[0].type.name == 'test.Foo'
        definition.constructor.arguments[0].isProvider()
        definition.requiredComponents.contains(ref.class.classLoader.loadClass("test.Foo"))
    }

    void "test bean definition reference references correct bean type for io.micronaut.context.BeanProvider"() {
        given:
        BeanDefinitionReference definition = buildBeanDefinitionReference('test.Test','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
class Test implements io.micronaut.context.BeanProvider<Foo>{

    public Foo get() {
        return new Foo();
    }
}

class Foo {}
''')
        expect:
        definition != null
        definition.getBeanType().name == 'test.Test'
    }

    void "test inject bean with io.micronaut.context.BeanProvider"() {
        given:
        BeanDefinitionReference ref = buildBeanDefinitionReference('test.Test','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;


@jakarta.inject.Singleton
class Test {
    io.micronaut.context.BeanProvider<Foo> provider;
    Test(io.micronaut.context.BeanProvider<Foo> provider) {
        this.provider = provider;
    }
    public Foo get() {
        return provider.get();
    }
}

@jakarta.inject.Singleton
class Foo {}
''')
        def definition = ref.load()

        expect:
        ref != null
        ref.getBeanType().name == 'test.Test'
        definition.constructor.arguments[0].typeParameters.length == 1
        definition.constructor.arguments[0].typeParameters.length == 1
        definition.constructor.arguments[0].typeParameters[0].type.name == 'test.Foo'
        definition.constructor.arguments[0].isProvider()
        definition.requiredComponents.contains(ref.class.classLoader.loadClass("test.Foo"))
    }

    void 'test inject missing provider'() {
        given:
        ApplicationContext context = buildContext('''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import io.micronaut.context.BeanProvider;

@jakarta.inject.Singleton
class Test {
    public BeanProvider<Foo> provider;
    public BeanProvider<Bar> barProvider;
    Test(BeanProvider<Foo> provider, BeanProvider<Bar> barProvider) {
        this.provider = provider;
        this.barProvider = barProvider;
    }
    public Foo get() {
        return provider.get();
    }
}

@jakarta.inject.Singleton
class Foo {}

class Bar {}
''')
        def bean = getBean(context, 'test.Test')

        expect:
        bean.provider.isPresent()
        !bean.barProvider.isPresent()

        cleanup:
        context.close()
    }
}
