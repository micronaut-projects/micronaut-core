package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.JavaAnnotationMetadataBuilder
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AccessorsStyle
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.convert.format.MapFormat
import io.micronaut.core.naming.NameUtils
import io.micronaut.core.naming.conventions.StringConvention
import org.intellij.lang.annotations.Language
import spock.lang.Issue

import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement

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
        AnnotationMetadata metadata = buildArgumentAnnotationMetadataForSetterAndField(code, 'setAnimals')
        AnnotationValue annotationValue = metadata.getAnnotation(MapFormat)

        then:
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


    private AnnotationMetadata buildArgumentAnnotationMetadataForSetterAndField(@Language("java") String cls, String methodName) {
        TypeElement element = buildTypeElement(cls)
        ExecutableElement method = (ExecutableElement) element.enclosedElements.find {
            it.kind == ElementKind.METHOD && it.simpleName.contentEquals(methodName)
        }
        if (!method) {
            throw new RuntimeException("Method ${methodName} not found.")
        }

        JavaAnnotationMetadataBuilder builder = newJavaAnnotationBuilder()
        AnnotationMetadata methodAnnotationMetadata = builder.build(method)

        String[] writerPrefixes = methodAnnotationMetadata
                    .getValue(AccessorsStyle.class, "writePrefixes", String[].class)
                    .orElse(new String[]{AccessorsStyle.DEFAULT_WRITE_PREFIX})

        if (!NameUtils.isWriterName(methodName, writerPrefixes) || method.parameters.size() != 1) {
            throw new RuntimeException("Method ${methodName} is not a setter.")
        }

        VariableElement argument = method.parameters.first()
        String expectedFieldName = NameUtils.getPropertyNameForSetter(methodName, writerPrefixes)

        Element field = element.enclosedElements.find {
            it.kind == ElementKind.FIELD && it.simpleName.contentEquals(expectedFieldName)
        }

        argument != null ? builder.buildForParent(field, argument) : null
    }

}
