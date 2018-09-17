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

package io.micronaut.configuration.security.ldap.context;

/**
 * Contract to provide settings to search LDAP.
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface SearchSettings {

    /**
     * @return True if the subtree should be searched
     */
    boolean isSubtree();

    /**
     * @return The base DN to start the search
     */
    String getBase();

    /**
     * @return The search filter
     */
    String getFilter();

    /**
     * @return The search filter arguments
     */
    Object[] getArguments();

    /**
     * A null value indicates all attributes should be returned.
     *
     * @return Which attributes should be returned from the search
     */
    String[] getAttributes();

}
