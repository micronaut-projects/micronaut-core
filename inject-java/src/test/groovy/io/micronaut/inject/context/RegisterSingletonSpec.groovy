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
package io.micronaut.inject.context

import io.micronaut.context.ApplicationContext
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Type
import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.Qualifiers
import jakarta.inject.Named
import jakarta.inject.Singleton
import spock.lang.Issue
import spock.lang.Specification

import java.lang.reflect.Proxy

class RegisterSingletonSpec extends Specification {

    void "test register singleton with generic types"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        context.registerBeanDefinition(
                RuntimeBeanDefinition.builder(new TestReporter())
                        .exposedTypes(TestReporter.class, Reporter.class)
                        .typeArguments(Reporter, Argument.of(Span))
                        .build()
        )

        then:
        context.containsBean(Argument.of(Reporter, Span))

        cleanup:
        context.close()
    }

    void "test register singleton and exposed type"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        context.registerBeanDefinition(
                RuntimeBeanDefinition.builder(Codec, ()-> new OverridingCodec())
                        .singleton(true)
                        .qualifier(Qualifiers.byName("foo"))
                        .replaces(ToBeReplacedCodec)
                        .build()
        ) // replaces ToBeReplacedCodec
        context.registerSingleton(Codec, {  } as Codec) // adds a new codec
        context.registerSingleton(Codec, new FooCodec()) // adds another codec
            context.registerBeanDefinition(RuntimeBeanDefinition.builder(new BarCodec())
                    .exposedTypes(BarCodec.class, Codec.class)
                    .build()) // should be registered with bean type BarCodec
        context.registerSingleton(Codec, new BazCodec(), Qualifiers.byName("baz"))

        then:
        def codecs = context.getBeansOfType(Codec)
        codecs.size() == 7
        codecs.find { it in FooCodec }
        codecs.find { it in BarCodec }
        codecs.find { it in BazCodec }
        !codecs.find { it in ToBeReplacedCodec }
        codecs.find { it in OverridingCodec }
        codecs.find { it in OtherCodec }
        codecs.find { it in StuffCodec }
        codecs.find { it in Proxy }
        codecs == context.getBeansOfType(Codec) // second resolve returns the same result
        context.getBeansOfType(FooCodec).size() == 0 // not an exposed type
        context.getBeansOfType(BarCodec).size() == 1 // BarCodec type is exposed
        context.findBean(FooCodec).isEmpty() // not an exposed type
        context.findBean(StuffCodec).isEmpty() // not an exposed type
        context.findBean(OtherCodec).isPresent() // an exposed type

        cleanup:
        context.close()
    }


    void "test register singleton method"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        def b = new B()

        when:
        context.registerSingleton(b)

        then:
        context.getBean(B, Qualifiers.byTypeArguments())
        context.getBean(B) == b
        b.a != null
        b.a == context.getBean(A)

        cleanup:
        context.close()
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/1851')
    void "test register singleton with type qualifier"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        !context.findBean(DynamicService, Qualifiers.byTypeArguments(String)).present

        when:
        context.registerSingleton(DynamicService, new DefaultDynamicService<String>(String), Qualifiers.byTypeArguments(String))

        then:
        context.findBean(DynamicService, Qualifiers.byTypeArguments(String)).present
        context.findBean(DynamicService, Qualifiers.byTypeArguments(String)).get() instanceof DefaultDynamicService
        context.findBean(DynamicService, Qualifiers.byTypeArguments(String)).get().type == String

        and:
        !context.findBean(DynamicService, Qualifiers.byTypeArguments(Long)).present

        when:
        context.registerSingleton(DynamicService, new DefaultDynamicService<Long>(Long), Qualifiers.byTypeArguments(Long))

        then:
        context.findBean(DynamicService, Qualifiers.byTypeArguments(Long)).present
        context.findBean(DynamicService, Qualifiers.byTypeArguments(Long)).get() instanceof DefaultDynamicService
        context.findBean(DynamicService, Qualifiers.byTypeArguments(Long)).get().type == Long

        cleanup:
        context.close()
    }

    static interface DynamicService<T> {}

    @Type(String)
    static class DefaultDynamicService<T> implements DynamicService<T> {
        final Class<T> type

        DefaultDynamicService(Class<T> type) {
            this.type = type
        }
    }

    static interface Codec {

    }

    static class OverridingCodec implements Codec {}
    static class FooCodec implements Codec {}
    static class BarCodec implements Codec {}
    static class BazCodec implements Codec {}
    @Singleton
    @Bean(typed = Codec)
    static class StuffCodec implements Codec {}
    @Singleton
    static class OtherCodec implements Codec {}

    @Singleton
    @Named("foo")
    static class ToBeReplacedCodec implements Codec {}

    static interface Reporter<B> {}
    static class Span {}
    static class TestReporter implements Reporter<Span> {}
}
