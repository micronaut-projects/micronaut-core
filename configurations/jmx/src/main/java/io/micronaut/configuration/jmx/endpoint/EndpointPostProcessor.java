package io.micronaut.configuration.jmx.endpoint;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.management.endpoint.annotation.Delete;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Write;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.management.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
@Requires(classes = Endpoint.class)
public class EndpointPostProcessor implements BeanCreatedEventListener<Object> {

    private static final Logger LOG = LoggerFactory.getLogger(EndpointPostProcessor.class);

    private final MBeanServer mBeanServer;
    private final EndpointNameGenerator endpointNameGenerator;

    public EndpointPostProcessor(MBeanServer mBeanServer,
                                 EndpointNameGenerator endpointNameGenerator) {
        this.mBeanServer = mBeanServer;
        this.endpointNameGenerator = endpointNameGenerator;
    }

    @Override
    public Object onCreated(BeanCreatedEvent<Object> event) {
        BeanDefinition beanDefinition = event.getBeanDefinition();
        Object bean = event.getBean();
        if (beanDefinition.hasDeclaredAnnotation(Endpoint.class)) {
            try {
                AnnotationValue<Endpoint> endpoint = beanDefinition.getAnnotationMetadata().getAnnotation(Endpoint.class);
                String beanDescription = StringUtils.capitalize(endpoint.getRequiredValue("id", String.class)) + " Endpoint";
                Collection<ExecutableMethod> methods = beanDefinition.getExecutableMethods();
                MBeanOperationInfo[] operations = methods.stream().map((ExecutableMethod method) -> {

                    String name = method.getMethodName();

                    String description = "";

                    MBeanParameterInfo[] signature = Arrays.stream(method.getArguments()).map(argument -> {
                        return new MBeanParameterInfo(argument.getName(), argument.getType().getName(), "");
                    }).toArray(MBeanParameterInfo[]::new);

                    Class returnType = method.getReturnType().getType();
                    if (Publishers.isSingle(returnType) || Publishers.isConvertibleToPublisher(returnType)) {
                        Argument[] typeParams = method.getReturnType().getTypeParameters();
                        if (typeParams.length > 0) {
                            returnType = typeParams[0].getType();
                        }
                    }
                    String type = returnType.getName();

                    int impact = MBeanOperationInfo.INFO; //read
                    if (method.hasAnnotation(Write.class)) {
                        impact = MBeanOperationInfo.ACTION_INFO;
                    } else if (method.hasAnnotation(Delete.class)) {
                        impact = MBeanOperationInfo.ACTION;
                    }

                    return new MBeanOperationInfo(name, description, signature, type, impact);
                }).toArray(MBeanOperationInfo[]::new);

                MBeanInfo mBeanInfo = new MBeanInfo(beanDefinition.getBeanType().getName(),
                        beanDescription,
                        new MBeanAttributeInfo[0],
                        new MBeanConstructorInfo[0],
                        operations,
                        new MBeanNotificationInfo[0]
                        );

                DynamicMBean mbean = new DynamicMBean() {

                    @Override
                    public Object getAttribute(String attribute) throws AttributeNotFoundException, MBeanException, ReflectionException {
                        return null;
                    }

                    @Override
                    public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {
                        //no op
                    }

                    @Override
                    public AttributeList getAttributes(String[] attributes) {
                        return new AttributeList();
                    }

                    @Override
                    public AttributeList setAttributes(AttributeList attributes) {
                        return attributes;
                    }

                    @Override
                    public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
                        Stream<ExecutableMethod> methodStream = beanDefinition.findPossibleMethods(actionName);
                        List<ExecutableMethod> methods = methodStream.collect(Collectors.toList());
                        if (methods.size() == 1) {
                            //noinspection unchecked
                            Object returnVal = methods.get(0).invoke(bean, params);
                            if (Publishers.isSingle(returnVal.getClass()) || Publishers.isConvertibleToPublisher(returnVal)) {
                                return Flowable.fromPublisher(Publishers.convertPublisher(returnVal, Publisher.class)).blockingFirst();
                            }
                            return returnVal;
                        } else {
                            //would be necessary at this point to convert the signature string[] to a class[]
                            //in order to find the correct method
                            return null;
                        }
                    }

                    @Override
                    public MBeanInfo getMBeanInfo() {
                        return mBeanInfo;
                    }
                };
                mBeanServer.registerMBean(mbean, endpointNameGenerator.generate(beanDefinition, bean));
            } catch (JMException e) {
                LOG.error("Failed to register an MBean for the endpoint " + bean.getClass().getName(), e);
            }
        }
        return bean;
    }
}
