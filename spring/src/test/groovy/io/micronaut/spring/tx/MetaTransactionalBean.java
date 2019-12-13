package io.micronaut.spring.tx;

import org.junit.Assert;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.TransactionAspectSupport;


@MetaAnnotation
public class MetaTransactionalBean {

    public String doSomething() {
        // should not throw
        final TransactionStatus transactionStatus = TransactionAspectSupport.currentTransactionStatus();
        Assert.assertNotNull(transactionStatus);
        return "foo";
    }
}
