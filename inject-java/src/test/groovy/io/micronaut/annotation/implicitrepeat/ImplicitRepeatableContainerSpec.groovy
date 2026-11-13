package io.micronaut.annotation.implicitrepeat

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.annotation.AnnotationRemapper
import io.micronaut.inject.annotation.RepeatableAnnotationContainerProvider
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.VisitorContext

class ImplicitRepeatableContainerSpec extends AbstractTypeElementSpec {

    void "the occurrences in a declared container are those of a repeatable annotation"() {
        when:
        def definition = buildBeanDefinition('test.Held', '''
package test;

import io.micronaut.annotation.implicitrepeat.Legacy;

@jakarta.inject.Singleton
@Legacy.List({@Legacy("a"), @Legacy("b")})
class Held {
}
''')

        then:
        definition.getAnnotationValuesByType(Legacy)*.stringValue()*.get() == ['a', 'b']
        definition.getAnnotationValuesByName(Legacy.name).size() == 2
        definition.findRepeatableAnnotation(Legacy).get() == Legacy.List.name
        definition.synthesizeAnnotationsByType(Legacy)*.value() == ['a', 'b']
    }

    void "the remappers apply to the occurrences held in a declared container, as to a direct one"() {
        when:
        def definition = buildBeanDefinition('test.Remapped', '''
package test;

import io.micronaut.annotation.implicitrepeat.Legacy;

@jakarta.inject.Singleton
class Remapped {
    @jakarta.inject.Inject
    void direct(@Legacy("d") String value) { }

    @jakarta.inject.Inject
    void held(@Legacy.List({@Legacy("a"), @Legacy("b")}) String value) { }
}
''')
        def direct = definition.injectedMethods.find { it.name == 'direct' }.arguments[0]
        def held = definition.injectedMethods.find { it.name == 'held' }.arguments[0]

        then:
        direct.annotationMetadata.getAnnotationValuesByType(Legacy)*.booleanValue('seen')*.orElse(false) == [true]
        held.annotationMetadata.getAnnotationValuesByType(Legacy)*.booleanValue('seen')*.orElse(false) == [true, true]
    }

    void "the occurrences a remapper produces are stored in the declared container"() {
        when:
        def definition = buildBeanDefinition('test.Made', '''
package test;

import io.micronaut.annotation.implicitrepeat.MakeTwoLegacy;

@jakarta.inject.Singleton
@MakeTwoLegacy
class Made {
}
''')

        then:
        !definition.hasAnnotation(MakeTwoLegacy)
        definition.getAnnotationValuesByType(Legacy)*.stringValue()*.get() as Set == ['x', 'y'] as Set
        definition.hasAnnotation(Legacy.List)
    }

    void "another annotation holding the repeatable annotation is not unwrapped"() {
        when:
        def definition = buildBeanDefinition('test.Wrapped', '''
package test;

import io.micronaut.annotation.implicitrepeat.Envelope;
import io.micronaut.annotation.implicitrepeat.Legacy;

@jakarta.inject.Singleton
@Envelope({@Legacy("a")})
class Wrapped {
}
''')

        then:
        definition.getAnnotation(Envelope).getAnnotations('value')*.stringValue()*.get() == ['a']
        definition.getAnnotationValuesByType(Legacy).isEmpty()
        !definition.hasAnnotation(Legacy.List)
    }

    void "removing the annotation removes its declared container"() {
        expect:
        buildClassElement('''
package test;

import io.micronaut.annotation.implicitrepeat.Legacy;

@Legacy("single")
class Removed {
}
''') { ClassElement element ->
            assert element.getAnnotationValuesByType(Legacy).size() == 1
            element.removeAnnotation(Legacy)
            element.getAnnotationValuesByType(Legacy).isEmpty() && !element.hasAnnotation(Legacy.List)
        }
    }

