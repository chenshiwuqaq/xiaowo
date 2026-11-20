package org.example.mynote.common;

import java.io.Serializable;

/**
 * 统一响应结果封装。
 *
 * <p>所有 REST 接口（流式 SSE 接口除外）都以此结构返回，前端据 code 判断成功与否，
 * 业务异常由 GlobalExceptionHandler 统一转换，避免每个控制器重复拼装。</p>
 *
 * @param <T> 业务数据类型
 * @author MyNote
 */
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 状态码，取值见 {@link ResultCode}
     */
    private Integer code;

    /**
     * 提示信息，失败时为可直接展示给用户的中文文案
     */
    private String message;

    /**
     * 业务数据
     */
    private T data;

    public Result() {
    }

    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 构造无数据的成功响应。
     *
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> Result<T> ok() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /**
     * 构造带数据的成功响应。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return 成功响应
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 构造带自定义提示语的成功响应。
     *
     * @param message 提示语
     * @param data    业务数据
     * @param <T>     数据类型
     * @return 成功响应
     */
    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    /**
     * 构造失败响应。
     *
     * @param code    状态码
     * @param message 失败提示
     * @param <T>     数据类型
     * @return 失败响应
     */
    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 按预置状态码构造失败响应。
     *
     * @param resultCode 预置状态码
     * @param <T>        数据类型
     * @return 失败响应
     */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
