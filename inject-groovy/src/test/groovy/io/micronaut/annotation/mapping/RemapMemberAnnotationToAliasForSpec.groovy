package io.micronaut.annotation.mapping

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec

class RemapMemberAnnotationToAliasForSpec extends AbstractBeanDefinitionSpec {

    void 'test member annotation transformed to AliasFor overrides the stereotype member'() {
        given:
            def metadata = buildTypeAnnotationMetadata('addann.OverridesTest', '''
package addann;

import io.micronaut.annotation.mapping.MyOverrides;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@interface SizeLike {
    int min() default 0
    int max() default 100
    String message() default "size"
}

@SizeLike(min = 10)
@Retention(RetentionPolicy.RUNTIME)
@interface ComposedSize {

    @MyOverrides(constraint = SizeLike.class)
    int min() default 5

    @MyOverrides(constraint = SizeLike.class, name = "message")
    String sizeMessage() default "composed size"
}

@ComposedSize(min = 3)
class OverridesTest {
}
''')

        expect: 'the explicitly set member overrides the declared stereotype value'
            metadata.hasStereotype('addann.SizeLike')
            metadata.intValue('addann.SizeLike', 'min').getAsInt() == 3

        and: 'the default of an overriding member applies too'
            metadata.stringValue('addann.SizeLike', 'message').get() == 'composed size'
    }

    void 'test repeated overrides and constraintIndex on a repeatable stereotype'() {
        given:
            def metadata = buildTypeAnnotationMetadata('addann.OverridesTest', '''
package addann;

import io.micronaut.annotation.mapping.MyOverrides;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Repeatable(PatternLikeList.class)
@interface PatternLike {
    String regexp()
}

@Retention(RetentionPolicy.RUNTIME)
@interface PatternLikeList {
    PatternLike[] value()
}

@Retention(RetentionPolicy.RUNTIME)
@interface SizeLike {
    int min() default 0
    int max() default 100
}

@SizeLike
@PatternLikeList([@PatternLike(regexp = "....."), @PatternLike(regexp = "bar")])
@Retention(RetentionPolicy.RUNTIME)
@interface ComposedZip {

    @MyOverrides(constraint = SizeLike.class, name = "min")
    @MyOverrides(constraint = SizeLike.class, name = "max")
    int size() default 5

    @MyOverrides(constraint = PatternLike.class, name = "regexp", constraintIndex = 1)
    String regex() default "\\\\d*"
}

@ComposedZip
class OverridesTest {
}
''')

        when:
            def patterns = metadata.getAnnotationValuesByName('addann.PatternLike')

        then: 'both occurrences are retained and only the selected one is overridden'
            patterns.size() == 2
            patterns[0].stringValue('regexp').get() == '.....'
            patterns[1].stringValue('regexp').get() == '\\d*'

        and: 'the repeated override applies the member default to both aliased members'
            metadata.intValue('addann.SizeLike', 'min').getAsInt() == 5
            metadata.intValue('addann.SizeLike', 'max').getAsInt() == 5
    }
}
