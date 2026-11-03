package com.helianhealth.iflow.core.lock;

import java.util.concurrent.TimeUnit;

/** 可替换的可重入租约锁；同一锁 ID 在租约有效期内由一个线程持有，重入不延长租约。 */
public interface Lock {

    /** 阻塞直到取得带租约的锁，中断不取消等待，并保留中断状态。 */
    void acquireLock(String lockId, long leaseTime, TimeUnit unit);

    /** 在指定时间内尝试取得带租约的锁；超时或中断时返回 false，中断时保留中断状态。 */
    boolean acquireLock(String lockId, long timeToTry, long leaseTime, TimeUnit unit);

    /** 释放当前线程持有的一次锁；未持有或租约已失效时不执行操作。 */
    void releaseLock(String lockId);
}
