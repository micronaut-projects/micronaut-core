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

package io.micronaut.graal.reflect;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.reflect.ClassLoadingReporter;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.hateos.JsonError;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

/**
 * Produces a reflection.json file usable via Graal for a Micronaut application's classloading requirements.
 *
 * @author graemerocher
 * @since 1.0
 */
@Experimental
public class GraalClassLoadingReporter implements ClassLoadingReporter {
    private static final String REFLECTION_JSON_FILE = "graal.reflection.json";
    private static final String NETTY_TYPE = "io.netty.channel.socket.nio.NioServerSocketChannel";
    private final Set<String> classes = new ConcurrentSkipListSet<>();

    /**
     * Default constructor.
     */
    public GraalClassLoadingReporter() {
        classes.add(NETTY_TYPE);
    }

    @Override
    public void onPresent(Class<?> type) {
        classes.add(type.getName());
    }

    @Override
    public void onMissing(String name) {
        classes.remove(name);
    }

    @Override
    public void close() {

        if (!ClassUtils.isPresent(NETTY_TYPE, GraalClassLoadingReporter.class.getClassLoader())) {
            classes.remove(NETTY_TYPE);
        }
        
        List<Map> json = classes.stream().map(s -> {
            if (s.equals(NETTY_TYPE)) {
                return CollectionUtils.mapOf(
                        "name", NETTY_TYPE,
                        "methods", Arrays.asList(
                                CollectionUtils.mapOf(
                                        "name", "<init>",
                                        "parameterTypes", Collections.emptyList()
                                )
                        )
                );
            } else {
                return CollectionUtils.mapOf(
                        "name", s,
                        "allDeclaredConstructors", true
                );
            }
        }).collect(Collectors.toList());

        List<String> beans = Arrays.asList(JsonError.class.getName(), "io.micronaut.http.hateos.DefaultLink");
        for (String bean : beans) {
            json.add(CollectionUtils.mapOf(
                    "name", bean,
                    "allPublicMethods", true,
                    "allDeclaredConstructors", true
            ));
        }

        json.add(CollectionUtils.mapOf(
                "name", "com.fasterxml.jackson.datatype.jdk8.Jdk8Module",
                "allDeclaredConstructors", true
        ));

        json.add(CollectionUtils.mapOf(
                "name", "com.fasterxml.jackson.datatype.jsr310.JSR310Module",
                "allDeclaredConstructors", true
        ));

        String f = System.getProperty(REFLECTION_JSON_FILE);
        File file = new File(StringUtils.isNotEmpty(f) ? f : "./reflect.json");

        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
        try {
            writer.writeValue(file, json);
        } catch (IOException e) {
            System.err.println("Could not write Graal reflect.json: " + e.getMessage());
        }
    }
}
