package io.micronaut.context;

import io.micronaut.context.annotation.Prototype;

/**
 * A bean {@link CreateBeanForeignResolutionContextSpec} creates through a resolution context of another implementation.
 */
@Prototype
public class ForeignContextBean {

    final BeanContext dependency;

    public ForeignContextBean(BeanContext dependency) {
        this.dependency = dependency;
    }
}
