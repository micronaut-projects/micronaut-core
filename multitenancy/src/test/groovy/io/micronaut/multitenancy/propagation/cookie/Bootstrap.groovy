package io.micronaut.multitenancy.propagation.cookie

import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import javax.inject.Singleton
import javax.inject.Inject

@Requires(property = 'spec.name', value = 'multitenancy.cookie.gorm')
@Singleton
class Bootstrap implements ApplicationEventListener<StartupEvent> {

    @Inject
    BookService bookService

    @Override
    void onApplicationEvent(StartupEvent event) {

        bookService.save('sherlock', 'Sherlock diary')
        bookService.save('watson', 'Watson diary')
    }
}
