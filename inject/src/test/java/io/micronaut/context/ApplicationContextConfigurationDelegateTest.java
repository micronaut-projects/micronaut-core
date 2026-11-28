/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.context;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationContextConfigurationDelegateTest {

    @Test
    void disabledPackageDeductionIsHonouredWhenTheContextSuppliesTheConversionService() {
        try (ApplicationContext context = ApplicationContext.builder().deducePackage(false).start()) {
            assertTrue(context.getEnvironment().getPackages().isEmpty(),
                () -> "Unexpected packages: " + context.getEnvironment().getPackages());
        }
    }

    @Test
    void forwardsEveryConfigurationMethodToTheDelegate() throws Exception {
        List<String> forwarded = new ArrayList<>();
        ApplicationContextConfiguration target = (ApplicationContextConfiguration) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{ApplicationContextConfiguration.class},
            (proxy, method, args) -> {
                forwarded.add(method.getName());
                return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
            });
        ApplicationContextConfiguration delegate = new ApplicationContextConfigurationDelegate(target);

        List<Method> methods = new ArrayList<>();
        for (Method method : ApplicationContextConfiguration.class.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) {
                methods.add(method);
            }
        }
        assertFalse(methods.isEmpty());
        for (Method method : methods) {
            assertEquals(0, method.getParameterCount(), method::toString);
            Method override = ApplicationContextConfigurationDelegate.class.getDeclaredMethod(method.getName());
            assertEquals(ApplicationContextConfigurationDelegate.class, override.getDeclaringClass(), method::toString);
            forwarded.clear();
            try {
                method.invoke(delegate);
            } catch (InvocationTargetException e) {
                throw new AssertionError("Invoking " + method + " failed", e.getCause());
            }
            assertEquals(List.of(method.getName()), forwarded, () -> method + " is not forwarded to the delegate");
        }
    }
}
