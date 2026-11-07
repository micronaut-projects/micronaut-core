package io.micronaut.inject.visitor.beans

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospectionReference
import io.micronaut.core.beans.BeanIntrospector

import java.util.function.Predicate

class BeanMethodDeclarationsSpec extends AbstractTypeElementSpec {

    void "the declarations of a method are read from the introspections of its levels, a generic one by erasure"() {
        given:
        def loader = buildClassLoader('test.StringRepo', '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Introspected;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD})
@interface Marker {
    String value();
}

@Introspected(hierarchy = true)
interface Repo<T> {
    @Executable
    @Marker("repo")
    void save(@Marker("repo-param") T t);

    @Executable
    void save(Integer count);

    @Executable
    void delete(Object o);
}

@Introspected(hierarchy = true)
abstract class Middle<X> implements Repo<X> {
}

@Introspected(hierarchy = true)
class StringRepo extends Middle<String> {
    @Executable
    @Marker("own")
    public void save(String s) { }

    @Executable
    public void save(Integer count) { }

    @Executable
    public void delete(String s) { }

    @Executable
    public void delete(Object o) { }
}
''')
        def introspector = new LoaderIntrospector(loader)
        Class<?> repoType = loader.loadClass('test.StringRepo')
        Class<?> repo = loader.loadClass('test.Repo')
        BeanIntrospection<?> introspection = introspector.findIntrospection(repoType).orElseThrow()
        def hierarchy = introspection.typeHierarchy.orElseThrow()
        def saveString = introspection.beanMethods.find { it.name == 'save' && it.arguments[0].type == String }
        def saveInteger = introspection.beanMethods.find { it.name == 'save' && it.arguments[0].type == Integer }
        def marker = loader.loadClass('test.Marker')

        when:
        def overridden = hierarchy.findOverridden(introspector, repo, 'save', String)

        then: 'save(String) overrides the generic save(T), erased to save(Object)'
        overridden.present
        overridden.get().arguments[0].type == Object
        overridden.get().arguments[0].isUnresolvedTypeVariable()

        and: 'an exact declaration wins, an unrelated overload of the same arity is not matched'
        hierarchy.findOverridden(introspector, repo, 'save', Integer).get().arguments[0].type == Integer
        !hierarchy.findOverridden(introspector, repo, 'delete', String).present
        hierarchy.findOverridden(introspector, repo, 'delete', Object).present

        and: 'a type outside the hierarchy, or without an introspection, has no declaration'
        !hierarchy.findOverridden(introspector, String, 'save', String).present

        when:
        def declarations = hierarchy.getMethodDeclarations(introspector, saveString)

        then: 'the own declaration first, then the generic one of the interface, with what each declares'
        hierarchy.getDeclaringTypes(saveString) == [repoType, repo]
        declarations*.declaringType() == [repoType, repo]
        declarations[0].annotationMetadata().stringValue(marker).get() == 'own'
        declarations[1].annotationMetadata().stringValue(marker).get() == 'repo'
        declarations[1].arguments()[0].annotationMetadata.stringValue(marker).get() == 'repo-param'
        declarations.every { it.returnArgument().type == void }

        and:
        hierarchy.getMethodDeclarations(introspector, saveInteger)*.declaringType() == [repoType, repo]
        hierarchy.getMethodDeclarations(introspector, saveInteger)[1].arguments()[0].type == Integer
    }

    void "an array of a type variable matches an array of the bound type"() {
        given:
        def loader = buildClassLoader('test.StringsRepo', '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Introspected;

@Introspected(hierarchy = true)
interface ArrayRepo<T> {
    @Executable
    void saveAll(T[] items);
}

@Introspected(hierarchy = true)
class StringsRepo implements ArrayRepo<String> {
    @Executable
    public void saveAll(String[] items) { }
}
''')
        def introspector = new LoaderIntrospector(loader)
        Class<?> repo = loader.loadClass('test.ArrayRepo')
        def hierarchy = introspector.findIntrospection(loader.loadClass('test.StringsRepo')).orElseThrow().typeHierarchy.orElseThrow()

        expect:
        hierarchy.findOverridden(introspector, repo, 'saveAll', String[]).present
        !hierarchy.findOverridden(introspector, repo, 'saveAll', int[]).present
        !hierarchy.findOverridden(introspector, repo, 'saveAll', String).present
    }

    static class LoaderIntrospector implements BeanIntrospector {
        final ClassLoader loader

        LoaderIntrospector(ClassLoader loader) {
            this.loader = loader
        }

        @Override
        Collection<BeanIntrospection<Object>> findIntrospections(Predicate<? super BeanIntrospectionReference<?>> filter) {
            []
        }

        @Override
        Collection<Class<?>> findIntrospectedTypes(Predicate<? super BeanIntrospectionReference<?>> filter) {
            []
        }

        @Override
        <T> Optional<BeanIntrospection<T>> findIntrospection(Class<T> beanType) {
            try {
                def name = beanType.packageName + '.$' + beanType.simpleName + '$Introspection'
                return Optional.of((BeanIntrospection<T>) loader.loadClass(name).getDeclaredConstructor().newInstance())
            } catch (ClassNotFoundException ignored) {
                return Optional.empty()
            }
        }
    }
}
