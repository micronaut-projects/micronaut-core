package example.api.v1;

public class HealthStatus {
    String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isUp() {
        if ( status != null && status.compareToIgnoreCase("UP") == 0) {
            return true;
        }

        return false;
    }
}
