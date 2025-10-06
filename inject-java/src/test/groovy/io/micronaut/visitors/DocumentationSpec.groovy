package io.micronaut.visitors

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PropertyElementQuery

class DocumentationSpec extends AbstractTypeElementSpec {

    void "test read class level documentation"() {
        def classElement = buildClassElement("""
package test;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * This is class level docs
 */
class Test {
    /**This is property level docs
     */
    @NotBlank
    @NotNull
    private String tenant;

    /**
     * This is method level docs*/
    String getTenant() {
        return tenant;
    }

    /**
        This is method level docs

     */
    void setTenant(String tenant) {
        this.tenant = tenant;
    }
}
""")

        expect:
        classElement.getDocumentation(true).get() == 'This is class level docs'
        classElement.getFields().get(0).getDocumentation(true).get() == 'This is property level docs'
        classElement.getEnclosedElements(ElementQuery.of(MethodElement.class).named("getTenant")).get(0).getDocumentation(true).get() == 'This is method level docs'
        classElement.getEnclosedElements(ElementQuery.of(MethodElement.class).named("setTenant")).get(0).getDocumentation(true).get() == 'This is method level docs'
    }

    void "test class documentation"() {
        def classElement = buildClassElement("""
package test;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * This is class level docs.
 *
 * @author Denis Stepanov
 * @since 123
 */
class Test {
    @NotBlank
    @NotNull
    private String tenant;

    /**
     * This is property get.
     *
     * @return The get tenant
     */
    String getTenant() {
        return tenant;
    }

    /**
     * This is property set.
     *
     * @param tenant The set tenant
     */
    void setTenant(String tenant) {
        this.tenant = tenant;
    }
}
""")

        expect:
            classElement.getDocumentation(true).get() == "This is class level docs."
            classElement.getDocumentation(false).get() == """ This is class level docs.

 @author Denis Stepanov
 @since 123
"""
    }

    void "test method documentation"() {
        def classElement = buildClassElement("""
package test;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * This is class level docs
 */
class Test {
    @NotBlank
    @NotNull
    private String tenant;

    /**
     * This is property get.
     *
     * @return The get tenant
     */
    String getTenant() {
        return tenant;
    }

    /**
     * This is property set.
     *
     * @param tenant The set tenant
     */
    void setTenant(String tenant) {
        this.tenant = tenant;
    }

    /**
     * The val.
     *
     * @return The 123 val
     */
    int getInt() {
        return 123;
    }
}
""")

        expect:
            classElement.getMethods().stream().filter { it.name == "getTenant"} .findAny().get().getDocumentation(true).get() == "This is property get."
            classElement.getMethods().stream().filter { it.name == "getTenant"} .findAny().get().getDocumentation(false).get() == """ This is property get.

 @return The get tenant
"""
            classElement.getMethods().stream().filter { it.name == "setTenant"} .findAny().get().getDocumentation(true).get() == "This is property set."
            classElement.getMethods().stream().filter { it.name == "getInt"} .findAny().get().getDocumentation(true).get() == "The val."
            classElement.getMethods().stream().filter { it.name == "getInt"} .findAny().get().returnType.getDocumentation(true).get() == "The 123 val"
    }

    void "test property level documentation"() {
        def classElement = buildClassElement("""
package test;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * This is class level docs
 */
class Test {
    /**
     * This is property level docs
     */
    @NotBlank
    @NotNull
    private String tenant;

    /**
     * This is property get
     */
    String getTenant() {
        return tenant;
    }

    /**
     * This is property set
     */
    void setTenant(String tenant) {
        this.tenant = tenant;
    }
}
""")

        expect:
        classElement.getBeanProperties(PropertyElementQuery.of(AnnotationMetadata.EMPTY_METADATA)).get(0).getDocumentation(true).get() == 'This is property level docs'
    }

    void "test property getter level documentation"() {
        def classElement = buildClassElement("""
package test;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * This is class level docs
 */
class Test {
    @NotBlank
    @NotNull
    private String tenant;

    /**
     * This is property get.
     */
    String getTenant() {
        return tenant;
    }

    /**
     * This is property set
     */
    void setTenant(String tenant) {
        this.tenant = tenant;
    }
}
""")

        expect:
        classElement.getBeanProperties(PropertyElementQuery.of(AnnotationMetadata.EMPTY_METADATA)).get(0).getDocumentation(true).get() == 'This is property get.'
    }

    void "test property getter level documentation 2"() {
        def classElement = buildClassElement("""
package test;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * This is class level docs
 */
class Test {
    @NotBlank
    @NotNull
    private String tenant;

    /**
     * This is property get.
     *
     * @return The get tenant
     */
    String getTenant() {
        return tenant;
    }

    /**
     * This is property set
     *
     * @param tenant The set tenant
     */
    void setTenant(String tenant) {
        this.tenant = tenant;
    }
}
""")

        expect:
        classElement.getBeanProperties(PropertyElementQuery.of(AnnotationMetadata.EMPTY_METADATA)).get(0).getDocumentation().get() == 'The get tenant'
    }

