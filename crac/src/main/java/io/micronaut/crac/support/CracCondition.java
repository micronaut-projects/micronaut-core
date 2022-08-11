/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.crac.support;

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.crac.CracConfiguration;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A condition that checks if CRaC support exists for the current classloader.
 *
 * @author Tim Yates
 * @since 3.7.0
 */
@Experimental
public class CracCondition implements Condition {

    // See https://github.com/CRaC/org.crac/blob/master/src/main/java/org/crac/Core.java
    private static final List<String> CRAC_LOCATIONS = Arrays.asList(
        "jdk.crac.Resource",
        "javax.crac.Resource"
    );

    @Override
    public boolean matches(ConditionContext context) {
        Optional<CracConfiguration> cracConfiguration = context.findBean(CracConfiguration.class);

        if (cracConfiguration.map(CracConfiguration::getEnabled).orElse(false)) {
            context.fail("CRaC is disabled");
            return false;
        }

        boolean located = Stream.concat(
                Stream.of(
                        cracConfiguration.map(CracConfiguration::getCracCompatClass).orElse(null),
                        System.getProperty("org.crac.Core.Compat")
                    )
                    .filter(Objects::nonNull),
                CRAC_LOCATIONS.stream()
            )
            .anyMatch(this::located);

        if (!located) {
            context.fail("CRaC is not available");
        }
        return located;
    }

    private boolean located(String s) {
        return ClassUtils.isPresent(s, null);
    }
}
