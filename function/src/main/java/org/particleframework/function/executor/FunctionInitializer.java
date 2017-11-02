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
package org.particleframework.function.executor;

import org.particleframework.context.ApplicationContext;

import java.io.Closeable;
import java.io.IOException;

/**
 * A super class that can be used to initialize a function
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class FunctionInitializer extends AbstractExecutor implements Closeable, AutoCloseable {

    protected final ApplicationContext applicationContext;

    @SuppressWarnings("unchecked")
    public FunctionInitializer() {
        this.applicationContext = buildApplicationContext(null);
        startEnvironment(applicationContext);
        applicationContext.inject(this);
    }

    @Override
    public void close() throws IOException {
        if(applicationContext != null) {
            applicationContext.close();
        }
    }
}
