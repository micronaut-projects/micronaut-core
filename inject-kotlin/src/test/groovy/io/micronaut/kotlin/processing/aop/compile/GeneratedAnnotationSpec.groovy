package io.micronaut.kotlin.processing.aop.compile

import io.micronaut.inject.writer.BeanDefinitionWriter
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import spock.lang.Issue
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.*

class GeneratedAnnotationSpec extends Specification {

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/4127')
    void 'test only 1 generated annotation is added'() {
        when:
        def bytes = getClassBytes('example.FooController' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''
package example

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.validation.Validated

@Validated
@Controller("/")
open class FooController {

    @Get
    open fun foo(): String {
        return ""
    }
}
''')
        then:
        bytes != null

        when:
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
