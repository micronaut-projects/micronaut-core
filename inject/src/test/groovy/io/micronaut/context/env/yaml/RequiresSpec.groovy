package io.micronaut.context.env.yaml

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import spock.lang.Specification

import javax.inject.Singleton

class RequiresSpec extends Specification {

    void "requirements for existing files are checked"() {
        when:
        ApplicationContext ctx = ApplicationContext.run()

        then:
        ctx.containsBean(ShouldAlsoLoad)
        ctx.containsBean(ShouldLoadBean)
        !ctx.containsBean(ShouldNotLoad)
    }

    @Requires(files = "/tmp")
    @Singleton
    static class ShouldLoadBean {}

    @Singleton
    static class ShouldAlsoLoad {}

    @Requires(files = "/this/does/not/exist.txt")
    @Singleton
    static class ShouldNotLoad {}
}
