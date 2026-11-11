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
package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.BeanDefinition

/**
 * An explicit {@code @FakeApiResponses} container (the shape of swagger's {@code @ApiResponses}) must not make the
 * generated bean definition hard-reference the nested annotation types: the empty array defaults of the nested
 * {@code @FakeApiResponse} ({@code content}, {@code headers}, {@code links}, {@code extensions}) were generated
 * as typed empty arrays ({@code anewarray FakeContent}, ...), so loading the definition threw
 * {@code NoClassDefFoundError} when those types were only on the compile classpath, and the whole bean was
 * silently skipped. See https://github.com/micronaut-projects/micronaut-core/issues/14371
 *
 * <p>The assertions below check the safety property the fix guarantees: an empty array default never carries
 * the annotation type itself as its component type, so the generated definition holds no hard reference to it.</p>
 */
class ApiResponsesContainerDefaultsSpec extends AbstractKotlinCompilerSpec {

    void 'test explicit container does not generate typed empty array defaults for nested annotations'() {
        when:
        BeanDefinition<?> definition = buildBeanDefinition('apiresponses.HelloController', '''
package apiresponses

import io.micronaut.context.annotation.Executable
import io.micronaut.kotlin.processing.annotations.FakeApiResponse
import io.micronaut.kotlin.processing.annotations.FakeApiResponses
import jakarta.inject.Singleton

@Singleton
class HelloController {

    @Executable
    @FakeApiResponses(
        FakeApiResponse(responseCode = "200", description = "Says hello"),
        FakeApiResponse(responseCode = "202", description = "Accepted")
    )
    fun hello(): String = "hello"
}
''')
        def metadata = definition.getRequiredMethod('hello').getAnnotationMetadata()
        def responseDefaults = metadata.getDefaultValues('io.micronaut.kotlin.processing.annotations.FakeApiResponse')
        def containerDefaults = metadata.getDefaultValues('io.micronaut.kotlin.processing.annotations.FakeApiResponses')

        then: 'the nested annotation defaults are registered'
        responseDefaults != null
        responseDefaults['content'] != null
        responseDefaults['headers'] != null
        responseDefaults['links'] != null
        responseDefaults['extensions'] != null

        and: 'no empty array default is a typed array of the annotation type it defaults'
        responseDefaults['content'].getClass().componentType != FakeContent
        responseDefaults['headers'].getClass().componentType != FakeHeader
        responseDefaults['links'].getClass().componentType != FakeLink
        responseDefaults['extensions'].getClass().componentType != FakeExtension

        and: 'the container defaults are untyped too'
        containerDefaults != null
        containerDefaults['extensions'] != null
        containerDefaults['extensions'].getClass().componentType != FakeExtension
    }

    void 'test single nested annotation keeps untyped empty array defaults'() {
        when:
        BeanDefinition<?> definition = buildBeanDefinition('apiresponses.SingleController', '''
package apiresponses

import io.micronaut.context.annotation.Executable
import io.micronaut.kotlin.processing.annotations.FakeApiResponse
import jakarta.inject.Singleton

@Singleton
class SingleController {

    @Executable
    @FakeApiResponse(responseCode = "200", description = "Says hello")
    fun hello(): String = "single"
}
''')
        def metadata = definition.getRequiredMethod('hello').getAnnotationMetadata()
        def responseDefaults = metadata.getDefaultValues('io.micronaut.kotlin.processing.annotations.FakeApiResponse')

        then: 'the nested annotation defaults are registered and untyped'
        responseDefaults != null
        responseDefaults['content'] != null
        responseDefaults['content'].getClass().componentType != FakeContent
        responseDefaults['headers'].getClass().componentType != FakeHeader
        responseDefaults['links'].getClass().componentType != FakeLink
        responseDefaults['extensions'].getClass().componentType != FakeExtension
    }
}
