package io.micronaut.ast.groovy

import io.micronaut.fixtures.context.MicronautApplicationTest
import org.codehaus.groovy.tools.FileSystemCompiler
import spock.lang.Issue

class GroovyCompilationSpec extends MicronautApplicationTest {

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/9915")
    def "compilation with the Groovy compiler works as expected"() {
        given:
        groovySourceFile("Annotation.groovy", """
import java.lang.annotation.ElementType
import java.lang.annotation.Target

@Target(ElementType.FIELD)
@interface Annotation {
}
""")

        groovySourceFile("Usage.groovy", """
// Usage.groovy
class Usage {
    @Annotation
    String field
}
""")
        when:
        FileSystemCompiler.commandLineCompile(testDirectory.resolve("src/main/java/Usage.groovy").toString())

        then:
        noExceptionThrown()
    }
}
