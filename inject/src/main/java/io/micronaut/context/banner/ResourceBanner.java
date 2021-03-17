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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Prints a banner from a resource.
 */
public class ResourceBanner implements Banner {

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
