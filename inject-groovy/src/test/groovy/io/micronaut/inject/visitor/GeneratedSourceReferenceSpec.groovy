/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Hand-written Groovy code referencing a class that a visitor generates, as source, during the same compilation.
 */
class GeneratedSourceReferenceSpec extends AbstractBeanDefinitionSpec {

    private static final String CONTROLLER = '''
package test

@io.micronaut.inject.visitor.GenerateSource(body = 'static String show(Long id) { "/books/" + id }; static final String BASE = "/books"')
class BookController {
}
'''

    @TempDir
    Path tempDir

    void setup() {
        GeneratedSourceVisitor.clearVisited()
    }

    void "statically compiled code references a class generated in the same compilation"() {
        when:
        def classLoader = buildClassLoader(CONTROLLER + '''
@groovy.transform.CompileStatic
class Links {
    String show(Long id) {
        BookControllerGenerated.show(id)
    }
    String base() {
        BookControllerGenerated.BASE
    }
    String qualified(Long id) {
        test.BookControllerGenerated.show(id)
    }
    String inClosure(Long id) {
        def closure = { Long i -> BookControllerGenerated.show(i) }
        closure(id)
    }
    String inLambda(Long id) {
        java.util.function.Function<Long, String> f = (Long i) -> BookControllerGenerated.show(i)
        f.apply(id)
    }
    Class<?> type() {
        BookControllerGenerated
    }
}
''')
        def links = classLoader.loadClass('test.Links').newInstance()

        then:
        links.show(1L) == '/books/1'
        links.base() == '/books'
        links.qualified(2L) == '/books/2'
        links.inClosure(3L) == '/books/3'
        links.inLambda(4L) == '/books/4'
        links.type().name == 'test.BookControllerGenerated'
    }

    void "dynamic code references a class generated in the same compilation"() {
        when:
        def classLoader = buildClassLoader(CONTROLLER + '''
class Links {
    String prefix = BookControllerGenerated.BASE

    String show(Long id) {
        BookControllerGenerated.show(id)
    }
    String qualified(Long id) {
        test.BookControllerGenerated.show(id)
    }
    String inClosure(Long id) {
        [id].collect { BookControllerGenerated.show(it) }.first()
    }
    String origin() {
        BookControllerGenerated.newInstance().origin()
    }
}
''')
        def links = classLoader.loadClass('test.Links').newInstance()

        then:
        links.prefix == '/books'
        links.show(1L) == '/books/1'
        links.qualified(2L) == '/books/2'
        links.inClosure(3L) == '/books/3'
        links.origin() == 'test.BookController'
    }

    void "a local variable or a field named like the generated class is not replaced"() {
        when:
        def classLoader = buildClassLoader(CONTROLLER + '''
class Links {
    String local() {
        def BookControllerGenerated = [show: { Long id -> "local " + id }]
        BookControllerGenerated.show(1L)
    }
}

class Shadowed {
    Map BookControllerGenerated = [BASE: 'field']

    String base() {
        BookControllerGenerated.BASE
    }
}
''')

        then:
        classLoader.loadClass('test.Links').newInstance().local() == 'local 1'
        classLoader.loadClass('test.Shadowed').newInstance().base() == 'field'
    }

    void "code in other source files and packages of the compilation references the generated class"() {
        given:
        def sources = [
                'test/BookController.groovy': CONTROLLER,
                'test/SamePackage.groovy'   : '''
package test

@groovy.transform.CompileStatic
class SamePackage {
    String show(Long id) { BookControllerGenerated.show(id) }
}
''',
                'other/StarImport.groovy'   : '''
package other

import test.*

@groovy.transform.CompileStatic
class StarImport {
    String show(Long id) { BookControllerGenerated.show(id) }
}
''',
                'other/Qualified.groovy'    : '''
package other

class Qualified {
    String show(Long id) { test.BookControllerGenerated.show(id) }
}
''',
                'Script.groovy'             : '''
test.BookControllerGenerated.show(5L)
'''
        ]

        when:
        def classLoader = compile(sources)

        then:
        classLoader.loadClass('test.SamePackage').newInstance().show(1L) == '/books/1'
        classLoader.loadClass('other.StarImport').newInstance().show(2L) == '/books/2'
        classLoader.loadClass('other.Qualified').newInstance().show(3L) == '/books/3'
        classLoader.loadClass('Script').newInstance().run() == '/books/5'
        GeneratedSourceVisitor.visited == ['test.BookController']

        cleanup:
        classLoader?.close()
    }

    void "type checked code references a class generated in the same compilation"() {
        when:
        def classLoader = buildClassLoader(CONTROLLER + '''
@groovy.transform.TypeChecked
class Links {
    String show(Long id) {
        BookControllerGenerated.show(id)
    }
}
''')

        then:
        classLoader.loadClass('test.Links').newInstance().show(1L) == '/books/1'
    }

    void "a generated class cannot be referenced from a static method, which Groovy verifies before the visitors run"() {
        when:
        buildClassLoader(CONTROLLER + '''
class Links {
    static String show(Long id) {
        BookControllerGenerated.show(id)
    }
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains("Apparent variable 'BookControllerGenerated' was found in a static scope")
    }

    void "a generated class cannot be used as a type, which Groovy resolves before the visitors run"() {
        when:
        buildClassLoader(CONTROLLER + '''
class Links {
    Object create() {
        new BookControllerGenerated()
    }
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('unable to resolve class BookControllerGenerated')
    }

    void "a reference to a class that is not generated still fails statically compiled code"() {
        when:
        buildClassLoader(CONTROLLER + '''
@groovy.transform.CompileStatic
class Links {
    String show(Long id) {
        BookControllerMissing.show(id)
    }
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('The variable [BookControllerMissing] is undeclared')
    }

    private URLClassLoader compile(Map<String, String> sources) {
        def sourceDir = Files.createDirectories(tempDir.resolve('src'))
        def targetDir = Files.createDirectories(tempDir.resolve('classes'))
        def configuration = new CompilerConfiguration()
        configuration.targetDirectory = targetDir.toFile()
        def unit = new CompilationUnit(configuration, null, new GroovyClassLoader(getClass().classLoader, configuration))
        sources.each { path, text ->
            def file = sourceDir.resolve(path)
            Files.createDirectories(file.parent)
            unit.addSource(Files.writeString(file, text).toFile())
        }
        unit.compile()
        new URLClassLoader([targetDir.toUri().toURL()] as URL[], getClass().classLoader)
    }
}
