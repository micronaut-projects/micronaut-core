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

    void 'test the default value of the overriding member replaces the declared stereotype value'() {
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

        expect: 'the overriding member default applies even when not explicitly set'
            metadata.hasStereotype('addann.SizeLike')
            metadata.intValue('addann.SizeLike', 'min').getAsInt() == 5

        and: 'members without an override are not touched'
            metadata.intValue('addann.SizeLike', 'max').orElse(100) == 100
    }

    void 'test repeated overrides and constraintIndex on a repeatable stereotype'() {
        given:
            def definition = buildBeanDefinition('addann.OverridesTest', '''
package addann;

import io.micronaut.annotation.mapping.MyOverrides;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Executable;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@interface SizeLike {
    int min() default 0;
    int max() default 100;
    String message() default "size";
}

@Retention(RetentionPolicy.RUNTIME)
@Repeatable(PatternLikeList.class)
@interface PatternLike {
    String regexp();
    String message() default "pattern";
}

@Retention(RetentionPolicy.RUNTIME)
@interface PatternLikeList {
    PatternLike[] value();
}

@SizeLike
@PatternLike(regexp = ".....")
@PatternLike(regexp = "bar")
@Retention(RetentionPolicy.RUNTIME)
@interface ComposedZip {

    @MyOverrides(constraint = SizeLike.class, name = "min")
    @MyOverrides(constraint = SizeLike.class, name = "max")
    int size() default 5;

    @MyOverrides(constraint = SizeLike.class, name = "message")
    String sizeMessage() default "size of 5";

    @MyOverrides(constraint = PatternLike.class, name = "regexp", constraintIndex = 1)
    String regex() default "\\\\d*";
}

@Bean
class OverridesTest {

    @ComposedZip
    @Executable
    public String getValue() {
        return "";
    }
}
''')
            def metadata = definition.getRequiredMethod("getValue").getAnnotationMetadata()

        when:
            def patternType = (Class) definition.getClass().getClassLoader().loadClass('addann.PatternLike')
            def patterns = metadata.getAnnotationValuesByType(patternType)

        then: 'both declared occurrences of the repeatable stereotype are retained'
            patterns.size() == 2

        and: 'only the occurrence selected by constraintIndex is overridden'
            patterns[0].stringValue('regexp').get() == '.....'
            patterns[1].stringValue('regexp').get() == '\\d*'

        and: 'a repeated override applies the same member default to several members'
            metadata.intValue('addann.SizeLike', 'min').getAsInt() == 5
            metadata.intValue('addann.SizeLike', 'max').getAsInt() == 5
            metadata.stringValue('addann.SizeLike', 'message').get() == 'size of 5'
    }

    static class MyOverridesTransformer implements TypedAnnotationTransformer<MyOverrides> {

        @Override
        List<AnnotationValue<?>> transform(AnnotationValue<MyOverrides> annotation, VisitorContext visitorContext) {
            return List.of(toAliasFor(annotation))
        }

        @Override
        Class<MyOverrides> annotationType() {
            return MyOverrides.class
        }

        static AnnotationValue<AliasFor> toAliasFor(AnnotationValue<MyOverrides> annotation) {
            AnnotationValueBuilder<AliasFor> builder = AnnotationValue.builder(AliasFor)
            annotation.annotationClassValue("constraint").ifPresent { builder.member("annotationName", it.name) }
            annotation.stringValue("name").ifPresent { builder.member("member", it) }
            annotation.intValue("constraintIndex").ifPresent { builder.member("index", it) }
            builder.member("applyDefault", true)
            return builder.build()
        }
    }

    static class MyOverridesListTransformer implements TypedAnnotationTransformer<MyOverridesList> {

        @Override
        List<AnnotationValue<?>> transform(AnnotationValue<MyOverridesList> annotation, VisitorContext visitorContext) {
            return annotation.<MyOverrides> getAnnotations("value")
                    .collect { MyOverridesTransformer.toAliasFor(it) as AnnotationValue<?> }
        }

        @Override
        Class<MyOverridesList> annotationType() {
            return MyOverridesList.class
        }
    }
}
