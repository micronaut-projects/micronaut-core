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
}
