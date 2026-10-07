package io.micronaut.inject.annotation

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.writer.ByteCodeWriterUtils
import io.micronaut.sourcegen.model.ClassDef
import io.micronaut.sourcegen.model.ClassTypeDef
import io.micronaut.sourcegen.model.MethodDef
import spock.lang.Specification

import javax.lang.model.element.Modifier
import java.lang.annotation.RetentionPolicy

class AnnotationMetadataGenUtilsSpec extends Specification {

    void "the metadata described for a generated class is the one the class holds: #shape"() {
        given:
        def classLoader = new GroovyClassLoader()
        AnnotationMetadata written = metadata.call(classLoader)

        when:
        AnnotationMetadata described = AnnotationMetadataGenUtils.runtimeMetadata(written)
        AnnotationMetadata held = load(classLoader, 'test.Generated', written)

        then:
        answers(described) == answers(held)
        answers(held).annotations.keySet() == names as Set

        where:
        shape                                        | names                                                  | metadata
        "annotations and stereotypes"                | ['a.Declared', 'a.Meta', 'a.Inherited', 'a.OtherMeta'] | { level() }
        "an annotation of source retention"          | ['a.Declared', 'a.Meta', 'a.Inherited', 'a.OtherMeta'] | { level().tap {
            addDeclaredAnnotation('a.Source', [:], RetentionPolicy.SOURCE)
            addDeclaredStereotype(['a.Source'], 'a.SourceMeta', [:], RetentionPolicy.SOURCE)
        } }
        "a hierarchy"                                | ['a.Declared', 'a.Meta', 'a.Inherited', 'a.OtherMeta', 'a.Root', 'a.RootMeta'] | { new AnnotationMetadataHierarchy(root(), level()) }
        "a hierarchy whose declared level is empty"  | ['a.Root', 'a.RootMeta']                               | { new AnnotationMetadataHierarchy(root(), new MutableAnnotationMetadata()) }
        "a hierarchy whose root is empty"            | ['a.Declared', 'a.Meta', 'a.Inherited', 'a.OtherMeta'] | { new AnnotationMetadataHierarchy(new MutableAnnotationMetadata(), level()) }
        "a hierarchy whose root is of another class" | ['a.Declared', 'a.Meta', 'a.Inherited', 'a.OtherMeta', 'a.Root', 'a.RootMeta'] | { GroovyClassLoader loader ->
            load(loader, 'test.Other', root())
            new AnnotationMetadataHierarchy(new AnnotationMetadataReference('test.Other' + AnnotationMetadata.CLASS_NAME_SUFFIX, root()), level())
        }
        "a hierarchy with nothing but another class" | ['a.Root', 'a.RootMeta']                               | { GroovyClassLoader loader ->
            load(loader, 'test.Other', root())
            new AnnotationMetadataHierarchy(new AnnotationMetadataReference('test.Other' + AnnotationMetadata.CLASS_NAME_SUFFIX, root()), new MutableAnnotationMetadata())
        }
        "an empty hierarchy"                         | []                                                     | { new AnnotationMetadataHierarchy(new MutableAnnotationMetadata(), new MutableAnnotationMetadata()) }
        "no metadata"                                | []                                                     | { AnnotationMetadata.EMPTY_METADATA }
    }

