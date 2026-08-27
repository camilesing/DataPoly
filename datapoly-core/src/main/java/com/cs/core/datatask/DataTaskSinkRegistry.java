// Use of this source code is governed by a BSD-style license
package com.cs.core.datatask;

import com.cs.common.datatask.DataTaskSink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Resolution point for delivery providers. Providers registered as Spring beans take
 * precedence over ones found through the JDK ServiceLoader mechanism
 * ({@code META-INF/services/com.cs.common.datatask.DataTaskSink}) — extension jars can
 * plug in without any Spring coupling. The list may be {@code null} (or empty) when the
 * application ships no sink beans, which is the default for this repository.
 */
@Slf4j
@Component
public class DataTaskSinkRegistry {

    private final Map<String, DataTaskSink> sinks = new LinkedHashMap<>();

    public DataTaskSinkRegistry(@Nullable List<DataTaskSink> registered) {
        try {
            List<DataTaskSink> spi = new ArrayList<>();
            for (DataTaskSink sink : ServiceLoader.load(DataTaskSink.class, DataTaskSink.class.getClassLoader())) {
                spi.add(sink);
            }
            registerAll(spi);
        } catch (Throwable t) {
            log.warn("Failed to discover DataTaskSink providers via ServiceLoader: {}", t.getMessage());
        }
        registerAll(registered);
    }

    private void registerAll(List<DataTaskSink> candidates) {
        if (null == candidates) {
            return;
        }
        for (DataTaskSink sink : candidates) {
            String type = sink.type();
            if (null == type || type.trim().isEmpty()) {
                log.warn("Ignore DataTaskSink without registration type: {}", sink.getClass().getName());
                continue;
            }
            DataTaskSink previous = sinks.put(type.trim(), sink);
            if (null != previous) {
                log.warn("Duplicate DataTaskSink type [{}] from {}, overridden by {}",
                        type, previous.getClass().getName(), sink.getClass().getName());
            }
        }
    }

    public Optional<DataTaskSink> get(String type) {
        return Optional.ofNullable(null == type ? null : sinks.get(type.trim()));
    }

    public Set<String> knownTypes() {
        return Collections.unmodifiableSet(sinks.keySet());
    }
}
