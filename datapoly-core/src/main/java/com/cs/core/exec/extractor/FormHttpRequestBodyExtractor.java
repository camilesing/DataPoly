// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.extractor;

import com.google.common.collect.Lists;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.*;

import javax.servlet.ServletInputStream;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.*;

@Service
public class FormHttpRequestBodyExtractor implements HttpRequestBodyExtractor {

    private static final String COMMA = ",";

    private List<MediaType> supportedMediaTypes = Lists.newArrayList(
            MediaType.APPLICATION_FORM_URLENCODED,
            MediaType.MULTIPART_FORM_DATA, MediaType.MULTIPART_MIXED
    );

    @Override
    public boolean support(MediaType mediaType) {
        if (mediaType == null) {
            return true;
        }
        for (MediaType supportedMediaType : supportedMediaTypes) {
            if (supportedMediaType.getType().equalsIgnoreCase("multipart")) {
                continue;
            }
            if (supportedMediaType.includes(mediaType)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Map<String, Object> read(Charset charset, ServletInputStream inputStream) {

        try {
            String body = StreamUtils.copyToString(inputStream, charset);
            String[] pairs = StringUtils.tokenizeToStringArray(body, "&");
            MultiValueMap<String, String> mapValues = new LinkedMultiValueMap<>(pairs.length);
            for (String pair : pairs) {
                int idx = pair.indexOf('=');
                if (idx == -1) {
                    mapValues.add(URLDecoder.decode(pair, charset.name()), null);
                } else {
                    String name = URLDecoder.decode(pair.substring(0, idx), charset.name());
                    String value = pair.substring(idx + 1);
                    if (null != value && value.contains(COMMA)) {
                        for (String v : value.split(COMMA)) {
                            mapValues.add(name, URLDecoder.decode(v, charset.name()));
                        }
                    } else {
                        mapValues.add(name, value);
                    }
                }
            }
            return new HashMap<>(mapValues);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
