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
package io.micronaut.docs.lifecycle;

import io.micronaut.context.BeanContext;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class PreDestroyBeanSpec {

    @Test
    public void testBeanClosingOnContextClose() {
        // tag::start[]
        BeanContext ctx = BeanContext.run();
        PreDestroyBean preDestroyBean = ctx.getBean(PreDestroyBean.class);
        Connection connection = ctx.getBean(Connection.class);
        ctx.stop();
        // end::start[]

        assertTrue(preDestroyBean.stopped.get());
        assertTrue(connection.stopped.get());
    }
}
