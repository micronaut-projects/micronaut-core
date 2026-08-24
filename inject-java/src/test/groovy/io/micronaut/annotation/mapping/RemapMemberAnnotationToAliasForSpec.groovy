package io.micronaut.annotation.mapping

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.annotation.AliasFor
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.AnnotationValueBuilder
import io.micronaut.inject.annotation.TypedAnnotationTransformer
import io.micronaut.inject.visitor.VisitorContext

class RemapMemberAnnotationToAliasForSpec extends AbstractTypeElementSpec {

    void 'test member annotation transformed to AliasFor overrides the stereotype member'() {
        given:
            def definition = buildBeanDefinition('addann.OverridesTest', '''
package addann;

import io.micronaut.annotation.mapping.MyOverrides;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Executable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@interface SizeLike {
    int min() default 0;
    int max() default 100;
}

@SizeLike(min = 10)
@Retention(RetentionPolicy.RUNTIME)
@interface ComposedSize {

    @MyOverrides(constraint = SizeLike.class)
    int min() default 5;

    @MyOverrides(constraint = SizeLike.class, name = "max")
    int limit() default 50;
}

@Bean
class OverridesTest {

    @ComposedSize(min = 3, limit = 30)
    @Executable
    public String getValue() {
        return "";
    }
}
''')
            def metadata = definition.getRequiredMethod("getValue").getAnnotationMetadata()

        expect: 'the transformed alias value wins over the value declared on the stereotype'
            metadata.hasStereotype('addann.SizeLike')
            metadata.intValue('addann.SizeLike', 'min').getAsInt() == 3

        and: 'an explicit name is used as the aliased member'
            metadata.intValue('addann.SizeLike', 'max').getAsInt() == 30
    }

    void 'test declared stereotype values are retained when the overriding member is not set'() {
        given:
            def definition = buildBeanDefinition('addann.OverridesTest', '''
package addann;

import io.micronaut.annotation.mapping.MyOverrides;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Executable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@interface SizeLike {
    int min() default 0;
    int max() default 100;
}

@SizeLike(min = 10)
@Retention(RetentionPolicy.RUNTIME)
@interface ComposedSize {

    @MyOverrides(constraint = SizeLike.class)
    int min() default 5;
}

@Bean
class OverridesTest {

    @ComposedSize
    @Executable
    public String getValue() {
        return "";
    }
}
''')
            def metadata = definition.getRequiredMethod("getValue").getAnnotationMetadata()

        expect: 'aliases only apply to explicitly set members'
            metadata.hasStereotype('addann.SizeLike')
            metadata.intValue('addann.SizeLike', 'min').getAsInt() == 10
    }

    static class MyOverridesTransformer implements TypedAnnotationTransformer<MyOverrides> {

        @Override
        List<AnnotationValue<?>> transform(AnnotationValue<MyOverrides> annotation, VisitorContext visitorContext) {
            AnnotationValueBuilder<AliasFor> builder = AnnotationValue.builder(AliasFor)
            annotation.annotationClassValue("constraint").ifPresent { builder.member("annotationName", it.name) }
            annotation.stringValue("name").ifPresent { builder.member("member", it) }
            return List.of(builder.build())
        }

        @Override
        Class<MyOverrides> annotationType() {
            return MyOverrides.class
        }
    }
}
