// Use of this source code is governed by a BSD-style license
package com.cs.manager.controller;

import com.cs.common.dto.DateCount;
import com.cs.common.dto.HourCount;
import com.cs.common.dto.NameCount;
import com.cs.common.dto.PageResult;
import com.cs.common.dto.ResultEntity;
import com.cs.core.datatask.DataTaskTestSupport;
import com.cs.core.dto.ApiAccessLogBasicResponse;
import com.cs.core.service.OverviewService;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Every overview endpoint is a thin delegation: the service result is wrapped in a
 * {@link ResultEntity} (or returned as the raw {@link PageResult}) with arguments passed through.
 */
public class OverviewControllerTest {

    private StubOverviewService service;
    private OverviewController controller;

    @Before
    public void setUp() {
        service = new StubOverviewService();
        controller = new OverviewController();
        DataTaskTestSupport.setField(controller, "overviewService", service);
    }

    @Test
    public void countDelegates() {
        Map<String, Integer> counters = Collections.singletonMap("totalCount", 3);
        service.countResult = counters;
        ResultEntity result = controller.count();
        assertEquals(Integer.valueOf(0), result.getCode());
        assertSame(counters, result.getData());
    }

    @Test
    public void trendDelegatesDays() {
        List<DateCount> trend = Collections.singletonList(DateCount.builder().ofDate("2026-11-01").total(5L).build());
        service.trendResult = trend;
        ResultEntity<List<DateCount>> result = controller.trend(7);
        assertEquals(Integer.valueOf(7), service.trendDays);
        assertSame(trend, result.getData());
    }

    @Test
    public void httpStatusDelegatesDays() {
        List<NameCount> ratio = Collections.singletonList(NameCount.builder().name("HTTP(200)").count(9L).build());
        service.httpStatusResult = ratio;
        ResultEntity<List<NameCount>> result = controller.httpStatus(30);
        assertEquals(Integer.valueOf(30), service.httpStatusDays);
        assertSame(ratio, result.getData());
    }

    @Test
    public void topPathDelegatesDaysAndLimit() {
        List<NameCount> top = Collections.singletonList(NameCount.builder().name("/api/x").count(2L).build());
        service.topPathResult = top;
        ResultEntity<List<NameCount>> result = controller.topPath(7, 10);
        assertEquals(Integer.valueOf(7), service.topPathDays);
        assertEquals(Integer.valueOf(10), service.topPathN);
        assertSame(top, result.getData());
    }

    @Test
    public void topAddrDelegatesDaysAndLimit() {
        List<NameCount> top = Collections.singletonList(NameCount.builder().name("10.0.0.1").count(4L).build());
        service.topAddrResult = top;
        ResultEntity<List<NameCount>> result = controller.topAddr(14, 5);
        assertEquals(Integer.valueOf(14), service.topAddrDays);
        assertEquals(Integer.valueOf(5), service.topAddrN);
        assertSame(top, result.getData());
    }

    @Test
    public void topClientDelegatesDaysAndLimit() {
        List<NameCount> top = Collections.singletonList(NameCount.builder().name("app").count(6L).build());
        service.topClientResult = top;
        ResultEntity<List<NameCount>> result = controller.topClient(3, 2);
        assertEquals(Integer.valueOf(3), service.topClientDays);
        assertEquals(Integer.valueOf(2), service.topClientN);
        assertSame(top, result.getData());
    }

    @Test
    public void callLogsPassEveryFilterThrough() {
        PageResult<ApiAccessLogBasicResponse> page = new PageResult<>();
        service.callLogsResult = page;
        assertSame(page, controller.callLogs(11L, 2, 20, 500, "2026-11-01 00:00:00", "2026-11-02 00:00:00"));
        assertEquals(Long.valueOf(11L), service.callLogsApiId);
        assertEquals(Integer.valueOf(2), service.callLogsPage);
        assertEquals(Integer.valueOf(20), service.callLogsSize);
        assertEquals(Integer.valueOf(500), service.callLogsStatusCode);
        assertEquals("2026-11-01 00:00:00", service.callLogsStartTime);
        assertEquals("2026-11-02 00:00:00", service.callLogsEndTime);
    }

    @Test
    public void callLogsOptionalFiltersDefaultToNull() {
        controller.callLogs(1L, 1, 10, null, null, null);
        assertNull(service.callLogsStatusCode);
        assertNull(service.callLogsStartTime);
        assertNull(service.callLogsEndTime);
    }

    @Test
    public void ratioEndpointsDelegate() {
        List<NameCount> one = Collections.singletonList(NameCount.builder().name("MYSQL").count(1L).build());
        service.datasourceTypeRatioResult = one;
        assertSame(one, controller.datasourceTypeRatio().getData());

        List<NameCount> two = Collections.singletonList(NameCount.builder().name("SQL").count(2L).build());
        service.engineRatioResult = two;
        assertSame(two, controller.engineRatio().getData());

        List<NameCount> three = Collections.singletonList(NameCount.builder().name("ds").count(3L).build());
        service.datasourceApiCountResult = three;
        assertSame(three, controller.datasourceApiCount().getData());

        List<NameCount> four = Collections.singletonList(NameCount.builder().name("GET").count(4L).build());
        service.methodRatioResult = four;
        assertSame(four, controller.methodRatio().getData());

        List<NameCount> five = Collections.singletonList(NameCount.builder().name("mod").count(5L).build());
        service.moduleApiCountResult = five;
        assertSame(five, controller.moduleApiCount().getData());
    }