    void "the containers described for a generated class are the ones the class registers"() {
        given:
        def declared = level()
        declared.addDeclaredRepeatable('a.Tags', new AnnotationValue<>('a.Tag', [value: 'x'] as Map<CharSequence, Object>))
        declared.addDeclaredRepeatable(Requirements.name, new AnnotationValue<>(Requires.name, [property: 'x'] as Map<CharSequence, Object>))
        def inherited = root()
        inherited.addDeclaredRepeatable('a.RootTags', new AnnotationValue<>('a.RootTag', [value: 'x'] as Map<CharSequence, Object>))

        expect: "the ones the framework registers itself are not"
        AnnotationMetadataGenUtils.repeatableAnnotationContainers(declared) == ['a.Tag': 'a.Tags']
        AnnotationMetadataGenUtils.repeatableAnnotationContainers(new AnnotationMetadataHierarchy(inherited, declared)) == ['a.Tag': 'a.Tags', 'a.RootTag': 'a.RootTags']
        AnnotationMetadataGenUtils.repeatableAnnotationContainers(root()) == [:]
        AnnotationMetadataGenUtils.repeatableAnnotationContainers(AnnotationMetadata.EMPTY_METADATA) == [:]

        when: "the class is generated"
        AnnotationMetadataWriter.write('test.Generated', new AnnotationMetadataHierarchy(inherited, declared))

        then:
        AnnotationMetadataSupport.getRepeatableAnnotation('a.Tag') == 'a.Tags'
        AnnotationMetadataSupport.getRepeatableAnnotation('a.RootTag') == 'a.RootTags'
    }

    private static MutableAnnotationMetadata level() {
        def metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredAnnotation('a.Declared', [value: 'x'])
        metadata.addDeclaredStereotype(['a.Declared'], 'a.Meta', [:])
        metadata.addAnnotation('a.Inherited', [:])
        metadata.addStereotype(['a.Inherited'], 'a.OtherMeta', [:])
        return metadata
    }

    private static MutableAnnotationMetadata root() {
        def metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredAnnotation('a.Root', [:])
        metadata.addDeclaredStereotype(['a.Root'], 'a.RootMeta', [:])
        return metadata
    }

    /**
     * Generates a class with the field a bean definition keeps its metadata in, and reads the field.
     */
    private static AnnotationMetadata load(GroovyClassLoader classLoader, String name, AnnotationMetadata metadata) {
        ClassTypeDef type = ClassTypeDef.of(name + AnnotationMetadata.CLASS_NAME_SUFFIX)
        Map<String, MethodDef> loadTypeMethods = [:]
        def field = AnnotationMetadataGenUtils.createAnnotationMetadataFieldAndInitialize(metadata, AnnotationMetadataGenUtils.createLoadClassValueExpressionFn(type, loadTypeMethods))
        def classDef = ClassDef.builder(type.name).addModifiers(Modifier.PUBLIC).addField(field)
        loadTypeMethods.values().each { classDef.addMethod(it) }
        Class<?> generated = classLoader.defineClass(type.name, ByteCodeWriterUtils.writeByteCode(classDef.build(), null))
        return (AnnotationMetadata) generated.getField(AnnotationMetadataGenUtils.FIELD_ANNOTATION_METADATA_NAME).get(null)
    }

    /**
     * What a lookup asks the metadata of a bean: which annotations it has, and how.
     */
    private static Map<String, Object> answers(AnnotationMetadata metadata) {
        Set<String> names = new TreeSet<>(metadata.annotationNames)
        names.addAll(metadata.declaredAnnotationNames)
        names.addAll(metadata.stereotypeAnnotationNames)
        names.addAll(metadata.declaredStereotypeAnnotationNames)
        names.addAll(['a.Source', 'a.SourceMeta'])
        return [
            annotations         : names.collectEntries { [it, [metadata.hasDeclaredAnnotation(it), metadata.hasAnnotation(it), metadata.hasDeclaredStereotype(it), metadata.hasStereotype(it)]] }.findAll { it.value.any() },
            declaredAnnotations : metadata.declaredAnnotationNames as TreeSet,
            declaredStereotypes : metadata.declaredStereotypeAnnotationNames as TreeSet,
            byStereotype        : metadata.getAnnotationNamesByStereotype('a.Meta') + metadata.getAnnotationNamesByStereotype('a.RootMeta'),
            declaredByStereotype: metadata.getDeclaredAnnotationNamesByStereotype('a.Meta') + metadata.getDeclaredAnnotationNamesByStereotype('a.RootMeta'),
            empty               : metadata.isEmpty()
        ]
    }
}
