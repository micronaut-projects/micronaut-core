package io.micronaut.inject.annotation

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import spock.lang.Requires

import javax.validation.constraints.Min

class AnnotationsOnGenericTypesSpec extends AbstractTypeElementSpec {

    @Requires({ jvm.isJava11Compatible() })
    void 'test annotations on type arguments for executable methods'() {
        given:
        def definition = buildBeanDefinition('io.micronaut.inject.annotationgenerics.Test', '''
package io.micronaut.inject.annotationgenerics;

import io.micronaut.context.annotation.Executable;
import javax.inject.Singleton;
import java.util.List;
import java.util.List;
import javax.validation.constraints.Min;

@Singleton
class Test {

    @Executable
    void test(List<@Min(10) Long> values) {
    
    }
}
''')
        def method = definition.getRequiredMethod("test", List)
        def annotationMetadata = method.arguments[0].typeParameters[0].annotationMetadata

        expect:"annotations on type arguments should be present"
        annotationMetadata.hasAnnotation(Min)
    }
}
