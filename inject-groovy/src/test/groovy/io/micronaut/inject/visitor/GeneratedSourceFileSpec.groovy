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
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionWriter
import jakarta.inject.Singleton
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.tools.javac.JavaAwareCompilationUnit
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class GeneratedSourceFileSpec extends AbstractBeanDefinitionSpec {

    @TempDir
    Path tempDir

    void setup() {
        GeneratedSourceVisitor.clearVisited()
        AllClassesVisitor.clearVisited()
    }

    void "a source file written by a visitor is compiled in the same Groovy compilation"() {
        when:
        def classLoader = buildClassLoader('''
package test

@io.micronaut.inject.visitor.GenerateSource
class Foo {
}
''')
        def generated = classLoader.loadClass('test.FooGenerated')

        then: 'the generated class is compiled with its annotations'
        generated.getAnnotation(Singleton) != null
        generated.getAnnotation(GeneratedFrom).value() == 'test.Foo'
        generated.newInstance().origin() == 'test.Foo'

        and: 'the generated class was visited, like any other class of the compilation, exactly once'
        AllClassesVisitor.visited.count { it == 'test.FooGenerated' } == 1
        AllClassesVisitor.visited.count { it == 'test.Foo' } == 1
        GeneratedSourceVisitor.visited == ['test.Foo']

        and: 'the bean definition of the generated singleton was written'
        def definitionName = 'test.$FooGenerated' + BeanDefinitionWriter.CLASS_SUFFIX
        classLoader.generatedClasses.containsKey(definitionName)
        BeanDefinition.isAssignableFrom(classLoader.loadClass(definitionName))
    }

    void "the generated bean is available from the context"() {
        given:
        def context = buildContext('''
package test

import jakarta.inject.Singleton

@io.micronaut.inject.visitor.GenerateSource
@Singleton
class Foo {
}
''')

        expect:
        getBean(context, 'test.FooGenerated').origin() == 'test.Foo'
        getBean(context, 'test.Foo') != null

        cleanup:
        context.close()
    }

    void "a generated source can itself generate a source"() {
        when:
        def classLoader = buildClassLoader('''
package test

@io.micronaut.inject.visitor.GenerateSource(chain = true)
class Foo {
}
''')

        then:
        classLoader.loadClass('test.FooGenerated').getAnnotation(GeneratedFrom).value() == 'test.Foo'
        classLoader.loadClass('test.FooGeneratedGenerated').getAnnotation(GeneratedFrom).value() == 'test.FooGenerated'
        GeneratedSourceVisitor.visited == ['test.Foo', 'test.FooGenerated']
        AllClassesVisitor.visited.count { it == 'test.Foo' } == 1
        AllClassesVisitor.visited.count { it == 'test.FooGenerated' } == 1
        AllClassesVisitor.visited.count { it == 'test.FooGeneratedGenerated' } == 1
    }

    void "sources generated from several classes are all compiled"() {
        when:
        def classLoader = buildClassLoader('''
package test

@io.micronaut.inject.visitor.GenerateSource
class Foo {
}

@io.micronaut.inject.visitor.GenerateSource
class Bar {
}
''')

        then:
        classLoader.loadClass('test.FooGenerated').getAnnotation(GeneratedFrom).value() == 'test.Foo'
        classLoader.loadClass('test.BarGenerated').getAnnotation(GeneratedFrom).value() == 'test.Bar'
        GeneratedSourceVisitor.visited == ['test.Foo', 'test.Bar']
    }

    void "the generated classes are written to the target directory of a compilation like the Gradle one"() {
        given: 'a compilation unit set up the way the Gradle Groovy compiler does, with sources on disk and a target directory'
        def sourceDir = Files.createDirectories(tempDir.resolve('src/test'))
        def targetDir = Files.createDirectories(tempDir.resolve('classes'))
        def source = Files.writeString(sourceDir.resolve('Foo.groovy'), '''
package test

@io.micronaut.inject.visitor.GenerateSource
class Foo {
}
''')
        def configuration = new CompilerConfiguration()
        configuration.targetDirectory = targetDir.toFile()
        configuration.jointCompilationOptions = [stubDir: Files.createDirectories(tempDir.resolve('stubs')).toFile()]

        and: 'the customizer Gradle incremental compilation registers, which maps every class to the file of its source unit'
        Map<String, String> classSources = [:]
        configuration.addCompilationCustomizers(new CompilationCustomizer(CompilePhase.CLASS_GENERATION) {
            @Override
            void call(SourceUnit su, GeneratorContext context, ClassNode classNode) {
                classSources[classNode.name] = new File(su.source.URI.path).canonicalPath
            }
        })
        def unit = new JavaAwareCompilationUnit(configuration, new GroovyClassLoader(getClass().classLoader, configuration))
        unit.addSources([source.toFile()] as File[])

        when:
        unit.compile()

        then: 'the generated class and its bean definition are in the target directory next to the source class'
        Files.exists(targetDir.resolve('test/Foo.class'))
        Files.exists(targetDir.resolve('test/FooGenerated.class'))
        Files.exists(targetDir.resolve('test/$FooGenerated' + BeanDefinitionWriter.CLASS_SUFFIX + '.class'))
        GeneratedSourceVisitor.visited == ['test.Foo']

        and: 'the generated class and its bean definition are attributed to the originating source file'
        classSources['test.Foo'] == source.toFile().canonicalPath
        classSources['test.FooGenerated'] == source.toFile().canonicalPath

        and: 'the generated class loads with its annotations'
        def classLoader = new URLClassLoader([targetDir.toUri().toURL()] as URL[], getClass().classLoader)
        classLoader.loadClass('test.FooGenerated').getAnnotation(GeneratedFrom).value() == 'test.Foo'

        cleanup:
        classLoader?.close()
    }

    void "a source cannot reference the class generated from it, since it is resolved before the visitor runs"() {
        when:
        buildClassLoader('''
package test

@io.micronaut.inject.visitor.GenerateSource
class Foo {
    FooGenerated generated
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('unable to resolve class FooGenerated')
    }

    void "a generated file can be written through its stream and read back, without originating elements"() {
        when:
        def classLoader = buildClassLoader('''
package test

@io.micronaut.inject.visitor.GenerateSource(stream = true)
class Foo {
}
''')

        then:
        classLoader.loadClass('test.FooGenerated').getAnnotation(GeneratedFrom).value() == 'test.Foo'
        GeneratedSourceVisitor.readBack[0] == 'memory:test/FooGenerated.groovy'
        GeneratedSourceVisitor.readBack[1].contains('class FooGenerated')
        GeneratedSourceVisitor.readBack[2] == GeneratedSourceVisitor.readBack[1]
        GeneratedSourceVisitor.readBack[3] == GeneratedSourceVisitor.readBack[1]

        and: 'no file is offered once class generation has started'
        GeneratedSourceVisitor.fileOfferedInFinish == false
    }

    void "a file written only once class generation has started fails instead of being lost"() {
        when:
        buildClassLoader('''
package test

@io.micronaut.inject.visitor.GenerateSource(late = "write")
class Foo {
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('generated sources must be written before class generation')
    }

    void "a file already handed to the compiler cannot be written again"() {
        when:
        buildClassLoader('''
package test

@io.micronaut.inject.visitor.GenerateSource(late = "rewrite")
class Foo {
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('has already been handed to the Groovy compiler')
    }

    void "a generated source that does not compile fails the compilation with its name"() {
        when:
        buildClassLoader('''
package test

@io.micronaut.inject.visitor.GenerateSource(body = "int broken = ")
class Foo {
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('test/FooGenerated.groovy')
    }
}
