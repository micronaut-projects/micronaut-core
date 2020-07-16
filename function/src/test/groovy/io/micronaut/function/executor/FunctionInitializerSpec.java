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
package io.micronaut.function.executor;

import org.junit.Assert;
import org.junit.Test;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class FunctionInitializerSpec   {

    @Test
    public void testFunctionInitializer() {
        MathFunction mathFunction = new MathFunction();
        Assert.assertEquals(1, MathFunction.initCount.get());
        Assert.assertEquals(1, MathFunction.injectCount.get());
        Assert.assertEquals(2, mathFunction.round(1.6f));
    }

    @Singleton
    public static class MathService {
        int round(float input) {
            return Math.round(input);
        }
    }

    @Singleton
    public static class MathFunction extends FunctionInitializer {
        static AtomicInteger initCount = new AtomicInteger(0);
        static AtomicInteger injectCount = new AtomicInteger(0);


        private MathService mathService;

        @Inject
        public void setMathService(MathService mathService) {
            this.mathService = mathService;
            injectCount.incrementAndGet();
        }

        @PostConstruct
        public void init() {
            initCount.incrementAndGet();
        }

        int round(float input) {
            return mathService.round(input);
        }

        public static void main(String...args) throws IOException {
            MathFunction mathFunction = new MathFunction();
            mathFunction.run(args, (context)-> mathFunction.round(context.get(float.class)));
        }
    }
}
