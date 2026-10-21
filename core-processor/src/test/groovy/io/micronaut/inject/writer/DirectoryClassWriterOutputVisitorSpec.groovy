package io.micronaut.inject.writer

import io.micronaut.inject.ast.Element
import spock.lang.Specification
import spock.lang.TempDir

class DirectoryClassWriterOutputVisitorSpec extends Specification {

    @TempDir
    File targetDir

    void "the entry of a service is empty when it is written over one with content"() {
        given: "an entry that a previous compilation wrote with content"
        def entry = new File(targetDir, 'META-INF/micronaut/a.Service/a.$Impl$Definition')
        entry.parentFile.mkdirs()
        entry.bytes = [1, 2, 3, 4] as byte[]

        when:
        new DirectoryClassWriterOutputVisitor(targetDir).visitServiceDescriptor('a.Service', 'a.$Impl$Definition', Mock(Element))

        then:
        entry.file
        entry.length() == 0
    }
}
