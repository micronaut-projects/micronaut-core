package io.micronaut.context

import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.QualifiedBeanType
import spock.lang.Specification

class BeanResolutionCustomizerConfigureSpec extends Specification {

    void "the customizer is handed the context before the first lookup"() {
        given:
        def customizer = new ContextAwareCustomizer()

        when:
        ApplicationContext context = ApplicationContext.builder()
                .beanResolutionCustomizer(customizer)
                .build()

        then:
        customizer.instances.any { it.beanContext.is(context) }
        customizer.instances.every { it.candidateChecks == 0 }

        when:
        context.start()
        context.getBean(ApplicationContext)

        then:
        customizer.instances.find { it.beanContext.is(context) }.candidateChecks > 0
        customizer.instances.every { it.contextMismatches == 0 }
        !context.contextConfiguration.beanResolutionCustomizer().is(customizer.instances.first())
        context.contextConfiguration.beanResolutionCustomizer().is(customizer)

        cleanup:
        context.close()
    }

    void "one builder building two contexts hands each context its own customizer"() {
        given:
        def customizer = new ContextAwareCustomizer()
        ApplicationContextBuilder builder = ApplicationContext.builder()
                .beanResolutionCustomizer(customizer)

        when:
        ApplicationContext first = builder.build()
        ApplicationContext second = builder.build()

        then:
        !first.is(second)
        customizer.instances.find { it.beanContext.is(first) }
        customizer.instances.find { it.beanContext.is(second) }

        when:
        first.start()
        second.start()
        first.getBean(ApplicationContext)
        second.getBean(ApplicationContext)

        then:
        customizer.instances.find { it.beanContext.is(first) }.candidateChecks > 0
        customizer.instances.find { it.beanContext.is(second) }.candidateChecks > 0
        customizer.instances.every { it.contextMismatches == 0 }

        cleanup:
        first.close()
        second.close()
    }

    void "a customizer that keeps the default serves every context itself"() {
        given:
        def customizer = new BeanResolutionCustomizer() {}
        ApplicationContextBuilder builder = ApplicationContext.builder()
                .beanResolutionCustomizer(customizer)

        when:
        ApplicationContext first = builder.build().start()
        ApplicationContext second = builder.build().start()

        then:
        ((DefaultBeanContext) first).beanResolutionCustomizer.is(customizer)
        ((DefaultBeanContext) second).beanResolutionCustomizer.is(customizer)
        BeanResolutionCustomizer.DEFAULT.forContext(first).is(BeanResolutionCustomizer.DEFAULT)

        cleanup:
        first.close()
        second.close()
    }

    void "the default customizer is unchanged"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.contextConfiguration.beanResolutionCustomizer().is(BeanResolutionCustomizer.DEFAULT)
        ((DefaultBeanContext) context).beanResolutionCustomizer.is(BeanResolutionCustomizer.DEFAULT)
        context.getBean(ApplicationContext).is(context)

        cleanup:
        context.close()
    }

    /**
     * The customizer the builder is given; it answers for no context itself and hands each context an instance
     * that holds it.
     */
    static class ContextAwareCustomizer implements BeanResolutionCustomizer {
        final List<PerContext> instances = []

        @Override
        BeanResolutionCustomizer forContext(BeanContext beanContext) {
            def instance = new PerContext(beanContext)
            instances.add(instance)
            return instance
        }

        @Override
        boolean isCandidateBean(Argument<?> beanType, QualifiedBeanType<?> candidate) {
            throw new IllegalStateException("The builder's customizer serves no context")
        }
    }

    static class PerContext implements BeanResolutionCustomizer {
        final BeanContext beanContext
        int candidateChecks
        int contextMismatches

        PerContext(BeanContext beanContext) {
            this.beanContext = beanContext
        }

        @Override
        boolean isCandidateBean(Argument<?> beanType, QualifiedBeanType<?> candidate) {
            candidateChecks++
            return candidate.isCandidateBean(beanType)
        }

        @Override
        boolean shouldInitializeBean(BeanResolutionContext resolutionContext, BeanDefinition<?> beanDefinition, Object bean) {
            if (!resolutionContext.context.is(beanContext)) {
                contextMismatches++
            }
            return true
        }
    }
}
