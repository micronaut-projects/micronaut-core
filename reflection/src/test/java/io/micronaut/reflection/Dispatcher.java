package io.micronaut.reflection;

import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A bean with every kind of injection point, a type the processors never saw.
 */
@Singleton
public class Dispatcher extends DispatcherBase {

    final Warehouse warehouse;
    final List<Courier> couriers;
    final Courier[] courierArray;
    final Map<String, Courier> couriersByName;
    final Optional<Missing> missing;
    final String region;
    final Integer retries;
    final List<String> events = new ArrayList<>();

    @Inject
    @Named("express")
    Courier expressCourier;

    @Inject
    @Named("slow")
    Courier slowCourier;

    @Value("${dispatcher.label:default-label}")
    String label;

    @Inject
    private BeanRegistration<Warehouse> warehouseRegistration;

    private Stream<Courier> courierStream;
    private String zone;
    private boolean started;
    private boolean stopped;

    @Inject
    public Dispatcher(Warehouse warehouse,
                      List<Courier> couriers,
                      Courier[] courierArray,
                      Map<String, Courier> couriersByName,
                      Optional<Missing> missing,
                      @Value("${dispatcher.region}") String region,
                      @Property(name = "dispatcher.retries") Integer retries) {
        this.warehouse = warehouse;
        this.couriers = couriers;
        this.courierArray = courierArray;
        this.couriersByName = couriersByName;
        this.missing = missing;
        this.region = region;
        this.retries = retries;
    }

    /**
     * Not selected: a constructor without {@code @Inject} loses to the annotated one.
     */
    public Dispatcher(Warehouse warehouse) {
        this(warehouse, List.of(), new Courier[0], Map.of(), Optional.empty(), "none", 0);
    }

    @Inject
    void setCourierStream(Stream<Courier> courierStream) {
        this.courierStream = courierStream;
    }

    @Value("${dispatcher.zone:west}")
    public void setZone(String zone) {
        this.zone = zone;
    }

    @PostConstruct
    void start() {
        started = true;
        events.add("start");
    }

    @PreDestroy
    void stop() {
        stopped = true;
        events.add("stop");
    }

    @Executable
    public String dispatch(String parcel) {
        return warehouse.getName() + ":" + parcel;
    }

    public String notExecutable() {
        return "hidden";
    }

    public Stream<Courier> getCourierStream() {
        return courierStream;
    }

    public String getZone() {
        return zone;
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isStopped() {
        return stopped;
    }

    public BeanRegistration<Warehouse> getWarehouseRegistration() {
        return warehouseRegistration;
    }

    /**
     * A type no bean implements.
     */
    public interface Missing {
    }
}
