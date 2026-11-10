package io.micronaut.annotation.processing.visitor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class JavaVisitorContextSpec extends AbstractTypeElementSpec {
    private List<TypeElementVisitor> localTypeElementVisitors

    def 'return enum from getClassElement'() {
        given:
        ClassElement original
        Optional<ClassElement> lookedUp
        def enumVisitor = new TypeElementVisitor<Object, Object>() {
            @Override
            void visitClass(ClassElement element, VisitorContext context) {
                original = element
                lookedUp = context.getClassElement(element.name)
            }
        }
        localTypeElementVisitors = [enumVisitor]

        buildClassLoader('example.Foo', '''
package example;
enum Foo {}
''')

        expect:
        lookedUp.isPresent()
        lookedUp.get().enum

        original.name == 'example.Foo'
        lookedUp.get().name == 'example.Foo'
    }

    def 'return enum from getClassElements'() {
        given:
        ClassElement[] lookedUp
        def enumVisitor = new TypeElementVisitor<Object, Object>() {
            @Override
            void visitClass(ClassElement element, VisitorContext context) {
                lookedUp = context.getClassElements(element.packageName, '*')
            }
        }
        localTypeElementVisitors = [enumVisitor]

        buildClassLoader('example.Foo', '''
package example;
enum Foo {}
''')

        expect:
        lookedUp.size() == 1
        lookedUp[0].enum
        lookedUp[0].name == 'example.Foo'
    }

    def 'return inner class from getClassElements'() {
        given:
        ClassElement[] lookedUp
        def typeVisitor = new TypeElementVisitor<Object, Object>() {
            @Override
            void visitClass(ClassElement element, VisitorContext context) {
                lookedUp = context.getClassElements(element.packageName, '*')
            }
        }
        localTypeElementVisitors = [typeVisitor]

        buildClassLoader('example.Foo', '''
package example;
class Foo {
  class Bar {}
}
''')

        expect:
        lookedUp.size() == 2
        lookedUp[0].name == 'example.Foo'
        lookedUp[1].name == 'example.Foo$Bar'
    }

    def 'write files whose originating elements include one without a native element'() {
        given:
        def visitor = new TypeElementVisitor<Object, Object>() {
            @Override
            void visitClass(ClassElement element, VisitorContext context) {
                context.visitMetaInfFile('test/originating.txt', ClassElement.of(String), element).ifPresent { file ->
                    file.openWriter().withCloseable { it.write(element.name) }
                }
                context.visitServiceDescriptor('test.Service', 'test.Implementation', ClassElement.of(String))
            }
        }
        localTypeElementVisitors = [visitor]

        when:
        def classLoader = buildClassLoader('example.Foo', '''
package example;
class Foo {}
''')

        then:
        classLoader.getResources('META-INF/test/originating.txt').toList()*.text == ['example.Foo']
        classLoader.getResources('META-INF/micronaut/test.Service/test.Implementation').toList().size() == 1
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return localTypeElementVisitors
    }
}
