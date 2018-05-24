/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.function.executor;

import org.junit.Assert;
import org.junit.Test;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class FunctionInitializerSpec   {

    @Test
    public void testFunctionInitializer() {
        Assert.assertEquals(new MathFunction().round(1.6f) , 2);
    }

    @Singleton
    public static class MathService {
        int round(float input) {
            return Math.round(input);
        }
    }

    public static class MathFunction extends FunctionInitializer {
        @Inject
        MathService mathService;

        int round(float input) {
            return mathService.round(input);
        }

        public static void main(String...args) throws IOException {
            MathFunction mathFunction = new MathFunction();
            mathFunction.run(args, (context)-> mathFunction.round(context.get(float.class)));
        }
    }
}
