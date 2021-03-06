/**
 * Copyright (C) 2012 Ness Computing, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nesscomputing.cache;

import java.util.Map;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.nesscomputing.lifecycle.Lifecycle;
import com.nesscomputing.lifecycle.LifecycleListener;
import com.nesscomputing.lifecycle.LifecycleStage;
import com.nesscomputing.logging.Log;

import org.weakref.jmx.MBeanExporter;

/**
 * Manage various {@link CacheStatistics} beans exported via JMX.  Exports them on first access,
 * and unexports them on lifecycle shutdown.
 */
@Singleton
@ThreadSafe
class JmxCacheStatisticsManager implements CacheStatisticsManager {
    private static final Log LOG = Log.findLog();
    private Lifecycle lifecycle = null;
    private MBeanExporter exporter = null;
    private final boolean jmxEnabled;
    @GuardedBy("this")
    private final Map<String, CacheStatistics> statistics = Maps.newHashMap();

    @Inject
    JmxCacheStatisticsManager(final CacheConfiguration config) {
        this.jmxEnabled = config.isJmxEnabled();
    }

    @Inject(optional=true)
    synchronized void injectOptionalDependencies(final Lifecycle lifecycle, final MBeanExporter exporter)
    {
        this.lifecycle = lifecycle;
        this.exporter = exporter;
    }

    @Override
    public synchronized Map<String, CacheStatistics> getCacheStatistics() {
        return ImmutableMap.copyOf(statistics);
    }

    @Override
    public synchronized CacheStatistics getCacheStatistics(String namespace) {
        CacheStatistics result = statistics.get(namespace);
        if (result == null) {
            result = new CacheStatistics(namespace);

            String jmxSafeNamespace = jmxSafe(namespace);
            LOG.debug("Initializing statistics for new cache namespace %s", namespace);

            if (!jmxSafeNamespace.equals(namespace)) {
                LOG.debug("Using jmx-safe version %s", jmxSafeNamespace);
            }

            if (jmxEnabled && exporter != null) {
                final String objectName = "ness.cache:namespace=" + jmxSafeNamespace;
                exporter.export(objectName, result);
                if (lifecycle != null) {
                    lifecycle.addListener(LifecycleStage.STOP_STAGE, new LifecycleListener() {
                        @Override
                        public void onStage(LifecycleStage lifecycleStage) {
                            exporter.unexport(objectName);
                        }
                    });
                }
            }

            statistics.put(namespace, result);
        }
        return result;
    }


    /**
     * Replace keys forbidden in a JMX key, with _.
     * @param input String, probably namespace
     * @return JMX-happy string
     */
    private String jmxSafe(String input) {
        if (input == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch(c){
                case ',':
                case '=':
                case ':':
                case '*':
                case '?':
                   sb.append('_');
                   break;
                default:
                   sb.append(c);
            }
        }
        return sb.toString();
    }
}
