package org.particleframework.inject.property

import org.particleframework.context.DefaultBeanContext
import org.particleframework.context.BeanContext
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

class SimplePropertyInjectSpec extends Specification {

    void "test that property injection works via the new operator"() {
        when:
        BeanContext context = new DefaultBeanContext()
        context.start()
        BookController controller = context.getBean(BookController)
        BookController2 controller2 = context.getBean(BookController2)

        then:
        controller != null
        controller.bookService != null
        !context.getBean(BookController).is(controller)
        !context.getBean(BookController2).is(controller2)
        context.getBeansOfType(BookService).contains(controller.bookService)
        controller.bookService.is(controller2.@bookService)
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

