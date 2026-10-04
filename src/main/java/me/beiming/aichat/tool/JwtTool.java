package me.beiming.aichat.tool;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT工具类
 */
public class JwtTool {

    /**
     * 创建JWT令牌
     * @param secret     密钥
     * @param expiration 过期时间（毫秒）
     * @param claims     自定义声明
     * @param id         JWT ID（用户ID）
     * @return JWT字符串
     */
    public static String createJWT(String secret, long expiration, Map<String, Object> claims, String id) {
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Date exp = new Date(nowMillis + expiration);

        return Jwts.builder()
                .setClaims(claims)
                .setId(id)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(SignatureAlgorithm.HS256, secret.getBytes(StandardCharsets.UTF_8))
                .compact();
    }

    /**
     * 解析JWT令牌
     * @param secret 密钥
     * @param token  JWT字符串
     * @return Claims
     */
    public static Claims parseJWT(String secret, String token) {
        return Jwts.parser()
                .setSigningKey(secret.getBytes(StandardCharsets.UTF_8))
                .parseClaimsJws(token)
                .getBody();
    }
}
