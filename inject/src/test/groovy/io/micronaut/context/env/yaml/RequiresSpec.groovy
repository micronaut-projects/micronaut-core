package io.micronaut.context.env.yaml

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import spock.lang.IgnoreIf
import spock.lang.Specification

import javax.inject.Singleton

@IgnoreIf({ os.windows })
class RequiresSpec extends Specification {

    void "requirements for existing files are checked"() {
        given:
        File tmpFile = new File("/tmp/foo.txt")
        tmpFile.text = "bar"
        tmpFile.deleteOnExit()

        when:
        ApplicationContext ctx = ApplicationContext.run()

        then:
        ctx.containsBean(ShouldLoadBean)
        ctx.containsBean(ShouldLoadAsWell)
        ctx.containsBean(ShouldAlsoLoad)
        !ctx.containsBean(ShouldNotLoad)
        !ctx.containsBean(ShouldNotLoadEither)
    }

    @Requires(resources = "file:/tmp/foo.txt")
    @Singleton
    static class ShouldLoadBean {}

    @Requires(resources = "classpath:logback.xml")
    @Singleton
    static class ShouldLoadAsWell{}

    @Singleton
    static class ShouldAlsoLoad {}

    @Requires(resources = "file:/this/does/not/exist.txt")
    @Singleton
    static class ShouldNotLoad {}

    @Requires(resources = "classpath:/this/does/not/exist.txt")
    @Singleton
    static class ShouldNotLoadEither {}
}
