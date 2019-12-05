package io.micronaut.inject.annotation

import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Property
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.AbstractTypeElementSpec

import javax.inject.Singleton

class AnnotationMetadataHierarchySpec extends AbstractTypeElementSpec{

    void "test basic method annotation metadata"() {

        given:
        def source = '''\
package test;

import io.micronaut.context.annotation.*;

@javax.inject.Singleton
class Test {

    @Executable
    void someMethod() {}
}
'''
        AnnotationMetadata methodMetadata = buildDeclaredMethodAnnotationMetadata(source, 'someMethod')
        AnnotationMetadata typeMetadata = buildTypeAnnotationMetadata(source)
        AnnotationMetadata annotationMetadata = new AnnotationMetadataHierarchy(typeMetadata, methodMetadata)

        expect:
        annotationMetadata.getAnnotationNamesByStereotype(javax.inject.Scope).contains(Singleton.name)
        annotationMetadata.declaredAnnotationNames.contains(Executable.name)
        annotationMetadata.annotationNames.contains(Executable.name)
        annotationMetadata.annotationNames.contains(Singleton.name)
        !annotationMetadata.declaredAnnotationNames.contains(Singleton.name)
        !annotationMetadata.getDeclaredAnnotationNamesByStereotype(javax.inject.Scope.name).contains(Singleton.name)
        !annotationMetadata.isDeclaredAnnotationPresent(Singleton)
        annotationMetadata.isAnnotationPresent(Singleton)
        annotationMetadata.hasAnnotation(Singleton)
        annotationMetadata.hasDeclaredAnnotation(Executable)
        !annotationMetadata.hasDeclaredAnnotation(Singleton)
        annotationMetadata.findAnnotation(Singleton).isPresent()
        annotationMetadata.synthesize(Singleton)
    }

    void "test repeatable annotations are combined"() {
        AnnotationMetadata parent = buildTypeAnnotationMetadata('''\
package test;

import io.micronaut.inject.annotation.repeatable.*;
import io.micronaut.context.annotation.*;

@Property(name="prop1", value="value1")
@Property(name="prop2", value="value2")
@Property(name="prop3", value="value3")
class Test {

}
''')
        AnnotationMetadata child = buildTypeAnnotationMetadata('''\
package test;

import io.micronaut.inject.annotation.repeatable.*;
import io.micronaut.context.annotation.*;

@Property(name="prop2", value="value2")    
@Property(name="prop3", value="value33")    
@Property(name="prop4", value="value4")    
class Test {
}
''')

        when:
        def className = "test"
        AnnotationMetadata metadata1 = writeAndLoadMetadata(className, parent)
        AnnotationMetadata metadata2 = writeAndLoadMetadata(className, child)

        then:
        AnnotationMetadataHierarchy hierarchy = new AnnotationMetadataHierarchy(metadata1, metadata2)
        List<AnnotationValue<Property>> properties = hierarchy.getAnnotationValuesByType(Property)

        then:
        properties.size() == 5
        properties[0].get("name", String).get() == "prop2"
        properties[1].get("name", String).get() == "prop3"
        properties[1].getValue(String).get() == "value33"
        properties[2].get("name", String).get() == "prop4"
        properties[3].get("name", String).get() == "prop1"
        properties[4].get("name", String).get() == "prop3"
        properties[4].getValue(String).get() == "value3"
    }
}
