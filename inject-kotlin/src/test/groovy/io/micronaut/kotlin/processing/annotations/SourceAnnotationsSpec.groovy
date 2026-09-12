package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

import java.lang.annotation.RetentionPolicy

/**
 * KSP symbols can only be read while the compilation runs, so the elements are read in a visitor and the
 * results recorded.
 */
class SourceAnnotationsSpec extends AbstractKotlinCompilerSpec {

    void "test the source view under KSP"() {
        given:
        SourceVisitor.RESULTS.clear()
        buildClassElement('test.MyBean', '''
package test

@MyRepeatable("a")
@MyRepeatable("b")
@jakarta.inject.Singleton
class MyBean<@TypeAnn("class-var") T> {

    @field:MyRepeatable("single")
    @field:Defaults(name = "x")
    var field: @TypeAnn("field-type") String = ""

    var typeVarField: T? = null

    @MyRepeatable("m")
    fun <@TypeAnn("method-var") M> method(@MyRepeatable("p") param: String): M? = null
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER)
@Repeatable
annotation class MyRepeatable(val value: String)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER)
annotation class TypeAnn(val value: String = "")

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FIELD)
annotation class Defaults(val name: String, val description: String = "", val tags: Array<String> = [], val count: Int = 3)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class MyAnn
''')
        def r = SourceVisitor.RESULTS

        expect: "the class: repetitions as written, no stereotype"
        r['class.names'] == ['test.MyRepeatable', 'test.MyRepeatable', 'jakarta.inject.Singleton']
        r['class.values'] == ['a', 'b']
        r['class.retentions'].every { it == RetentionPolicy.RUNTIME }
        r['class.metadataHasScope'] == true
        r['class.sourceHasScope'] == false

        and: "the field, with the defaults it left out, empty ones included"
        r['field.names'] == ['test.MyRepeatable', 'test.Defaults']
        r['field.single'] == 'single'
        r['field.defaults.retention'] == RetentionPolicy.SOURCE
        r['field.defaults.name'] == 'x'
        r['field.defaults.description'] == ''
        r['field.defaults.tags'] == [] as String[]
        r['field.defaults.count'] == 3
        r['field.defaults.hasDescriptionValue'] == false
        r['field.type.names'] == ['test.TypeAnn']
        r['field.type.value'] == 'field-type'

        and: "a use of a type variable reports what the use wrote, the declaration the type parameter annotations"
        r['use.metadataHasTypeAnn'] == true
        r['use.source'] == []
        r['declaration.names'] == ['test.TypeAnn']
        r['declaration.value'] == 'class-var'
        r['declaration.element.names'] == ['test.TypeAnn']

        and: "a method, its parameter and its declared type variable"
        r['method.names'] == ['test.MyRepeatable']
        r['method.value'] == 'm'
        r['parameter.names'] == ['test.MyRepeatable']
        r['parameter.value'] == 'p'
        r['method.metadataHasSingleton'] == true
        r['method.sourceHasSingleton'] == false
        r['method.typeVariable.names'] == ['test.TypeAnn']
        r['method.typeVariable.value'] == 'method-var'

        and: "what a visitor adds does not appear and the meta-annotations of an annotation class do"
        r['ann.metadataHasAdded'] == true
        r['ann.names'] == ['kotlin.annotation.Retention', 'kotlin.annotation.Target']
    }

    static class SourceVisitor implements TypeElementVisitor<Object, Object> {

        static final Map<String, Object> RESULTS = [:]

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name != 'test.MyBean' || !element.hasDeclaredAnnotation('test.MyRepeatable') || !context.getClassElement('test.MyAnn').isPresent()) {
                return
            }
            def source = element.getSourceAnnotations()
            RESULTS['class.names'] = names(source)
            RESULTS['class.values'] = source.findAll { it.annotationName == 'test.MyRepeatable' }.collect { it.stringValue().get() }
            RESULTS['class.retentions'] = source*.retentionPolicy
            RESULTS['class.metadataHasScope'] = element.getAnnotationMetadata().hasDeclaredStereotype(AnnotationUtil.SCOPE)
            RESULTS['class.sourceHasScope'] = source.any { it.annotationName == AnnotationUtil.SCOPE }

            def field = element.getFields().find { it.name == 'field' }
            def fieldSource = field.getSourceAnnotations()
            RESULTS['field.names'] = names(fieldSource)
            RESULTS['field.single'] = fieldSource[0].stringValue().get()
            AnnotationValue<?> defaults = fieldSource[1]
            RESULTS['field.defaults.retention'] = defaults.retentionPolicy
            RESULTS['field.defaults.name'] = defaults.stringValue('name').get()
            RESULTS['field.defaults.description'] = defaults.getDefaultValues().get('description')
            RESULTS['field.defaults.tags'] = defaults.getDefaultValues().get('tags')
            RESULTS['field.defaults.count'] = defaults.getDefaultValues().get('count')
            RESULTS['field.defaults.hasDescriptionValue'] = defaults.getValues().containsKey('description')
            def fieldType = field.getType().getTypeAnnotationMetadata().getSourceAnnotations()
            RESULTS['field.type.names'] = names(fieldType)
            RESULTS['field.type.value'] = fieldType[0].stringValue().get()

            def use = (GenericPlaceholderElement) element.getFields().find { it.name == 'typeVarField' }.getGenericType()
            RESULTS['use.metadataHasTypeAnn'] = use.getGenericTypeAnnotationMetadata().hasAnnotation('test.TypeAnn')
            RESULTS['use.source'] = names(use.getGenericTypeAnnotationMetadata().getSourceAnnotations())
            def declaration = element.getDeclaredGenericPlaceholders()[0]
            def declarationSource = declaration.getGenericTypeAnnotationMetadata().getSourceAnnotations()
            RESULTS['declaration.names'] = names(declarationSource)
            RESULTS['declaration.value'] = declarationSource[0].stringValue().get()
            RESULTS['declaration.element.names'] = names(declaration.getSourceAnnotations())

            def method = element.getMethods().find { it.name == 'method' }
            RESULTS['method.names'] = names(method.getSourceAnnotations())
            RESULTS['method.value'] = method.getSourceAnnotations()[0].stringValue().get()
            RESULTS['parameter.names'] = names(method.getParameters()[0].getSourceAnnotations())
            RESULTS['parameter.value'] = method.getParameters()[0].getSourceAnnotations()[0].stringValue().get()
            RESULTS['method.metadataHasSingleton'] = method.getAnnotationMetadata().hasAnnotation('jakarta.inject.Singleton')
            RESULTS['method.sourceHasSingleton'] = method.getSourceAnnotations().any { it.annotationName == 'jakarta.inject.Singleton' }
            def typeVariable = method.getDeclaredTypeVariables()[0].getGenericTypeAnnotationMetadata().getSourceAnnotations()
            RESULTS['method.typeVariable.names'] = names(typeVariable)
            RESULTS['method.typeVariable.value'] = typeVariable[0].stringValue().get()

            def ann = context.getClassElement('test.MyAnn').get()
            ann.annotate('test.Added')
            RESULTS['ann.metadataHasAdded'] = ann.getAnnotationMetadata().hasDeclaredAnnotation('test.Added')
            RESULTS['ann.names'] = names(ann.getSourceAnnotations())
        }

        private static List<String> names(List<AnnotationValue<?>> values) {
            return values*.annotationName
        }
    }
}
