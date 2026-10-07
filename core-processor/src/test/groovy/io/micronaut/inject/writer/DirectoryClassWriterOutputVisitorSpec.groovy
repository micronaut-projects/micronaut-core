package io.micronaut.inject.writer

import io.micronaut.inject.ast.Element
import spock.lang.Specification
import spock.lang.TempDir

class DirectoryClassWriterOutputVisitorSpec extends Specification {

    @TempDir
    File targetDir

    void "the entry of a service holds what was written last"() {
        given:
        def visitor = new DirectoryClassWriterOutputVisitor(targetDir)
        def entry = new File(targetDir, 'META-INF/micronaut/a.Service/a.$Impl$Definition')

        when:
        visitor.visitServiceDescriptor('a.Service', 'a.$Impl$Definition', Mock(Element), [1, 2, 3, 4] as byte[])

        then:
        entry.bytes == [1, 2, 3, 4] as byte[]

        when: "less is written over it, as when the class is compiled again"
        visitor.visitServiceDescriptor('a.Service', 'a.$Impl$Definition', Mock(Element), [9] as byte[])

        then:
        entry.bytes == [9] as byte[]

        when: "no content is written over it"
        visitor.visitServiceDescriptor('a.Service', 'a.$Impl$Definition', Mock(Element))

        then:
        entry.file
        entry.length() == 0
    }

    void "a visitor that writes no content is given the entry without it"() {
        given:
        List<List<String>> visited = []
        ClassWriterOutputVisitor visitor = new AbstractClassWriterOutputVisitor(false) {
            @Override
            OutputStream visitClass(String classname, Element... originatingElements) {
                throw new UnsupportedOperationException()
            }

            @Override
            void visitServiceDescriptor(String type, String classname, Element originatingElement) {
                visited << [type, classname]
            }

            @Override
            Optional<GeneratedFile> visitMetaInfFile(String path, Element... originatingElements) {
                return Optional.empty()
            }

            @Override
            Optional<GeneratedFile> visitGeneratedFile(String path) {
                return Optional.empty()
            }

            @Override
            Optional<GeneratedFile> visitGeneratedFile(String path, Element... originatingElements) {
                return Optional.empty()
            }
        }

        when:
        visitor.visitServiceDescriptor('a.Service', 'a.$Impl$Definition', Mock(Element), [1, 2] as byte[])

        then:
        visited == [['a.Service', 'a.$Impl$Definition']]
    }
}
