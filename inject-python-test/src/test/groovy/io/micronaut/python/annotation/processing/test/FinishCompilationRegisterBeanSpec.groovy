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
package io.micronaut.python.annotation.processing.test

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.inject.visitor.rounds.FailingCompilationVisitor
import io.micronaut.inject.visitor.rounds.Recorded
import io.micronaut.inject.visitor.rounds.RegisteredRecord
import io.micronaut.python.compiler.PyronautCompiler
import jakarta.inject.Singleton
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * A bean registered from {@link TypeElementVisitor#finishCompilation(VisitorContext)} is written for the Python classes
 * of a compilation by the Python compiler, and for its Java classes by javac, each once: a visitor that finishes the
 * compilation does not visit again, in javac, the Java classes generated for the Python classes.
 */
class FinishCompilationRegisterBeanSpec extends AbstractPythonTypeElementSpec {

    private static final String COMPILATION = 'compilation'
    private static final String ANNOTATED = 'annotated'

    @TempDir
    Path tempDir

    private Path javaSources

    @Override
    protected void configureCompiler(PyronautCompiler.Builder compilerBuilder) {
        if (javaSources != null) {
            compilerBuilder.javaSrc(javaSources.toString())
        }
    }

    void "test a bean registered once the compilation is finished is written"() {
        given:
        def context = buildContext('''
class CompiledFirst:
    pass

class CompiledSecond:
    pass
''')

        expect:"each Python class once, though the visitor of every class visits the classes javac compiles"
        recordedClasses(context, COMPILATION) == ['python.CompiledFirst', 'python.CompiledSecond']
        context.getBeansOfType(RegisteredRecord, Qualifiers.byName(COMPILATION)).every { it instanceof RegisteredRecord }

        cleanup:
        context?.close()
    }

    void "test the Python and the Java classes of a compilation are finished once each"() {
        given:"a Java source compiled with the Python source"
        javaSources = tempDir.resolve('java')
        Path javaFile = javaSources.resolve('mixed/CompiledJava.java')
        Files.createDirectories(javaFile.parent)
        Files.writeString(javaFile, '''
package mixed;

@jakarta.inject.Singleton
public class CompiledJava {
}
''')
        def context = buildContext('''
from jakarta.inject import Singleton

@Singleton
class CompiledPython:
    pass
''')

        expect:"a visitor of every class finishes the Python class once, and the Java class too"
        recordedClasses(context, COMPILATION) == ['mixed.CompiledJava', 'python.CompiledPython']

        and:"so does a visitor of the classes of an annotation"
        recordedClasses(context, ANNOTATED) == ['mixed.CompiledJava', 'python.CompiledPython']

        cleanup:
        context?.close()
    }

    void "test failing from finishCompilation fails the compilation"() {
        when:
        buildContext("""
class $className:
    pass
""")

        then:
        def e = thrown(Exception)
        e.message.contains(message + "python.$className")

        where:
        className                           | message
        FailingCompilationVisitor.FAIL      | FailingCompilationVisitor.FAILED_MESSAGE
        FailingCompilationVisitor.THROW     | FailingCompilationVisitor.THROWN_MESSAGE
    }

    /**
     * The classes recorded by the beans of the qualifier, each as many times as it was recorded, without the Java
     * classes the Python compiler generates beside the Python classes, such as {@code CompiledFirstTargetTypeMapping}:
     * those are classes of the javac compilation, which only javac visits.
     */
    private static List<String> recordedClasses(def context, String qualifier) {
        context.getBeanDefinitions(RegisteredRecord, Qualifiers.byName(qualifier))
                .collectMany { it.stringValues(Recorded, 'classes') as List<String> }
                .findAll { !it.endsWith('TargetTypeMapping') }
                .sort()
    }

    /**
     * Registers a bean of {@link RegisteredRecord} once the compilation is finished, for the classes whose name
     * starts with {@code Compiled}, whatever their language.
     */
    static class CompilationRecordingVisitor implements TypeElementVisitor<Object, Object> {

        private final List<String> compiled = []

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.simpleName.startsWith('Compiled')) {
                compiled.add(element.name)
            }
        }

        @Override
        void finishCompilation(VisitorContext context) {
            register(context, compiled, COMPILATION)
        }
    }

    /**
     * Registers a bean of {@link RegisteredRecord} once the compilation is finished, for the singletons whose name
     * starts with {@code Compiled}.
     */
    static class AnnotatedRecordingVisitor implements TypeElementVisitor<Singleton, Object> {

        private final List<String> compiled = []

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.simpleName.startsWith('Compiled')) {
                compiled.add(element.name)
            }
        }

        @Override
        void finishCompilation(VisitorContext context) {
            register(context, compiled, ANNOTATED)
        }
    }

    private static void register(VisitorContext context, List<String> compiled, String qualifier) {
        if (compiled.isEmpty()) {
            return
        }
        List<String> classes = compiled.toSorted()
        ClassElement[] originatingElements = classes.collect { context.getClassElement(it).orElseThrow() } as ClassElement[]
        ClassElement beanType = context.getRequiredClassElement(RegisteredRecord.name, context.elementAnnotationMetadataFactory)
        context.registerBean(beanType, originatingElements)
                .qualifier(qualifier)
                .annotate(AnnotationValue.builder(Recorded)
                        .member('classes', classes as String[])
                        .member('phase', qualifier)
                        .build())
        compiled.clear()
    }
}
