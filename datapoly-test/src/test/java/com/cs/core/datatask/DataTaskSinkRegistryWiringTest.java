// Use of this source code is governed by a BSD-style license
package com.cs.core.datatask;

import com.cs.common.datatask.DataTaskSink;
import com.cs.common.datatask.SinkRequest;
import com.cs.common.datatask.SinkSession;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;

public class DataTaskSinkRegistryWiringTest {

    private AnnotationConfigApplicationContext context;

    @After
    public void tearDown() {
        if (null != context) {
            context.close();
        }
    }

    // ------------------------------------------------------------------ cases

    @Test
    public void registryBootsWithoutAnySinkBeans() {
        context = new AnnotationConfigApplicationContext(DataTaskSinkRegistry.class);

        DataTaskSinkRegistry registry = context.getBean(DataTaskSinkRegistry.class);
        Assert.assertTrue(registry.knownTypes().isEmpty());
        Assert.assertFalse(registry.get("csv").isPresent());
    }

    @Test
    public void springBeanSinkIsDiscoveredAndResolvable() {
        context = new AnnotationConfigApplicationContext(DataTaskSinkRegistry.class, OneSinkConfig.class);

        DataTaskSinkRegistry registry = context.getBean(DataTaskSinkRegistry.class);
        Assert.assertEquals(Collections.singleton("csv"), registry.knownTypes());
        DataTaskSink sink = registry.get("csv").orElse(null);
        Assert.assertNotNull(sink);
        Assert.assertSame(context.getBean("csvSink"), sink);
    }

    @Test
    public void nullListIsToleratedAndLaterRegistrationOverridesDuplicateType() {
        DataTaskSinkRegistry registry = new DataTaskSinkRegistry(null);
        Assert.assertTrue(registry.knownTypes().isEmpty());

        DataTaskSink first = new StubSink("dup");
        DataTaskSink second = new StubSink("dup");
        registry = new DataTaskSinkRegistry(Arrays.asList(first, second));
        Assert.assertSame(second, registry.get("dup").orElse(null));
    }

    // ------------------------------------------------------------------ fixtures

    @Configuration
    static class OneSinkConfig {

        @Bean
        DataTaskSink csvSink() {
            return new StubSink("csv");
        }
    }

    static class StubSink implements DataTaskSink {

        private final String type;

        StubSink(String type) {
            this.type = type;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public SinkSession openSession(SinkRequest request) {
            throw new UnsupportedOperationException("stub");
        }
    }
}
