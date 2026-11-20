package org.example.mynote.common;

/**
 * 当前登录用户上下文。
 *
 * <p>令牌在拦截器中解析一次，后续 DAO/Service 通过本类取 userId，避免每个方法都传参。
 * 采用 ThreadLocal 且必须在请求结束时清理，否则线程复用会串号。</p>
 *
 * @author MyNote
 */
public final class UserContext {

    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();

    private UserContext() {
    }

    /**
     * 写入当前请求的用户 id。
     *
     * @param userId 用户 id
     */
    public static void set(Long userId) {
        CURRENT_USER.set(userId);
    }

    /**
     * 读取当前用户 id。
     *
     * @return 用户 id，未登录时为 null
     */
    public static Long get() {
        return CURRENT_USER.get();
    }

    /**
     * 读取当前用户 id，未登录直接抛业务异常。
     *
     * @return 用户 id
     */
    public static Long require() {
        Long userId = CURRENT_USER.get();
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    /**
     * 清理线程变量，必须与 {@link #set} 成对出现。
     */
    public static void clear() {
        CURRENT_USER.remove();
    }
}
