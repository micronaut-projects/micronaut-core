package example.crema;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What {@code example.crema.runtime.RuntimeProbe}, a class loaded at run time, observed when it was initialized.
 */
public final class RuntimeProbeResults {

    public static final String INITIALIZED = "initialized";
    public static final String IMAGE_CODE_PROPERTY = "imageCodeProperty";
    public static final String IN_IMAGE_CODE = "inImageCode";
    public static final String TABLE_SERVICES = "tableServices";
    public static final String CONDITIONED_TABLE_SERVICES = "conditionedTableServices";
    public static final String SCANNED_SERVICES = "scannedServices";

    private static final Map<String, Object> VALUES = new ConcurrentHashMap<>();

    private RuntimeProbeResults() {
    }

    public static void put(String key, Object value) {
        VALUES.put(key, value);
    }

    public static Object get(String key) {
        return VALUES.get(key);
    }

    public static void clear() {
        VALUES.clear();
    }
}
