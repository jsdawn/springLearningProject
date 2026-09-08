package com.example.common.exception;

import com.alibaba.csp.sentinel.Tracer;
import com.example.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理 @RequestBody 参数校验失败（Form/DTO 对象字段校验）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ApiResponse.fail(400, message);
    }

    /**
     * 处理 @ModelAttribute（GET 查询参数/表单参数）校验失败，例如分页 pageNum/pageSize。
     */
    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> {
                    String code = error.getCode();
                    if (code != null && code.startsWith("typeMismatch")) {
                        return error.getField() + " 参数类型不正确";
                    }
                    if (error.getDefaultMessage() != null && !error.getDefaultMessage().isEmpty()) {
                        return error.getDefaultMessage();
                    }
                    return error.getField() + " 参数不合法";
                })
                .collect(Collectors.joining("; "));
        return ApiResponse.fail(400, message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        return ApiResponse.fail(400, e.getName() + " 参数类型不正确");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        return ApiResponse.fail(400, e.getParameterName() + " 参数不能为空");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        return ApiResponse.fail(400, "请求体格式错误");
    }

    /**
     * 处理 @RequestParam / @PathVariable 等参数校验失败（QueryString / Path 参数）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return ApiResponse.fail(400, message);
    }

    /**
     * 处理业务逻辑异常（如商品不存在），记录到 Sentinel 上下文以触发熔断降级统计
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        Tracer.trace(e);
        return ApiResponse.fail(400, e.getMessage());
    }

    /**
     * 处理未登录/登录已失效：由 LoginUserHolder.require() 系列方法在 ThreadLocal
     * 上下文为空时抛出，常见于绕过网关直接调用、Token 已被黑名单踢掉等场景。
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ApiResponse<Void> handleUnauthorizedException(UnauthorizedException e) {
        return ApiResponse.fail(401, e.getMessage());
    }

    /**
     * 处理重复提交：下单幂等 token 的 Lua 原子消费失败时抛出，
     * 含义是"令牌已使用/已过期/不存在"。返回 HTTP 409 Conflict。
     */
    @ExceptionHandler(DuplicateSubmitException.class)
    public ApiResponse<Void> handleDuplicateSubmitException(DuplicateSubmitException e) {
        return ApiResponse.fail(409, e.getMessage());
    }

    /**
     * 处理业务状态异常（如数据库操作失败），记录到 Sentinel 上下文以触发熔断降级统计
     */
    @ExceptionHandler(IllegalStateException.class)
    public ApiResponse<Void> handleIllegalStateException(IllegalStateException e) {
        log.error("业务状态异常", e);
        Tracer.trace(e);
        return ApiResponse.fail(500, e.getMessage());
    }

    /**
     * 兜底异常处理：记录完整堆栈到日志，避免异常被静默吞掉导致排查困难
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("未处理异常", e);
        return ApiResponse.fail(500, "Internal server error");
    }
}
