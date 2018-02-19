package example.api.v1;

import javax.validation.constraints.NotBlank;

/**
 * @author sdelamo
 * @since 1.0
 */
public class HealthStatus {
    private String status;

    public HealthStatus(String status) {
        this.status = status;
    }

    protected HealthStatus() {
    }

    @NotBlank
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
