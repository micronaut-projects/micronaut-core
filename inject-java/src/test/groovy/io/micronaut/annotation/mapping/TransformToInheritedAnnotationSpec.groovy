package io.micronaut.annotation.mapping

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.annotation.TypedAnnotationTransformer
import io.micronaut.inject.visitor.VisitorContext

import java.lang.annotation.Inherited

class TransformToInheritedAnnotationSpec extends AbstractTypeElementSpec {

    void 'test transforming'() {
        given:
            def definition = buildBeanDefinition('addann.TransformToInherited', '''
package addann;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Executable;

interface MyInterfaceX {

    @io.micronaut.annotation.mapping.MyGet2
    @Executable
    String getHelloWorld();
}

@Bean
class TransformToInherited implements MyInterfaceX {

    @Override
    public String getHelloWorld() {
        return "Hello world";
    }
}
''')
        expect:
            definition.getRequiredMethod("getHelloWorld").hasAnnotation(MyGet2)
    }

    static class TheAnnotationMapper implements TypedAnnotationTransformer<MyGet2> {


        @Override
        List<AnnotationValue<?>> transform(AnnotationValue<MyGet2> annotation, VisitorContext visitorContext) {
            return List.of(
                    annotation.mutate().stereotype(
                            AnnotationValue.builder(Inherited).build()
                    ).build()
            )
        }

        @Override
        Class<MyGet2> annotationType() {
            return MyGet2.class
        }
    }


}
