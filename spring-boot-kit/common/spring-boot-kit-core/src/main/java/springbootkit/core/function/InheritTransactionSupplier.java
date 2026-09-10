package springbootkit.core.function;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @Description Supplier类支撑 spring 事务继承
 * @Date 2025/9/3
 * @Version 0.0.1
 */
public abstract class InheritTransactionSupplier<T> implements Supplier<T> {
    private final Map<Object, Object> transactionResourceMap = TransactionSynchronizationManager.getResourceMap();
    private final List<Object> history = new ArrayList<>();
    private final Thread caller = Thread.currentThread();
    @Override
    public T get() {
        if(hasTransaction()){
            try {
                bindTransaction();
                return inheritedGet();
            } finally {
                unbindTransaction();
            }
        } else {
            return inheritedGet();
        }
    }



    public abstract T inheritedGet();
    /**
     * 传播事务
     */
    private void bindTransaction() {
        for (Map.Entry<Object, Object> resource : transactionResourceMap.entrySet()) {
            //异步线程
            if(isAsyncThread()){
                TransactionSynchronizationManager.bindResource(resource.getKey(), resource.getValue());
                history.add(resource.getKey());
            }
        }
    }

    /**
     * 解绑线程事务
     */
    private void unbindTransaction() {
        history.forEach(TransactionSynchronizationManager::unbindResourceIfPossible);
        history.clear();
    }

    private boolean hasTransaction() {
        return !transactionResourceMap.isEmpty();
    }

    private boolean isAsyncThread(){
        return !Thread.currentThread().equals(caller);
    }
}
