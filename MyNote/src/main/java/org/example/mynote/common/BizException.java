package org.example.mynote.common;

/**
 * 业务异常。
 *
 * <p>可预期的失败（参数不合法、越权、账号重复等）一律抛本异常，由全局异常处理器转成
 * {@link Result}，控制器中不再写 if-else 拼装错误码。</p>
 *
 * @author MyNote
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Integer code;

    public BizException(String message) {
        super(message);
        this.code = ResultCode.BIZ_LIMIT.getCode();
    }

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public Integer getCode() {
        return code;
    }
}
