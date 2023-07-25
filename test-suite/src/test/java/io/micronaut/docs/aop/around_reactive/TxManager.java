package io.micronaut.docs.aop.around_reactive;

import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.propagation.PropagatedContextElement;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Singleton
public class TxManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TxManager.class);

    private int txCount = 0;
    private final List<String> transactionsLog = new ArrayList<>();

    public Optional<String> findTx(ContextView contextView) {
        if (ReactorPropagation.findAllContextElements(contextView, TxPropagatedContext.class).count() > 1) {
            throw new IllegalStateException();
        }
        return ReactorPropagation.findContextElement(contextView, TxPropagatedContext.class).map(TxPropagatedContext::tx);
    }

    public List<String> getTransactionsLog() {
        return transactionsLog;
    }

    public <T> Publisher<T> inTransactionFlux(Function<String, Publisher<T>> supplier) {
        return Flux.defer(() -> {
            String newTx = newTransaction();
            LOGGER.info("Opening transaction: {}", newTx);
            transactionsLog.add("OPEN " + newTx);

            return Flux.usingWhen(Flux.just(newTx), tx -> {
                LOGGER.info("IN transaction: {}", tx);
                transactionsLog.add("IN " + tx);
                return Flux.from(supplier.apply(tx)).contextWrite(ctx -> ReactorPropagation.addContextElement(ctx, new TxPropagatedContext(tx)));
            }, tx -> {
                LOGGER.info("Commit transaction: {}", tx);
                transactionsLog.add("COMMIT " + tx);
                return Mono.just(tx);
            }, (tx, e) -> {
                LOGGER.info("Rollback transaction: {}", tx);
                transactionsLog.add("ROLLBACK " + tx);
                return Mono.just(tx);
            }, tx -> {
                LOGGER.info("Cancel transaction: {}", tx);
                transactionsLog.add("CANCEL " + tx);
                return Mono.just(tx);
            });
        });
    }

    public <T> Publisher<T> inTransactionMono(Function<String, Publisher<T>> supplier) {
        return Mono.defer(() -> {
            String newTx = newTransaction();
            LOGGER.info("Opening transaction: {}", newTx);
            transactionsLog.add("OPEN " + newTx);

            return Mono.usingWhen(Mono.just(newTx), tx -> {
                LOGGER.info("IN transaction: {}", tx);
                transactionsLog.add("IN " + tx);
                return Mono.from(supplier.apply(tx)).contextWrite(ctx -> ReactorPropagation.addContextElement(ctx, new TxPropagatedContext(newTx)));
            }, tx -> {
                LOGGER.info("Commit transaction: {}", tx);
                transactionsLog.add("COMMIT " + tx);
                return Mono.just(tx);
            }, (tx, e) -> {
                LOGGER.info("Rollback transaction: {}", tx);
                transactionsLog.add("ROLLBACK " + tx);
                return Mono.just(tx);
            }, tx -> {
                LOGGER.info("Cancel transaction: {}", tx);
                transactionsLog.add("CANCEL " + tx);
                return Mono.just(tx);
            });
        });
    }

    private String newTransaction() {
        return "TX" + ++txCount;
    }

    private record TxPropagatedContext(String tx) implements PropagatedContextElement {
    }

}
