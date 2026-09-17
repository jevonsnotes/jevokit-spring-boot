package io.github.jevonsnotes.jevokit.core.function;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Supplier类支撑 spring 事务继承
 */
public abstract class InheritTransactionSupplier<T> implements Supplier<T> {
    private final Map<Object, Object> transactionResourceMap = TransactionSynchronizationManager.getResourceMap();
    private final List<Object> history = new ArrayList<>();
    private final Thread caller = Thread.currentThread();
    /**
     * Supplier 入口：调用线程有事务绑定时先把事务资源传播到执行线程，执行完恢复解绑
     *
     * @return 业务结果
     */
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



    /**
     * 业务逻辑实现（事务资源已按需绑定，等价于直接写 Supplier.get 的方法体）
     *
     * @return 业务结果
     */
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

    /**
     * 判断构造时是否捕获到事务资源绑定
     *
     * @return 有事务资源返回 true
     */
    private boolean hasTransaction() {
        return !transactionResourceMap.isEmpty();
    }

    /**
     * 判断当前执行线程是否已离开构造线程（异步场景才需要手工传播事务资源）
     *
     * @return 非构造线程返回 true
     */
    private boolean isAsyncThread(){
        return !Thread.currentThread().equals(caller);
    }
}
