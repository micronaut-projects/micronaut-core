package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers

import java.lang.annotation.Annotation

/**
 * {@link AnnotationValue#of(Annotation)} reads a live annotation in the form the compiled metadata of an
 * annotated element records it, so that the two compare equal.
 */
class AnnotationValueOfAnnotationSpec extends AbstractTypeElementSpec {

    void 'test an annotation read off a live instance equals the one compiled into a definition'() {
        given:
        ApplicationContext context = buildContext('''
package avof;

import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Qualifier;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

interface Fish {
}

@Located(region = @Region(value = "east", zones = {Zone.NORTH, Zone.SOUTH}), kind = Kind.COD, caught = String.class,
         kinds = {Kind.COD, Kind.HADDOCK}, caughts = {String.class, Integer.class}, seas = {"north", "baltic"}, weights = {1, 2},
         regions = {@Region(value = "west", zones = {Zone.SOUTH})}, name = "cod")
@Prototype
class Cod implements Fish {
}

@Located(region = @Region(value = "east", zones = {Zone.NORTH, Zone.SOUTH}), kind = Kind.COD, caught = String.class,
         kinds = {Kind.COD, Kind.HADDOCK}, caughts = {String.class, Integer.class}, seas = {"north", "baltic"}, weights = {1, 2},
         regions = {@Region(value = "west", zones = {Zone.SOUTH})}, name = "cod")
class Same {
}

@Located(region = @Region(value = "west", zones = {Zone.NORTH, Zone.SOUTH}), kind = Kind.COD, caught = String.class,
         kinds = {Kind.COD, Kind.HADDOCK}, caughts = {String.class, Integer.class}, seas = {"north", "baltic"}, weights = {1, 2},
         regions = {@Region(value = "west", zones = {Zone.SOUTH})}, name = "cod")
class OtherRegion {
}

enum Kind {
    COD, HADDOCK
}

enum Zone {
    NORTH, SOUTH
}

@Retention(RUNTIME)
@interface Region {
    String value();

    Zone[] zones();
}

@Qualifier
@Retention(RUNTIME)
@interface Located {
    Region region();

    Region[] regions();

    Kind kind();

    Kind[] kinds();

    Class<?> caught();

    Class<?>[] caughts();

    String[] seas();

    int[] weights();

    String name();
}
''')
        Class<?> fish = context.classLoader.loadClass('avof.Fish')
        Class<?> cod = context.classLoader.loadClass('avof.Cod')
        BeanDefinition<?> definition = context.getBeanDefinition(cod)
        AnnotationValue<Annotation> compiled = definition.getAnnotation('avof.Located')
        Annotation same = locatedOn(context, 'avof.Same')
        Annotation otherRegion = locatedOn(context, 'avof.OtherRegion')

        expect: 'the compiled metadata holds the annotation'
        compiled != null
        compiled.values.keySet() == ['region', 'regions', 'kind', 'kinds', 'caught', 'caughts', 'seas', 'weights', 'name'] as Set

        and: 'and the one read off the live instance is equal to it, member by member and as a whole'
        AnnotationValue<Annotation> live = AnnotationValue.of(same)
        live.values.keySet() == compiled.values.keySet()
        compiled.values.keySet().every { member ->
            assert io.micronaut.core.annotation.AnnotationUtil.areEqual(live.values[member], compiled.values[member]), "member $member differs: live=${live.values[member]} compiled=${compiled.values[member]}"
            true
        }
        live == compiled
        compiled == live
        live.hashCode() == compiled.hashCode()

        and: 'a nested annotation that differs is not equal'
        AnnotationValue.of(otherRegion) != compiled

        and: 'which is what resolving a bean by the live instance turns on'
        context.getBeanDefinitions(fish, Qualifiers.byAnnotation(same)).size() == 1
        context.getBeanDefinitions(fish, Qualifiers.byAnnotation(otherRegion)).isEmpty()

        cleanup:
        context.close()
    }

    void 'test @NonBinding on a member of a nested annotation is not honoured'() {
        given:
        ApplicationContext context = buildContext('''
package avofnested;

import io.micronaut.context.annotation.NonBinding;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Qualifier;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

interface Fish {
}

@Located(region = @Region(value = "east", note = "the cod"), note = "a cod")
@Prototype
class Cod implements Fish {
}

@Located(region = @Region(value = "east", note = "the cod"), note = "something else")
class SameRegion {
}

@Located(region = @Region(value = "east", note = "something else"), note = "a cod")
class OtherRegionNote {
}

@Retention(RUNTIME)
@interface Region {
    String value();

    @NonBinding
    String note();
}

@Qualifier
@Retention(RUNTIME)
@interface Located {
    Region region();

    @NonBinding
    String note();
}
''')
        Class<?> fish = context.classLoader.loadClass('avofnested.Fish')
        Annotation sameRegion = locatedOn(context, 'avofnested.SameRegion')
        Annotation otherRegionNote = locatedOn(context, 'avofnested.OtherRegionNote')

        expect: 'the factory reads every member, the non-binding ones of the qualifier and of the nested annotation alike'
        AnnotationValue.of(sameRegion).values.keySet() == ['region', 'note'] as Set
        (AnnotationValue.of(sameRegion).values.region as AnnotationValue).values.keySet() == ['value', 'note'] as Set

        and: 'the qualifier leaves out its own non-binding member'
        context.getBeanDefinitions(fish, Qualifiers.byAnnotation(sameRegion)).size() == 1

        and: 'but compares the nested annotation whole, non-binding member included'
        context.getBeanDefinitions(fish, Qualifiers.byAnnotation(otherRegionNote)).isEmpty()

        cleanup:
        context.close()
    }

    private static Annotation locatedOn(ApplicationContext context, String className) {
        Class<?> holder = context.classLoader.loadClass(className)
        Annotation annotation = holder.annotations.find { it.annotationType().simpleName == 'Located' }
        assert annotation != null
        return annotation
    }
}
