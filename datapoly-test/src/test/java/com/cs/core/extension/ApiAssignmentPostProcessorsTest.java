// Use of this source code is governed by a BSD-style license
package com.cs.core.extension;

import org.junit.*;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;

/**
 * Behavior of the {@link ApiAssignmentPostProcessors} composite: processor fan-out,
 * per-processor exception isolation, Spring event publication, and the dual-channel
 * (Spring bean + META-INF/services) aggregation with bean precedence.
 */
public class ApiAssignmentPostProcessorsTest {

    static final class RecordingProcessor implements ApiAssignmentPostProcessor {

        final List<ApiDebugPostContext> debugContexts = new ArrayList<>();
        final List<ApiUpdatePostContext> updateContexts = new ArrayList<>();

        @Override
        public void postDebug(ApiDebugPostContext context) {
            debugContexts.add(context);
        }

        @Override
        public void postUpdate(ApiUpdatePostContext context) {
            updateContexts.add(context);
        }
    }

    static final class ThrowingProcessor implements ApiAssignmentPostProcessor {

        @Override
        public void postDebug(ApiDebugPostContext context) {
            throw new IllegalStateException("boom-debug");
        }

        @Override
        public void postUpdate(ApiUpdatePostContext context) {
            throw new IllegalStateException("boom-update");
        }
    }

    static final class EventCollector implements ApplicationEventPublisher {

        final List<Object> events = new ArrayList<>();

        @Override
        public void publishEvent(ApplicationEvent event) {
            events.add(event);
        }

        @Override
        public void publishEvent(Object event) {
            events.add(event);
        }
    }

    @Test
    public void postDebugNotifiesProcessorsAndPublishesEvent() {
        RecordingProcessor recorder = new RecordingProcessor();
        EventCollector collector = new EventCollector();
        ApiAssignmentPostProcessors processors =
                new ApiAssignmentPostProcessors(Collections.singletonList(recorder), collector);

        ApiDebugPostContext context = ApiDebugPostContext.builder()
                .success(true).answer("rows").logs("log").elapsedMs(7L).build();
        processors.postDebug(context);

        Assert.assertEquals(Collections.singletonList(context), recorder.debugContexts);
        Assert.assertEquals(1, collector.events.size());
        ApiAssignmentDebugEvent event = (ApiAssignmentDebugEvent) collector.events.get(0);
        Assert.assertSame(context, event.getContext());
    }

    @Test
    public void postUpdateNotifiesProcessorsAndPublishesEvent() {
        RecordingProcessor recorder = new RecordingProcessor();
        EventCollector collector = new EventCollector();
        ApiAssignmentPostProcessors processors =
                new ApiAssignmentPostProcessors(Collections.singletonList(recorder), collector);

        ApiUpdatePostContext context = ApiUpdatePostContext.builder().build();
        processors.postUpdate(context);

        Assert.assertEquals(Collections.singletonList(context), recorder.updateContexts);
        Assert.assertEquals(1, collector.events.size());
        ApiAssignmentUpdateEvent event = (ApiAssignmentUpdateEvent) collector.events.get(0);
        Assert.assertSame(context, event.getContext());
    }

    @Test
    public void throwingProcessorDoesNotBreakOthersOrEvent() {
        RecordingProcessor recorder = new RecordingProcessor();
        EventCollector collector = new EventCollector();
        ApiAssignmentPostProcessors processors = new ApiAssignmentPostProcessors(
                Arrays.asList(new ThrowingProcessor(), recorder), collector);

        ApiDebugPostContext debugContext = ApiDebugPostContext.builder().success(false).errorMessage("boom").build();
        processors.postDebug(debugContext);
        ApiUpdatePostContext updateContext = ApiUpdatePostContext.builder().build();
        processors.postUpdate(updateContext);

        Assert.assertEquals(Collections.singletonList(debugContext), recorder.debugContexts);
        Assert.assertEquals(Collections.singletonList(updateContext), recorder.updateContexts);
        Assert.assertEquals(2, collector.events.size());
    }

    @Test
    public void nullPublisherAndEmptyCompositeAreSafe() {
        ApiAssignmentPostProcessors processors =
                new ApiAssignmentPostProcessors(Collections.<ApiAssignmentPostProcessor>emptyList(), null);
        processors.postDebug(ApiDebugPostContext.builder().build());
        processors.postUpdate(ApiUpdatePostContext.builder().build());
        Assert.assertTrue(processors.registered().isEmpty());

        ApiAssignmentPostProcessors.empty().postDebug(ApiDebugPostContext.builder().build());
        ApiAssignmentPostProcessors.empty().postUpdate(ApiUpdatePostContext.builder().build());
    }

    @Test
    public void aggregateMergesSpringBeansWithSpiProviders() {
        RecordingProcessor bean = new RecordingProcessor();
        ApiAssignmentPostProcessors processors =
                ApiAssignmentPostProcessors.aggregate(Collections.singletonList(bean), null);

        List<ApiAssignmentPostProcessor> registered = processors.registered();
        Assert.assertEquals(2, registered.size());
        Assert.assertSame(bean, registered.get(0));
        Assert.assertEquals(TestSpiFixtureProcessor.class, registered.get(1).getClass());
    }

    @Test
    public void aggregateDeduplicatesByClassNameKeepingSpringBean() {
        ApiAssignmentPostProcessor bean = new TestSpiFixtureProcessor();
        ApiAssignmentPostProcessors processors =
                ApiAssignmentPostProcessors.aggregate(Collections.singletonList(bean), null);

        Assert.assertEquals(1, processors.registered().size());
        Assert.assertSame(bean, processors.registered().get(0));
    }
}
