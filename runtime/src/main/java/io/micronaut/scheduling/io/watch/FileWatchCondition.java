/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.scheduling.io.watch;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.util.CollectionUtils;

import java.io.File;
import java.util.List;

/**
 * Custom condition to only enable file watch if the watch paths exist.
 *
 * @author graemerocher
 * @since 1.2.0
 */
@Introspected
public class FileWatchCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        BeanContext beanContext = context.getBeanContext();
        if (beanContext instanceof ApplicationContext) {
            List<String> paths = ((ApplicationContext) beanContext)
                    .getEnvironment()
                    .getProperty(FileWatchConfiguration.PATHS, ConversionContext.LIST_OF_STRING)
                    .orElse(null);

            if (CollectionUtils.isNotEmpty(paths)) {


                boolean matchedPaths = paths.stream().anyMatch(p -> new File(p).exists());
                if (!matchedPaths) {
                    context.fail("File watch disabled because no paths matching the watch pattern exist (Paths: " + paths + ")");
                }
                return matchedPaths;
            }
        }

        context.fail("File watch disabled because no watch paths specified");
        return false;
    }
}
