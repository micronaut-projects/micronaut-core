package io.micronaut.aop.compile

import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.support.Parser
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import spock.lang.Issue

import javax.tools.JavaFileObject

class GeneratedAnnotationSpec extends AbstractTypeElementSpec {
    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/4127')
    void 'test only 1 generated annotation is added'() {
        when:
        def files = Parser.generate('example.FooController', '''
package example;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.validation.Validated;

@Validated
@Controller("/")
class FooController {

    @Get
    String foo() {
        return "";
    }
}
''')
        JavaFileObject f = files.find { it -> it.name.contains('FooControllerDefinition$Intercepted.class') }
        def bytes = f.openInputStream().withCloseable {it.bytes }
        ClassReader reader = new ClassReader(bytes)
        int generatedAnnotations = 0
        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (descriptor.contains("Generated")) {
                    generatedAnnotations++
                }
                return super.visitAnnotation(descriptor, visible)
            }
        },ClassReader.SKIP_CODE)

        then:"Only one generated annotation is added"
        generatedAnnotations == 1
    }
}
