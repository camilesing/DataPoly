// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.module;

import com.cs.common.service.VarModuleInterface;
import com.cs.core.exec.annotation.*;
import com.cs.core.exec.annotation.Module;
import com.cs.core.executor.AlarmHttpRequestFactory;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@NoArgsConstructor
@Module(HttpModule.VAR_NAME)
public class HttpModule implements VarModuleInterface {

    protected static final String VAR_NAME = "http";
    protected static final RestTemplate template = new RestTemplate(new AlarmHttpRequestFactory());

    private HttpHeaders httpHeaders = new HttpHeaders();
    private Class<?> responseType = Object.class;
    private MultiValueMap<String, Object> params = new LinkedMultiValueMap<>();
    private MultiValueMap<String, Object> data = new LinkedMultiValueMap<>();
    private Map<String, ?> variables = new HashMap<>();
    private String url;
    private HttpMethod method = HttpMethod.GET;
    private HttpEntity<Object> entity = null;
    private Object requestBody;

    public HttpModule(String url) {
        this.url = url;
    }

    @Override
    public String getVarModuleName() {
        return VAR_NAME;
    }

    @Comment("comment.http.connect")
    public HttpModule connect(@Comment("comment.param.targetUrl") String url) {
        return new HttpModule(url);
    }

    @Comment("comment.http.param")
    public HttpModule param(@Comment("comment.param.paramName") String key, @Comment("comment.param.paramValue") Object... values) {
        if (values != null) {
            for (Object value : values) {
                this.params.add(key, value);
            }
        }
        return this;
    }

    @Comment("comment.http.paramBatch")
    public HttpModule param(@Comment("comment.param.paramValue") Map<String, Object> values) {
        values.forEach((key, value) -> param(key, Objects.toString(value, "")));
        return this;
    }

    @Comment("comment.http.data")
    public HttpModule data(@Comment("comment.param.paramName") String key, @Comment("comment.param.paramValue") Object... values) {
        if (values != null) {
            for (Object value : values) {
                this.data.add(key, value);
            }
        }
        return this;
    }

    @Comment("comment.http.dataBatch")
    public HttpModule data(@Comment("comment.param.paramValue") Map<String, Object> values) {
        values.forEach((key, value) -> data(key, Objects.toString(value, "")));
        return this;
    }

    @Comment("comment.http.header")
    public HttpModule header(@Comment("comment.param.headerKey") String key, @Comment("comment.param.headerValue") String value) {
        httpHeaders.add(key, value);
        return this;
    }

    @Comment("comment.http.headerBatch")
    public HttpModule header(@Comment("comment.param.headerValue") Map<String, Object> values) {
        values.entrySet()
                .stream()
                .filter(it -> it.getValue() != null)
                .forEach(entry -> header(entry.getKey(), entry.getValue().toString()));
        return this;
    }

    @Comment("comment.http.method")
    public HttpModule method(@Comment("comment.param.httpMethod") HttpMethod method) {
        this.method = method;
        return this;
    }

    @Comment("comment.http.body")
    public HttpModule body(@Comment("comment.param.bodyValue") Object requestBody) {
        this.requestBody = requestBody;
        this.contentType(MediaType.APPLICATION_JSON);
        return this;
    }

    @Comment("comment.http.contentType")
    public HttpModule contentType(@Comment("comment.param.contentType") String contentType) {
        return contentType(MediaType.parseMediaType(contentType));
    }

    @Comment("comment.http.contentType")
    public HttpModule contentType(@Comment("comment.param.contentType") MediaType mediaType) {
        this.httpHeaders.setContentType(mediaType);
        return this;
    }

    @Comment("comment.http.expectBytes")
    public HttpModule expectBytes() {
        this.responseType = byte[].class;
        return this;
    }

    @Comment("comment.http.expectString")
    public HttpModule expectString() {
        this.responseType = String.class;
        return this;
    }

    @Comment("comment.http.get")
    public ResponseEntity<?> get() {
        this.method(HttpMethod.GET);
        return this.execute();
    }

    @Comment("comment.http.post")
    public ResponseEntity<?> post() {
        this.method(HttpMethod.POST);
        return this.execute();
    }

    @Comment("comment.http.put")
    public ResponseEntity<?> put() {
        this.method(HttpMethod.PUT);
        return this.execute();
    }

    @Comment("comment.http.delete")
    public ResponseEntity<?> delete() {
        this.method(HttpMethod.DELETE);
        return this.execute();
    }

    @Comment("comment.http.execute")
    public ResponseEntity<?> execute() {
        if (!this.params.isEmpty()) {
            String queryString = this.params.entrySet().stream()
                    .map(it -> it.getValue().stream()
                            .map(value -> it.getKey() + "=" + value)
                            .collect(Collectors.joining("&"))
                    ).collect(Collectors.joining("&"));
            if (StringUtils.isNotBlank(queryString)) {
                this.url += (this.url.contains("?") ? "&" : "?") + queryString;
            }
        }
        if (!this.data.isEmpty()) {
            this.entity = new HttpEntity<>(this.data, this.httpHeaders);
        } else if (this.entity == null && this.requestBody != null) {
            this.entity = new HttpEntity<>(this.requestBody, this.httpHeaders);
        } else {
            this.entity = new HttpEntity<>(null, this.httpHeaders);
        }
        return template.exchange(url, this.method, entity, responseType, variables);
    }
}