// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.executor.controller;

import com.cs.common.consts.Constants;
import com.cs.core.servlet.ApiSwaggerService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@Tag(name = "Swagger接口文档")
@CrossOrigin
@RestController
@RequestMapping(value = Constants.API_DOC_PATH_PREFIX)
public class ApiSwaggerController {

    /** 与原 springfox JsonSerializer 行为对齐：序列化时排除 null 字段 */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Resource
    private ApiSwaggerService apiSwaggerService;

    @GetMapping(value = {"/swagger.json", "/knife4j/swagger.json"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getSwaggerJson(HttpServletRequest request) {
        OpenAPI oas = apiSwaggerService.getSwaggerJson(request);
        try {
            return new ResponseEntity<>(JSON_MAPPER.writeValueAsString(oas), HttpStatus.OK);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize OpenAPI document", e);
        }
    }

    @GetMapping(value = {"/knife4j/swagger-resources"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> getSwaggerResource(HttpServletRequest request) {
        Map<String, Object> resources = new HashMap<>();
        resources.put("name", "DataPoly在线接口文档");
        resources.put("url", "/swagger.json");
        resources.put("swaggerVersion", "3.0");
        resources.put("location", "/swagger.json");
        return new ResponseEntity<>(Lists.newArrayList(resources), HttpStatus.OK);
    }

}
