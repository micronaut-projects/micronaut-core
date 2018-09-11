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

package io.micronaut.views;

import java.util.Optional;

/**
 * Holder for both Model and View.
 *
 * @author Sergio del Amo
 * @author graemerocher
 * @since 1.0
 */
public class ModelAndView {

    private String view;

    private Object model;

    /**
     * Empty constructor.
     */
    public ModelAndView() {
    }

    /**
     * Constructor.
     *
     * @param view  view name to be rendered
     * @param model Model to be rendered against the view
     */
    public ModelAndView(String view, Object model) {
        this.view = view;
        this.model = model;
    }

    /**
     * @return view name to be rendered
     */
    public Optional<String> getView() {
        return Optional.ofNullable(view);
    }

    /**
     * Sets the view to use.
     *
     * @param view the view name
     */
    public void setView(String view) {
        this.view = view;
    }

    /**
     * @return model to render
     */
    public Optional<Object> getModel() {
        return Optional.ofNullable(model);
    }

    /**
     * Sets the model to use.
     *
     * @param model model to be rendered
     */
    public void setModel(Object model) {
        this.model = model;
    }
}
