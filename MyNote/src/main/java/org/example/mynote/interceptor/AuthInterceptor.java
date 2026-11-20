package org.example.mynote.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.mynote.common.Constants;
import org.example.mynote.common.Result;
import org.example.mynote.common.ResultCode;
import org.example.mynote.common.UserContext;
import org.example.mynote.util.JwtUtil;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 登录态拦截器。
 *
 * <p>解析 Authorization 头中的 JWT 并把 userId 放入 {@link UserContext}。
 * 这里只做"是谁"的判断，"能不能操作这条数据"由各 Service 在查询时带上 user_id 条件保证，
 * 不依赖前端传来的 id 做权限判断。</p>
 *
 * @author MyNote
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public AuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 预检请求不带令牌，直接放行交给 CORS 处理
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String header = request.getHeader(Constants.HEADER_TOKEN);
        if (header == null || !header.startsWith(Constants.TOKEN_PREFIX)) {
            writeUnauthorized(response);
            return false;
        }
        Long userId = jwtUtil.parseUserId(header.substring(Constants.TOKEN_PREFIX.length()).trim());
        if (userId == null) {
            writeUnauthorized(response);
            return false;
        }
        UserContext.set(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                               Exception ex) {
        // 线程复用场景下必须清理，否则下一个请求可能读到上一个用户
        UserContext.clear();
    }

    /**
     * 输出 401 响应体，保持与全局异常处理相同的结构。
     *
     * @param response 响应
     */
    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<Void> result = Result.fail(ResultCode.UNAUTHORIZED);
        response.getWriter().write("{\"code\":" + result.getCode() + ",\"message\":\""
                + result.getMessage() + "\",\"data\":null}");
    }
}
