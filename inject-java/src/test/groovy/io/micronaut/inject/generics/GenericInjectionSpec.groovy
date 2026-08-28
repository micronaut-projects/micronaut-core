package io.micronaut.inject.generics

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.core.type.Argument
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.stream.Collectors

class GenericInjectionSpec extends Specification {
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(["spec.name": GenericInjectionSpec.simpleName])

    void "test narrow injection by generic type"() {
        when:
        def beanType = Argument.of(Engine, V8)
        def bean = context.getBean(Vehicle)
        def nonExistent = Argument.of(Engine, V10)
        def present = Argument.of(Shop, Book)
        def notPresent = Argument.of(Shop, String)

        then:
        bean.start() == 'Starting V8'
        bean.v6Engines.size() == 1
        bean.v6Engines.first().start() == 'Starting V6'
        bean.anotherV8.start() == 'Starting V8'
        bean.anotherV8.is(bean.engine)
        context.findBeanDefinition(present).isPresent()
        !context.findBeanDefinition(notPresent).isPresent()
        !context.findBeanDefinition(nonExistent).isPresent()
        !context.findBean(nonExistent).isPresent()
        context.getBeansOfType(nonExistent).size() == 0
        context.getBeanDefinition(beanType)
        context.containsBean(beanType)
        context.streamOfType(beanType).collect(Collectors.toList()).size() == 1
        context.getBeansOfType(beanType).size() == 1

        when:
        context.getBean(nonExistent)

        then:
        thrown(NoSuchBeanException)

        when:
        context.destroyBean(beanType)
        def another = context.getBean(beanType)

        then:
        !bean.is(another)

    }

    void "test narrow injection by generic type with intercepted candidate"() {
        when:
        def forestBear = Argument.of(Bear, Forest)
        def den = context.getBean(ForestDen)
        def brownBearDefinition = context.getBeanDefinition(BrownBear)
        def whiteBearDefinition = context.getBeanDefinition(WhiteBear)

        then:
        den.bear instanceof BrownBear
        den.fieldBear instanceof BrownBear
        den.bears*.class == [BrownBear]
        den.bearProvider.get() instanceof BrownBear
        context.getBean(forestBear) instanceof BrownBear
        context.getBeanDefinition(forestBear).beanType == BrownBear
        context.getBeansOfType(forestBear).toList()*.class == [BrownBear]

        and:
        brownBearDefinition.isCandidateBean(forestBear)
        !whiteBearDefinition.isCandidateBean(forestBear)
        brownBearDefinition.getTypeArguments(Bear)[0].type == Forest
        whiteBearDefinition.getTypeArguments(Bear)[0].type == Arctic
    }
}
