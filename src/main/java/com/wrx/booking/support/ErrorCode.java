package com.wrx.booking.support;

import org.springframework.http.HttpStatus;

/**
 * 统一错误码。
 */
public enum ErrorCode {
    SUCCESS("SUCCESS", "成功", HttpStatus.OK),
    UNAUTHORIZED("UNAUTHORIZED", "未登录或登录已失效", HttpStatus.UNAUTHORIZED),
    INVALID_REQUEST("INVALID_REQUEST", "请求参数不合法", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "资源不存在", HttpStatus.NOT_FOUND),
    REDIS_ERROR("REDIS_ERROR", "Redis 执行异常", HttpStatus.OK),
    NOT_WARMED_UP("NOT_WARMED_UP", "Redis 库存未预热", HttpStatus.OK),
    DUPLICATE_BOOKING("DUPLICATE_BOOKING", "用户已预约该资源时段", HttpStatus.OK),
    SOLD_OUT("SOLD_OUT", "资源时段库存不足", HttpStatus.OK),
    INVALID_DURATION("INVALID_DURATION", "预约时长只能是 1、2 或 4 小时", HttpStatus.OK),
    SLOT_NOT_BOOKABLE("SLOT_NOT_BOOKABLE", "预约时段必须是今天尚未开始的完整时段", HttpStatus.OK),
    NON_CONTIGUOUS_SLOTS("NON_CONTIGUOUS_SLOTS", "所选时长没有连续可用的资源时段", HttpStatus.OK),
    INTERNAL_ERROR("INTERNAL_ERROR", "系统异常", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
