// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.executor.controller;

import com.cs.common.consts.Constants;
import com.cs.core.servlet.ApiSwaggerService;
import com.google.common.collect.Lists;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.spring.web.json.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

@Api(tags = {"Swagger接口文档"})
@CrossOrigin
@RestController
@RequestMapping(value = Constants.API_DOC_PATH_PREFIX)
public class ApiSwaggerController {

    @Resource
    private ApiSwaggerService apiSwaggerService;
    @Resource
    private JsonSerializer jsonSerializer;

    @GetMapping(value = {"/swagger.json", "/knife4j/swagger.json"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Json> getSwaggerJson(HttpServletRequest request) {
        OpenAPI oas = apiSwaggerService.getSwaggerJson(request);
        return new ResponseEntity(this.jsonSerializer.toJson(oas), HttpStatus.OK);
    }

    @GetMapping(value = {"/knife4j/swagger-resources"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Json> getSwaggerResource(HttpServletRequest request) {
        Map<String, Object> resources = new HashMap<>();
        resources.put("name", "DataPoly在线接口文档");
        resources.put("url", "/swagger.json");
        resources.put("swaggerVersion", "3.0");
        resources.put("location", "/swagger.json");
        return new ResponseEntity(this.jsonSerializer.toJson(Lists.newArrayList(resources)), HttpStatus.OK);
    }

}
