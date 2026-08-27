// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.core.exec.extractor;

import org.springframework.http.MediaType;

import javax.servlet.ServletInputStream;
import java.nio.charset.Charset;
import java.util.Map;

public interface HttpRequestBodyExtractor {

    boolean support(MediaType mediaType);

    Map<String, Object> read(Charset charset, ServletInputStream inputStream);
}
