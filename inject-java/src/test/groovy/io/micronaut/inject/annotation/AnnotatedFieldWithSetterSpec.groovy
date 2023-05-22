package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.convert.format.MapFormat
import io.micronaut.core.naming.conventions.StringConvention
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.PropertyElement
import spock.lang.Issue

@Issue('https://github.com/micronaut-projects/micronaut-core/issues/4308')
class AnnotatedFieldWithSetterSpec extends AbstractTypeElementSpec {

    void 'test basic merge of field annotations into setter argument annotations'() {
        given:
        def code = '''
package test;

import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;
import jakarta.inject.Singleton;
import java.util.Map;

@Singleton
class AnnotatedFieldWithSetter {
    @MapFormat(keyFormat = StringConvention.RAW)
    private Map<String, String> animals;

    public void setAnimals(Map<String, String> animals) {
        this.animals = animals;
    }
}
'''

        when:
        ClassElement classElement = buildClassElement(code)
        PropertyElement propertyElement = classElement.getBeanProperties().iterator().next()

        then:
        AnnotationMetadata metadata = propertyElement.getAnnotationMetadata()
        AnnotationValue annotationValue = metadata.getAnnotation(MapFormat)
        annotationValue != null
        annotationValue.get('keyFormat', StringConvention).orElse(null) == StringConvention.RAW
    }

    void 'test that annotation on a field is applied to the corresponding setter argument'() {
        ApplicationContext context = ApplicationContext.run([
                'spec.name': 'AnnotatedFieldWithSetterSpec',
                'conf.animals.VERY_FAST': 'rabbit',
        ])

        def config = context.getBean(AnnotatedFieldWithSetter)

        expect:
        config.animals.containsKey('VERY_FAST')

        cleanup:
        context.close()
    }

}
