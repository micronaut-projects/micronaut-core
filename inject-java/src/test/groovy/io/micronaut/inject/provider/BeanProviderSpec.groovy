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

import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.annotation.TestCachePuts

class BeanProviderSpec extends AbstractTypeElementSpec {

    void "test bean definition reference references correct bean type for Provider"() {
        given:
        BeanDefinitionReference definition = buildBeanDefinitionReference('test.Test','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@javax.inject.Singleton
class Test implements javax.inject.Provider<Foo>{

    public Foo get() {
        return new Foo();
    }
}

class Foo {}
''')
        expect:
        definition != null
        definition.getBeanType().name == 'test.Foo'
    }

    void "test inject bean with provider"() {
        given:
        BeanDefinitionReference ref = buildBeanDefinitionReference('test.Test','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;


@javax.inject.Singleton
class Test {
    javax.inject.Provider<Foo> provider;
    Test(javax.inject.Provider<Foo> provider) {
        this.provider = provider;
    }
    public Foo get() {
        return provider.get();
    }
}

@javax.inject.Singleton
class Foo {}
''')
        def definition = ref.load()

        expect:
        ref != null
        ref.getBeanType().name == 'test.Test'
        definition.constructor.arguments[0].typeParameters.length == 1
        definition.constructor.arguments[0].typeParameters.length == 1
        definition.constructor.arguments[0].typeParameters[0].type.name == 'test.Foo'
    }
}
