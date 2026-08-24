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

    void 'test constraintIndex override on an explicit repeatable container'() {
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
@Repeatable(PatternLikeList.class)
@interface PatternLike {
    String regexp();
}

@Retention(RetentionPolicy.RUNTIME)
@interface PatternLikeList {
    PatternLike[] value();
}

@PatternLikeList({ @PatternLike(regexp = "....."), @PatternLike(regexp = "bar") })
@Retention(RetentionPolicy.RUNTIME)
@interface ComposedZip {

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

        then: 'an explicitly declared container behaves the same as repeated annotations'
            patterns.size() == 2
            patterns[0].stringValue('regexp').get() == '.....'
            patterns[1].stringValue('regexp').get() == '\\d*'
    }

    void 'test explicitly set overriding members win over their defaults'() {
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
}

@Retention(RetentionPolicy.RUNTIME)
@Repeatable(PatternLikeList.class)
@interface PatternLike {
    String regexp();
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

    @MyOverrides(constraint = PatternLike.class, name = "regexp", constraintIndex = 1)
    String regex() default "\\\\d*";
}

@Bean
class OverridesTest {

    @ComposedZip(size = 3, regex = "[0-9]+")
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

        then:
            metadata.intValue('addann.SizeLike', 'min').getAsInt() == 3
            metadata.intValue('addann.SizeLike', 'max').getAsInt() == 3
            patterns.size() == 2
            patterns[0].stringValue('regexp').get() == '.....'
            patterns[1].stringValue('regexp').get() == '[0-9]+'
    }

    void 'test an override without an index applies to every occurrence'() {
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
@Repeatable(PatternLikeList.class)
@interface PatternLike {
    String regexp();
    String message() default "pattern";
}

@Retention(RetentionPolicy.RUNTIME)
@interface PatternLikeList {
    PatternLike[] value();
}

@PatternLike(regexp = ".....")
@PatternLike(regexp = "bar")
@Retention(RetentionPolicy.RUNTIME)
@interface ComposedZip {

    @MyOverrides(constraint = PatternLike.class, name = "message")
    String patternMessage() default "not a zip";
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

        then: 'both occurrences receive the override, the other members are retained'
            patterns.size() == 2
            patterns[0].stringValue('message').get() == 'not a zip'
            patterns[1].stringValue('message').get() == 'not a zip'
            patterns[0].stringValue('regexp').get() == '.....'
            patterns[1].stringValue('regexp').get() == 'bar'
    }

    void 'test an override with an index outside the declared occurrences is dropped'() {
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
@Repeatable(PatternLikeList.class)
@interface PatternLike {
    String regexp();
}

@Retention(RetentionPolicy.RUNTIME)
@interface PatternLikeList {
    PatternLike[] value();
}

@PatternLike(regexp = ".....")
@PatternLike(regexp = "bar")
@Retention(RetentionPolicy.RUNTIME)
@interface ComposedZip {

    @MyOverrides(constraint = PatternLike.class, name = "regexp", constraintIndex = 5)
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

        then: 'no occurrence is modified and no occurrence is added'
            patterns.size() == 2
            patterns[0].stringValue('regexp').get() == '.....'
            patterns[1].stringValue('regexp').get() == 'bar'
    }

    void 'test an override of an annotation that is not declared introduces a new stereotype'() {
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
}

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

        expect: 'the aliased annotation is contributed as a stereotype even without a declaration'
            metadata.hasStereotype('addann.SizeLike')
            metadata.intValue('addann.SizeLike', 'min').getAsInt() == 5
    }

    void 'test recursive composition mirroring the TCK FrenchZipcode shape'() {
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
@Repeatable(SizeLikeList.class)
@interface SizeLike {
    int min() default 0;
    int max() default 100;
    String message() default "size";
}

@Retention(RetentionPolicy.RUNTIME)
@interface SizeLikeList {
    SizeLike[] value();
}

@Retention(RetentionPolicy.RUNTIME)
@Repeatable(PatternLikeList.class)
@interface PatternLike {
    String regexp();
}

@Retention(RetentionPolicy.RUNTIME)
@interface PatternLikeList {
    PatternLike[] value();
}

@Retention(RetentionPolicy.RUNTIME)
@interface NotNullLike {
    String message() default "notNull";
}

@NotNullLike
@SizeLike(min = 1)
@Retention(RetentionPolicy.RUNTIME)
@interface NotEmptyLike {
}

@NotEmptyLike
@SizeLike
@PatternLike(regexp = ".....")
@PatternLike(regexp = "bar")
@Retention(RetentionPolicy.RUNTIME)
@interface FrenchZip {

    @MyOverrides(constraint = SizeLike.class, name = "min")
    @MyOverrides(constraint = SizeLike.class, name = "max")
    int size() default 5;

    @MyOverrides(constraint = SizeLike.class, name = "message")
    String sizeMessage() default "french size";

    @MyOverrides(constraint = PatternLike.class, name = "regexp", constraintIndex = 1)
    String regex() default "\\\\d*";
}

@Bean
class OverridesTest {

    @FrenchZip
    @Executable
    public String getValue() {
        return "";
    }
}
''')
            def metadata = definition.getRequiredMethod("getValue").getAnnotationMetadata()

        when:
            def patternType = (Class) definition.getClass().getClassLoader().loadClass('addann.PatternLike')
            def sizeType = (Class) definition.getClass().getClassLoader().loadClass('addann.SizeLike')
            def patterns = metadata.getAnnotationValuesByType(patternType)
            def sizes = metadata.getAnnotationValuesByType(sizeType)

        then: 'the composition is recursive: stereotypes of composed annotations are present'
            metadata.hasStereotype('addann.NotEmptyLike')
            metadata.hasStereotype('addann.NotNullLike')

        and: 'the constraintIndex override only touches the selected occurrence'
            patterns.size() == 2
            patterns[0].stringValue('regexp').get() == '.....'
            patterns[1].stringValue('regexp').get() == '\\d*'

        and: 'each nesting level keeps its own occurrence of the repeatable annotation'
            sizes.size() == 2

        and: 'the overrides only apply to the occurrence declared on the composing annotation'
            def direct = sizes.find { it.intValue('min').orElse(-1) == 5 }
            def nested = sizes.find { it.intValue('min').orElse(-1) == 1 }
            direct != null
            nested != null
            direct.intValue('max').getAsInt() == 5
            direct.stringValue('message').get() == 'french size'
            nested.intValue('max').isEmpty()
            nested.stringValue('message').isEmpty()
    }

    void 'test overriding a member of a composed annotation cascades through its alias chain'() {
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
}

@SizeLike(min = 10)
@Retention(RetentionPolicy.RUNTIME)
@interface ComposedSize {

    @MyOverrides(constraint = SizeLike.class)
    int min() default 5;
}

@ComposedSize
@Retention(RetentionPolicy.RUNTIME)
@interface OuterComposed {

    @MyOverrides(constraint = ComposedSize.class, name = "min")
    int outerMin() default 7;
}

@Bean
class OverridesTest {

    @OuterComposed
    @Executable
    public String getValue() {
        return "";
    }
}
''')
            def metadata = definition.getRequiredMethod("getValue").getAnnotationMetadata()

        expect: 'the override lands on the directly composed annotation'
            metadata.intValue('addann.ComposedSize', 'min').getAsInt() == 7

        and: 'the overridden member has its own alias, so the override cascades into the nested annotation'
            metadata.intValue('addann.SizeLike', 'min').getAsInt() == 7
    }

    void 'test overriding a class typed member'() {
        given:
            def definition = buildBeanDefinition('addann.OverridesTest', '''
package addann;

import io.micronaut.annotation.mapping.MyOverrides;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Executable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@interface MarkerLike {
    Class<?> type() default Object.class;
    String message() default "marker";
}

@MarkerLike
@Retention(RetentionPolicy.RUNTIME)
@interface ComposedMarker {

    @MyOverrides(constraint = MarkerLike.class, name = "type")
    Class<?> markerType() default String.class;
}

@Bean
class OverridesTest {

    @ComposedMarker(markerType = Integer.class)
    @Executable
    public String getValue() {
        return "";
    }
}
''')
            def metadata = definition.getRequiredMethod("getValue").getAnnotationMetadata()

        expect:
            metadata.classValue('addann.MarkerLike', 'type').get() == Integer
    }

    void 'test occurrence indexes count direct annotations and container values in declaration order'() {
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
@Repeatable(PatternLikeList.class)
@interface PatternLike {
    String regexp();
}

@Retention(RetentionPolicy.RUNTIME)
@interface PatternLikeList {
    PatternLike[] value();
}

@PatternLike(regexp = "direct")
@PatternLikeList({ @PatternLike(regexp = "c0"), @PatternLike(regexp = "c1") })
@Retention(RetentionPolicy.RUNTIME)
@interface MixedComposed {

    @MyOverrides(constraint = PatternLike.class, name = "regexp", constraintIndex = 2)
    String regex() default "over";
}

@Bean
class OverridesTest {

    @MixedComposed
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

        then: 'a direct annotation and container values are indexed together in declaration order'
            patterns.size() == 3
            patterns[0].stringValue('regexp').get() == 'direct'
            patterns[1].stringValue('regexp').get() == 'c0'
            patterns[2].stringValue('regexp').get() == 'over'
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
