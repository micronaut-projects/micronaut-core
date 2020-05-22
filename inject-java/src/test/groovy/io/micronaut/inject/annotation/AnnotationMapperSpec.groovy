package io.micronaut.inject.annotation


import edu.umd.cs.findbugs.annotations.NonNull
import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.Creator
import io.micronaut.core.bind.annotation.Bindable
import io.micronaut.http.annotation.QueryValue
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.visitor.VisitorContext

import java.lang.annotation.Annotation
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

class AnnotationMapperSpec extends AbstractTypeElementSpec {

    void "test annotation mapper map stereotypes correctly"() {
        def metadata = buildMethodAnnotationMetadata('''
package test;

@javax.inject.Singleton
class Test {

    @io.micronaut.inject.annotation.CustomCreator
    @io.micronaut.inject.annotation.CustomQuery
    Test create() {
        return new Test();
    }
    
}
''', 'create')

        expect:
        metadata != null
        metadata.hasAnnotation(ConfigurationInject)
        metadata.hasDeclaredAnnotation(ConfigurationInject)

        metadata.hasStereotype(Creator)
        !metadata.hasDeclaredAnnotation(Creator)
        metadata.hasAnnotation(QueryValue)
        metadata.stringValue(QueryValue).get() == 'test'
        metadata.hasStereotype(Bindable)
        metadata.stringValue(Bindable).isPresent()
        metadata.stringValue(Bindable).get() == 'test'
    }

    @Override
    protected List<AnnotationMapper<? extends Annotation>> getLocalAnnotationMappers(@NonNull String annotationName) {
        if (annotationName == CustomCreator.name) {

            return [
                    new CustomCreatorMapper()
            ]
        }
        if (annotationName == CustomQuery.name) {

            return [
                    new CustomQueryMapper()
            ]
        }
        return Collections.emptyList()
    }

    static class CustomCreatorMapper implements AnnotationMapper<CustomCreator> {

        @Override
        List<AnnotationValue<?>> map(AnnotationValue<CustomCreator> annotation, VisitorContext visitorContext) {
            return Collections.singletonList(
                    AnnotationValue.builder(ConfigurationInject).build()
            )
        }
    }

    static class CustomQueryMapper implements AnnotationMapper<CustomQuery> {

        @Override
        List<AnnotationValue<?>> map(AnnotationValue<CustomQuery> annotation, VisitorContext visitorContext) {
            return Collections.singletonList(
                    AnnotationValue.builder(QueryValue).value("test").build()
            )
        }
    }

}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@interface CustomCreator {}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@interface CustomQuery {}