package erp.repository;

import java.util.Set;

public interface Mutexes<ID> {
    /**
     * -1:锁不存在 0:锁失败 1:锁成功
     */
    int lock(ID id, String processName);

    /**
     * 返回false那就是已创建了
     */
    boolean newAndLock(ID id, String processName);

    void unlockAll(Set<Object> ids);

    String getLockProcess(ID id);

    void removeAll(Set<Object> ids);
}
