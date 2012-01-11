package ness.cache2;

import static org.junit.Assert.assertEquals;

import java.net.InetSocketAddress;

import net.spy.memcached.BinaryConnectionFactory;
import net.spy.memcached.DefaultConnectionFactory;
import net.spy.memcached.FailureMode;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.compat.log.Log4JLogger;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.thimbleware.jmemcached.CacheImpl;
import com.thimbleware.jmemcached.Key;
import com.thimbleware.jmemcached.LocalCacheElement;
import com.thimbleware.jmemcached.MemCacheDaemon;
import com.thimbleware.jmemcached.storage.CacheStorage;
import com.thimbleware.jmemcached.storage.hash.ConcurrentLinkedHashMap;
import com.thimbleware.jmemcached.storage.hash.ConcurrentLinkedHashMap.EvictionPolicy;

@Ignore // Filed as http://code.google.com/p/spymemcached/issues/detail?id=189
public class SpyMemcachedRuntimeExceptionTest {

    private MemCacheDaemon<LocalCacheElement> daemon;
    private MemcachedClient client;

    @Before
    public void setUpLogging()
    {
        System.setProperty("net.spy.log.LoggerImpl", Log4JLogger.class.getName());
    }

    private InetSocketAddress setUp(boolean binary) {
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", 11212);

        daemon = new MemCacheDaemon<LocalCacheElement>();

        CacheStorage<Key, LocalCacheElement> storage = ConcurrentLinkedHashMap.create(EvictionPolicy.FIFO, 10000, 10000000);

        daemon.setCache(new CacheImpl(storage));
        daemon.setBinary(binary);
        daemon.setAddr(new InetSocketAddress("127.0.0.1", addr.getPort()));
        daemon.start();
        return addr;
    }

    @Test
    public void testCrashedCacheDefaultStrict() throws Exception {
        InetSocketAddress addr = setUp(false);

        client = new MemcachedClient(new DefaultConnectionFactory() {
            @Override
            public FailureMode getFailureMode() {
                return FailureMode.Retry;
            }
        }, Lists.newArrayList(addr));

        runCrashTest(false);
    }

    @Test
    public void testCrashedCacheDefaultLoose() throws Exception {
        InetSocketAddress addr = setUp(false);

        client = new MemcachedClient(new DefaultConnectionFactory() {
            @Override
            public FailureMode getFailureMode() {
                return FailureMode.Retry;
            }
        }, Lists.newArrayList(addr));

        runCrashTest(true);
    }

    @Test
    public void testCrashedCacheBinary() throws Exception {
        InetSocketAddress addr = setUp(true);

        client = new MemcachedClient(new BinaryConnectionFactory() {
            @Override
            public FailureMode getFailureMode() {
                return FailureMode.Retry;
            }
        }, Lists.newArrayList(addr));

        runCrashTest(false);
    }

    private void runCrashTest(boolean giveGracePeriod) {
        client.set("a", 100, "b");
        assertEquals("b", client.get("a"));

        daemon.stop();
        daemon.start();

        if (giveGracePeriod) {
            try {
                client.get("a");
            } catch (Exception e) {
                // ignore to let the connection fail
            }
        }

        client.set("a", 100, "b");

        assertEquals("b", client.get("a"));
    }

    @After
    public final void tearDown() {
        client.shutdown();
        daemon.stop();
    }
}
