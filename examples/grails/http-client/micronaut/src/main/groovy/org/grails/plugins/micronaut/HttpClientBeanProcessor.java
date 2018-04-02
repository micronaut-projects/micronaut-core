package org.grails.plugins.micronaut;

import io.micronaut.context.DefaultApplicationContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.Collection;
import java.util.Optional;

public class HttpClientBeanProcessor implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // TODO - Hackus Majoris
        // This is a temporary approach...

        DefaultApplicationContext defaultApplicationContext = new DefaultApplicationContext();
        defaultApplicationContext.start();

        defaultApplicationContext.getAllBeanDefinitions()
                .stream()
                .filter(bd -> bd.isSingleton())
                .forEach(bd -> {
                    Collection<?> beans = defaultApplicationContext.getBeansOfType(bd.getBeanType());
                    if (beans.size() == 1) {
                        Optional<?> beanOptional = beans.stream().findFirst();
                        if (beanOptional.isPresent()) {
                            Object bean = beanOptional.get();
                            beanFactory.registerSingleton(bd.getName(), bean);
                        }
                    }
                });
    }
}
