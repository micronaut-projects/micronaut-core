package io.micronaut.aop.compile

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.InstantiatableBeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor

class IntroductionWithAroundSpec extends AbstractTypeElementSpec {

    void "test that around advice is applied to introduction concrete methods"() {
        when:"An introduction advice type is compiled that includes a concrete method that is annotated with around advice"
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;
import java.net.*;
import jakarta.validation.constraints.*;
import jakarta.inject.Singleton;

@Stub
@Singleton
abstract class MyBean {
    abstract void save(@NotBlank String name, @Min(1L) int age);
    abstract void saveTwo(@Min(1L) String name);

    @io.micronaut.aop.simple.Mutating("name")
    public String myConcrete(String name) {
        return name;
    }
}

''')

        then:"The around advice is applied to the concrete method"
        beanDefinition != null

        when:
        ApplicationContext context = ApplicationContext.run()
        def instance = ((InstantiatableBeanDefinition) beanDefinition).instantiate(context)

        then:
        instance.myConcrete("test") == 'changed'

        cleanup:
        context.close()
    }

    void "test around advice with introduction and default methods"() {
        when:"An introduction advice with default methods"
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;
import java.net.*;
import jakarta.validation.constraints.*;
import jakarta.inject.Singleton;

@Stub
@Singleton
interface MyBean extends StatefulEntityRepository<SomeEntity, String, State> {
    @io.micronaut.aop.simple.Mutating("name")
    @Override default String updateLifecycleState(String name, String id, test.State lifecycleState) {
        return StatefulEntityRepository.super.updateLifecycleState(name,id,lifecycleState);
    }
}

interface StatefulEntityRepository<E extends StatefulEntity<I, E, S>, I, S extends Enum<S>>
    extends ResourceEntityRepository<E, I> {

    default S getLifecycleState(E entity) {
        return entity.lifecycleState();
    }

    @io.micronaut.aop.simple.Mutating("name")
    default String updateLifecycleState(String name, I id, S lifecycleState) {
        return name;
    }
}

interface ResourceEntityRepository<E extends ResourceEntity<I, E>, I>
    extends CrudRepository<E, I> {}
interface CrudRepository<E, I> {}
interface ResourceEntity<IdT, EntityT extends ResourceEntity<IdT, EntityT>> {}
interface StatefulEntity<IdT, EntityT extends StatefulEntity<IdT, EntityT, StateT>, StateT extends Enum<StateT>>
        extends ResourceEntity<IdT, EntityT> {
    StateT lifecycleState();
}

record SomeEntity(State lifecycleState) implements StatefulEntity<String, SomeEntity, State> {}
enum State {
    ONE, TWO
}
''')

        then:"The around advice is applied to the concrete method"
        beanDefinition != null

        when:
        ApplicationContext context = ApplicationContext.run()
        def instance = ((InstantiatableBeanDefinition) beanDefinition).instantiate(context)

        then:
        instance.updateLifecycleState("test", "test2", null) == 'changed'

        cleanup:
        context.close()
    }
}
