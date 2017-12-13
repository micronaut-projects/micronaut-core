/*
 * Copyright 2017 original authors
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
package org.particleframework.core.async.publisher;

import org.particleframework.core.reflect.ClassUtils;
import org.reactivestreams.Publisher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Utility methods for reactive types
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class PublisherUtils {

    private static final List<Class<?>> reactiveTypes = new ArrayList<>(3);
    private static final List<Class<?>> singleTypes = new ArrayList<>(3);

    static {
        ClassLoader classLoader = PublisherUtils.class.getClassLoader();
        singleTypes.add(CompletableFuturePublisher.class);
        List<String> typeNames = Arrays.asList(
                "io.reactivex.Maybe",
                "io.reactivex.Observable",
                "reactor.core.publisher.Flux"
        );
        for (String name : typeNames) {
            Optional<Class> aClass = ClassUtils.forName(name, classLoader);
            aClass.ifPresent(reactiveTypes::add);
        }
        for (String name : Arrays.asList("io.reactivex.Single","reactor.core.publisher.Mono")) {
            Optional<Class> aClass = ClassUtils.forName(name, classLoader);
            aClass.ifPresent(aClass1 -> {
                singleTypes.add(aClass1);
                reactiveTypes.add(aClass1);
            });

        }
    }

    public static boolean isPublisher(Class<?> type) {
        if (Publisher.class.isAssignableFrom(type)) {
            return true;
        } else {
            for (Class<?> reactiveType : reactiveTypes) {
                if (reactiveType.isAssignableFrom(type)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static boolean isSingle(Class<?> type) {
        for (Class<?> reactiveType : singleTypes) {
            if (reactiveType.isAssignableFrom(type)) {
                return true;
            }
        }
        return false;
    }
}