    void "test property setter level documentation"() {
        def classElement = buildClassElement("""
package test;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * This is class level docs
 */
class Test {
    @NotBlank
    @NotNull
    private String tenant;

    String getTenant() {
        return tenant;
    }

    /**
     * This is property set.
     */
    void setTenant(String tenant) {
        this.tenant = tenant;
    }
}
""")

        expect:
        classElement.getBeanProperties(PropertyElementQuery.of(AnnotationMetadata.EMPTY_METADATA)).get(0).getDocumentation(true).get() == 'This is property set.'
    }

    void "test property setter level documentation 2"() {
        def classElement = buildClassElement("""
package test;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * This is class level docs
 */
class Test {
    @NotBlank
    @NotNull
    private String tenant;

    String getTenant() {
        return tenant;
    }

    /**
     * This is property set.
     *
     * @param tenant The set tenant
     */
    void setTenant(String tenant) {
        this.tenant = tenant;
    }
}
""")

        expect:
        classElement.getBeanProperties(PropertyElementQuery.of(AnnotationMetadata.EMPTY_METADATA)).get(0).getDocumentation().get() == 'The set tenant'
    }

    void "test parameter javadoc"() {
        expect:
            buildClassElement('''
package test;

class MyBean {
    /**
     * My method
     *
     * @param test The tstDoc
     * @param abc  The 123abc
     */
    void test(String test, int abc) {
    }
}

''') { ClassElement classElement ->
                assert classElement.getMethods()[0].getParameters()[0].getDocumentation().get() == "The tstDoc"
                assert classElement.getMethods()[0].getParameters()[1].getDocumentation().get() == "The 123abc"
                classElement
            }
    }

    void "test record parameter javadoc"() {
        expect:
            buildClassElement('''
package test;

/**
 * My method
 *
 * @param test The tstDoc
 * @param abc  The 123abc
 */
record MyBean(String test, int abc) {
}

''') { ClassElement classElement ->
                assert classElement.getPrimaryConstructor().get().getParameters()[0].getDocumentation().get() == "The tstDoc"
                assert classElement.getPrimaryConstructor().get().getParameters()[1].getDocumentation().get() == "The 123abc"
                classElement
            }
    }

    void "test record parameter javadoc 2"() {
        expect:
            buildClassElement('''
package test;

/**
 * My method
 *
 * @param test The tstDoc
 * @param abc  The 123abc
 */
record MyBean(String test, int abc) {

    /**
     * @param test The foo
     */
    public MyBean(String test) {
        this(test, 123);
    }
}

''') { ClassElement classElement ->
                assert classElement.getPrimaryConstructor().get().getParameters()[0].getDocumentation().get() == "The tstDoc"
                assert classElement.getPrimaryConstructor().get().getParameters()[1].getDocumentation().get() == "The 123abc"
                assert classElement.getAccessibleConstructors().size() == 2
                assert classElement.getAccessibleConstructors()[0].getParameters()[0].getDocumentation().get() == "The foo"
                assert classElement.getAccessibleConstructors()[1].getParameters()[0].getDocumentation().get() == "The tstDoc"
                classElement
            }
    }

    void "test record parameter javadoc 3"() {
        expect:
            buildClassElement('''
package test;

/**
 * My method
 *
 * @param test The tstDoc
 * @param abc  The 123abc
 */
record MyBean(String test, int abc) {


    /**
     * @param test The foo
     * @param abc The bar
     */
    MyBean(String test, int abc) {
        this.test = test;
        this.abc = abc;
    }
}

''') { ClassElement classElement ->
                assert classElement.getPrimaryConstructor().get().getParameters()[0].getDocumentation().get() == "The foo"
                assert classElement.getPrimaryConstructor().get().getParameters()[1].getDocumentation().get() == "The bar"
                assert classElement.getAccessibleConstructors().size() == 1
                assert classElement.getAccessibleConstructors()[0].getParameters()[0].getDocumentation().get() == "The foo"
                assert classElement.getAccessibleConstructors()[0].getParameters()[1].getDocumentation().get() == "The bar"
                assert classElement.getBeanProperties(PropertyElementQuery.of(AnnotationMetadata.EMPTY_METADATA)).get(0).getDocumentation().get() == "The tstDoc"
                assert classElement.getBeanProperties(PropertyElementQuery.of(AnnotationMetadata.EMPTY_METADATA)).get(1).getDocumentation().get() == "The 123abc"
                classElement
            }
    }
}
