/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.context;

import io.micronaut.core.annotation.Internal;

import javax.annotation.Nullable;
import java.util.List;

/**
 * An interface for configuring an application context.
 *
 * @author Zachary Klein
 * @since 1.0
 */
@Internal
public interface ApplicationContextConfiguration {

    /**
     * @return True if the environments should be deduced
     */
    @Nullable
    Boolean getDeduceEnvironments();

    /**
     * @return The environment names
     */
    List<String> getEnvironments();

}
