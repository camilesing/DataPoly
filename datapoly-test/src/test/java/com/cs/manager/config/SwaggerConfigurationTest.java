// Use of this source code is governed by a BSD-style license
package com.cs.manager.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.Test;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;

import java.util.List;

import static org.junit.Assert.*;

public class SwaggerConfigurationTest {

    @Test
    public void managerApiScansManagerControllerPackage() {
        GroupedOpenApi grouped = new SwaggerConfiguration().managerApi();

        assertNotNull(grouped);
        assertEquals("Manager的接口", grouped.getGroup());
        assertEquals(List.of("com.cs.manager.controller"), grouped.getPackagesToScan());
    }

    @Test
    public void operationCustomizerAddsAuthorizationHeaderParameter() {
        GroupedOpenApi grouped = new SwaggerConfiguration().managerApi();

        assertEquals(1, grouped.getOperationCustomizers().size());
        OperationCustomizer customizer = grouped.getOperationCustomizers().iterator().next();

        Operation operation = new Operation();
        assertSame(operation, customizer.customize(operation, null));

        List<Parameter> parameters = operation.getParameters();
        assertNotNull(parameters);
        assertEquals(1, parameters.size());
        Parameter authorization = parameters.get(0);
        assertEquals("Authorization", authorization.getName());
        assertEquals("header", authorization.getIn());
        assertFalse(authorization.getRequired());
        assertTrue(authorization.getDescription().contains("Bearer {token}"));
    }

    @Test
    public void managerOpenApiCarriesDocMetadata() {
        OpenAPI openApi = new SwaggerConfiguration().managerOpenApi();

        assertNotNull(openApi);
        assertNotNull(openApi.getInfo());
        assertEquals("DataPoly管理服务API文档", openApi.getInfo().getTitle());
        assertEquals("在线API文档", openApi.getInfo().getDescription());
        assertEquals("1.0", openApi.getInfo().getVersion());
    }
}
