package io.micronaut.ast.groovy.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.Introspected
import io.micronaut.inject.ast.AnnotationElement
import jakarta.inject.Singleton
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.ErrorCollector
import org.codehaus.groovy.control.SourceUnit

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

    private static GroovyVisitorContext newVisitorContext() {
        def cc = new CompilerConfiguration()
        def sourceUnit = new SourceUnit("test", "", cc, new GroovyClassLoader(), new ErrorCollector(cc))
        return new GroovyVisitorContext(sourceUnit, new CompilationUnit())
    }
}
