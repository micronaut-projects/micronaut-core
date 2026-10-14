package io.micronaut.reflection

import io.micronaut.core.type.GenericPlaceholder
import spock.lang.Specification

import java.lang.annotation.Annotation
import java.lang.reflect.AnnotatedType
import java.lang.reflect.GenericDeclaration
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable

/**
 * A {@link TypeVariable} implemented outside the JDK may report its bounds only through
 * {@link TypeVariable#getBounds()}, or none at all, where the variables of the JDK always report at least
 * {@code Object} as an annotated bound. Such a variable reads as a placeholder of its bound, or of
 * {@code Object} when it has none.
 */
class ForeignTypeVariableSpec extends Specification {

    void "a variable with bounds but no annotated bounds reads as a placeholder of its bound"() {
        when:
        def argument = ReflectionArguments.ofExact(new ForeignTypeVariable("T", Number))

        then:
        argument instanceof GenericPlaceholder
        argument.type == Number
        ((GenericPlaceholder) argument).variableName == "T"
        ((GenericPlaceholder) argument).bounds*.type == [Number]
    }

    void "a variable bounded by Object without annotated bounds reads as a placeholder of Object"() {
        when:
        def argument = ReflectionArguments.ofExact(new ForeignTypeVariable("T", Object))

        then:
        argument instanceof GenericPlaceholder
        argument.type == Object
        ((GenericPlaceholder) argument).variableName == "T"
        ((GenericPlaceholder) argument).bounds*.type == [Object]
    }

    void "a variable with no bounds at all reads as a placeholder of Object"() {
        when:
        def argument = ReflectionArguments.ofExact(new ForeignTypeVariable("T"))

        then:
        argument instanceof GenericPlaceholder
        argument.type == Object
        ((GenericPlaceholder) argument).variableName == "T"
        ((GenericPlaceholder) argument).bounds*.type == [Object]
    }

    void "a variable with no annotated bounds is read as a type argument"() {
        given:
        def variable = new ForeignTypeVariable("E", CharSequence)
        def list = new ParameterizedType() {
            Type[] getActualTypeArguments() { [variable] as Type[] }
            Type getRawType() { List }
            Type getOwnerType() { null }
        }

        when:
        def argument = ReflectionArguments.ofExact(list)

        then:
        argument.type == List
        argument.typeParameters[0] instanceof GenericPlaceholder
        argument.typeParameters[0].type == CharSequence
        argument.typeParameters[0].name == "E"
    }

    void "a variable with no annotated bounds also reads through the lossy conversion"() {
        when:
        def argument = ReflectionArguments.of(new ForeignTypeVariable("T"))

        then:
        argument.type == Object
    }

    private static final class ForeignTypeVariable implements TypeVariable<GenericDeclaration> {

        private final String name
        private final Type[] bounds

        ForeignTypeVariable(String name, Type... bounds) {
            this.name = name
            this.bounds = bounds
        }

        @Override
        Type[] getBounds() { bounds.clone() }

        @Override
        GenericDeclaration getGenericDeclaration() { throw new UnsupportedOperationException() }

        @Override
        String getName() { name }

        @Override
        AnnotatedType[] getAnnotatedBounds() { new AnnotatedType[0] }

        @Override
        <A extends Annotation> A getAnnotation(Class<A> annotationClass) { null }

        @Override
        Annotation[] getAnnotations() { new Annotation[0] }

        @Override
        Annotation[] getDeclaredAnnotations() { new Annotation[0] }
    }
}
