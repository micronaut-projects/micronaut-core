package io.micronaut.context

import io.micronaut.inject.BeanDefinition
import spock.lang.Specification

/**
 * {@code createBean} begins the creation of the bean on the container's own resolution context, so that what is
 * resolved for the bean knows it is being created. The protected overload accepts a resolution context of any
 * implementation, which still creates the bean, without a creation to begin.
 */
class CreateBeanForeignResolutionContextSpec extends Specification {

    void 'test createBean creates the bean through a resolution context of another implementation'() {
        given:
        DefaultBeanContext context = ApplicationContext.run() as DefaultBeanContext
        BeanDefinition<ForeignContextBean> definition = context.getBeanDefinition(ForeignContextBean)
        BeanResolutionContext foreign = new ForeignResolutionContext(real: new DefaultBeanResolutionContext(context, definition))

        when:
        ForeignContextBean bean = context.createBean(foreign, ForeignContextBean, (Qualifier) null)

        then:
        bean != null
        bean.dependency != null
        foreign.rootDefinition == definition

        cleanup:
        context.close()
    }

    static class ForeignResolutionContext implements BeanResolutionContext {
        @Delegate
        BeanResolutionContext real
    }
}
