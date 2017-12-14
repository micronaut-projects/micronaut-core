package org.particleframework.management.endpoint.beans;

import org.particleframework.context.BeanContext;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.qualifiers.Qualifiers;
import org.particleframework.management.endpoint.Endpoint;
import org.particleframework.management.endpoint.Read;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Endpoint("beans")
public class BeansEndpoint {

    private BeanContext beanContext;

    public BeansEndpoint(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Read
    public Map<String, Object> getBeans() {
        Collection<BeanDefinition<?>> definitions = beanContext.getBeanDefinitions(Qualifiers.byType(Object.class));
        return Collections.emptyMap();
    }
}
