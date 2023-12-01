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
package io.micronaut.inject.property

import io.micronaut.context.ApplicationContext
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

class SimplePropertyInjectSpec extends Specification {

    void "test that property injection works via the new operator"() {
        when:
        ApplicationContext context = ApplicationContext.run()
        BookController controller = context.getBean(BookController)
        BookController2 controller2 = context.getBean(BookController2)

        then:
        controller != null
        controller.bookService != null
        !context.getBean(BookController).is(controller)
        !context.getBean(BookController2).is(controller2)
        context.getBeansOfType(BookService).contains(controller.bookService)
        controller.bookService.is(controller2.@bookService)

        cleanup:
        context.close()
    }

}

@Singleton
class BookService {
}

class BookController {
    @Inject BookService bookService
}

class BookController2 {
    @Inject private BookService bookService
}

