package org.example.mynote.common;

/**
 * 接口状态码。
 *
 * <p>只区分"通信层是否成功"与"业务是否可继续"两类语义，具体业务失败信息放在 message 中，
 * 便于前端直接展示，不需要再维护一份码表翻译。</p>
 *
 * @author MyNote
 */
public enum ResultCode {

    /**
     * 成功
     */
    SUCCESS(200, "操作成功"),

    /**
     * 参数校验不通过
     */
    PARAM_ERROR(400, "参数不符合要求"),

    /**
     * 未登录或登录已过期
     */
    UNAUTHORIZED(401, "登录已过期，请重新登录"),

    /**
     * 无权访问该资源
     */
    FORBIDDEN(403, "没有权限操作该数据"),

    /**
     * 资源不存在
     */
    NOT_FOUND(404, "资源不存在"),

    /**
     * 账号或密码错误
     */
    LOGIN_FAILED(1001, "账号或密码不正确"),

    /**
     * 账号已存在
     */
    ACCOUNT_EXISTS(1002, "该账号已被使用"),

    /**
     * 账号与邮箱不匹配，无法自助重置密码
     */
    RESET_FAILED(1003, "账号与绑定邮箱不匹配，无法重置"),

    /**
     * 旧密码不正确
     */
    OLD_PASSWORD_WRONG(1004, "当前密码不正确"),

    /**
     * 业务规则限制
     */
    BIZ_LIMIT(1005, "操作受到限制"),

    /**
     * AI 能力不可用（未配置模型或未填 Key）
     */
    AI_NOT_CONFIGURED(2001, "还没有配置 AI 模型，请到「个人设置 - AI 模型」中填写 API Key"),

    /**
     * AI 服务调用失败
     */
    AI_CALL_FAILED(2002, "AI 服务暂时不可用，助手先休息一下"),

    /**
     * 天气取不到。属于"可忽略的失败"：前端把卡片切成占位即可，不弹窗打扰书写
     */
    WEATHER_UNAVAILABLE(3001, "天气暂时取不到，先空一会儿"),

    /**
     * 系统内部错误
     */
    SYSTEM_ERROR(500, "服务开小差了，请稍后重试");

    private final Integer code;

    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
