package org.particleframework.inject

import org.particleframework.context.DefaultComponentDefinition
import org.particleframework.context.DefaultComponentDefinitionClass
import org.particleframework.context.DefaultContext
import org.particleframework.context.Context
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 10/05/2017.
 */
class DefaultContextSpec extends Specification {

    void "test basic property injection"() {
        given:
        Context context = new DefaultContext() {
            @Override
            protected Iterable<DefaultComponentDefinitionClass> resolveComponentDefinitionClasses() {
                return [new DefaultComponentDefinitionClass(BookServiceComponentDef), new DefaultComponentDefinitionClass(BookControllerDef)]
            }
        }

        when:
        BookController controller = context.getBean(BookController)

        then:
        controller.bookService != null
    }
}

class BookServiceComponentDef extends DefaultComponentDefinition<BookService> {
    BookServiceComponentDef() {
        super(BookService, BookService.getConstructor())
    }
}

class BookControllerDef extends DefaultComponentDefinition<BookController> {
    BookControllerDef() {
        super(BookController, BookController.getConstructor())
        addInjectionPoint(BookController.getDeclaredField("bookService"))
    }
}

@Singleton
class BookService {

}

class BookController {
    @Inject BookService bookService
}

class BookController2 {
    @Inject BookService bookService
}
