package org.example.mynote.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.example.mynote.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 登录令牌工具。
 *
 * <p>使用 HS256 自签名的 JWT，服务端无状态，不引入 Redis 等额外中间件。
 * 密钥长度不足时启动即报错，避免上线后签发弱密钥令牌。</p>
 *
 * @author MyNote
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    /**
     * HS256 要求密钥至少 256 bit
     */
    private static final int MIN_SECRET_BYTES = 32;

    private static final String CLAIM_ACCOUNT = "account";

    private final SecretKey key;

    private final long expireMillis;

    public JwtUtil(AppProperties properties) {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("mynote.jwt.secret 长度不足 32 字节，请修改配置");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = properties.getJwt().getExpireHours() * 60L * 60L * 1000L;
    }

    /**
     * 签发令牌。
     *
     * @param userId  用户 id
     * @param account 登录账号，仅用于日志排查
     * @return JWT 字符串
     */
    public String create(Long userId, String account) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim(CLAIM_ACCOUNT, account)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expireMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析令牌并返回用户 id。
     *
     * @param token JWT 字符串
     * @return 用户 id，令牌无效或过期时返回 null
     */
    public Long parseUserId(String token) {
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("令牌解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 令牌有效期（毫秒），供前端提示续期使用。
     *
     * @return 有效期
     */
    public long getExpireMillis() {
        return expireMillis;
    }
}
