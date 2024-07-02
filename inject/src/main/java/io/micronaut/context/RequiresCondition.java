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
package io.micronaut.context;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.conditions.MatchesConditionUtils;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NextMajorVersion;
import io.micronaut.inject.BeanDefinitionReference;

import java.util.ArrayList;
import java.util.List;

/**
 * An abstract {@link Condition} implementation that is based on the presence
 * of {@link Requires} annotation.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@NextMajorVersion("Make internal")
public class RequiresCondition implements Condition {

    public static final String MEMBER_PROPERTY = "property";
    public static final String MEMBER_NOT_EQUALS = "notEquals";
    public static final String MEMBER_DEFAULT_VALUE = "defaultValue";
    public static final String MEMBER_PATTERN = "pattern";
    public static final String MEMBER_MISSING_PROPERTY = "missingProperty";
    public static final String MEMBER_ENV = "env";
    public static final String MEMBER_NOT_ENV = "notEnv";
    public static final String MEMBER_CONDITION = "condition";
    public static final String MEMBER_SDK = "sdk";
    public static final String MEMBER_VERSION = "version";
    public static final String MEMBER_MISSING_CLASSES = "missing";
    public static final String MEMBER_RESOURCES = "resources";
    public static final String MEMBER_CONFIGURATION = "configuration";
    public static final String MEMBER_CLASSES = "classes";
    public static final String MEMBER_ENTITIES = "entities";
    public static final String MEMBER_BEANS = "beans";
    public static final String MEMBER_MISSING_BEANS = "missingBeans";
    public static final String MEMBER_OS = "os";
    public static final String MEMBER_NOT_OS = "notOs";
    public static final String MEMBER_BEAN = "bean";
    public static final String MEMBER_BEAN_PROPERTY = "beanProperty";

    private final AnnotationMetadata annotationMetadata;

    /**
     * @param annotationMetadata The annotation metadata
     */
    public RequiresCondition(AnnotationMetadata annotationMetadata) {
        this.annotationMetadata = annotationMetadata;
    }

    @Override
    public boolean matches(ConditionContext context) {
        List<AnnotationValue<Requires>> requirements = annotationMetadata.getAnnotationValuesByType(Requires.class);
        if (requirements.isEmpty()) {
            return true;
        }
        AnnotationMetadataProvider component = context.getComponent();
        boolean isBeanReference = component instanceof BeanDefinitionReference;

        if (component instanceof AbstractInitializableBeanDefinitionAndReference<?>) {
            for (AnnotationValue<Requires> requirement : requirements) {
                if (!processPreStartRequirements(context, requirement)) {
                    return false;
                }
                if (!processPostStartRequirements(context, requirement)) {
                    return false;
                }
            }
            return true;
        }

        // here we use AnnotationMetadata to avoid loading the classes referenced in the annotations directly
        if (isBeanReference) {
            for (AnnotationValue<Requires> requirement : requirements) {
                // if annotation value has evaluated expressions, postpone
                // decision until the bean is loaded
                if (requirement.hasEvaluatedExpressions()) {
                    continue;
                }

                if (!processPreStartRequirements(context, requirement)) {
                    return false;
                }
            }
        } else {
            for (AnnotationValue<Requires> requires : requirements) {
                if (!processPostStartRequirements(context, requires)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * This method will process requirements for a {@link BeanDefinitionReference} that has not yet been loaded. Unlike {@link #processPostStartRequirements(ConditionContext, AnnotationValue)}
     * this method is executed prior to the bean being loaded and processes requirements that do not require all the beans to be loaded.
     */
    private boolean processPreStartRequirements(ConditionContext context, AnnotationValue<Requires> requirements) {
        List<Condition> preConditions = new ArrayList<>(5);
        List<Condition> postConditions = new ArrayList<>(5);
        MatchesConditionUtils.createConditions(requirements, preConditions, postConditions);
        for (Condition condition : preConditions) {
            if (!condition.matches(context)) {
                return false;
            }
        }
        return true;
    }

    /**
     * This method will run conditions that require all beans to be loaded. These conditions included "beans", "bean", "missingBeans" and custom conditions.
     */
    private boolean processPostStartRequirements(ConditionContext context, AnnotationValue<Requires> requirements) {
        List<Condition> conditions = new ArrayList<>(10);
        MatchesConditionUtils.createConditions(requirements, conditions, conditions);
        for (Condition condition : conditions) {
            if (!condition.matches(context)) {
                return false;
            }
        }
        return true;
    }
}
