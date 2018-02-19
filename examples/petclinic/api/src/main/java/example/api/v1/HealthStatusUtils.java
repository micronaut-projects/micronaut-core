package example.api.v1;

public class HealthStatusUtils {

    static public boolean isUp(HealthStatus healthStatus) {
        if ( healthStatus.getStatus() != null && healthStatus.getStatus().compareToIgnoreCase("UP") == 0) {
            return true;
        }
        return false;
    }
}
