package me.beiming.aichat.interceptor;


import me.beiming.aichat.config.JwtConfig;
import me.beiming.aichat.constant.MessageConstant;
import me.beiming.aichat.constant.RedisConstant;
import me.beiming.aichat.tool.JwtTool;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtUserInterceptor implements HandlerInterceptor {
    @Autowired
    private JwtConfig jwtConfig;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String token = request.getHeader(jwtConfig.getTokenName());

        if (token == null || token.isEmpty()) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":0,\"msg\":\"" + MessageConstant.TOKEN_MISSING + "\",\"data\":null}");
            return false;
        }
        try {
            Claims claims = JwtTool.parseJWT(jwtConfig.getSecret(), token);
            String id = claims.getId();
            Object o = redisTemplate.opsForValue().get(RedisConstant.JWT_ID + id);
            if (o == null) {
                response.setStatus(403);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":0,\"msg\":\"" + MessageConstant.USER_NOT_EXIST + "\",\"data\":null}");
                return false;
            }
        } catch (Exception e) {
            response.setContentType("application/json;charset=UTF-8");
            String errorMsg;
            int statusCode;
            
            if (e.getMessage() != null && (e.getMessage().contains("expired") || e.getMessage().contains("Expired"))) {
                errorMsg = MessageConstant.TOKEN_EXPIRED;
                statusCode = 401;
            } else {
                errorMsg = "令牌无效";
                statusCode = 403;
            }
            
            response.setStatus(statusCode);
            response.getWriter().write("{\"code\":0,\"msg\":\"" + errorMsg + "\",\"data\":null}");
            return false;
        }
        return true;
    }
}