    void "a contained occurrence takes the retention of its type"() {
        when:
        def definition = buildBeanDefinition('test.SourceHeld', '''
package test;

import io.micronaut.annotation.implicitrepeat.SourceLegacy;

@jakarta.inject.Singleton
@SourceLegacy.List({@SourceLegacy("a"), @SourceLegacy("b")})
class SourceHeld {
}
''')

        then:
        definition.getAnnotationValuesByName(SourceLegacy.name).isEmpty()
        !definition.hasAnnotation(SourceLegacy.name)
    }

    void "a declared container keeps the stereotypes it is supplied with"() {
        when:
        def definition = buildBeanDefinition('test.Supplied', '''
package test;

import io.micronaut.annotation.implicitrepeat.MakeLegacyContainer;

@jakarta.inject.Singleton
@MakeLegacyContainer
class Supplied {
}
''')

        then:
        definition.getAnnotationValuesByType(Legacy)*.stringValue()*.get() == ['s']
        definition.hasStereotype(io.micronaut.context.annotation.Primary)
    }

    void "a declared container supplied as a stereotype keeps its own stereotypes"() {
        when:
        def definition = buildBeanDefinition('test.HeldAsStereotype', '''
package test;

import io.micronaut.annotation.implicitrepeat.MakeHeld;

@jakarta.inject.Singleton
@MakeHeld
class HeldAsStereotype {
}
''')

        then:
        definition.hasAnnotation(Holder)
        definition.getAnnotationValuesByType(Legacy)*.stringValue()*.get() == ['h']
        definition.hasStereotype(io.micronaut.context.annotation.Primary)
    }

    void "removing an occurrence clears the stereotypes it contributes"() {
        expect:
        buildClassElement('''
package test;

import io.micronaut.annotation.implicitrepeat.MarkedLegacy;

@MarkedLegacy("single")
class Marked {
}
''') { ClassElement element ->
            assert element.hasStereotype(io.micronaut.context.annotation.Primary)
            assert element.getAnnotationValuesByType(MarkedLegacy).size() == 1
            element.removeAnnotation(MarkedLegacy)
            element.getAnnotationValuesByType(MarkedLegacy).isEmpty() && !element.hasStereotype(io.micronaut.context.annotation.Primary)
        }
    }

    static class LegacyContainers implements RepeatableAnnotationContainerProvider {

        @Override
        Map<String, String> getRepeatableContainers() {
            return [
                (Legacy.name): Legacy.List.name,
                (SourceLegacy.name): SourceLegacy.List.name,
                (MarkedLegacy.name): MarkedLegacy.List.name
            ]
        }
    }

    static class LegacyRemapper implements AnnotationRemapper {

        @Override
        String getPackageName() {
            return "io.micronaut.annotation.implicitrepeat"
        }

        @Override
        List<AnnotationValue<?>> remap(AnnotationValue<?> annotation, VisitorContext visitorContext) {
            if (annotation.annotationName == MakeTwoLegacy.name) {
                return [
                    AnnotationValue.builder(Legacy).value('x').build(),
                    AnnotationValue.builder(Legacy).value('y').build()
                ] as List<AnnotationValue<?>>
            }
            if (annotation.annotationName == MakeLegacyContainer.name) {
                return [
                    AnnotationValue.builder(Legacy.List)
                        .values(AnnotationValue.builder(Legacy).value('s').build())
                        .stereotypes([AnnotationValue.builder(io.micronaut.context.annotation.Primary).build()])
                        .build()
                ] as List<AnnotationValue<?>>
            }
            if (annotation.annotationName == MakeHeld.name) {
                return [
                    AnnotationValue.builder(Holder)
                        .stereotypes([
                            AnnotationValue.builder(Legacy.List)
                                .values(AnnotationValue.builder(Legacy).value('h').member('seen', true).build())
                                .stereotypes([AnnotationValue.builder(io.micronaut.context.annotation.Primary).build()])
                                .build()
                        ])
                        .build()
                ] as List<AnnotationValue<?>>
            }
            if (annotation.annotationName == Legacy.name && !annotation.contains('seen')) {
                // marks every Legacy occurrence it is handed, so the test sees which ones were remapped
                return [annotation.mutate().member('seen', true).build()] as List<AnnotationValue<?>>
            }
            return [annotation] as List<AnnotationValue<?>>
        }
    }
}
