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
import io.micronaut.context.ApplicationContextConfiguration;
import io.micronaut.context.annotation.Requirements;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertiesLoader;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.context.env.graalvm.GraalBuildTimeEnvironment;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.graal.AutomaticFeatureUtils;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.EnvironmentConditional;
import io.micronaut.inject.ExecutableMethodsDefinition;
import io.micronaut.inject.ExecutableMethodsDefinitionProvider;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

@Internal
@AutomaticFeature
public class InjectFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        GraalBuildTimeEnvironment.PRELOADED = new PropertiesLoader(
                Environment.DEFAULT_NAME,
                Collections.emptySet(),
                Arrays.asList("classpath:/", "file:config/"),
                ClassPathResourceLoader.defaultLoader(GraalBuildTimeEnvironment.class.getClassLoader()))
                .read();

        GraalBuildTimeEnvironment environment = new GraalBuildTimeEnvironment(new ApplicationContextConfiguration() {
            @Override
            public List<String> getEnvironments() {
                return Collections.singletonList("test");
            }
        }, GraalBuildTimeEnvironment.PRELOADED);

        Predicate<BeanDefinitionReference> predicate = beanDefinitionReference -> {
            if (beanDefinitionReference instanceof EnvironmentConditional) {
                if (!((EnvironmentConditional) beanDefinitionReference).isEnabled(environment)) {
                    return false;
                }
            }

            BeanDefinition beanDefinition = beanDefinitionReference.load();
            if (beanDefinition instanceof EnvironmentConditional) {
                if (!((EnvironmentConditional) beanDefinition).isEnabled(environment)) {
                    return false;
                }
            }

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
                    BeanDefinition beanDefinition = beanDefinitionReference.load();
                    if (beanDefinition != null) {
//                RuntimeClassInitialization.initializeAtBuildTime(beanDefinition.getClass());
                        if (beanDefinition instanceof ExecutableMethodsDefinitionProvider) {
                            ExecutableMethodsDefinition exec = ((ExecutableMethodsDefinitionProvider) beanDefinition).getExecutableMethodsDefinition();
                            if (exec != null) {
                                AutomaticFeatureUtils.initializeAtBuildTime(access, exec.getClass().getName());
                            }
                        }
                    }
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

