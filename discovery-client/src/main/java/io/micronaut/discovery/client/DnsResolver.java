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
package io.micronaut.discovery.client;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * DnsResolver implementation.
 *
 * Forked from https://raw.githubusercontent.com/Netflix/eureka/master/eureka-client/src/main/java/com/netflix/discovery/endpoint/DnsResolver.java
 *
 * @author Tomasz Bak
 */
final class DnsResolver {

    private static final String DNS_PROVIDER_URL = "dns:";
    private static final String DNS_NAMING_FACTORY = "com.sun.jndi.dns.DnsContextFactory";
    private static final String JAVA_NAMING_FACTORY_INITIAL = "java.naming.factory.initial";
    private static final String JAVA_NAMING_PROVIDER_URL = "java.naming.provider.url";

    private static final String TXT_RECORD_TYPE = "TXT";

    private static final DirContext DIR_CONTEXT = getDirContext();

    private DnsResolver() {
    }

    /**
     * Looks up the DNS name provided in the JNDI context.
     *
     * @param discoveryDnsName The discovery DNS name
     * @throws NamingException when the record cannot be found
     * @return A set of cname records
     */
    static Set<String> getCNamesFromTxtRecord(String discoveryDnsName) throws NamingException {
        Attributes attrs = DIR_CONTEXT.getAttributes(discoveryDnsName, new String[]{TXT_RECORD_TYPE});
        Attribute attr = attrs.get(TXT_RECORD_TYPE);
        String txtRecord = null;
        if (attr != null) {
            txtRecord = attr.get().toString();

            /**
             * compatible splited txt record of "host1 host2 host3" but not "host1" "host2" "host3".
             * some dns service provider support txt value only format "host1 host2 host3"
             */
            if (txtRecord.startsWith("\"") && txtRecord.endsWith("\"")) {
                txtRecord = txtRecord.substring(1, txtRecord.length() - 1);
            }
        }

        Set<String> cnamesSet = new TreeSet<>();
        if (txtRecord == null || txtRecord.trim().isEmpty()) {
            return cnamesSet;
        }
        String[] cnames = txtRecord.split(" ");
        Collections.addAll(cnamesSet, cnames);
        return cnamesSet;
    }

    /**
     * Load up the DNS JNDI context provider.
     */
    private static DirContext getDirContext() {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(JAVA_NAMING_FACTORY_INITIAL, DNS_NAMING_FACTORY);
        env.put(JAVA_NAMING_PROVIDER_URL, DNS_PROVIDER_URL);

        try {
            return new InitialDirContext(env);
        } catch (Throwable e) {
            throw new RuntimeException("Cannot get dir context for some reason", e);
        }
    }

}
