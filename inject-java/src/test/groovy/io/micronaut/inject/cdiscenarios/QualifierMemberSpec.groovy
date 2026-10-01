package io.micronaut.inject.cdiscenarios

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.qualifiers.Qualifiers

import java.lang.annotation.Annotation

/**
 * CDI 2.4.2: the members of a qualifier take part in resolution, so a bean qualified
 * {@code @Chunky(realChunky = true)} does not satisfy an injection point qualified
 * {@code @Chunky(realChunky = false)}.
 */
class QualifierMemberSpec extends AbstractTypeElementSpec {

    void 'test byAnnotation(Annotation) compares the qualifier members'() {
        given:
        def context = buildContext('''
package qualifiermember;

import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Qualifier;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

interface Fish {
}

@Chunky(realChunky = true)
@Prototype
class Cod implements Fish {
}

@Chunky(realChunky = true)
class ReallyChunky {
}

@Chunky(realChunky = false)
class NotReallyChunky {
}

@Qualifier
@Retention(RUNTIME)
@interface Chunky {
    boolean realChunky();
}
''')
        Class<?> fish = context.classLoader.loadClass('qualifiermember.Fish')
        Annotation chunkyTrue = annotationOn(context, 'qualifiermember.ReallyChunky')
        Annotation chunkyFalse = annotationOn(context, 'qualifiermember.NotReallyChunky')

        expect: 'the bean is resolved by a qualifier carrying the same member value'
        context.getBeanDefinitions(fish, Qualifiers.byAnnotation(chunkyTrue)).size() == 1

        and: 'and is not resolved by one carrying a different member value'
        context.getBeanDefinitions(fish, Qualifiers.byAnnotation(chunkyFalse)).isEmpty()

        cleanup:
        context.close()
    }

    void 'test byAnnotation(Annotation) matches a member left at its default'() {
        given:
        def context = buildContext('''
package qualifiermemberdefault;

import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Qualifier;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

interface Fish {
}

@Chunky
@Prototype
class Cod implements Fish {
}

@Chunky(realChunky = false)
class NotReallyChunky {
}

@Chunky(realChunky = true)
class ReallyChunky {
}

@Qualifier
@Retention(RUNTIME)
@interface Chunky {
    boolean realChunky() default false;
}
''')
        Class<?> fish = context.classLoader.loadClass('qualifiermemberdefault.Fish')
        Annotation chunkyFalse = annotationOn(context, 'qualifiermemberdefault.NotReallyChunky')
        Annotation chunkyTrue = annotationOn(context, 'qualifiermemberdefault.ReallyChunky')

        expect: 'writing the default down is the same qualifier as leaving it out'
        context.getBeanDefinitions(fish, Qualifiers.byAnnotation(chunkyFalse)).size() == 1

        and: 'and the other value still does not resolve it'
        context.getBeanDefinitions(fish, Qualifiers.byAnnotation(chunkyTrue)).isEmpty()

        cleanup:
        context.close()
    }

    void 'test byAnnotation(Annotation) ignores @NonBinding members'() {
        given:
        def context = buildContext('''
package qualifiermembernonbinding;

import io.micronaut.context.annotation.NonBinding;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Qualifier;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

interface Fish {
}

@Chunky(realChunky = true, description = "a chunky cod")
@Prototype
class Cod implements Fish {
}

@Chunky(realChunky = true, description = "something else entirely")
class ReallyChunky {
}

@Chunky(realChunky = false, description = "a chunky cod")
class NotReallyChunky {
}

@Qualifier
@Retention(RUNTIME)
@interface Chunky {
    boolean realChunky();

    @NonBinding
    String description() default "";
}
''')
        Class<?> fish = context.classLoader.loadClass('qualifiermembernonbinding.Fish')
        Annotation chunkyTrue = annotationOn(context, 'qualifiermembernonbinding.ReallyChunky')
        Annotation chunkyFalse = annotationOn(context, 'qualifiermembernonbinding.NotReallyChunky')

        expect: 'the non-binding member does not stop the binding member from matching'
        context.getBeanDefinitions(fish, Qualifiers.byAnnotation(chunkyTrue)).size() == 1

        and: 'and does not make the binding member match'
        context.getBeanDefinitions(fish, Qualifiers.byAnnotation(chunkyFalse)).isEmpty()

        cleanup:
        context.close()
    }

