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
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.ConstraintViolationException;
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

    /**
     * Method-level validation (@Validated on controllers): parameter constraint violations
     * are client errors, not 500s.
     */
    @ExceptionHandler(value = {ConstraintViolationException.class})
    public ResponseEntity<ResultEntity> constraintViolationException(ConstraintViolationException e) {
        String errorMessage = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));

        return respond(ResponseErrorCode.ERROR_INVALID_ARGUMENT,
                ResultEntity.failed(ResponseErrorCode.ERROR_INVALID_ARGUMENT, errorMessage));
    }

    /**
     * Boot 3 起 Spring MVC 对未映射路径恒抛 NoHandlerFoundException（原
     * spring.mvc.throw-exception-if-no-handler-found=false 已弃用失效），在此等价恢复
     * 旧 404 语义，避免未匹配路径落入 500 内部错误。
     */
    @ExceptionHandler(value = {NoHandlerFoundException.class})
    public ResponseEntity<ResultEntity> noHandlerFoundException(NoHandlerFoundException e) {
        return respond(ResponseErrorCode.ERROR_PATH_NOT_EXISTS,
                ResultEntity.failed(ResponseErrorCode.ERROR_PATH_NOT_EXISTS,
                        e.getHttpMethod() + " " + e.getRequestURL() + " not exists"));
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
