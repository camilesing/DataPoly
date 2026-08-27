// Copyright tang.  All rights reserved.
// Use of this source code is governed by a BSD-style license
package com.cs.manager.exception;

import com.cs.common.dto.ResultEntity;
import com.cs.common.exception.*;
import com.cs.common.util.I18nUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@Slf4j
@ControllerAdvice
public class ExceptionController {

    /**
     * Whether to map error codes to real HTTP status codes; when disabled all errors return HTTP 200 (legacy built-in frontend compatibility)
     */
    @Value("${datapoly.manager.http-status-mapping.enabled:true}")
    private boolean httpStatusMappingEnabled;

    @ExceptionHandler(value = {MethodArgumentNotValidException.class})
    public ResponseEntity<ResultEntity> argumentValidException(MethodArgumentNotValidException e) {
        log.error("Invalid arguments error:", e);

        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return respond(ResponseErrorCode.ERROR_INVALID_ARGUMENT,
                ResultEntity.failed(ResponseErrorCode.ERROR_INVALID_ARGUMENT, errorMessage));
    }

    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<ResultEntity> errorHandler(Exception e) {
        if (e instanceof CommonException) {
            CommonException ex = (CommonException) e;
            return respond(ex.getCode(), ResultEntity.failed(ex.getCode(), ex.getMessage()));
        }

        log.error("Error:", e);
        // Raw exception details go to logs only; the response body carries a generic message (H1)
        return respond(ResponseErrorCode.ERROR_INTERNAL_ERROR,
                ResultEntity.failed(ResponseErrorCode.ERROR_INTERNAL_ERROR,
                        I18nUtils.getMessage("exception.ERROR_INTERNAL_ERROR")));
    }

    private ResponseEntity<ResultEntity> respond(ResponseErrorCode code, ResultEntity entity) {
        return ResponseEntity.status(httpStatusMappingEnabled ? code.getHttpStatus() : 200).body(entity);
    }
}
