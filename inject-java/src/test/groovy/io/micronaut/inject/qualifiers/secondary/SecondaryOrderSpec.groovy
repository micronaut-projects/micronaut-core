/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.inject.qualifiers.secondary

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NonUniqueBeanException

class SecondaryOrderSpec extends AbstractTypeElementSpec {

    void "test @Order decides between candidates that are all @Secondary"() {
        given:
        ApplicationContext context = buildContext('''
package test;

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;

interface Service {
}

@Singleton
@Secondary
@Order(10)
class Low implements Service {
}

@Singleton
@Secondary
@Order(-10)
class High implements Service {
}
''')

        when:
        def service = getBean(context, 'test.Service')

        then:"the highest precedence secondary is picked, as it would be without @Secondary"
        service.getClass().name == 'test.High'

        cleanup:
        context.close()
    }

    void "test candidates that are all @Secondary with the same order are still ambiguous"() {
        given:
        ApplicationContext context = buildContext('''
package test;

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;

interface Service {
}

@Singleton
@Secondary
@Order(10)
class One implements Service {
}

@Singleton
@Secondary
@Order(10)
class Two implements Service {
}
''')

        when:
        getBean(context, 'test.Service')

        then:
        def e = thrown(NonUniqueBeanException)
        e.message.contains('Multiple possible bean candidates found')

        cleanup:
        context.close()
    }

    void "test a non-secondary candidate still wins over secondary ones with a better order"() {
        given:
        ApplicationContext context = buildContext('''
package test;

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Order;
import jakarta.inject.Singleton;

interface Service {
}

@Singleton
@Secondary
@Order(-100)
class SecondaryOne implements Service {
}

@Singleton
@Secondary
@Order(-200)
class SecondaryTwo implements Service {
}

@Singleton
@Order(100)
class Regular implements Service {
}
''')

        when:
        def service = getBean(context, 'test.Service')

        then:
        service.getClass().name == 'test.Regular'

        cleanup:
        context.close()
    }
}
