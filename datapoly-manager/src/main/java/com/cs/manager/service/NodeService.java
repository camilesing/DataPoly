// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.service;

import com.cs.common.consts.Constants;
import com.cs.common.enums.NodeStatusEnum;
import com.cs.common.exception.*;
import com.cs.core.dto.TopologyNodeResponse;
import com.cs.core.executor.AlarmHttpRequestFactory;
import com.cs.manager.config.DataPolyUrlConfiguration;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.lang.management.*;
import java.util.*;

@Slf4j
@Service
public class NodeService {

    private static final RestTemplate restTemplate = new RestTemplate(new AlarmHttpRequestFactory());

    @Resource
    private DiscoveryClient discoveryClient;
    @Resource
    private DataPolyUrlConfiguration datapolyUrlConfiguration;

    public String getGatewayAddr() {
        List<ServiceInstance> instances = discoveryClient.getInstances(Constants.GATEWAY_APPLICATION_NAME);
        ServiceInstance instance = instances.stream().findAny().orElse(null);
        // Prefer the externally configured address only when the service actually exists
        if (StringUtils.isNotBlank(datapolyUrlConfiguration.getGateway()) && instance != null) {
            log.info("Configured Gateway Address found :{},Skip auto self discover", datapolyUrlConfiguration.getGateway());
            return datapolyUrlConfiguration.getGateway();
        }
        if (null != instance) {
            return String.format("http://%s:%d", instance.getHost(), instance.getPort());
        }
        throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, "gateway.not.found");
    }

    public String getApiPrefix() {
        List<ServiceInstance> instances = discoveryClient.getInstances(Constants.GATEWAY_APPLICATION_NAME);
        ServiceInstance instance = instances.stream().findAny().orElse(null);
        // Prefer the externally configured address only when the service actually exists
        if (StringUtils.isNotBlank(datapolyUrlConfiguration.getGateway()) && instance != null) {
            log.info("Configured Gateway Address found :{},Skip auto self discover", datapolyUrlConfiguration.getGateway());
            return datapolyUrlConfiguration.getGateway() + "/" + Constants.API_PATH_PREFIX + "/";
        }
        if (null != instance) {
            return String.format("http://%s:%d/%s/", instance.getHost(), instance.getPort(), Constants.API_PATH_PREFIX);
        }
        throw new CommonException(ResponseErrorCode.ERROR_RESOURCE_NOT_EXISTS, "gateway.not.found");
    }

    public List<TopologyNodeResponse> getNodesTopology() {
        List<String> serviceIds = discoveryClient.getServices();
        if (CollectionUtils.isEmpty(serviceIds)) {
            return Collections.emptyList();
        }

        List<TopologyNodeResponse> nodes = new ArrayList<>();
        for (String serviceId : serviceIds) {
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
            for (ServiceInstance instance : instances) {
                NodeMetrics metrics = fetchNodeMetrics(instance);
                nodes.add(
                        TopologyNodeResponse.builder()
                                .serviceId(instance.getServiceId())
                                .instanceId(instance.getInstanceId())
                                .host(instance.getHost())
                                .port(instance.getPort())
                                .memory(metrics.getMemory())
                                .cpu(metrics.getCpu())
                                .disk(metrics.getDisk())
                                .status(metrics.getStatus())
                                .build());
            }
        }
        return nodes;
    }

    /**
     * Fetch node metrics.
     */
    private NodeMetrics fetchNodeMetrics(ServiceInstance instance) {
        String baseUrl = String.format("http://%s:%d", instance.getHost(), instance.getPort());
        try {
            return fetchMetricsFromActuator(baseUrl);
        } catch (Exception e) {
            log.warn("Failed to fetch metrics from actuator for {}: {}", baseUrl, e.getMessage());
            return fetchLocalMetrics();
        }
    }

    /**
     * Fetch metrics from Actuator endpoints.
     */
    private NodeMetrics fetchMetricsFromActuator(String baseUrl) {
        NodeMetrics.NodeMetricsBuilder builder = NodeMetrics.builder();

        // Health status
        NodeStatusEnum status = checkHealth(baseUrl);
        builder.status(status);

        // Memory usage
        try {
            Integer memoryUsage = fetchMemoryUsage(baseUrl);
            builder.memory(memoryUsage);
        } catch (Exception e) {
            log.debug("Failed to fetch memory metric: {}", e.getMessage());
            builder.memory(0);
        }

        // CPU usage
        try {
            Integer cpuUsage = fetchCpuUsage(baseUrl);
            builder.cpu(cpuUsage);
        } catch (Exception e) {
            log.debug("Failed to fetch cpu metric: {}", e.getMessage());
            builder.cpu(0);
        }

        // Disk usage
        try {
            Integer diskUsage = fetchDiskUsage(baseUrl);
            builder.disk(diskUsage);
        } catch (Exception e) {
            log.debug("Failed to fetch disk metric: {}", e.getMessage());
            builder.disk(0);
        }

        return builder.build();
    }

    /**
     * Check health status.
     */
    private NodeStatusEnum checkHealth(String baseUrl) {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    baseUrl + "/actuator/health", Map.class);
            if (response.getBody() != null) {
                String healthStatus = (String) response.getBody().get("status");
                if ("UP".equalsIgnoreCase(healthStatus)) {
                    return NodeStatusEnum.normal;
                }
            }
            return NodeStatusEnum.warning;
        } catch (Exception e) {
            log.warn("Health check failed for {}: {}", baseUrl, e.getMessage());
            return NodeStatusEnum.warning;
        }
    }

    /**
     * Fetch memory usage percentage.
     */
    private Integer fetchMemoryUsage(String baseUrl) {
        Long used = fetchMetricValue(baseUrl, "jvm.memory.used");
        Long max = fetchMetricValue(baseUrl, "jvm.memory.max");
        if (used != null && max != null && max > 0) {
            return (int) ((used * 100) / max);
        }
        return 0;
    }

    /**
     * Fetch CPU usage percentage.
     */
    private Integer fetchCpuUsage(String baseUrl) {
        Double cpuUsage = fetchMetricValueDouble(baseUrl, "system.cpu.usage");
        if (cpuUsage != null) {
            return (int) (cpuUsage * 100);
        }
        return 0;
    }

    /**
     * Fetch disk usage percentage.
     */
    private Integer fetchDiskUsage(String baseUrl) {
        Long free = fetchMetricValue(baseUrl, "disk.free");
        Long total = fetchMetricValue(baseUrl, "disk.total");
        if (free != null && total != null && total > 0) {
            return (int) (((total - free) * 100) / total);
        }
        return 0;
    }

    /**
     * Fetch a long metric value from the Actuator metrics endpoint.
     */
    @SuppressWarnings("unchecked")
    private Long fetchMetricValue(String baseUrl, String metricName) {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    baseUrl + "/actuator/metrics/" + metricName, Map.class);
            if (response.getBody() != null) {
                List<Map<String, Object>> measurements = (List<Map<String, Object>>) response.getBody().get("measurements");
                if (measurements != null && !measurements.isEmpty()) {
                    Object value = measurements.get(0).get("value");
                    if (value instanceof Number) {
                        return ((Number) value).longValue();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to fetch metric {}: {}", metricName, e.getMessage());
        }
        return null;
    }

    /**
     * Fetch a double metric value from the Actuator metrics endpoint.
     */
    @SuppressWarnings("unchecked")
    private Double fetchMetricValueDouble(String baseUrl, String metricName) {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    baseUrl + "/actuator/metrics/" + metricName, Map.class);
            if (response.getBody() != null) {
                List<Map<String, Object>> measurements = (List<Map<String, Object>>) response.getBody().get("measurements");
                if (measurements != null && !measurements.isEmpty()) {
                    Object value = measurements.get(0).get("value");
                    if (value instanceof Number) {
                        return ((Number) value).doubleValue();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to fetch metric {}: {}", metricName, e.getMessage());
        }
        return null;
    }

    /**
     * Fetch local (this JVM) metrics as a fallback.
     */
    private NodeMetrics fetchLocalMetrics() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        int memoryUsage = maxMemory > 0 ? (int) ((usedMemory * 100) / maxMemory) : 0;

        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        int cpuUsage = 0;
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            cpuUsage = (int) (((com.sun.management.OperatingSystemMXBean) osBean).getSystemCpuLoad() * 100);
        }

        java.io.File file = new java.io.File(".");
        long totalSpace = file.getTotalSpace();
        long freeSpace = file.getFreeSpace();
        int diskUsage = totalSpace > 0 ? (int) (((totalSpace - freeSpace) * 100) / totalSpace) : 0;

        NodeStatusEnum status = (memoryUsage > 90 || cpuUsage > 90) ? NodeStatusEnum.warning : NodeStatusEnum.normal;

        return NodeMetrics.builder()
                .memory(memoryUsage)
                .cpu(cpuUsage)
                .disk(diskUsage)
                .status(status)
                .build();
    }

    /**
     * Internal node metrics holder.
     */
    @Data
    @Builder
    private static class NodeMetrics {
        private Integer memory;
        private Integer cpu;
        private Integer disk;
        private NodeStatusEnum status;
    }
}
