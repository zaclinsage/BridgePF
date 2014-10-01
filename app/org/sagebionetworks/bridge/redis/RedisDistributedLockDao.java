package org.sagebionetworks.bridge.redis;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;

public class RedisDistributedLockDao implements DistributedLockDao {
    
    private static final int LOCK_EXPIRATION_IN_SECONDS = 3 * 60;
    private JedisStringOps stringOps = new JedisStringOps();
    
    private String createKey(Class<?> clazz, String identifier) {
        return String.format("lock:%s:%s", clazz.getCanonicalName(), identifier);
    }
    
    @Override
    public String createLock(Class<?> clazz, String identifier) {
        String key = createKey(clazz, identifier);
        String id = BridgeUtils.generateGuid();
        // We'd really lke a setnxex command... apparently we're not alone
        String setResult = stringOps.setnx(key, id).execute();
        if (setResult == null) {
            throw new BridgeServiceException("Lock already set.");
        }
        String expResult = stringOps.expire(key, LOCK_EXPIRATION_IN_SECONDS).execute();
        if (expResult == null) {
            throw new BridgeServiceException("Lock expiration not set.");
        }
        return id;
    }

    @Override
    public void releaseLock(Class<?> clazz, String identifier, String lockId) {
        if (lockId != null) {
            String key = createKey(clazz, identifier);
            String getResult = stringOps.get(key).execute();
            if (getResult != null && !getResult.equals(lockId)) {
                throw new BridgeServiceException("Must be lock owner to release lock.");
            }
            String delResult = stringOps.delete(key).execute();
            if (delResult == null) {
                // Try to expire it as a last ditch effort
                stringOps.expire(key, 2);
                throw new BridgeServiceException("Lock not released (attempting to expire)");
            }
        }
    }

    @Override
    public boolean isLocked(Class<?> clazz, String identifier) {
        String key = createKey(clazz, identifier);
        String getResult = stringOps.get(key).execute();
        return (getResult == null) ? false : true;
    }

}
