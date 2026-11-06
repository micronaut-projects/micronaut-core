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
package io.micronaut.context.banner;

import io.micronaut.core.io.ResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Prints a banner from a resource.
 */
public class ResourceBanner implements Banner {

    /**
     * The classpath location of the text banner.
     *
     * @since 5.3.0
     */
    public static final String RESOURCE = "micronaut-banner.txt";

    private static final Logger LOG = LoggerFactory.getLogger(ResourceBanner.class);

    private final URL resource;
    private final PrintStream out;

    /**
     * Constructor.
     * @param resource The resource with the banner
     * @param out The print stream
     */
    public ResourceBanner(URL resource, PrintStream out) {
        this.resource = resource;
        this.out = out;
    }

    /**
     * Finds the text banner of the application.
     *
     * @param resourceLoader The loader of the {@link #RESOURCE} text
     * @param out The print stream
     * @return The banner, empty if the application has no text banner
     * @since 5.3.0
     */
    public static Optional<Banner> resolve(ResourceLoader resourceLoader, PrintStream out) {
        return resourceLoader.getResource(RESOURCE).map(resource -> new ResourceBanner(resource, out));
    }

    @Override
    public void print() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
            String banner = reader.lines().collect(Collectors.joining("\n"));
            out.println(banner + "\n");
        } catch (IOException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("There was an error printing the banner.");
            }
        }
    }
}
