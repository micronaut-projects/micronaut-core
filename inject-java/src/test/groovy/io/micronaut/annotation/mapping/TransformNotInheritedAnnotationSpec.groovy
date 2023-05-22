package io.micronaut.annotation.mapping

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.HttpMethodMapping
import io.micronaut.inject.annotation.TypedAnnotationTransformer
import io.micronaut.inject.visitor.VisitorContext

class TransformNotInheritedAnnotationSpec extends AbstractTypeElementSpec {

    void 'test transforming'() {
        given:
            def definition = buildBeanDefinition('addann.TransformNotInherited', '''
package addann;

import io.micronaut.context.annotation.Bean;

interface MyInterface {

    @io.micronaut.annotation.mapping.MyGet1
    String getHelloWorld();
}

@Bean
class TransformNotInherited implements MyInterface {

    @Override
    public String getHelloWorld() {
        return "Hello world";
    }
}
''')
        expect:
            definition.getRequiredMethod("getHelloWorld").hasAnnotation(Get)
            definition.getRequiredMethod("getHelloWorld").hasStereotype(HttpMethodMapping)
    }

    static class TheAnnotationMapper implements TypedAnnotationTransformer<MyGet1> {


        @Override
        List<AnnotationValue<?>> transform(AnnotationValue<MyGet1> annotation, VisitorContext visitorContext) {
            return List.of(
                    AnnotationValue.builder(Get.class).build()
            )
        }

        @Override
        Class<MyGet1> annotationType() {
            return MyGet1.class
        }
    }


}
