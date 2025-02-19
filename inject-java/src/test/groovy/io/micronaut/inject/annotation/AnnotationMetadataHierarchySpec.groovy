package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Property
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue
import org.intellij.lang.annotations.Language

class AnnotationMetadataHierarchySpec extends AbstractTypeElementSpec{

    void "test merge annotation metadata"() {
        when:
            @Language("java")
            def source = '''\
package test;

import io.micronaut.context.annotation.*;

@Property(name = "myprop", value = "xyz")
class Test {

    @Property(name = "myprop", value = "abc")
    void someMethod() {}
}
'''
            AnnotationMetadata methodMetadata = buildMethodAnnotationMetadata(source, 'someMethod')
            AnnotationMetadata typeMetadata = buildTypeAnnotationMetadata(source)
            AnnotationMetadata annotationMetadata = new AnnotationMetadataHierarchy(typeMetadata, methodMetadata).merge()

        then:
            annotationMetadata.stringValue(Property.class).get() == "abc"
    }

    void "test merge annotation metadata2"() {
        when:
            @Language("java")
            def source = '''\
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties(value = "xyz", includes = "abc", excludes = "lol")
class Test {

    @ConfigurationProperties(value = "qwe", excludes = "foo")
    void someMethod() {}
}
'''
            AnnotationMetadata methodMetadata = buildMethodAnnotationMetadata(source, 'someMethod')
            AnnotationMetadata typeMetadata = buildTypeAnnotationMetadata(source)
            AnnotationMetadata annotationMetadata = new AnnotationMetadataHierarchy(typeMetadata, methodMetadata).merge()

        then:
            annotationMetadata.stringValue(ConfigurationReader.class, "prefix").get() == "qwe"
            annotationMetadata.stringValue(ConfigurationReader.class, "includes").get() == "abc"
            annotationMetadata.stringValue(ConfigurationReader.class, "excludes").get() == "foo"
    }

    void "test basic method annotation metadata"() {

        given:
        def source = '''\
package test;

import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
class Test {

    @Executable
    void someMethod() {}
}
'''
        AnnotationMetadata methodMetadata = buildMethodAnnotationMetadata(source, 'someMethod')
        AnnotationMetadata typeMetadata = buildTypeAnnotationMetadata(source)
        AnnotationMetadata annotationMetadata = new AnnotationMetadataHierarchy(typeMetadata, methodMetadata)

        expect:
        annotationMetadata.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE).contains(AnnotationUtil.SINGLETON)
        annotationMetadata.declaredAnnotationNames.contains(Executable.name)
        annotationMetadata.annotationNames.contains(Executable.name)
        annotationMetadata.annotationNames.contains(AnnotationUtil.SINGLETON)
        !annotationMetadata.declaredAnnotationNames.contains(AnnotationUtil.SINGLETON)
        !annotationMetadata.getDeclaredAnnotationNamesByStereotype(AnnotationUtil.SCOPE).contains(AnnotationUtil.SINGLETON)
        !annotationMetadata.isDeclaredAnnotationPresent(AnnotationUtil.SINGLETON)
        annotationMetadata.isAnnotationPresent(AnnotationUtil.SINGLETON)
        annotationMetadata.hasAnnotation(AnnotationUtil.SINGLETON)
        annotationMetadata.hasDeclaredAnnotation(Executable)
        !annotationMetadata.hasDeclaredAnnotation(AnnotationUtil.SINGLETON)
        annotationMetadata.findAnnotation(AnnotationUtil.SINGLETON).isPresent()
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
        hierarchy.synthesizeAll().length == 2
        hierarchy.synthesizeDeclared().length == 2
        hierarchy.synthesizeAnnotationsByType(Property).length == 5
        hierarchy.synthesizeDeclaredAnnotationsByType(Property).length == 5
        hierarchy.synthesizeAll()[0].toString().contains("io.micronaut.context.annotation.PropertySource")
        hierarchy.synthesizeAll()[1].toString().contains("io.micronaut.context.annotation.PropertySource")
    }

    void "test default values are propagated"() {
        given:
        def source = '''\
package test;

import io.micronaut.inject.annotation.*;

@Nested
class Test {

    @Nested("hello")
    void someMethod() {}
}
'''
        AnnotationMetadata methodMetadata = buildMethodAnnotationMetadata(source, 'someMethod')
        AnnotationMetadata typeMetadata = buildTypeAnnotationMetadata(source)
        AnnotationMetadata annotationMetadata = new AnnotationMetadataHierarchy(typeMetadata, methodMetadata)

        expect:
        annotationMetadata.getAnnotation(Nested).get("num", Integer).get() == 10
    }
}
