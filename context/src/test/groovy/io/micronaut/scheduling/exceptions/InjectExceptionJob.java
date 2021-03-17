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
package io.micronaut.scheduling.exceptions;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Requires(property = "injection-exception.task.enabled", value = "true")
public class InjectExceptionJob {

    @Inject NotInjectable notInjectable;

    @Scheduled(fixedRate = "10m")
    public void runSomething() {

    }

}
