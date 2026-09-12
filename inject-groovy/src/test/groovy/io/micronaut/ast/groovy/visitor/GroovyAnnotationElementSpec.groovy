package io.micronaut.ast.groovy.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.core.annotation.Generated
import io.micronaut.core.annotation.Introspected
import io.micronaut.inject.ast.AnnotationElement
import jakarta.inject.Singleton
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.ErrorCollector
import org.codehaus.groovy.control.SourceUnit

import java.lang.annotation.ElementType
import java.lang.annotation.RetentionPolicy

class GroovyAnnotationElementSpec extends AbstractBeanDefinitionSpec {

    void "test isInherited for an annotation declared as @Inherited"() {
        given:
        def element = buildClassElement("""
package test

import java.lang.annotation.Inherited

@Inherited
@interface MyAnn {
}
""")
        expect:
        element instanceof AnnotationElement
        ((AnnotationElement) element).isInherited()
    }

    void "test isInherited for an annotation not declared as @Inherited"() {
        given:
        def element = buildClassElement("""
package test

@interface MyAnn {
}
""")
        expect:
        element instanceof AnnotationElement
        !((AnnotationElement) element).isInherited()
    }

    void "test isInherited survives a copy of the element"() {
        given:
        def element = (AnnotationElement) buildClassElement("""
package test

import java.lang.annotation.Inherited

@Inherited
@interface MyAnn {
}
""")
        def copy = element.withAnnotationMetadata(element.getAnnotationMetadata())

        expect:
        copy instanceof AnnotationElement
        ((AnnotationElement) copy).isInherited()
    }

    void "test isInherited for annotations on the classpath"() {
        given:
        def visitorContext = newVisitorContext()
        def introspected = visitorContext.getClassElement(Introspected.name).get()
        def singleton = visitorContext.getClassElement(Singleton.name).get()

        expect:
        introspected instanceof AnnotationElement
        ((AnnotationElement) introspected).isInherited()
        singleton instanceof AnnotationElement
        !((AnnotationElement) singleton).isInherited()
    }

    void "test isInherited for annotations resolved from a class"() {
        given:
        def visitorContext = newVisitorContext()
        def introspected = visitorContext.getClassElement(Introspected).get()
        def singleton = visitorContext.getClassElement(Singleton).get()

        expect:
        ((AnnotationElement) introspected).isInherited()
        !((AnnotationElement) singleton).isInherited()
    }

    void "test getTargets, getRepeatableContainer and getRetentionPolicy as declared in the source"() {
        given:
        def element = (AnnotationElement) buildClassElement("test.MyAnn", """
package test

import java.lang.annotation.*

@Target([ElementType.TYPE, ElementType.METHOD, ElementType.TYPE_USE])
@Retention(RetentionPolicy.SOURCE)
@Repeatable(MyAnns)
@interface MyAnn {
}

@interface MyAnns {
    MyAnn[] value()
}
""")
        expect:
        element.getTargets() == EnumSet.of(ElementType.TYPE, ElementType.METHOD, ElementType.TYPE_USE)
        element.getRepeatableContainer().get() == 'test.MyAnns'
        element.getRetentionPolicy() == RetentionPolicy.SOURCE

        and: "a copy of the element keeps the answers"
        def copy = (AnnotationElement) element.withAnnotationMetadata(element.getAnnotationMetadata())
        copy.getTargets() == element.getTargets()
        copy.getRepeatableContainer().get() == 'test.MyAnns'
        copy.getRetentionPolicy() == RetentionPolicy.SOURCE
    }

    void "test a single @Target value"() {
        given:
        def element = (AnnotationElement) buildClassElement("""
package test

import java.lang.annotation.*

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
@interface MyAnn {
}
""")
        expect:
        element.getTargets() == EnumSet.of(ElementType.FIELD)
        !element.getRepeatableContainer().isPresent()
        element.getRetentionPolicy() == RetentionPolicy.CLASS
    }

    void "test the JLS defaults when the annotation declares no @Target, @Repeatable or @Retention"() {
        given:
        def element = (AnnotationElement) buildClassElement("""
package test

@interface MyAnn {
}
""")
        expect:
        element.getTargets() == AnnotationElement.DEFAULT_TARGETS
        !element.getRepeatableContainer().isPresent()
        element.getRetentionPolicy() == RetentionPolicy.RUNTIME
    }

    void "test an empty @Target means the annotation targets nothing"() {
        given:
        def element = (AnnotationElement) buildClassElement("""
package test

import java.lang.annotation.*

@Target([])
@interface MyAnn {
}
""")
        expect:
        element.getTargets().isEmpty()
    }

    void "test getTargets, getRepeatableContainer and getRetentionPolicy for annotations on the classpath"() {
        given:
        def visitorContext = newVisitorContext()
        def introspected = (AnnotationElement) visitorContext.getClassElement(Introspected.name).get()
        def singleton = (AnnotationElement) visitorContext.getClassElement(Singleton.name).get()
        def property = (AnnotationElement) visitorContext.getClassElement(Property.name).get()
        def generated = (AnnotationElement) visitorContext.getClassElement(Generated.name).get()

        expect:
        introspected.getTargets() == EnumSet.of(ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.PACKAGE)
        singleton.getTargets() == AnnotationElement.DEFAULT_TARGETS
        property.getRepeatableContainer() == Optional.of(PropertySource.name)
        singleton.getRepeatableContainer() == Optional.empty()
        generated.getRetentionPolicy() == RetentionPolicy.CLASS
        singleton.getRetentionPolicy() == RetentionPolicy.RUNTIME
    }

    private static GroovyVisitorContext newVisitorContext() {
        def cc = new CompilerConfiguration()
        def sourceUnit = new SourceUnit("test", "", cc, new GroovyClassLoader(), new ErrorCollector(cc))
        return new GroovyVisitorContext(sourceUnit, new CompilationUnit())
    }
}
