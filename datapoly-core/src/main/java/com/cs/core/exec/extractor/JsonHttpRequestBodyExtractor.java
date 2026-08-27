// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.extractor;

import cn.hutool.core.io.IoUtil;
import com.fasterxml.jackson.databind.*;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import javax.servlet.ServletInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.Map.Entry;

@Service
public class JsonHttpRequestBodyExtractor implements HttpRequestBodyExtractor {

    private List<MediaType> supportedMediaTypes = Lists.newArrayList(
            MediaType.APPLICATION_JSON
    );

    private ObjectMapper objectMapper = new ObjectMapper();

    public JsonHttpRequestBodyExtractor() {
        objectMapper.configure(DeserializationFeature.USE_LONG_FOR_INTS, true);
    }

    @Override
    public boolean support(MediaType mediaType) {
        if (mediaType == null) {
            return false;
        }
        for (MediaType supportedMediaType : supportedMediaTypes) {
            if (supportedMediaType.includes(mediaType)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Map<String, Object> read(Charset charset, ServletInputStream inputStream) {
        try {
            String jsonString = IoUtil.read(inputStream, charset);
            if (StringUtils.isBlank(jsonString)) {
                return Collections.emptyMap();
            }
            Map<String, Object> resultMap = new HashMap<>();
            JsonNode rootNode = objectMapper.readTree(jsonString);
            if (!rootNode.isContainerNode()) {
                return Collections.emptyMap();
            }

            Iterator<Entry<String, JsonNode>> fields = rootNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode jsonNode = entry.getValue();
                Object value = objectMapper.convertValue(jsonNode, Object.class);
                resultMap.put(entry.getKey(), value);
            }
            return resultMap;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
