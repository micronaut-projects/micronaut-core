/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.context.conditions;

import io.micronaut.context.RequiresCondition;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * The matches conditions util class.
 *
 * @author Denis Stepanov
 * @since 4.6
 */
@Internal
public class MatchesConditionUtils {

    private MatchesConditionUtils() {
    }

    /**
     * Create conditions from the annotation value.
     *
     * @param requirement    The requirement
     * @param preConditions  The pre-conditions collection to fill
     * @param postConditions The post-conditions collection to fill
     */
    public static void createConditions(AnnotationValue<Requires> requirement,
                                        List<Condition> preConditions,
                                        List<Condition> postConditions) {
        if (requirement.contains(RequiresCondition.MEMBER_CLASSES)) {
            AnnotationClassValue<?>[] classes = requirement.annotationClassValues(RequiresCondition.MEMBER_CLASSES);
            if (classes.length > 0) {
                preConditions.add(new MatchesPresenceOfClassesCondition(classes));
            }
        }
        if (requirement.contains(RequiresCondition.MEMBER_MISSING_CLASSES)) {
            AnnotationClassValue<?>[] classes = requirement.annotationClassValues(RequiresCondition.MEMBER_MISSING_CLASSES);
            if (classes.length > 0) {
                preConditions.add(new MatchesAbsenceOfClassesCondition(classes));
            }
        }
        if (requirement.contains(RequiresCondition.MEMBER_ENV)) {
            String[] env = requirement.stringValues(RequiresCondition.MEMBER_ENV);
            if (env.length > 0) {
                preConditions.add(new MatchesEnvironmentCondition(env));
            }
        }
        if (requirement.contains(RequiresCondition.MEMBER_NOT_ENV)) {
            String[] env = requirement.stringValues(RequiresCondition.MEMBER_NOT_ENV);
            if (env.length > 0) {
                preConditions.add(new MatchesNotEnvironmentCondition(env));
            }
        }
        if (requirement.contains(RequiresCondition.MEMBER_ENTITIES)) {
            AnnotationClassValue<?>[] classes = requirement.annotationClassValues(RequiresCondition.MEMBER_ENTITIES);
            if (classes.length > 0) {
                preConditions.add(new MatchesPresenceOfEntitiesCondition(classes));
            }
        }
        if (requirement.contains(RequiresCondition.MEMBER_PROPERTY)) {
            String property = requirement.stringValue(RequiresCondition.MEMBER_PROPERTY).orElse(null);
            if (StringUtils.isNotEmpty(property)) {
                MatchesPropertyCondition.Condition condition = MatchesPropertyCondition.Condition.CONTAINS;
                String value = requirement.stringValue().orElse(null);
                if (value != null) {
                    condition = MatchesPropertyCondition.Condition.EQUALS;
                }
                String defaultValue = requirement.stringValue(RequiresCondition.MEMBER_DEFAULT_VALUE).orElse(null);
                if (value == null) {
                    String notEquals = requirement.stringValue(RequiresCondition.MEMBER_NOT_EQUALS).orElse(null);
                    if (notEquals != null) {
                        value = notEquals;
                        condition = MatchesPropertyCondition.Condition.NOT_EQUALS;
                    } else {
                        String pattern = requirement.stringValue(RequiresCondition.MEMBER_PATTERN).orElse(null);
                        if (pattern != null) {
                            value = pattern;
                            condition = MatchesPropertyCondition.Condition.PATTERN;
                        }
                    }
                }
                preConditions.add(new MatchesPropertyCondition(property, value, defaultValue, condition));
            }
        }
        if (requirement.contains(RequiresCondition.MEMBER_MISSING_PROPERTY)) {
            String property = requirement.stringValue(RequiresCondition.MEMBER_MISSING_PROPERTY).orElse(null);
            if (StringUtils.isNotEmpty(property)) {
                preConditions.add(new MatchesMissingPropertyCondition(property));
            }
        }
        if (requirement.contains(RequiresCondition.MEMBER_CONFIGURATION)) {
            String configurationName = requirement.stringValue(RequiresCondition.MEMBER_CONFIGURATION).orElse(null);
            if (StringUtils.isNotEmpty(configurationName)) {
                String minimumVersion = requirement.stringValue(RequiresCondition.MEMBER_VERSION).orElse(null);
                preConditions.add(new MatchesConfigurationCondition(configurationName, minimumVersion));
            }
        }
        if (requirement.contains(RequiresCondition.MEMBER_SDK)) {
            Requires.Sdk sdk = requirement.enumValue(RequiresCondition.MEMBER_SDK, Requires.Sdk.class).orElse(null);
            String version = requirement.stringValue(RequiresCondition.MEMBER_VERSION).orElse(null);
            if (sdk != null && StringUtils.isNotEmpty(version)) {
                preConditions.add(new MatchesSdkCondition(sdk, version));
            }
        }
        if (requirement.contains(RequiresCondition.MEMBER_RESOURCES)) {
            final String[] resourcePaths = requirement.stringValues(RequiresCondition.MEMBER_RESOURCES);
            if (ArrayUtils.isNotEmpty(resourcePaths)) {
                preConditions.add(new MatchesPresenceOfResourcesCondition(resourcePaths));
            }
        }
        if (requirement.contains(RequiresCondition.MEMBER_OS)) {
            final Set<Requires.Family> os = requirement.enumValuesSet(RequiresCondition.MEMBER_OS, Requires.Family.class);
            if (!os.isEmpty()) {
                preConditions.add(new MatchesCurrentOsCondition(os));
            }
        }
        if (requirement.contains(RequiresCondition.MEMBER_NOT_OS)) {
            final Set<Requires.Family> notOs = requirement.enumValuesSet(RequiresCondition.MEMBER_NOT_OS, Requires.Family.class);
            if (!notOs.isEmpty()) {
                preConditions.add(new MatchesCurrentNotOsCondition(notOs));
            }
        }
        if (requirement.contains(RequiresCondition.MEMBER_BEAN)) {
            AnnotationClassValue<?> bean = requirement.annotationClassValue(RequiresCondition.MEMBER_BEAN).orElse(null);
            preConditions.add(new MatchesPresenceOfClassesCondition(new AnnotationClassValue[]{bean}));
            postConditions.add(new MatchesPresenceOfBeansCondition(new AnnotationClassValue[]{bean}));
        }
        if (requirement.contains(RequiresCondition.MEMBER_BEANS)) {
            AnnotationClassValue<?>[] beans = requirement.annotationClassValues(RequiresCondition.MEMBER_BEANS);
            if (beans.length != 0) {
                // For presence beans check we add a pre-check for the bean class to exist
                preConditions.add(new MatchesPresenceOfClassesCondition(beans));
                postConditions.add(new MatchesPresenceOfBeansCondition(beans));
            }
        }
        if (requirement.contains(RequiresCondition.MEMBER_MISSING_BEANS)) {
            AnnotationClassValue<?>[] beans = requirement.annotationClassValues(RequiresCondition.MEMBER_MISSING_BEANS);
            if (beans.length != 0) {
                postConditions.add(new MatchesAbsenceOfBeansCondition(beans));
            }
        }
        if (requirement.contains(RequiresCondition.MEMBER_CONDITION)) {
            requirement.annotationClassValue(RequiresCondition.MEMBER_CONDITION)
                .ifPresent(annotationClassValue -> postConditions.add(new MatchesCustomCondition(annotationClassValue)));
        }
    }

}
