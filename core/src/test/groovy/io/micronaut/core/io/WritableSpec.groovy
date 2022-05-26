package io.micronaut.core.io

import io.micronaut.core.version.SemanticVersion
import spock.lang.Requires
import spock.lang.Specification
import spock.util.environment.Jvm

// fails due to https://issues.apache.org/jira/browse/GROOVY-10145
@Requires({
    SemanticVersion.isAtLeastMajorMinor(GroovySystem.version, 4, 0) ||
            !Jvm.current.isJava16Compatible()
})
class WritableSpec extends Specification {

    void "test flush is called"() {
        given:
        Writable writable = (writer) -> {}
        OutputStream outputStream = Mock(OutputStream)

        when:
        writable.writeTo(outputStream)

        then:
        1 * outputStream.flush()
    }
}
