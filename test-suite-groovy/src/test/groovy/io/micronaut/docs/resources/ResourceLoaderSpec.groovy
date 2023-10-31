package io.micronaut.docs.resources

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class ResourceLoaderSpec extends Specification {

    void "test example for ResourceResolver"() {

        when:
        ApplicationContext applicationContext = ApplicationContext.run(
                'spec.name': 'ResourceLoaderSpec',
                'test'
        )
        MyResourceLoader myResourceLoader = applicationContext.getBean(MyResourceLoader)

        then:
        myResourceLoader
        Optional<String> text = myResourceLoader.getClasspathResourceAsText('hello.txt');
        text.isPresent()
        'Hello!' == text.get().trim()

        cleanup:
        applicationContext.stop()
    }
}
