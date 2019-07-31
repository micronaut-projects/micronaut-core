package io.micronaut.docs.events.factory;

// tag::class[]
public class V8Engine implements Engine {
    private final int cylinders = 8;
    private double rodLength; // <1>

    public V8Engine(double rodLength) {
        this.rodLength = rodLength;
    }

    public String start() {
        return "Starting V" + String.valueOf(getCylinders()) + " [rodLength=" + String.valueOf(getRodLength()) + "]";
    }

    public final int getCylinders() {
        return cylinders;
    }

    public double getRodLength() {
        return rodLength;
    }

    public void setRodLength(double rodLength) {
        this.rodLength = rodLength;
    }
}
// end::class[]