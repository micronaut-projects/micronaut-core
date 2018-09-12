package io.micronaut.configuration.jmx.endpoint;

import io.micronaut.inject.BeanDefinition;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

interface EndpointNameGenerator {

    ObjectName generate(BeanDefinition beanDefinition, Object object) throws MalformedObjectNameException;
}
