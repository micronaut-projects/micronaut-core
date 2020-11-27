package io.micronaut.inject.annotation


import edu.umd.cs.findbugs.annotations.NonNull
import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.Creator
import io.micronaut.core.bind.annotation.Bindable
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.HttpMethodMapping
import io.micronaut.http.annotation.QueryValue
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
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

    void "test annotation mapper with repeated annotation values"() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

@javax.inject.Singleton
class Test {


    @io.micronaut.context.annotation.Executable
    Test create(@io.micronaut.inject.annotation.CustomHeader String query) {
        return new Test();
    }
    
}

''')

        expect:
        definition != null
        def header = definition.getRequiredMethod('create', String).arguments[0].synthesize(Header)
        header != null
        header.name() == 'test'
        header.value() == 'test'
    }

    void "test stereotypes are not applied to the mapped from annotation"() {
        given:
        def metadata = buildMethodAnnotationMetadata('''
package test;

@javax.inject.Singleton
class Test {

    @io.micronaut.inject.annotation.CustomGet
    void test() {
    }   
}

''', 'test')

        expect:
        metadata != null
        def annotations = metadata.getAnnotationTypesByStereotype(HttpMethodMapping)
        annotations.size() == 1
        annotations[0] == Get
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
        if (annotationName == CustomGet.name) {

            return [
                    new CustomGetMapper()
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

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@interface CustomHeader {}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@interface CustomGet {}

class CustomHeaderMapper implements TypedAnnotationMapper<CustomHeader> {

    @Override
    List<AnnotationValue<?>> map(AnnotationValue<CustomHeader> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
                AnnotationValue.builder(Header).member("name", "test").value("test").build()
        )
    }

    @Override
    Class<CustomHeader> annotationType() {
        return CustomHeader
    }
}

class CustomGetMapper implements TypedAnnotationMapper<CustomGet> {

    @Override
    List<AnnotationValue<?>> map(AnnotationValue<CustomGet> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
                AnnotationValue.builder(Get).value("/test").build()
        )
    }

    @Override
    Class<CustomGet> annotationType() {
        return CustomGet
    }
}
