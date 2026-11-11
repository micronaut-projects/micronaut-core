package io.micronaut.context.env

import spock.lang.Specification

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * What a call of each public method of the environment does when another thread makes it while a
 * refresh is active, see the Javadoc of {@code DefaultEnvironment#activeRefresh}:
 * <ul>
 * <li>A start, stop or refresh is queued, and the refresh carries it out.</li>
 * <li>A change is queued whole once the calling thread has a call queued, and the refresh carries
 * it out in its turn, as the thread would have made it once that call was done.</li>
 * <li>A lookup takes effect at once.</li>
 * </ul>
 * The methods are all the public ones an environment has, also those it inherits from interfaces
 * such as the property resolver and the conversion service. A public method added later, there or
 * here, fails this spec until it is placed in one of the lists. If it changes
 * the environment, it is to be queued as the other changes are, and given a case in
 * {@link CallsDuringRefreshScenarioSpec}.
 */
class EnvironmentMethodsSpec extends Specification {

    static final Set<String> STARTS_STOPS_AND_REFRESHES = ['start', 'stop', 'refresh', 'refreshAndDiff'] as Set

    static final Set<String> CHANGES = ['addPropertySource', 'removePropertySource', 'addPackage',
                                        'addConfigurationIncludes', 'addConfigurationExcludes', 'addConverter', 'close'] as Set

    static final Set<String> LOOKUPS = ['isPresent', 'getPlaceholderResolver', 'scan', 'getClassLoader', 'isActive',
                                       'getPackages', 'getActiveNames', 'getPropertySources', 'isRunning', 'convert',
                                       'canConvert', 'getMutableConversionService', 'getResourceAsStream', 'getResource',
                                       'getResources', 'supportsPrefix', 'forBase', 'getPropertyEntry',
                                       'getPropertySourceLoaders', 'containsProperty', 'containsProperties',
                                       'getPropertyEntries', 'getPropertyPathMatches', 'getProperties', 'getProperty',
                                       'getAllProperties', 'getRequiredProperty', 'get', 'convertRequired'] as Set

    void "every public method of the environment is a start, stop or refresh, a change, or a lookup"() {
        when: "every public method an environment has, inherited ones included"
        Set<String> names = DefaultEnvironment.methods
                .findAll { Method method -> method.declaringClass != Object && !Modifier.isStatic(method.modifiers) && !method.synthetic && !method.bridge }
                .collect { Method method -> method.name } as Set

        then: "none is left out"
        names - STARTS_STOPS_AND_REFRESHES - CHANGES - LOOKUPS == [] as Set

        and: "none is in two lists"
        STARTS_STOPS_AND_REFRESHES.intersect(CHANGES).isEmpty()
        STARTS_STOPS_AND_REFRESHES.intersect(LOOKUPS).isEmpty()
        CHANGES.intersect(LOOKUPS).isEmpty()
    }
}
