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

package io.micronaut.function.groovy;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.function.executor.FunctionInitializer;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class for Function scripts.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class FunctionScript extends FunctionInitializer implements PropertySource {

    private Map<String, Object> props;

    /**
     * Constuctor.
     */
    public FunctionScript() {
    }

    /**
     * Constructor.
     * @param applicationContext applicationContext
     */
    protected FunctionScript(ApplicationContext applicationContext) {
        super(applicationContext, false);
    }

    @Override
    @Internal
    public Object get(String key) {
        return resolveProps().get(key);
    }

    @Override
    public final String getName() {
        return NameUtils.hyphenate(getClass().getSimpleName());
    }

    @Override
    @Internal
    public Iterator<String> iterator() {
        return resolveProps().keySet().iterator();
    }

    /**
     * Add a property to the script.
     *
     * @param name name of the property
     * @param value value
     */
    protected void addProperty(String name, Object value) {
        resolveProps().put(name, value);
    }

    @Override
    @Internal
    protected void startThis(ApplicationContext applicationContext) {
        // no-op this, equivalent behaviour will be called from the script constructor
    }

    @Override
    @Internal
    protected void injectThis(ApplicationContext applicationContext) {
        // no-op this, equivalent behaviour will be called from the script constructor
    }

    private Map<String, Object> resolveProps() {
        if (props == null) {
            props = new LinkedHashMap<>();
        }
        return props;
    }
}
