package me.beiming.aichat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.beiming.aichat.config.JwtConfig;
import me.beiming.aichat.config.LoginSecurityConfig;
import me.beiming.aichat.constant.MessageConstant;
import me.beiming.aichat.constant.RedisConstant;
import me.beiming.aichat.entity.*;
import me.beiming.aichat.dto.RefreshTokenDTO;
import me.beiming.aichat.dto.TokenResponseDto;
import me.beiming.aichat.entity.user.User;
import me.beiming.aichat.entity.user.UserApi;
import me.beiming.aichat.entity.user.UserConversation;
import me.beiming.aichat.mapper.UserApiMapper;
import me.beiming.aichat.mapper.UserConversationMapper;
import me.beiming.aichat.mapper.UserMapper;
import me.beiming.aichat.tool.JwtTool;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("ALL")
@Slf4j
@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private JwtConfig jwtConfig;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private LoginSecurityConfig loginSecurityConfig;
    @Autowired
    private UserApiMapper userApiMapper;
    @Autowired
    private McpService mcpService;
    @Autowired
    private ConversationService conversationService;
    @Autowired
    private UserConversationMapper userConversationMapper;

    private final WebClient webClient = WebClient.builder()
            .build();

    public Result<TokenResponseDto> login(String username, String password) {
        String lockKey = RedisConstant.LOGIN_LOCKED + username;
        String failCountKey = RedisConstant.LOGIN_FAIL + username;
        Boolean isLocked = redisTemplate.hasKey(lockKey);
        if (isLocked) {
            Long ttl = redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
            long remainingMinutes = ttl != null ? (ttl + 59) / 60 : 30;
            return Result.error(String.format(MessageConstant.ACCOUNT_LOCKED, remainingMinutes));
        }
        User user = userMapper.selectByUsername(username);
        if (user == null) return Result.error(MessageConstant.USERNAME_OR_PASSWORD_ERROR);
        if (user.getStatus() == 0) return Result.error(MessageConstant.USER_DISABLED);
        byte[] bytes = DigestUtils.md5Digest(password.getBytes());
        String encryptedPassword = DigestUtils.md5DigestAsHex(bytes);
        if (!user.getPassword().equals(encryptedPassword)) {
            Long failCount = redisTemplate.opsForValue().increment(failCountKey);
            if (failCount == 1)
                redisTemplate.expire(failCountKey, loginSecurityConfig.getFailTimeWindow(), TimeUnit.MILLISECONDS);
            if (failCount >= loginSecurityConfig.getMaxFailCount()) {
                redisTemplate.opsForValue().set(lockKey, RedisConstant.LOCKED, loginSecurityConfig.getLockDuration(), TimeUnit.MILLISECONDS);
                redisTemplate.delete(failCountKey);
                return Result.error(MessageConstant.PASSWORD_ERROR_TOO_MANY);
            }
            int remainingAttempts = loginSecurityConfig.getMaxFailCount() - failCount.intValue();
            return Result.error(String.format(MessageConstant.USERNAME_OR_PASSWORD_ERROR_WITH_REMAINING, remainingAttempts));
        }
        redisTemplate.delete(failCountKey);
        String accessToken = JwtTool.createJWT(jwtConfig.getSecret(), jwtConfig.getExpiration(), new HashMap<>(), String.valueOf(user.getUserId()));
        String refreshToken = JwtTool.createJWT(jwtConfig.getSecret(), jwtConfig.getRefreshExpiration(), new HashMap<>(), String.valueOf(user.getUserId()));
        redisTemplate.opsForValue().set(RedisConstant.JWT_ACCESS + user.getUserId(), accessToken, jwtConfig.getExpiration(), TimeUnit.MILLISECONDS);
        redisTemplate.opsForValue().set(RedisConstant.JWT_REFRESH + user.getUserId(), refreshToken, jwtConfig.getRefreshExpiration(), TimeUnit.MILLISECONDS);
        redisTemplate.opsForValue().set(RedisConstant.JWT_ID + user.getUserId(), user, jwtConfig.getExpiration(), TimeUnit.MILLISECONDS);
        return Result.success(new TokenResponseDto(accessToken, refreshToken));
    }

    public Result<String> register(User user) {
        User u = userMapper.selectByUsername(user.getUsername());
        if (u != null) return Result.error(MessageConstant.USER_ALREADY_EXIST);
        byte[] bytes = DigestUtils.md5Digest(user.getPassword().getBytes());
        user.setPassword(DigestUtils.md5DigestAsHex(bytes));
        user.setStatus(1);
        user.setUserId(java.util.UUID.randomUUID().toString());
        user.setCreateTime(new java.util.Date());
        user.setUpdateTime(new java.util.Date());
        userMapper.insert(user);
        return Result.success(MessageConstant.REGISTER_SUCCESS);
    }

    public Result<TokenResponseDto> refreshToken(RefreshTokenDTO dto) {
        String refreshToken = dto.getRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            log.error("令牌为空");
            return Result.error(MessageConstant.REFRESH_TOKEN_EMPTY);
        }
        try {
            io.jsonwebtoken.Claims claims = JwtTool.parseJWT(jwtConfig.getSecret(), refreshToken);
            String userId = claims.getId();
            String storedRefreshToken = (String) redisTemplate.opsForValue().get(RedisConstant.JWT_REFRESH + userId);
            if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
                log.error("刷新令牌无效或已过期");
                return Result.error(MessageConstant.REFRESH_TOKEN_INVALID);
            }
            User user = (User) redisTemplate.opsForValue().get(RedisConstant.JWT_ID + userId);
            if (user == null) {
                user = userMapper.selectByUserId(userId);
                if (user == null) {
                    log.error("用户不存在");
                    return Result.error(MessageConstant.USER_NOT_EXIST);
                }
            }
            if (user.getStatus() == 0) {
                log.error("用户已禁用");
                return Result.error(MessageConstant.USER_DISABLED);
            }
            String newAccessToken = JwtTool.createJWT(jwtConfig.getSecret(), jwtConfig.getExpiration(), new HashMap<>(), userId);
            String newRefreshToken = JwtTool.createJWT(jwtConfig.getSecret(), jwtConfig.getRefreshExpiration(), new HashMap<>(), userId);
            redisTemplate.opsForValue().set(RedisConstant.JWT_ACCESS + userId, newAccessToken, jwtConfig.getExpiration(), TimeUnit.MILLISECONDS);
            redisTemplate.opsForValue().set(RedisConstant.JWT_REFRESH + userId, newRefreshToken, jwtConfig.getRefreshExpiration(), TimeUnit.MILLISECONDS);
            redisTemplate.opsForValue().set(RedisConstant.JWT_ID + userId, user, jwtConfig.getExpiration(), TimeUnit.MILLISECONDS);
            log.info("刷新令牌成功");
            return Result.success(new TokenResponseDto(newAccessToken, newRefreshToken));
        } catch (Exception e) {
            log.error("刷新令牌失败", e);
            return Result.error(MessageConstant.REFRESH_TOKEN_INVALID);
        }
    }

    public Result<List<ModelInfo>> fetchModels(String apiKey, String baseURL) {
        if (apiKey == null || apiKey.isBlank()) {
            return Result.error(MessageConstant.API_KEY_EMPTY_ERROR);
        }
        if (baseURL == null || baseURL.isBlank()) {
            return Result.error("Base URL 不能为空");
        }
        String url = baseURL.endsWith("/") ? baseURL + "v1/models" : baseURL + "/v1/models";
        List<ModelInfo> models = new ArrayList<>();
        String json = webClient.get()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10));
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode jsonNode = objectMapper.readTree(json).get("data");
            for (int i = 0; i < jsonNode.size(); i++) {
                JsonNode modelNode = jsonNode.get(i);
                ModelInfo model = new ModelInfo();
                if (modelNode.has("id")) model.setId(modelNode.get("id").asText());
                if (modelNode.has("object")) model.setObject(modelNode.get("object").asText());
                if (modelNode.has("created")) model.setCreated(modelNode.get("created").asLong());
                if (modelNode.has("owned_by")) model.setOwned_by(modelNode.get("owned_by").asText());
                models.add(model);
            }
        } catch (JsonProcessingException e) {
            log.info("解析Json失败：无法解析来自{}的模型列表", url);
            return Result.error("解析Json失败：无法解析来自" + url + "的模型列表");
        }
        log.info("拉取到模型：{}", models);
        return Result.success(models);
    }

    public Result configApi(String userId, String apiKey, String baseURL, String modelName, String configName) {
        Result<?> validationResult = fetchModels(apiKey, baseURL);
        if (validationResult.getCode() != 1) return Result.error(validationResult.getMsg());
        if (modelName != null && !modelName.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<ModelInfo> models = (List<ModelInfo>) validationResult.getData();
            boolean modelExists = models.stream().anyMatch(m -> m.getId().equals(modelName));
            if (!modelExists) return Result.error("模型 \"" + modelName + "\" 不在可用列表中");
        }
        UserApi userApi = new UserApi();
        userApi.setApiKey(apiKey);
        userApi.setBaseURL(baseURL);
        userApi.setModelName(modelName);
        userApi.setConfigName(configName);
        userApi.setUserId(userId);
        userApiMapper.insert(userApi);
        log.info("用户 {} 保存 API 配置成功 - configName: {}, baseURL: {}, model: {}", userId, configName, baseURL, modelName);
        return Result.success();
    }

    public Result updateConfig(Long id, String userId, String apiKey, String baseURL, String modelName, String configName) {
        UserApi userApi = userApiMapper.selectById(id);
        if (userApi == null) return Result.error("配置不存在");
        if (!userApi.getUserId().equals(userId)) return Result.error("无权修改该配置");
        Result<?> validationResult = fetchModels(apiKey, baseURL);
        if (validationResult.getCode() != 1) return Result.error(validationResult.getMsg());
        if (modelName != null && !modelName.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<ModelInfo> models = (List<ModelInfo>) validationResult.getData();
            boolean modelExists = models.stream().anyMatch(m -> m.getId().equals(modelName));
            if (!modelExists) return Result.error("模型 \"" + modelName + "\" 不在可用列表中");
        }
        userApi.setApiKey(apiKey);
        userApi.setBaseURL(baseURL);
        userApi.setModelName(modelName);
        userApi.setConfigName(configName);
        userApiMapper.updateById(userApi);
        log.info("用户 {} 更新 API 配置成功 - id: {}, configName: {}, baseURL: {}, model: {}",
                userId, id, configName, baseURL, modelName);
        return Result.success();
    }

    public Result<List<UserApi>> getApiConfig(String userId) {
        QueryWrapper<UserApi> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        List<UserApi> userApiList = userApiMapper.selectList(wrapper);
        if (userApiList == null || userApiList.isEmpty()) return Result.error("未找到API配置，请先配置");
        return Result.success(userApiList);
    }

    /**
     * 注销用户。
     * <p>
     * 执行以下清理操作：
     * <ol>
     *   <li>将用户状态设为禁用（status=0）</li>
     *   <li>清除 Redis 中的 JWT 令牌（access + refresh + 用户缓存）</li>
     *   <li>删除用户所有 API 配置</li>
     *   <li>断开所有 MCP Server 连接并删除配置</li>
     *   <li>删除该用户所有的conversation</li>
     * </ol>
     * </p>
     *
     * @param userId 用户 ID
     * @return 操作结果
     */
    public Result<Void> deactivateUser(String userId) {
        // 1. 检查用户是否存在
        User user = userMapper.selectByUserId(userId);
        if (user == null) {
            return Result.error(MessageConstant.USER_NOT_EXIST);
        }

        // 2. 设置状态为禁用
        user.setStatus(0);
        user.setUpdateTime(new java.util.Date());
        userMapper.updateById(user);

        // 3. 清除 Redis 令牌
        redisTemplate.delete(RedisConstant.JWT_ACCESS + userId);
        redisTemplate.delete(RedisConstant.JWT_REFRESH + userId);
        redisTemplate.delete(RedisConstant.JWT_ID + userId);

        // 4. 删除用户所有 API 配置
        QueryWrapper<UserApi> apiWrapper = new QueryWrapper<>();
        apiWrapper.eq("user_id", userId);
        userApiMapper.delete(apiWrapper);

        // 5. 清理所有 MCP Server（断开连接 + 删除 MySQL 记录）
        mcpService.removeAllMcpServers(userId);

        // 6. 删除用户所有conversation
        List<UserConversation> userConversations = userConversationMapper.selectByUserId(userId);
        for ( UserConversation uc : userConversations) {
            conversationService.deleteConversation(uc.getConversationId());
        }

        log.info("用户已注销: userId={}, username={}", userId, user.getUsername());
        return Result.success();
    }
}
