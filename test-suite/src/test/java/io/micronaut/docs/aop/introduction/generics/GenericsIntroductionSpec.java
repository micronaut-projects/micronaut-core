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
package io.micronaut.docs.aop.introduction.generics;

import static org.junit.Assert.assertEquals;

import io.micronaut.context.ApplicationContext;
import org.junit.Test;

public class GenericsIntroductionSpec {

    @Test
    public void testStubIntroduction() {
        ApplicationContext applicationContext = ApplicationContext.run();

        // tag::test[]
        SpecificPublisher publisher = applicationContext.getBean(SpecificPublisher.class);

        assertEquals("SpecificEvent", publisher.publish(new SpecificEvent()));
        // end::test[]

        applicationContext.stop();
    }
    
}
