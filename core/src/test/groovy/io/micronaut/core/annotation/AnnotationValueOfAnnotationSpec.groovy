package io.micronaut.core.annotation

import spock.lang.Specification

import java.lang.annotation.Annotation
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * {@link AnnotationValue#of(Annotation)} reads a live annotation the way compiled metadata records it.
 */
class AnnotationValueOfAnnotationSpec extends Specification {

    void 'test the annotation name and retention'() {
        when:
        AnnotationValue<Chunky> value = AnnotationValue.of(chunkyOn(Cod))

        then:
        value.annotationName == Chunky.name
        value.retentionPolicy == RetentionPolicy.RUNTIME
    }

    void 'test primitive and string members are kept as they are'() {
        when:
        AnnotationValue<Chunky> value = AnnotationValue.of(chunkyOn(Cod))

        then:
        value.values.realChunky == true
        value.values.weight == 12
        value.values.sea == 'north'
        value.values.seas == ['north', 'baltic'] as String[]
        value.values.weights == [1, 2] as int[]
    }

    void 'test an enum member is recorded by the name of its constant'() {
        when:
        AnnotationValue<Chunky> value = AnnotationValue.of(chunkyOn(Cod))

        then:
        value.values.kind == 'COD'
        value.values.kind instanceof String
        value.values.kinds == ['COD', 'HADDOCK'] as String[]
        value.values.kinds instanceof String[]
    }

    void 'test a class member is recorded as an annotation class value'() {
        when:
        AnnotationValue<Chunky> value = AnnotationValue.of(chunkyOn(Cod))

        then:
        value.values.caught == new AnnotationClassValue<>(String)
        value.values.caughts == [new AnnotationClassValue<>(String), new AnnotationClassValue<>(Integer)] as AnnotationClassValue[]
        value.values.caughts instanceof AnnotationClassValue[]
    }

    void 'test a nested annotation is recorded as an annotation value of every one of its members'() {
        when:
        AnnotationValue<Chunky> value = AnnotationValue.of(chunkyOn(Cod))
        AnnotationValue<Net> net = value.values.net as AnnotationValue<Net>
        AnnotationValue<Net>[] nets = value.values.nets as AnnotationValue<Net>[]

        then: 'the nested annotation converts its own members too'
        net.annotationName == Net.name
        net.values == [mesh: 3, material: 'NYLON', knot: new AnnotationClassValue<>(Object)]
        net.values.material instanceof String

        and: 'an array of nested annotations converts every element'
        nets.length == 2
        nets[0].annotationName == Net.name
        nets[0].values == [mesh: 4, material: 'HEMP', knot: new AnnotationClassValue<>(Object)]
        nets[1].values == [mesh: 5, material: 'NYLON', knot: new AnnotationClassValue<>(String)]
    }

    void 'test a member left at its default is present'() {
        when: 'Haddock writes nothing down'
        AnnotationValue<Chunky> value = AnnotationValue.of(chunkyOn(Haddock))

        then: 'the live instance still answers every member'
        value.values.keySet() == ['realChunky', 'weight', 'sea', 'seas', 'weights', 'kind', 'kinds', 'caught', 'caughts', 'net', 'nets'] as Set
        value.values.realChunky == false
        value.values.weight == 0
        value.values.sea == ''
        value.values.kind == 'HADDOCK'
        value.values.caught == new AnnotationClassValue<>(Object)
        (value.values.net as AnnotationValue).values == [mesh: 1, material: 'NYLON', knot: new AnnotationClassValue<>(Object)]
        (value.values.nets as AnnotationValue[]).length == 0
    }

    void 'test two instances written the same way read equal'() {
        expect:
        AnnotationValue.of(chunkyOn(Cod)) == AnnotationValue.of(chunkyOn(Cod))
        AnnotationValue.of(chunkyOn(Cod)).hashCode() == AnnotationValue.of(chunkyOn(Cod)).hashCode()
        AnnotationValue.of(chunkyOn(Cod)) != AnnotationValue.of(chunkyOn(Haddock))
    }

    void 'test a member that cannot be read is reported rather than skipped'() {
        given: 'an annotation that will not answer for one of its members, and answers the rest with their defaults'
        // only one member fails: the order in which the members are read is not specified, and the message
        // must name the member that failed, whichever came first
        Chunky unreadable = (Chunky) Proxy.newProxyInstance(Chunky.classLoader, [Chunky] as Class[], { Object proxy, Method method, Object[] args ->
            if (method.name == 'annotationType') {
                return Chunky
            }
            if (method.name == 'realChunky') {
                throw new UnsupportedOperationException("the member ${method.name} cannot be read")
            }
            return method.defaultValue
        } as InvocationHandler)

        when:
        AnnotationValue.of(unreadable)

        then:
        IllegalStateException e = thrown()
        e.message.startsWith('Cannot read member [')
        e.message.contains("] of annotation [${Chunky.name}]")
        e.message.contains('UnsupportedOperationException: the member realChunky cannot be read')
        e.cause != null
    }

    void 'test a null annotation is rejected'() {
        when:
        AnnotationValue.of(null)

        then:
        thrown(NullPointerException)
    }

    enum Kind {
        COD, HADDOCK
    }

    enum Material {
        NYLON, HEMP
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Net {
        int mesh() default 1

        Material material() default Material.NYLON

        Class<?> knot() default Object
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Chunky {
        boolean realChunky() default false

        int weight() default 0

        String sea() default ''

        String[] seas() default []

        int[] weights() default []

        Kind kind() default Kind.HADDOCK

        Kind[] kinds() default []

        Class<?> caught() default Object

        Class<?>[] caughts() default []

        Net net() default @Net

        Net[] nets() default []
    }

    private static Chunky chunkyOn(Class<?> holder) {
        Chunky chunky = holder.getAnnotation(Chunky)
        assert chunky != null
        return chunky
    }

    @Chunky(realChunky = true, weight = 12, sea = 'north', seas = ['north', 'baltic'], weights = [1, 2],
            kind = Kind.COD, kinds = [Kind.COD, Kind.HADDOCK], caught = String, caughts = [String, Integer],
            net = @Net(mesh = 3), nets = [@Net(mesh = 4, material = Material.HEMP), @Net(mesh = 5, knot = String)])
    static class Cod {
    }

    @Chunky
    static class Haddock {
    }
}
