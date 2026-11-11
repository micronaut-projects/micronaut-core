package io.micronaut.kotlin.processing.ast.visitor

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.PrimitiveElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

/**
 * Pins the KSP behaviour that this change deliberately leaves alone: an annotated {@code Int} is a class
 * element for {@code kotlin.Int}, a plain one the shared primitive constant.
 */
class AnnotatedPrimitiveElementSpec extends AbstractKotlinCompilerSpec {

    void "test KSP primitives"() {
        given:
        PrimitiveVisitor.RESULTS.clear()
        buildClassElement('test.PrimitiveBean', '''
package test

class PrimitiveBean {
    var plain: Int = 0
    var annotated: @TypeAnn("f") Int = 0
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.TYPE)
annotation class TypeAnn(val value: String = "")
''')
        def r = PrimitiveVisitor.RESULTS

        expect:
        r['plain.isPrimitive'] == true
        r['plain.isConstant'] == true
        r['plain.typeAnnotationsEmpty'] == true
        r['annotated.isPrimitive'] == false
        r['annotated.name'] == 'java.lang.Integer'
        r['annotated.typeAnn'] == 'f'
    }

    static class PrimitiveVisitor implements TypeElementVisitor<Object, Object> {

        static final Map<String, Object> RESULTS = [:]

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name != 'test.PrimitiveBean') {
                return
            }
            def plain = element.getFields().find { it.name == 'plain' }.getType()
            RESULTS['plain.isPrimitive'] = plain.isPrimitive()
            RESULTS['plain.isConstant'] = plain.is(PrimitiveElement.INT)
            RESULTS['plain.typeAnnotationsEmpty'] = plain.getTypeAnnotationMetadata().isEmpty()
            def annotated = element.getFields().find { it.name == 'annotated' }.getType()
            RESULTS['annotated.isPrimitive'] = annotated.isPrimitive()
            RESULTS['annotated.name'] = annotated.name
            RESULTS['annotated.typeAnn'] = annotated.getTypeAnnotationMetadata().stringValue('test.TypeAnn').orElse(null)
        }
    }
}
