package io.micronaut.inject.beanbuilder

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.inject.visitor.TypeElementVisitor

class BeanElementBuilderInjectFieldsSpec extends AbstractTypeElementSpec {

    void "inject() injects the public and non-public fields of a type registered via the builder"() {
        given:
        def context = buildContext('''
package injectbuilder;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
class Test {
}

@Singleton
class Dependency {
}

class Holder {
    @Inject
    public Dependency publicDependency;

    @Inject
    Dependency packageDependency;
}
''')
        def holderType = context.classLoader.loadClass("injectbuilder.Holder")
        def holder = context.getBean(holderType, Qualifiers.byName("built"))

        expect:
        holder.publicDependency != null
        holder.packageDependency.is(holder.publicDependency)

        cleanup:
        context.close()
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        [new InjectFieldsVisitor()]
    }
}
