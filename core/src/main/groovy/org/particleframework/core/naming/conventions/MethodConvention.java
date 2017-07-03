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
package org.particleframework.core.naming.conventions;

import java.util.Locale;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public enum MethodConvention {

    /**
     * The index method of controllers
     */
    INDEX,

    /**
     * The show method of controllers
     */
    SHOW,

    /**
     * The show method of controllers
     */
    SAVE,

    /**
     * The default update method of controllers
     */
    UPDATE,

    /**
     * The default delete method of controllers
     */
    DELETE,

    /**
     * The default options method of controllers
     */
    OPTIONS,

    /**
     * The default head method of controllers
     */
    HEAD;

    private final String lowerCase;

    MethodConvention() {
        this.lowerCase = name().toLowerCase(Locale.ENGLISH);
    }

    public String lowerCaseName() {
        return this.lowerCase;
    }
}
