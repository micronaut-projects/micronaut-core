/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.docs.aop.retry

import io.micronaut.context.ApplicationContext
import reactor.core.publisher.Mono
import spock.lang.Specification

class ProgrammaticRetrySpec extends Specification {

    void 'test programmatic retry examples'() {
        given:
        ApplicationContext context = ApplicationContext.run()
        ProgrammaticBookService service = context.getBean(ProgrammaticBookService)

        when:
        service.reset()
        String syncTitle = service.listBooks().first().title
        service.reset()
        String reactiveTitle = Mono.from(service.streamBooks()).block().title
        service.reset()
        String asyncTitle = service.findBook('The Stand').toCompletableFuture().get().title

        then:
        syncTitle == 'The Stand'
        reactiveTitle == 'The Stand'
        asyncTitle == 'The Stand'

        when:
        service.reset()
        String circuitTitle = service.findBookWithCircuitBreaker('The Stand').title

        then:
        circuitTitle == 'The Stand'

        cleanup:
        context.close()
    }
}