    @Test
    public void apiTrendEndpointsDelegateApiId() {
        List<NameCount> status = Collections.singletonList(NameCount.builder().name("HTTP(200)").count(7L).build());
        service.apiStatusRatioResult = status;
        ResultEntity<List<NameCount>> statusResult = controller.apiStatusRatio(8L, 9);
        assertEquals(Long.valueOf(8L), service.apiStatusRatioApiId);
        assertEquals(Integer.valueOf(9), service.apiStatusRatioDays);
        assertSame(status, statusResult.getData());

        List<DateCount> daily = Collections.singletonList(DateCount.builder().ofDate("2026-11-15").total(1L).build());
        service.apiDailyTrendResult = daily;
        ResultEntity<List<DateCount>> dailyResult = controller.apiDailyTrend(8L, 9);
        assertEquals(Long.valueOf(8L), service.apiDailyTrendApiId);
        assertEquals(Integer.valueOf(9), service.apiDailyTrendDays);
        assertSame(daily, dailyResult.getData());

        List<HourCount> hourly = Collections.singletonList(HourCount.builder().hour(10).count(12L).build());
        service.apiHourlyTrendResult = hourly;
        ResultEntity<List<HourCount>> hourlyResult = controller.apiHourlyTrend(8L, "2026-11-15");
        assertEquals(Long.valueOf(8L), service.apiHourlyTrendApiId);
        assertEquals("2026-11-15", service.apiHourlyTrendDate);
        assertSame(hourly, hourlyResult.getData());
    }

    private static class StubOverviewService extends OverviewService {
        Map<String, Integer> countResult = Collections.emptyMap();
        List<DateCount> trendResult = Collections.emptyList();
        List<NameCount> httpStatusResult = Collections.emptyList();
        List<NameCount> topPathResult = Collections.emptyList();
        List<NameCount> topAddrResult = Collections.emptyList();
        List<NameCount> topClientResult = Collections.emptyList();
        PageResult<ApiAccessLogBasicResponse> callLogsResult = new PageResult<>();
        List<NameCount> datasourceTypeRatioResult = Collections.emptyList();
        List<NameCount> engineRatioResult = Collections.emptyList();
        List<NameCount> datasourceApiCountResult = Collections.emptyList();
        List<NameCount> methodRatioResult = Collections.emptyList();
        List<NameCount> moduleApiCountResult = Collections.emptyList();
        List<NameCount> apiStatusRatioResult = Collections.emptyList();
        List<DateCount> apiDailyTrendResult = Collections.emptyList();
        List<HourCount> apiHourlyTrendResult = Collections.emptyList();

        Integer trendDays;
        Integer httpStatusDays;
        Integer topPathDays;
        Integer topPathN;
        Integer topAddrDays;
        Integer topAddrN;
        Integer topClientDays;
        Integer topClientN;
        Long callLogsApiId;
        Integer callLogsPage;
        Integer callLogsSize;
        Integer callLogsStatusCode;
        String callLogsStartTime;
        String callLogsEndTime;
        Long apiStatusRatioApiId;
        Integer apiStatusRatioDays;
        Long apiDailyTrendApiId;
        Integer apiDailyTrendDays;
        Long apiHourlyTrendApiId;
        String apiHourlyTrendDate;

        @Override
        public Map<String, Integer> count() {
            return countResult;
        }

        @Override
        public List<DateCount> trend(Integer days) {
            trendDays = days;
            return trendResult;
        }

        @Override
        public List<NameCount> httpStatus(Integer days) {
            httpStatusDays = days;
            return httpStatusResult;
        }

        @Override
        public List<NameCount> topPath(Integer days, Integer n) {
            topPathDays = days;
            topPathN = n;
            return topPathResult;
        }

        @Override
        public List<NameCount> topAddr(Integer days, Integer n) {
            topAddrDays = days;
            topAddrN = n;
            return topAddrResult;
        }

        @Override
        public List<NameCount> topClient(Integer days, Integer n) {
            topClientDays = days;
            topClientN = n;
            return topClientResult;
        }

        @Override
        public PageResult<ApiAccessLogBasicResponse> pageByApiId(Long apiId, Integer page, Integer size,
                                                                 Integer statusCode, String startTime, String endTime) {
            callLogsApiId = apiId;
            callLogsPage = page;
            callLogsSize = size;
            callLogsStatusCode = statusCode;
            callLogsStartTime = startTime;
            callLogsEndTime = endTime;
            return callLogsResult;
        }

        @Override
        public List<NameCount> datasourceTypeRatio() {
            return datasourceTypeRatioResult;
        }

        @Override
        public List<NameCount> engineRatio() {
            return engineRatioResult;
        }

        @Override
        public List<NameCount> datasourceApiCount() {
            return datasourceApiCountResult;
        }

        @Override
        public List<NameCount> methodRatio() {
            return methodRatioResult;
        }

        @Override
        public List<NameCount> moduleApiCount() {
            return moduleApiCountResult;
        }

        @Override
        public List<NameCount> apiStatusRatio(Long apiId, Integer days) {
            apiStatusRatioApiId = apiId;
            apiStatusRatioDays = days;
            return apiStatusRatioResult;
        }

        @Override
        public List<DateCount> apiDailyTrend(Long apiId, Integer days) {
            apiDailyTrendApiId = apiId;
            apiDailyTrendDays = days;
            return apiDailyTrendResult;
        }

        @Override
        public List<HourCount> apiHourlyTrend(Long apiId, String date) {
            apiHourlyTrendApiId = apiId;
            apiHourlyTrendDate = date;
            return apiHourlyTrendResult;
        }
    }
}
