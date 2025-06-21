package io.micronaut.kotlin.processing.aop.introduction

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.annotation.processing.test.KotlinCompiler
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.processing.ProcessingException
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class MyRepo3Spec extends AbstractKotlinCompilerSpec {

    void 'test my repo 3'() {
        given:
            def definition = KotlinCompiler.buildIntroducedBeanDefinition('test.MyRepo3', '''
package test

data class MyEntity(val id: Long, var name: String)

@io.micronaut.kotlin.processing.aop.introduction.RepoDef
interface MyRepoX : io.micronaut.kotlin.processing.aop.introduction.CrudRepo<MyEntity, Int>

@io.micronaut.kotlin.processing.aop.introduction.RepoDef
interface MyRepo3 : io.micronaut.kotlin.processing.aop.introduction.CoroutineCrudRepository<MyEntity, Int>

''')
        expect:
            definition.getExecutableMethods().size() == 12
            definition.getExecutableMethods().stream().allMatch { it.hasStereotype(Marker) }
    }


    static class MyRepositoryVisitor implements TypeElementVisitor<Object, Object> {

        boolean enable

        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            enable = element.getSimpleName() == "MyRepo3"
            if (enable) {
                // Trigger a second round of BeanDefinitionProcessor
                context.visitGeneratedSourceFile(
                        "test",
                        "HelloWorld",
                        element
                ).ifPresent(sourceFile -> {
                    try {
                        sourceFile.write(writer -> writer.write("data class HelloWorld(val name: String)"))
                    } catch (Exception e) {
                        throw new ProcessingException(element, "Failed to generate a builder: " + e.getMessage(), e);
                    }
                })
            }
        }

        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            if (enable) {
                element.annotate(Marker)
            }
        }
    }
}
