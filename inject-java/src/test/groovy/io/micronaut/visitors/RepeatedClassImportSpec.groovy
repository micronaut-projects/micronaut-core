package io.micronaut.visitors

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.annotation.Executable
import io.micronaut.core.beans.BeanIntrospectionReference
import io.micronaut.visitors.imports.first.FirstPackageBean
import io.micronaut.visitors.imports.first.ImportedOuter
import io.micronaut.visitors.imports.second.SecondPackageBean
import spock.lang.Unroll

class RepeatedClassImportSpec extends AbstractTypeElementSpec {

    @Unroll
    void "test binary outer and non-static inner imports in either order: #innerFirst"() {
        given:
        def names = [ImportedOuter.name, ImportedOuter.Inner.name]
        if (innerFirst) {
            names = names.reverse()
        }
        def classLoader = buildClassLoader('test.Importer', """
package test;
import io.micronaut.context.annotation.ClassImport;
import io.micronaut.core.annotation.Introspected;

@ClassImport(classNames = "${names[0]}", annotate = Introspected.class)
@ClassImport(classNames = "${names[1]}", annotateNames = "io.micronaut.core.annotation.Introspected")
class Importer {}
""")

        expect:
        introspection(classLoader, 'test', ImportedOuter).beanType == ImportedOuter
        def inner = introspection(classLoader, 'test', ImportedOuter.Inner)
        inner.beanType == ImportedOuter.Inner

        where:
        innerFirst << [false, true]
    }

    void "test each package import keeps its filters annotations and target package"() {
        given:
        def classLoader = buildClassLoader('test.Importer', '''
package test;
import io.micronaut.context.annotation.ClassImport;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Named;

@ClassImport(packages = "io.micronaut.visitors.imports.first", includedAnnotations = Named.class,
    excludedAnnotations = Deprecated.class, annotate = Introspected.class, targetPackage = "test.first")
@ClassImport(packages = "io.micronaut.visitors.imports.second", includedAnnotations = Deprecated.class,
    excludedAnnotations = Named.class, annotateNames = {"io.micronaut.core.annotation.Introspected",
    "io.micronaut.context.annotation.Executable"}, targetPackage = "test.second")
class Importer {}
''')

        expect:
        !introspection(classLoader, 'test.first', FirstPackageBean).hasAnnotation(Executable)
        introspection(classLoader, 'test.second', SecondPackageBean).hasAnnotation(Executable)

        when:
        introspection(classLoader, 'test.first', FirstPackageBean.Excluded)

        then:
        thrown(ClassNotFoundException)

        when:
        introspection(classLoader, 'test.first', ImportedOuter)

        then:
        thrown(ClassNotFoundException)
    }

    void "test overlapping class and package imports merge annotations and visit the class once"() {
        given:
        ImportTypeElementSpec.DefaultTypeElementVisitor.ENABLED = true
        def classLoader = buildClassLoader('test.Importer', '''
package test;
import io.micronaut.context.annotation.ClassImport;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Named;

@ClassImport(classes = io.micronaut.visitors.imports.first.FirstPackageBean.class,
    annotate = Introspected.class)
@ClassImport(classNames = "io.micronaut.visitors.imports.first.FirstPackageBean",
    annotateNames = "io.micronaut.context.annotation.Executable")
@ClassImport(packages = "io.micronaut.visitors.imports.first", includedAnnotations = Named.class,
    excludedAnnotations = Deprecated.class, annotate = Introspected.class)
class Importer {}
''')

        expect:
        introspection(classLoader, 'test', FirstPackageBean).hasAnnotation(Executable)
        ImportTypeElementSpec.DefaultTypeElementVisitor.VISITED_CLASSES.count { it.name == FirstPackageBean.name } == 1

        cleanup:
        ImportTypeElementSpec.DefaultTypeElementVisitor.cleanup()
    }

    void "test class imports ignore package filters and package imports default to all classes"() {
        given:
        def classLoader = buildClassLoader('test.Importer', '''
package test;
import io.micronaut.context.annotation.ClassImport;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Named;

@ClassImport(classNames = "io.micronaut.visitors.imports.first.FirstPackageBean",
    packages = "does.not.exist", includedAnnotations = Deprecated.class,
    excludedAnnotations = Named.class, annotate = Introspected.class)
@ClassImport(packages = "io.micronaut.visitors.imports.second",
    annotateNames = "io.micronaut.core.annotation.Introspected")
class Importer {}
''')

        expect:
        introspection(classLoader, 'test', FirstPackageBean).beanType == FirstPackageBean
        introspection(classLoader, 'test', SecondPackageBean).beanType == SecondPackageBean
    }

    void "test conflicting target packages fail compilation"() {
        when:
        buildClassLoader('test.Importer', '''
package test;
import io.micronaut.context.annotation.ClassImport;
import io.micronaut.core.annotation.Introspected;

@ClassImport(classes = io.micronaut.visitors.imports.first.FirstPackageBean.class,
    annotate = Introspected.class, targetPackage = "test.first")
@ClassImport(classNames = "io.micronaut.visitors.imports.first.FirstPackageBean",
    annotate = Introspected.class, targetPackage = "test.second")
class Importer {}
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Cannot import class [io.micronaut.visitors.imports.first.FirstPackageBean] into conflicting target packages [test.first] and [test.second]')
    }

    void "test repeated imports alone produce bean definitions with per-occurrence annotations"() {
        given:
        def context = buildContext('test.Importer', '''
package test;
import io.micronaut.context.annotation.ClassImport;
import io.micronaut.core.annotation.Introspected;

@ClassImport(classes = io.micronaut.visitors.imports.first.FirstPackageBean.class,
    annotate = Introspected.class)
@ClassImport(classNames = "io.micronaut.visitors.MyImportedBean", annotateNames = "jakarta.inject.Singleton")
class Importer {}
''')

        expect:
        getBean(context, MyImportedBean.name).greet() == 'Hello'
        introspection(context.classLoader, 'test', FirstPackageBean).beanType == FirstPackageBean
        !context.getBeanDefinition(FirstPackageBean).isSingleton()

        cleanup:
        context?.close()
    }

    private static introspection(ClassLoader classLoader, String targetPackage, Class<?> beanType) {
        def name = targetPackage + '.$' + beanType.name.replace('.', '_') + '$Introspection'
        ((BeanIntrospectionReference) classLoader.loadClass(name).newInstance()).load()
    }
}
