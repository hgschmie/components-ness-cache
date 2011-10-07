package ness.cache2;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import io.trumpet.lifecycle.Lifecycle;
import io.trumpet.lifecycle.LifecycleStage;
import io.trumpet.lifecycle.guice.LifecycleModule;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Random;
import java.util.Set;

import ness.cache2.Cache;
import ness.cache2.CacheConfiguration;
import ness.cache2.CacheModule;
import ness.cache2.NamespacedCache;
import ness.discovery.client.DiscoveryClient;
import ness.discovery.client.ReadOnlyDiscoveryClient;
import ness.discovery.client.ServiceInformation;
import ness.discovery.client.testing.MockedDiscoveryClient;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.kaching.platform.testing.AllowDNSResolution;
import com.kaching.platform.testing.AllowNetworkAccess;
import com.kaching.platform.testing.AllowNetworkListen;
import com.thimbleware.jmemcached.CacheImpl;
import com.thimbleware.jmemcached.Key;
import com.thimbleware.jmemcached.LocalCacheElement;
import com.thimbleware.jmemcached.MemCacheDaemon;
import com.thimbleware.jmemcached.storage.CacheStorage;
import com.thimbleware.jmemcached.storage.hash.ConcurrentLinkedHashMap;
import com.thimbleware.jmemcached.storage.hash.ConcurrentLinkedHashMap.EvictionPolicy;

/**
 * @author christopher
 *
 */
@AllowDNSResolution
@AllowNetworkListen(ports = {11212, 11213, 11214})
@AllowNetworkAccess(endpoints = {"127.0.0.1:11212", "127.0.0.1:11213", "127.0.0.1:11214"})
public class NamedMemcacheTest {
    private static final long RANDOM_SEED = 1234;
    private static final int NUM_WRITES = 1000;
    private static final String NS = "shard-integration-test";

    private MemCacheDaemon<LocalCacheElement> daemon1, daemon2, daemon3;
    private ServiceInformation announce1, announce2, announce3;
    private InetSocketAddress addr1, addr2, addr3;

    private final DateTime expiry = new DateTime().plusYears(100);

    public final MemCacheDaemon<LocalCacheElement> createDaemon(int port) {
        MemCacheDaemon<LocalCacheElement> daemon = new MemCacheDaemon<LocalCacheElement>();

        CacheStorage<Key, LocalCacheElement> storage = ConcurrentLinkedHashMap.create(EvictionPolicy.FIFO, 10000, 10000000);

        daemon.setCache(new CacheImpl(storage));
        daemon.setBinary(true);
        daemon.setAddr(new InetSocketAddress("127.0.0.1", port));
        daemon.start();

        return daemon;
    }


    final CacheConfiguration configuration = new CacheConfiguration() {
        @Override
        public CacheType getCacheType() {
            return CacheType.MEMCACHE;
        }
        @Override
        public boolean isCacheSynchronous() {
            return true;
        }
        @Override
        public boolean isJmxEnabled() {
            return false;
        }
    };

    @Before
    public final void setUp() {
        addr1 = new InetSocketAddress("127.0.0.1", 11212);
        daemon1 = createDaemon(addr1.getPort());
        announce1 = ServiceInformation.forService("memcached", "1", "memcache", addr1.getHostName(), addr1.getPort());
        addr2 = new InetSocketAddress("127.0.0.1", 11213);
        daemon2 = createDaemon(addr2.getPort());
        announce2 = ServiceInformation.forService("memcached", "2", "memcache", addr2.getHostName(), addr2.getPort());
        addr3 = new InetSocketAddress("127.0.0.1", 11214);
        daemon3 = createDaemon(addr3.getPort());
        announce3 = ServiceInformation.forService("memcached", "3", "memcache", addr3.getHostName(), addr3.getPort());

        discovery.announce(announce1);
        discovery.announce(announce2);
        discovery.announce(announce3);

        Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                install (new CacheModule(configuration, null, "1", false));
                install (new CacheModule(configuration, null, "2", false));
                install (new CacheModule(configuration, null, "3", false));

                install (new LifecycleModule());

                bind (ReadOnlyDiscoveryClient.class).toInstance(discovery);
                
                requestInjection (NamedMemcacheTest.this);
            }
        });
        lifecycle.executeTo(LifecycleStage.START_STAGE);
    }

    @After
    public final void tearDown() {
        lifecycle.executeTo(LifecycleStage.STOP_STAGE);
        daemon1.stop();
        daemon2.stop();
        daemon3.stop();
    }

    private final Set<String> allKeys = Sets.newHashSet();

    private void writeLots(Cache cache) {
        Random r = new Random(RANDOM_SEED);
        NamespacedCache c = cache.withNamespace(NS);

        for (int i = 0; i < NUM_WRITES; i++) {
            byte[] data = new byte[4];
            r.nextBytes(data);
            String key = Integer.toString(r.nextInt());
            c.set(key, data, expiry);

            allKeys.add(key);
        }
    }

    private boolean verifyWrites(Cache cache) {
        Random r = new Random(RANDOM_SEED);
        NamespacedCache c = cache.withNamespace(NS);

        for (int i = 0; i < NUM_WRITES; i++) {
            byte[] data = new byte[4];
            r.nextBytes(data);
            String key = Integer.toString(r.nextInt());
            if (!Arrays.equals(data, c.get(key))) {
            	return false;
            }
        }
        
        return true;
    }

    private final DiscoveryClient discovery = MockedDiscoveryClient.builder().build();
    @Inject
    Lifecycle lifecycle;
    @Inject
    @Named("1")
    Cache cache1;
    @Inject
    @Named("2")
    Cache cache2;
    @Inject
    @Named("3")
    Cache cache3;

    @Test
    public void testSimpleReadWrite() {
    	writeLots(cache1);
    	assertTrue(verifyWrites(cache1));
    	writeLots(cache2);
    	assertTrue(verifyWrites(cache2));
    	writeLots(cache3);
    	assertTrue(verifyWrites(cache3));
    }
    
    @Test
    public void testLocalizedWrites() {
    	writeLots(cache1);
    	assertTrue(verifyWrites(cache1));
    	assertFalse(verifyWrites(cache2));
    	assertFalse(verifyWrites(cache3));
    }
}
