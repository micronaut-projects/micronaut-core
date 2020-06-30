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
package io.micronaut.docs.context.env;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.DefaultEnvironment;
import io.micronaut.context.env.Environment;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class DefaultEnvironmentSpec {

    // tag::disableEnvDeduction[]
    @Test
    public void testDisableEnvironmentDeductionViaBuilder() {
        ApplicationContext ctx = ApplicationContext.builder().deduceEnvironment(false).start();
        assertFalse(ctx.getEnvironment().getActiveNames().contains(Environment.TEST));
        ctx.close();
    }
    // end::disableEnvDeduction[]
}
