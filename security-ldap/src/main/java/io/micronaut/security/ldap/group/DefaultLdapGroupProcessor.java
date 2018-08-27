package io.micronaut.security.ldap.group;

import io.micronaut.security.ldap.LdapConfigurationProperties;
import io.micronaut.security.ldap.context.LdapSearchResult;
import io.micronaut.security.ldap.context.LdapSearchService;
import io.micronaut.security.ldap.context.SearchProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.naming.NamingException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Singleton
public class DefaultLdapGroupProcessor implements LdapGroupProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultLdapGroupProcessor.class);

    @Override
    public Set<String> process(String attribute, LdapSearchResult result, SearchProvider groupResults) throws NamingException {
        Set<String> groupSet = new HashSet<>();

        List<LdapSearchResult> groupSearch = groupResults.get();

        if (groupSearch.isEmpty() && LOG.isDebugEnabled()) {
            LOG.debug("No groups found!");
        }

        for (LdapSearchResult groupResult: groupSearch) {
            groupResult.getAttributes()
                    .get(attribute, List.class)
                    .ifPresent(groups -> {
                        for (Object group: groups) {
                            processGroup(group.toString()).ifPresent(groupSet::add);
                        }
                    });

            if (LOG.isTraceEnabled()) {
                LOG.trace("The following groups were found for [{}]: {}", result.getDn(), groupSet);
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Attempting to add any additional groups...");
        }

        groupSet.addAll(getAdditionalGroups(result));

        return groupSet;
    }
}
