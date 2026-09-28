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
package io.micronaut.docs.aop.retry

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class NamedRetrySpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
        'spec.name': 'NamedRetrySpec',
        'micronaut.retry.policies.books.attempts': 5,
        'micronaut.retry.policies.books.delay': '1ms'
    )

    void 'test named retry policy'() {
        given:
        NamedRetryBookService service = context.getBean(NamedRetryBookService)

        expect:
        service.findBook('The Stand').title == 'The Stand'
        service.reset() == 3

        when:
        service.getBook('The Stand')

        then:
        thrown(UncheckedIOException)
        service.reset() == 2
    }
}
