package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class AnnotateMethodSpec extends AbstractKotlinCompilerSpec {

    void 'test annotating'() {
        when:
            def definition = buildBeanDefinition('addann.AnnotationMethodClass', '''
package addann;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Bean;

@Bean
class AnnotationMethodClass {

    @Executable
    fun myMethod1() : MyBean1? {
        return null
    }

    @Executable
    fun myMethod2() : MyBean1? {
        return null
    }

}

class MyBean1 {
}

''')
        then: "myMethod1 has added annotation on the method and it's seen on the return type"
            definition.getRequiredMethod("myMethod1").hasAnnotation(MyAnnotation)
            definition.getRequiredMethod("myMethod1").getReturnType().getAnnotationMetadata().hasAnnotation(MyAnnotation)
            definition.getRequiredMethod("myMethod1").getReturnType().asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation)

        and: "myMethod2 doesn't have the same annotation on the same type"
            !definition.getRequiredMethod("myMethod2").hasAnnotation(MyAnnotation)
            !definition.getRequiredMethod("myMethod2").getReturnType().getAnnotationMetadata().hasAnnotation(MyAnnotation)
            !definition.getRequiredMethod("myMethod2").getReturnType().asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation)
    }

    static class AnnotationMethodVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.getSimpleName() == "AnnotationMethodClass") {
                def myMethod1 = element.findMethod("myMethod1").get()
                assert myMethod1.getAnnotationMetadata().getAnnotationNames().asList() == [Executable.class.name, Bean.class.name, AnnotationUtil.NULLABLE]
                myMethod1.annotate(MyAnnotation)
                assert myMethod1.getAnnotationMetadata().getAnnotationNames().asList() == [Executable.class.name, Bean.class.name, AnnotationUtil.NULLABLE, MyAnnotation.class.name]
                assert myMethod1.getAnnotationMetadata().getAnnotationNames().asList() == [Executable.class.name, Bean.class.name, AnnotationUtil.NULLABLE, MyAnnotation.class.name]
                assert myMethod1.getReturnType().getAnnotationNames().isEmpty()
                assert myMethod1.getReturnType().getTypeAnnotationMetadata().getAnnotationNames().isEmpty()
                assert myMethod1.getReturnType().getType().getAnnotationMetadata().isEmpty()

                // Validate the cache is working
                assert context.getClassElement("addann.AnnotationMethodClass").get()
                        .findMethod("myMethod1").get()
                        .getAnnotationMetadata().getAnnotationNames().asList() == [Executable.class.name, Bean.class.name, AnnotationUtil.NULLABLE, MyAnnotation.class.name]

                // Test the second method with the same type doesn't have the annotations

                def myMethod2 = element.findMethod("myMethod2").get()
                assert myMethod2.getAnnotationMetadata().getAnnotationNames().asList() == [Executable.class.name, Bean.class.name, AnnotationUtil.NULLABLE]
                assert myMethod2.getReturnType().getAnnotationNames().isEmpty()
                assert myMethod2.getReturnType().getTypeAnnotationMetadata().getAnnotationNames().isEmpty()

                // Validate the cache is working
                assert context.getClassElement("addann.AnnotationMethodClass").get()
                        .findMethod("myMethod2").get()
                        .getAnnotationMetadata().getAnnotationNames().asList() == [Executable.class.name, Bean.class.name, AnnotationUtil.NULLABLE]

                assert context.getClassElement("addann.MyBean1").get().getAnnotationMetadata().isEmpty()
            }
        }
    }

}
