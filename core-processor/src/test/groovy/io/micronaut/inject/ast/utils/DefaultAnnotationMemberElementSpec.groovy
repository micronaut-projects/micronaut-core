package io.micronaut.inject.ast.utils

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.AnnotationMemberElement
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementModifier
import io.micronaut.inject.ast.MemberElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PrimitiveElement
import io.micronaut.inject.ast.PropertyElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata
import spock.lang.Specification

import java.util.function.Supplier

/**
 * The adapter answers for the element a compiler reports an annotation member as, and the member's own
 * metadata where a method element would answer with its declaring type's as well.
 */
class DefaultAnnotationMemberElementSpec extends Specification {

    void "test the member delegates to the element it wraps"() {
        given:
        ClassElement declaring = Mock(ClassElement)
        ClassElement owning = Mock(ClassElement)
        AnnotationMetadata ownMetadata = Mock(AnnotationMetadata)
        PropertyElement property = Mock(PropertyElement) {
            getName() >> 'value'
            getDeclaringType() >> declaring
            getOwningType() >> owning
            getNativeType() >> 'native'
            getAnnotationMetadata() >> ownMetadata
            isPackagePrivate() >> true
            isProtected() >> true
            isPublic() >> false
            isPrivate() >> true
            isAbstract() >> true
            isStatic() >> true
            isFinal() >> true
            isSynthetic() >> true
            getModifiers() >> EnumSet.of(ElementModifier.FINAL)
            getDocumentation(true) >> Optional.of('doc')
            isReflectionRequired(_ as ClassElement) >> true
            isAccessible(_ as ClassElement, false) >> true
            hides(_ as MemberElement) >> true
            getDescription(true) >> 'description'
            toString() >> 'the property'
        }
        Supplier<Map<CharSequence, Object>> defaults = { [value: 'default'] as Map<CharSequence, Object> }
        ClassElement type = PrimitiveElement.INT.toArray()
        def member = new DefaultAnnotationMemberElement(property, type, defaults)

        expect:
        member.getName() == 'value'
        member.getType().is(type)
        member.getGenericType().is(type)
        member.isArray()
        member.getArrayDimensions() == 1
        member.isPrimitive() == type.isPrimitive()
        !member.isVoid()
        member.getDefaultValue() == Optional.of('default')
        member.getDeclaringType().is(declaring)
        member.getOwningType().is(owning)
        member.getNativeType() == 'native'
        member.getAnnotationMetadata().is(ownMetadata)
        member.isPackagePrivate()
        member.isProtected()
        !member.isPublic()
        member.isPrivate()
        member.isAbstract()
        member.isStatic()
        member.isFinal()
        member.isSynthetic()
        member.getModifiers() == EnumSet.of(ElementModifier.FINAL)
        member.getDocumentation(true) == Optional.of('doc')
        member.isReflectionRequired(declaring)
        member.isAccessible(declaring, false)
        member.hides(property)
        member.getDescription(true) == 'description'
        member.toString() == 'the property'

        and: "two members over the same element are the same member"
        member == new DefaultAnnotationMemberElement(property, type, defaults)
        member.hashCode() == property.hashCode()
        member != property
    }

    void "test a method member answers its own metadata, not its declaring type's"() {
        given:
        ElementAnnotationMetadata methodMetadata = Mock(ElementAnnotationMetadata)
        AnnotationMetadata combined = Mock(AnnotationMetadata)
        MethodElement method = Mock(MethodElement) {
            getName() >> 'value'
            getMethodAnnotationMetadata() >> methodMetadata
            getAnnotationMetadata() >> combined
        }
        def member = new DefaultAnnotationMemberElement(method, PrimitiveElement.INT, { [:] } as Supplier)

        expect:
        member.getAnnotationMetadata().is(methodMetadata)
        member.isPrimitive()
        member.getDefaultValue() == Optional.empty()
    }

    void "test mutations go to the element and answer the member"() {
        given:
        MemberElement element = Mock(MemberElement)
        def member = new DefaultAnnotationMemberElement(element, PrimitiveElement.VOID, { [:] } as Supplier)
        def consumer = { builder -> } as java.util.function.Consumer
        def predicate = { AnnotationValue v -> true } as java.util.function.Predicate

        when:
        def results = [
            member.annotate('test.Ann'),
            member.annotate('test.Ann', consumer),
            member.annotate(Deprecated),
            member.annotate(Deprecated, consumer),
            member.annotate(AnnotationValue.builder('test.Ann').build()),
            member.removeAnnotation('test.Ann'),
            member.removeAnnotation(Deprecated),
            member.removeAnnotationIf(predicate),
            member.removeStereotype('test.Ann'),
            member.removeStereotype(Deprecated),
        ]

        then:
        member.isVoid()
        results.every { it.is(member) }
        1 * element.annotate('test.Ann')
        1 * element.annotate('test.Ann', consumer)
        1 * element.annotate(Deprecated)
        1 * element.annotate(Deprecated, consumer)
        1 * element.annotate(_ as AnnotationValue)
        1 * element.removeAnnotation('test.Ann')
        1 * element.removeAnnotation(Deprecated)
        1 * element.removeAnnotationIf(predicate)
        1 * element.removeStereotype('test.Ann')
        1 * element.removeStereotype(Deprecated)
    }

    void "test a copy wraps a copy of the element"() {
        given:
        MemberElement copied = Mock(MemberElement) { getName() >> 'value' }
        MemberElement element = Mock(MemberElement) {
            getName() >> 'value'
            withAnnotationMetadata(AnnotationMetadata.EMPTY_METADATA) >> copied
        }
        def member = new DefaultAnnotationMemberElement(element, PrimitiveElement.INT, { [value: 1] } as Supplier)

        when:
        AnnotationMemberElement copy = member.withAnnotationMetadata(AnnotationMetadata.EMPTY_METADATA)

        then:
        copy instanceof DefaultAnnotationMemberElement
        copy.getName() == 'value'
        copy.getDefaultValue() == Optional.of(1)
        copy.getType() == PrimitiveElement.INT
    }

    void "test the interface default copy is the Element default"() {
        given: "an implementation that does not support copying"
        def member = new AnnotationMemberElement() {
            ClassElement getType() { PrimitiveElement.INT }
            Optional<Object> getDefaultValue() { Optional.empty() }
            AnnotationMetadata getAnnotationMetadata() { AnnotationMetadata.EMPTY_METADATA }
            ClassElement getDeclaringType() { null }
            String getName() { 'value' }
            boolean isProtected() { false }
            boolean isPublic() { true }
            Object getNativeType() { this }
        }

        when:
        member.withAnnotationMetadata(AnnotationMetadata.EMPTY_METADATA)

        then:
        thrown(UnsupportedOperationException)
    }
}
