/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.context;

import org.particleframework.context.event.BeanCreatedEvent;
import org.particleframework.context.event.BeanCreatedEventListener;
import org.particleframework.context.processor.ExecutableMethodProcessor;
import org.particleframework.core.annotation.AnnotationUtil;
import org.particleframework.core.reflect.GenericTypeUtils;
import org.particleframework.inject.ExecutableMethod;
import org.particleframework.inject.annotation.Executable;

import java.lang.annotation.Annotation;
import java.util.*;

/**
 * <p>A {@link BeanCreatedEventListener} that will monitor the creation of {@link ExecutableMethodProcessor} instances
 * and call {@link ExecutableMethodProcessor#process(ExecutableMethod)} for each available {@link ExecutableMethod}</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class ExecutableMethodProcessorListener implements BeanCreatedEventListener<ExecutableMethodProcessor> {

    private volatile Map<Class<? extends Annotation>, List<ExecutableMethod>> executableMethodsByAnnotation;

    @Override
    public ExecutableMethodProcessor onCreated(BeanCreatedEvent<ExecutableMethodProcessor> event) {
        ExecutableMethodProcessor processor = event.getBean();
        BeanContext beanContext = event.getSource();
        if (beanContext instanceof ApplicationContext) {

            Map<Class<? extends Annotation>, List<ExecutableMethod>> methodsByAnnotation = getExecutableMethodsByAnnotation((ApplicationContext) beanContext);
            if(!methodsByAnnotation.isEmpty()) {

                Optional<Class> targetAnnotation = GenericTypeUtils.resolveInterfaceTypeArgument(processor.getClass(), ExecutableMethodProcessor.class);
                if (!targetAnnotation.isPresent()) {
                    for (List<ExecutableMethod> executableMethods : methodsByAnnotation.values()) {
                        for (ExecutableMethod executableMethod : executableMethods) {
                            processor.process(executableMethod);
                        }
                    }
                } else {
                    Class annotationType = targetAnnotation.get();
                    methodsByAnnotation
                            .keySet()
                            .stream()
                            .filter((type) ->
                                    type == annotationType || AnnotationUtil.findAnnotationWithStereoType(type, annotationType) != null
                            )
                            .forEach((key) -> {
                                        List<ExecutableMethod> executableMethods = methodsByAnnotation.get(key);
                                        for (ExecutableMethod executableMethod : executableMethods) {
                                            processor.process(executableMethod);
                                        }
                                    }
                            );
                }
            }
        }

        return processor;
    }

    Map<Class<? extends Annotation>, List<ExecutableMethod>> getExecutableMethodsByAnnotation(ApplicationContext beanContext) {
        Map<Class<? extends Annotation>, List<ExecutableMethod>> result = executableMethodsByAnnotation;
        if (result == null) {
            synchronized (this) { // double check
                result = executableMethodsByAnnotation;
                if (result == null) {
                    executableMethodsByAnnotation = result = loadExecutableMethods(beanContext);
                }
            }
        }
        return result;
    }

    private Map<Class<? extends Annotation>, List<ExecutableMethod>> loadExecutableMethods(ApplicationContext applicationContext) {
        Iterable<ExecutableMethod> executableMethods = applicationContext.findServices(ExecutableMethod.class);
        Map<Class<? extends Annotation>, List<ExecutableMethod>> result = new LinkedHashMap<>();
        for (ExecutableMethod executableMethod : executableMethods) {
            Set<? extends Annotation> annotations = executableMethod.getExecutableAnnotations();
            Class declaringType = executableMethod.getDeclaringType();
            if(applicationContext.findBeanDefinition(declaringType).isPresent()) {

                if(annotations.isEmpty()) {
                    Annotation executableType = AnnotationUtil.findAnnotationWithStereoType(declaringType, Executable.class);
                    if(executableType != null) {
                        registerExecutableMethod(result, executableType, executableMethod);
                    }
                }
                else {

                    for (Annotation annotation : annotations) {
                        registerExecutableMethod(result, annotation, executableMethod);
                    }
                }
            }

        }
        return result;
    }

    private void registerExecutableMethod(Map<Class<? extends Annotation>, List<ExecutableMethod>> result, Annotation annotation, ExecutableMethod executableMethod) {
        Class<? extends Annotation> key = annotation.annotationType();
        List<ExecutableMethod> methodList = result.computeIfAbsent(key, k -> new ArrayList<>());
        methodList.add(executableMethod);
        result.put(key, methodList);
    }
}
