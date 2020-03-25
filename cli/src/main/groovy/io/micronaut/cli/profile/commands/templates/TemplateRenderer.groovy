/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.cli.profile.commands.templates

import groovy.transform.CompileDynamic
import io.micronaut.cli.codegen.model.Model
import io.micronaut.cli.io.support.Resource

/**
 * API for locating and rendering templates in the code generation layer
 *
 * @author Graeme Rocher
 * @since 1.0
 */
interface TemplateRenderer {

    /**
     * Render with the given named arguments
     *
     * @param namedArguments The named arguments are 'template', 'destination' and 'model'
     */
    @CompileDynamic
    void render(Map<String, Object> namedArguments)
    /**
     * Render the given template to the give destination for the given model
     *
     * @param template The contents template
     * @param destination The destination
     * @param model The model
     */
    void render(CharSequence template, File destination, Model model)

    /**
     * Render the given template to the given destination
     *
     * @param template The contents of the template
     * @param destination The destination
     * @param model The model
     */
    void render(CharSequence template, File destination)

    /**
     * Render the given template to the given destination
     *
     * @param template The contents of the template
     * @param destination The destination
     * @param model The model
     */
    void render(CharSequence template, File destination, Map model)
    /**
     * Render the given template to the given destination
     *
     * @param template The contents of the template
     * @param destination The destination
     * @param model The model
     */
    void render(CharSequence template, File destination, Map model, boolean overwrite)

    /**
     * Render the given template to the give destination for the given model
     *
     * @param template The template
     * @param destination The destination
     * @param model The model
     */
    void render(File template, File destination, Model model)

    /**
     * Render the given template to the given destination
     *
     * @param template The template
     * @param destination The destination
     * @param model The model
     */
    void render(File template, File destination)

    /**
     * Render the given template to the given destination
     *
     * @param template The template
     * @param destination The destination
     * @param model The model
     */
    void render(File template, File destination, Map model)

    /**
     * Render the given template to the given destination
     *
     * @param template The template
     * @param destination The destination
     * @param model The model
     */
    void render(File template, File destination, Map model, boolean overwrite)

    /**
     * Render the given template to the give destination for the given model
     *
     * @param template The contents template
     * @param destination The destination
     * @param model The model
     */
    void render(Resource template, File destination, Model model)

    /**
     * Render the given template to the give destination for the given model
     *
     * @param template The contents template
     * @param destination The destination
     * @param model The model
     */
    void render(Resource template, File destination, Model model, boolean overwrite)

    /**
     * Render the given template to the given destination
     *
     * @param template The template
     * @param destination The destination
     * @param model The model
     */
    void render(Resource template, File destination)

    /**
     * Render the given template to the given destination
     *
     * @param template The template
     * @param destination The destination
     * @param model The model
     */
    void render(Resource template, File destination, Map model)

    /**
     * Render the given template to the given destination
     *
     * @param template The template
     * @param destination The destination
     * @param model The model
     */
    void render(Resource template, File destination, Map model, boolean overwrite)

    /**
     * Find templates matching the given pattern
     *
     * @param pattern The pattern
     * @return The available templates
     */
    Iterable<Resource> templates(String pattern)

    /**
     * Find a template at the given location
     *
     * @param location The location
     * @return The resource or null if it doesn't exist
     */
    Resource template(Object location)
}