/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context.graal;

import com.oracle.svm.core.annotate.AutomaticFeature;
import io.micronaut.context.annotation.Requirements;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.graal.AutomaticFeatureUtils;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinitionReference;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import java.util.function.Predicate;

@Internal
@AutomaticFeature
public class InjectFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        Predicate<BeanDefinitionReference> predicate = beanDefinitionReference -> {
            try {
                AnnotationValue<Requirements> annotation = beanDefinitionReference.getAnnotationMetadata().getAnnotation(Requirements.class);
                if (annotation != null) {
                    for (AnnotationValue<Requires> requiresAnnotationValue : annotation.getAnnotations("value", Requires.class)) {
                        String[] classes = requiresAnnotationValue.stringValues("classes");
                        for (String aClass : classes) {
                            try {
                                Class.forName(aClass, false, beanDefinitionReference.getClass().getClassLoader());
                            } catch (ClassNotFoundException e) {
                                System.out.println("DEFINITION " + beanDefinitionReference.getName() + " REQUIRES MISSING CLASS: " + aClass);
                                return false;
                            }
                        }
                    }
                }
                return true;
            } catch (Throwable e) {
                System.out.println("NOT FOUND DURING ANALYSIS: " + e.getMessage());
            }
            return false;
        };
        SoftServiceLoader.preCache(BeanDefinitionReference.class, predicate, BeanDefinitionReference.class.getClassLoader())
                .forEach(beanDefinitionReference -> {
                    RuntimeClassInitialization.initializeAtBuildTime(beanDefinitionReference.getClass());
                    AutomaticFeatureUtils.initializeAtBuildTime(access, beanDefinitionReference.getBeanDefinitionName());
                });
        SoftServiceLoader.preCache(BeanConfiguration.class, BeanConfiguration.class.getClassLoader())
                .forEach(beanConfiguration -> {
                    RuntimeClassInitialization.initializeAtBuildTime(beanConfiguration.getClass());
                });
        SoftServiceLoader.preCache(PropertySourceLoader.class, PropertySourceLoader.class.getClassLoader())
                .forEach(propertySourceLoader -> {
                    RuntimeClassInitialization.initializeAtBuildTime(propertySourceLoader.getClass());
                });
    }

}

