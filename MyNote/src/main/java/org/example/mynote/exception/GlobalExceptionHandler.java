package org.example.mynote.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.example.mynote.common.BizException;
import org.example.mynote.common.Result;
import org.example.mynote.common.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 *
 * <p>把异常统一转成 {@link Result}，HTTP 状态码保持 200，业务成败由 body.code 表达，
 * 这样前端拦截器只需处理一种响应形态，不会出现"200 里套错误"和"4xx 里套错误"两套逻辑。</p>
 *
 * @author MyNote
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常：可预期的失败，日志只记 info。
     *
     * @param e 业务异常
     * @return 失败响应
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.info("业务异常 code={} message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验失败：取第一条字段错误直接展示。
     *
     * @param e 校验异常
     * @return 失败响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return Result.fail(ResultCode.PARAM_ERROR.getCode(),
                message.isEmpty() ? ResultCode.PARAM_ERROR.getMessage() : message);
    }

    /**
     * 请求体不可读（JSON 写错、编码不对、缺字段等）。
     *
     * <p>这类错误发生在进入控制器之前，若不单独接住就会落进兜底分支变成 500，
     * 前端会误以为是服务端故障，实际是请求本身的问题。</p>
     *
     * @param e 异常
     * @return 失败响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.info("请求体解析失败: {}", e.getMessage());
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), "请求内容格式不正确，请检查提交的数据");
    }

    /**
     * 请求方法不支持（例如用 GET 调了只接受 POST 的接口）。
     *
     * @param e 异常
     * @return 失败响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), "该接口不支持 " + e.getMethod() + " 方法");
    }

    /**
     * 参数类型不匹配（例如 /api/note/abc 这种把 id 写错的情况）。
     * 不接住就会落进兜底分支变成 500，前端只看到一个没有信息量的错误。
     *
     * @param e 异常
     * @return 失败响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), "参数「" + e.getName() + "」格式不正确");
    }

    /**
     * 上传超限。
     *
     * @param e 异常
     * @return 失败响应
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleUploadSize(MaxUploadSizeExceededException e) {
        return Result.fail(ResultCode.PARAM_ERROR.getCode(), "文件太大，单个文件请控制在 20MB 以内");
    }

    /**
     * 兜底异常：打印堆栈，返回统一文案，不把内部错误细节泄露给前端。
     *
     * @param e       异常
     * @param request 请求，用于记录出错的接口
     * @return 失败响应
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e, HttpServletRequest request) {
        log.error("接口异常 uri={}", request.getRequestURI(), e);
        return Result.fail(ResultCode.SYSTEM_ERROR);
    }
}
