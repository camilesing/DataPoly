// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.common.enums;

public enum ParamLocationEnum {
    REQUEST_HEADER("header", "请求头"),
    REQUEST_FORM("query", "表单数据"),
    REQUEST_BODY("body", "请求体"),
    ;

    private String name;
    private String in;

    ParamLocationEnum(String in, String name) {
        this.name = name;
        this.in = in;
    }

    public String getName() {
        return name;
    }

    public String getIn() {
        return in;
    }

    public boolean isParameter() {
        return this == REQUEST_FORM || this == REQUEST_HEADER;
    }

    public boolean isHeader() {
        return this == REQUEST_HEADER;
    }

    public boolean isRequestBody() {
        return this == REQUEST_BODY;
    }
}
