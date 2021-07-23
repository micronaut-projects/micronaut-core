package io.micronaut.docs.inject.anninheritance

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.BeanDefinition
import spock.lang.AutoCleanup
import spock.lang.Specification

class AnnotationInheritanceSpec extends Specification {
    @AutoCleanup ApplicationContext context =  ApplicationContext.run(
            "datasource.url": "jdbc://someurl"
    )
    void "test annotation inheritance"() {
        given:
        final BeanDefinition<BookRepository> beanDefinition = context.getBeanDefinition(BookRepository.class);
        final String name = beanDefinition.stringValue(AnnotationUtil.NAMED).orElse(null);

        expect:
        name == 'bookRepository'
        beanDefinition.isSingleton()
    }
}
