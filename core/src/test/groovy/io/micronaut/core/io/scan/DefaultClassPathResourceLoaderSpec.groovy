package io.micronaut.core.io.scan

import spock.lang.Specification
import spock.lang.Subject
import spock.mock.MockFactory

class DefaultClassPathResourceLoaderSpec extends Specification {

    ClassLoader parent = Mock() {
        getResource(_) >> {
            args -> new URL("jar:" + this.class.classLoader.getResource("test.war") + "!/WEB-INF/classes!/" + args[0])
        }
    }

    @Subject
    DefaultClassPathResourceLoader loader = new DefaultClassPathResourceLoader(parent)

    def 'get file resource in war file'() {
        when:
        Optional<InputStream> input = loader.getResourceAsStream("application.yml")
        then:
        !input.empty
        input.get().text.contains("application configuration")
    }

    def 'no such resource in war file'() {
        when:
        Optional<InputStream> input = loader.getResourceAsStream("no-such-file.yml")
        then:
        input.empty
    }
}
