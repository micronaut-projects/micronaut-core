package example.crema.runtime;

import example.crema.RuntimeProbeResults;
import example.crema.ScanService;
import example.crema.TableService;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.util.NativeImageUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Loads services from a class that is loaded at run time, which Crema interprets in a native image.
 *
 * <p>The work is done by the static initializer, so that running it does not need reflection or method handles on
 * a class defined at run time. The results go to {@link RuntimeProbeResults}.</p>
 */
public class RuntimeProbe {

    static {
        ClassLoader classLoader = RuntimeProbe.class.getClassLoader();
        RuntimeProbeResults.put(RuntimeProbeResults.IMAGE_CODE_PROPERTY, String.valueOf(System.getProperty(NativeImageUtils.PROPERTY_IMAGE_CODE_KEY)));
        RuntimeProbeResults.put(RuntimeProbeResults.IN_IMAGE_CODE, Boolean.valueOf(NativeImageUtils.inImageCode()));
        RuntimeProbeResults.put(RuntimeProbeResults.TABLE_SERVICES, names(SoftServiceLoader.load(TableService.class, classLoader).iterator()));
        RuntimeProbeResults.put(RuntimeProbeResults.CONDITIONED_TABLE_SERVICES, names(SoftServiceLoader.load(TableService.class, classLoader, new NotEndingWithB()).iterator()));
        RuntimeProbeResults.put(RuntimeProbeResults.SCANNED_SERVICES, names(SoftServiceLoader.load(ScanService.class, classLoader).iterator()));
        RuntimeProbeResults.put(RuntimeProbeResults.INITIALIZED, Boolean.TRUE);
    }

    private RuntimeProbe() {
    }

    private static <S> List<String> names(Iterator<ServiceDefinition<S>> definitions) {
        List<String> names = new ArrayList<>();
        while (definitions.hasNext()) {
            ServiceDefinition<S> definition = definitions.next();
            if (definition.isPresent()) {
                names.add(definition.getName());
            }
        }
        return names;
    }
}
