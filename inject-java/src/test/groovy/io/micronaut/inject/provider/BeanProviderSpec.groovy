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
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.context.exceptions.NonUniqueBeanException
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import io.micronaut.inject.qualifiers.Qualifiers

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
        when:
        def bean = getBean(context, 'test.Test')

        def type = context.classLoader.loadClass('test.Foo')
        def fooProvider = context.getProvider(type)
        def fooProvider2 = context.getProvider(Argument.of(type))

        then:
        bean.provider.isPresent()
        !bean.barProvider.isPresent()
        bean.provider.find(null).isPresent()
        !bean.barProvider.find(null).isPresent()
        bean.provider.get().is(fooProvider.get())
        fooProvider.get().is(fooProvider2.get())
        fooProvider.asJakartaProvider().get().is(fooProvider.get())

        when:
        bean.barProvider.get()

        then:
        thrown(NoSuchBeanException)

        when:
        bean.barProvider.getDefinition()

        then:
        thrown(NoSuchBeanException)

        when:
        BeanDefinition definition = bean.provider.getDefinition()

        then:
        definition != null

        cleanup:
        context.close()
    }

    void "test BeanProvider's find method" () {
        given:
        ApplicationContext context = buildContext('''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import io.micronaut.context.BeanProvider;

@jakarta.inject.Qualifier
@java.lang.annotation.Documented
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@interface OneQualifier { }

@jakarta.inject.Qualifier
@java.lang.annotation.Documented
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@interface TwoQualifier { }

interface BeanNumber { }

@OneQualifier
class BeanOne implements BeanNumber { }

@OneQualifier
@TwoQualifier
class BeanOneTwo implements BeanNumber { }

@jakarta.inject.Singleton
class Test {
    public BeanProvider<BeanNumber> provider;
    public BeanProvider<BeanNumber> providerTwo;
    Test(@OneQualifier BeanProvider<BeanNumber> provider, @TwoQualifier BeanProvider<BeanNumber> providerTwo) {
        this.provider = provider;
        this.providerTwo = providerTwo;
    }
}
''')
        when: 'retrieve test bean'
        def bean = getBean(context, 'test.Test')

        then: 'providerTwo successfully finds BeanOneTwo because of injection point qualifier'
        bean.provider.isPresent()
        bean.providerTwo.find(null).get().class.name == 'test.BeanOneTwo'

        when: 'attempt to find bean through provider with null qualifier as argument'
        bean.provider.find(null)

        then: 'NonUniqueBeanException is thrown, as both BeanOne and BeanOneTwo are qualified by @OneQualifier'
        thrown(NonUniqueBeanException)

        when: 'attempt to find bean through provider with @TwoQualifier as argument'
        def metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredAnnotation('test.TwoQualifier', Collections.emptyMap())
        def foundBean = bean.provider.find(Qualifiers.byAnnotation(metadata, 'test.TwoQualifier'))

        then: 'BeanOneTwo is found'
        foundBean.isPresent()
        foundBean.get().class.name == 'test.BeanOneTwo'

        cleanup:
        context.close()
    }

    void "test BeanProvider's get by qualifier method" () {
        given:
        ApplicationContext context = buildContext('''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import io.micronaut.context.BeanProvider;

@jakarta.inject.Qualifier
@java.lang.annotation.Documented
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@interface OneQualifier { }

@jakarta.inject.Qualifier
@java.lang.annotation.Documented
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@interface TwoQualifier { }

interface BeanNumber { }

@OneQualifier
class BeanOne implements BeanNumber { }

@OneQualifier
@TwoQualifier
class BeanOneTwo implements BeanNumber { }

@jakarta.inject.Singleton
class Test {
    public BeanProvider<BeanNumber> provider;
    public BeanProvider<BeanNumber> providerTwo;
    Test(@OneQualifier BeanProvider<BeanNumber> provider, @TwoQualifier BeanProvider<BeanNumber> providerTwo) {
        this.provider = provider;
        this.providerTwo = providerTwo;
    }
}
''')
        when: 'retrieve test bean'
        def bean = getBean(context, 'test.Test')

        then: 'providerTwo successfully gets BeanOneTwo because of injection point qualifier'
        bean.provider.isPresent()
        bean.providerTwo.get(null).class.name == 'test.BeanOneTwo'

        when: 'attempt to get bean through provider with null qualifier as argument'
        bean.provider.get(null)

        then: 'NonUniqueBeanException is thrown, as both BeanOne and BeanOneTwo are qualified by @OneQualifier'
        thrown(NonUniqueBeanException)

        when: 'attempt to get bean through provider with @TwoQualifier as argument'
        def metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredAnnotation('test.TwoQualifier', Collections.emptyMap())
        def foundBean = bean.provider.get(Qualifiers.byAnnotation(metadata, 'test.TwoQualifier'))

        then: 'BeanOneTwo is returned'
        foundBean.class.name == 'test.BeanOneTwo'

        cleanup:
        context.close()
    }

    void "test Jakarta Provider is triggering containsBean" () {
        given:
        ApplicationContext context = buildContext('''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.Provider;

interface BeanNumber { }

@jakarta.inject.Singleton
class BeanNumberImpl implements BeanNumber {
}

@jakarta.inject.Singleton
class Test {
    public Provider<BeanNumber> provider;
    Test(Provider<BeanNumber> provider) {
        this.provider = provider;
    }
}
''')
        def containsBeanCacheField = context.getClass().superclass.declaredFields.find {it.name == "containsBeanCache"}
        containsBeanCacheField.accessible = true
        Map containsBeanCache = containsBeanCacheField.get(context)

        when: 'retrieve test bean'
        int mapSize = containsBeanCache.size()
        def bean = getBean(context, 'test.Test')

        then: 'containsBean is triggered'
        containsBeanCache.size() == mapSize + 1

        then: 'bean exists'
        bean.provider.get()

        cleanup:
        context.close()
    }

    void "test BeanProvider is not triggering containsBean" () {
        given:
        ApplicationContext context = buildContext('''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import io.micronaut.context.BeanProvider;

interface BeanNumber { }

@jakarta.inject.Singleton
class Test {
    public BeanProvider<BeanNumber> provider;
    Test(BeanProvider<BeanNumber> provider) {
        this.provider = provider;
    }
}
''')
        def containsBeanCacheField = context.getClass().superclass.declaredFields.find {it.name == "containsBeanCache"}
        containsBeanCacheField.accessible = true
        Map containsBeanCache = containsBeanCacheField.get(context)

        when: 'retrieve test bean'
        int mapSize = containsBeanCache.size()
        def bean = getBean(context, 'test.Test')

        then: 'containsBean is not triggered'
        containsBeanCache.size() == mapSize

        then: 'containsBean is triggered'
        !bean.provider.isPresent()
        containsBeanCache.size() == mapSize + 1

        cleanup:
        context.close()
    }
}
