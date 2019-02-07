package io.micronaut.spring.tx;

import io.micronaut.spring.tx.annotation.Transactional;
import org.junit.Assert;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import javax.inject.Singleton;

@Transactional
@Singleton
public class TransactionalBean {

    String doSomething() {
        // should not throw
        final TransactionStatus transactionStatus = TransactionAspectSupport.currentTransactionStatus();
        Assert.assertNotNull(transactionStatus);
        return "foo";
    }
}
