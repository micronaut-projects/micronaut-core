/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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
import io.micronaut.context.DefaultBeanResolutionContext
import io.micronaut.core.type.Argument

class JakartaProviderCreatedSingletonSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
class Holder {
    final Provider<Single> single;
    final Provider<Proto> proto;

    Holder(Provider<Single> single, Provider<Proto> proto) {
        this.single = single;
        this.proto = proto;
    }
}

@Singleton
class Single {
}

@Prototype
class Proto {
}
'''

    void "a jakarta provider returns the singleton the context holds, and the one created after it was destroyed"() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        def holder = getBean(context, 'test.Holder')

        when:
        def first = holder.single.get()

        then:
        holder.single.get().is(first)
        getBean(context, 'test.Single').is(first)

        when:
        context.destroyBean(first)
        def second = holder.single.get()

        then:
        !second.is(first)
        holder.single.get().is(second)

        cleanup:
        context.close()
    }

    void "a jakarta provider of a prototype creates an instance on every call"() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        def holder = getBean(context, 'test.Holder')

        expect:
        !holder.proto.get().is(holder.proto.get())
        !holder.proto.get().is(holder.proto.get())

        cleanup:
        context.close()
    }

    void "a #definition.class.simpleName that is not a singleton returns the created singleton and a new prototype"() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        Argument<Object> single = Argument.of(context.classLoader.loadClass('test.Single'))
        Argument<Object> proto = Argument.of(context.classLoader.loadClass('test.Proto'))
        def singleProvider = definition.buildProvider(new DefaultBeanResolutionContext(context, null), context, single, null, false)
        def protoProvider = definition.buildProvider(new DefaultBeanResolutionContext(context, null), context, proto, null, false)

        expect:
        singleProvider.get().is(context.getBean(single))
        singleProvider.get().is(singleProvider.get())
        !protoProvider.get().is(protoProvider.get())

        cleanup:
        context.close()

        where:
        definition << [new JakartaProviderBeanDefinition(), new JavaxProviderBeanDefinition()]
    }

    void "a #definition.class.simpleName that is a singleton keeps the bean it looked up first"() {
        given:
        ApplicationContext context = buildContext(SOURCE)
        Argument<Object> argument = Argument.of(context.classLoader.loadClass('test.Proto'))
        def provider = definition.buildProvider(new DefaultBeanResolutionContext(context, null), context, argument, null, true)

        when:
        def first = provider.get()

        then:
        provider.get().is(first)
        !context.getBean(argument).is(first)

        cleanup:
        context.close()

        where:
        definition << [new JakartaProviderBeanDefinition(), new JavaxProviderBeanDefinition()]
    }
}
