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

import io.micronaut.core.convert.value.ConvertibleValues;

import javax.naming.directory.Attributes;

/**
 * Contains the data returned from an LDAP search.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class LdapSearchResult {

    private ConvertibleValues<Object> attributes;
    private String dn;

    /**
     * @param attributes The LDAP attributes
     * @param dn         The DN
     */
    public LdapSearchResult(Attributes attributes, String dn) {
        this.setAttributes(attributes);
        this.setDn(dn);
    }

    /**
     * @return A {@link ConvertibleValues} representation of the LDAP attributes
     */
    public ConvertibleValues<Object> getAttributes() {
        return attributes;
    }

    /**
     * @param attributes The LDAP attributes
     */
    public void setAttributes(Attributes attributes) {
        this.attributes = new AttributesConvertibleValues(attributes);
    }

    /**
     * @return The DN
     */
    public String getDn() {
        return dn;
    }

    /**
     * @param dn The DN
     */
    public void setDn(String dn) {
        this.dn = dn;
    }

}