    void 'test byAnnotation(Annotation) compares members of every shape'() {
        given:
        def context = buildContext('''
package qualifiermembershapes;

import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Qualifier;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

interface Fish {
}

@Chunky(kind = Kind.COD, kinds = {Kind.COD, Kind.HADDOCK}, caught = String.class, caughts = {String.class},
        seas = {"north", "baltic"}, weights = {1, 2}, net = @Net(mesh = 3), nets = {@Net(mesh = 4)})
@Prototype
class Cod implements Fish {
}

@Prototype
class Haddock implements Fish {
}

@Chunky(kind = Kind.COD, kinds = {Kind.COD, Kind.HADDOCK}, caught = String.class, caughts = {String.class},
        seas = {"north", "baltic"}, weights = {1, 2}, net = @Net(mesh = 3), nets = {@Net(mesh = 4)})
class Same {
}

@Chunky(kind = Kind.HADDOCK, kinds = {Kind.COD, Kind.HADDOCK}, caught = String.class, caughts = {String.class},
        seas = {"north", "baltic"}, weights = {1, 2}, net = @Net(mesh = 3), nets = {@Net(mesh = 4)})
class OtherKind {
}

@Chunky(kind = Kind.COD, kinds = {Kind.HADDOCK, Kind.COD}, caught = String.class, caughts = {String.class},
        seas = {"north", "baltic"}, weights = {1, 2}, net = @Net(mesh = 3), nets = {@Net(mesh = 4)})
class OtherKinds {
}

@Chunky(kind = Kind.COD, kinds = {Kind.COD, Kind.HADDOCK}, caught = Integer.class, caughts = {String.class},
        seas = {"north", "baltic"}, weights = {1, 2}, net = @Net(mesh = 3), nets = {@Net(mesh = 4)})
class OtherCaught {
}

@Chunky(kind = Kind.COD, kinds = {Kind.COD, Kind.HADDOCK}, caught = String.class, caughts = {Integer.class},
        seas = {"north", "baltic"}, weights = {1, 2}, net = @Net(mesh = 3), nets = {@Net(mesh = 4)})
class OtherCaughts {
}

@Chunky(kind = Kind.COD, kinds = {Kind.COD, Kind.HADDOCK}, caught = String.class, caughts = {String.class},
        seas = {"north"}, weights = {1, 2}, net = @Net(mesh = 3), nets = {@Net(mesh = 4)})
class OtherSeas {
}

@Chunky(kind = Kind.COD, kinds = {Kind.COD, Kind.HADDOCK}, caught = String.class, caughts = {String.class},
        seas = {"north", "baltic"}, weights = {1, 3}, net = @Net(mesh = 3), nets = {@Net(mesh = 4)})
class OtherWeights {
}

@Chunky(kind = Kind.COD, kinds = {Kind.COD, Kind.HADDOCK}, caught = String.class, caughts = {String.class},
        seas = {"north", "baltic"}, weights = {1, 2}, net = @Net(mesh = 5), nets = {@Net(mesh = 4)})
class OtherNet {
}

@Chunky(kind = Kind.COD, kinds = {Kind.COD, Kind.HADDOCK}, caught = String.class, caughts = {String.class},
        seas = {"north", "baltic"}, weights = {1, 2}, net = @Net(mesh = 3), nets = {@Net(mesh = 6)})
class OtherNets {
}

enum Kind {
    COD, HADDOCK
}

@Retention(RUNTIME)
@interface Net {
    int mesh();
}

@Qualifier
@Retention(RUNTIME)
@interface Chunky {
    Kind kind();

    Kind[] kinds();

    Class<?> caught();

    Class<?>[] caughts();

    String[] seas();

    int[] weights();

    Net net();

    Net[] nets();
}
''')
        Class<?> fish = context.classLoader.loadClass('qualifiermembershapes.Fish')

        expect: 'the same members of every shape resolve the bean, and leave the unqualified one out'
        context.getBeanDefinitions(fish, Qualifiers.byAnnotation(annotationOn(context, 'qualifiermembershapes.Same'))).size() == 1

        and: 'and a difference in any one of them resolves nothing'
        ['OtherKind', 'OtherKinds', 'OtherCaught', 'OtherCaughts', 'OtherSeas', 'OtherWeights', 'OtherNet', 'OtherNets'].every {
            context.getBeanDefinitions(fish, Qualifiers.byAnnotation(annotationOn(context, "qualifiermembershapes.$it"))).isEmpty()
        }

        cleanup:
        context.close()
    }

    void 'test byAnnotation(Annotation) still matches a qualifier with no members by type'() {
        given:
        def context = buildContext('''
package qualifiermembernone;

import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Qualifier;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

interface Fish {
}

@Chunky
@Prototype
class Cod implements Fish {
}

@Prototype
class Haddock implements Fish {
}

@Chunky
class Chunk {
}

@Qualifier
@Retention(RUNTIME)
@interface Chunky {
}
''')
        Class<?> fish = context.classLoader.loadClass('qualifiermembernone.Fish')
        Annotation chunky = annotationOn(context, 'qualifiermembernone.Chunk')

        expect:
        context.getBeanDefinitions(fish, Qualifiers.byAnnotation(chunky)).size() == 1

        cleanup:
        context.close()
    }

    private static Annotation annotationOn(context, String className) {
        Class<?> holder = context.classLoader.loadClass(className)
        Annotation annotation = holder.annotations.find { it.annotationType().simpleName == 'Chunky' }
        assert annotation != null
        return annotation
    }
}
