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

package io.micronaut.openapi.javadoc;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * A parsed javadoc description.
 *
 * @author graemerocher
 * @since 1.0
 */
public class JavadocDescription {

    private String methodDescription;
    private Map<CharSequence, CharSequence> parameters = new HashMap<>(4);
    private String returnDescription;

    /**
     * @return The description
     */
    @Nullable public String getMethodDescription() {
        return methodDescription;
    }

    /**
     * Sets the method description.
     *
     * @param methodDescription The method description
     */
    public void setMethodDescription(String methodDescription) {
        this.methodDescription = methodDescription;
    }

    /**
     * @return The parameter descriptions
     */
    public Map<CharSequence, CharSequence> getParameters() {
        return parameters;
    }

    /**
     * The return description.
     * @return The return description
     */
    @Nullable public String getReturnDescription() {
        return returnDescription;
    }

    /**
     * Sets the return description.
     *
     * @param returnDescription The return description.
     */
    public void setReturnDescription(String returnDescription) {
        this.returnDescription = returnDescription;
    }
}
