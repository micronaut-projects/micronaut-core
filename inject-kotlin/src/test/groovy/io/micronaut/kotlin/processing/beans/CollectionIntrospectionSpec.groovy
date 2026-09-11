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
package io.micronaut.kotlin.processing.beans

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec

class CollectionIntrospectionSpec extends AbstractKotlinCompilerSpec {

    void "test introspection of HashMap subclass has unique properties"() {
        when:
        def introspection = buildBeanIntrospection('test.Reversed', '''
package test

import io.micronaut.core.annotation.Introspected
import jakarta.inject.Singleton

@Introspected
@Singleton
class Reversed<A, B> : java.util.HashMap<B, A>()
''')

        then:
        introspection.beanProperties*.name == introspection.beanProperties*.name.unique()
        introspection.getProperty('keys').isPresent()
        introspection.getRequiredProperty('entries', Set).asArgument().typeParameters[0].type == Map.Entry
    }

    void "test introspection of ArrayList subclass has unique properties"() {
        when:
        def introspection = buildBeanIntrospection('test.Strings', '''
package test

import io.micronaut.core.annotation.Introspected
import jakarta.inject.Singleton

@Introspected
@Singleton
class Strings : java.util.ArrayList<String>()
''')

        then:
        introspection.beanProperties*.name == introspection.beanProperties*.name.unique()
        introspection.getProperty('size').isPresent()
    }
}
