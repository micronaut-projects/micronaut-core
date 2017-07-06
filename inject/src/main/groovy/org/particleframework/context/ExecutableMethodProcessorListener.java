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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>A {@link BeanCreatedEventListener} that will monitor the creation of {@link ExecutableMethodProcessor} instances
 * and call {@link ExecutableMethodProcessor#process(ApplicationContext, ExecutableMethod)} for each available {@link ExecutableMethod}</p>
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

                Class targetAnnotation = GenericTypeUtils.resolveInterfaceTypeArgument(processor.getClass(), ExecutableMethodProcessor.class);
                if (targetAnnotation == null) {
                    for (List<ExecutableMethod> executableMethods : methodsByAnnotation.values()) {
                        for (ExecutableMethod executableMethod : executableMethods) {
                            processor.process((ApplicationContext) beanContext, executableMethod);
                        }
                    }
                } else {
                    methodsByAnnotation
                            .keySet()
                            .stream()
                            .filter((type) -> AnnotationUtil.findAnnotationWithStereoType(type, targetAnnotation) != null)
                            .forEach((key) -> {
                                        List<ExecutableMethod> executableMethods = methodsByAnnotation.get(key);
                                        for (ExecutableMethod executableMethod : executableMethods) {
                                            processor.process((ApplicationContext) beanContext, executableMethod);
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
            Iterable<? extends Annotation> annotations = executableMethod.getExecutableAnnotations();
            for (Annotation annotation : annotations) {
                Class<? extends Annotation> key = annotation.annotationType();
                List<ExecutableMethod> methodList = result.computeIfAbsent(key, k -> new ArrayList<>());
                methodList.add(executableMethod);
                result.put(key, methodList);
            }
        }
        return result;
    }
}
