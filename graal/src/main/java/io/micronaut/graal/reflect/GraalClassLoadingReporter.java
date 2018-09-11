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
    /**
     * System property that indicates the location of the reflection JSON file.
     */
    public static final String REFLECTION_JSON_FILE = "graalvm.reflection.json";
    /**
     * System property that indicates whether class analysis is is enabled.
     */
    public static final String GRAAL_CLASS_ANALYSIS = "graalvm.class.analysis";

    private static final String NETTY_TYPE = "io.netty.channel.socket.nio.NioServerSocketChannel";
    private final Set<String> classes = new ConcurrentSkipListSet<>();
    private final Set<String> beans = new ConcurrentSkipListSet<>();
    private final Set<String> arrays = new ConcurrentSkipListSet<>();

    /**
     * Default constructor.
     */
    public GraalClassLoadingReporter() {
        classes.add(NETTY_TYPE);
        arrays.add("io.micronaut.http.MediaType[]");
    }

    @Override
    public boolean isEnabled() {
        String property = System.getProperty("java.vm.name");
        if (!Boolean.getBoolean(GRAAL_CLASS_ANALYSIS) || property == null || !property.contains("GraalVM")) {
            return false;
        } else {
            String f = System.getProperty(REFLECTION_JSON_FILE);
            if (StringUtils.isNotEmpty(f)) {
                File file = new File(f);
                boolean enabled = !file.exists();
                if (enabled) {
                    System.out.println("Graal Class Loading Analysis Enabled.");
                }
                return enabled;
            } else {
                File parent = new File("build");
                if (!parent.exists() || !parent.isDirectory()) {
                    parent = new File("target");
                }

                if (!parent.exists() || !parent.isDirectory()) {
                    return false;
                } else {
                    File file = new File(parent, "reflect.json");
                    boolean enabled = !file.exists();
                    if (enabled) {
                        System.out.println("Graal Class Loading Analysis Enabled.");
                    }
                    return enabled;
                }
            }
        }
    }

    @Override
    public void onPresent(Class<?> type) {
        if (isValidType(type)) {
            classes.add(type.getName());
        }
    }

    @Override
    public void onBeanPresent(Class<?> type) {
        if (isValidType(type)) {
            beans.add(type.getName());
        }
    }

    @Override
    public void onMissing(String name) {
        classes.remove(name);
    }

    @Override
    public void close() {
        String f = System.getProperty(REFLECTION_JSON_FILE);
        File file;
        if (StringUtils.isNotEmpty(f)) {
            file = new File(f);
        } else {
            File parent = new File("build");
            if (!parent.exists() || !parent.isDirectory()) {
                parent = new File("target");
            }

            if (!parent.exists() || !parent.isDirectory()) {
                return;
            } else {
                file = new File(parent, "reflect.json");
            }
        }

        if (!file.exists()) {
            ClassLoader cls = GraalClassLoadingReporter.class.getClassLoader();

            if (!ClassUtils.isPresent(NETTY_TYPE, cls)) {
                classes.remove(NETTY_TYPE);
            }

            if (ClassUtils.isPresent("io.netty.channel.socket.nio.NioSocketChannel", cls)) {
                classes.add("io.netty.channel.socket.nio.NioSocketChannel");
            }

            if (ClassUtils.isPresent("sun.security.ssl.SSLContextImpl$TLSContext", cls)) {
                classes.add("sun.security.ssl.SSLContextImpl$TLSContext");
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

            for (String array : arrays) {
                json.add(CollectionUtils.mapOf(
                    "name", "[L" + array.substring(0, array.length() - 2) + ";",
                    "allDeclaredConstructors", true
                ));
            }

            beans.addAll(Arrays.asList(JsonError.class.getName(), "io.micronaut.http.hateos.DefaultLink"));

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


            ObjectMapper mapper = new ObjectMapper();
            ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
            try {
                System.out.println("Writing reflect.json file to destination: " + file);
                writer.writeValue(file, json);
            } catch (IOException e) {
                System.err.println("Could not write Graal reflect.json: " + e.getMessage());
            }
        }
    }

    private boolean isValidType(Class<?> type) {
        return type != null && !type.isPrimitive() && type != void.class;
    }
}
