package io.micronaut.security.ldap.group;

import io.micronaut.security.ldap.LdapConfigurationProperties;
import io.micronaut.security.ldap.context.LdapSearchResult;
import io.micronaut.security.ldap.context.LdapSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Singleton
public class DefaultLdapGroupProcessor implements LdapGroupProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultLdapGroupProcessor.class);

    private final LdapConfigurationProperties ldap;
    private final LdapSearchService searchService;

    DefaultLdapGroupProcessor(LdapConfigurationProperties ldap, LdapSearchService searchService) {
        this.ldap = ldap;
        this.searchService = searchService;
    }

    @Override
    public Set<String> getGroups(DirContext managerContext, LdapSearchResult result) throws NamingException {
        LdapConfigurationProperties.GroupProperties groupProperties = ldap.getGroup();
        Set<String> groupSet = new HashSet<>();
        if (groupProperties.isEnabled()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Group search is enabled. Searching for groups...");
            }

            List<LdapSearchResult> groupSearch = searchService.searchForGroups(managerContext, result.getDn());

            if (groupSearch.isEmpty() && LOG.isDebugEnabled()) {
                LOG.debug("No groups found!");
            }

            for (LdapSearchResult groupResult: groupSearch) {
                groupResult.getAttributes()
                        .get(groupProperties.getAttribute(), List.class)
                        .ifPresent(groups -> {
                            for (Object group: groups) {
                                processGroup(group.toString()).ifPresent(groupSet::add);
                            }
                        });

                if (LOG.isTraceEnabled()) {
                    LOG.trace("The following groups were found for [{}]: {}", result.getDn(), groupSet);
                }
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Group search is disabled.");
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Attempting to add any additional groups...");
        }
        groupSet.addAll(getAdditionalGroups(managerContext, result));
        return groupSet;
    }
}
