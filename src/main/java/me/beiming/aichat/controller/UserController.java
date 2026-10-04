package me.beiming.aichat.controller;


import me.beiming.aichat.constant.MessageConstant;
import me.beiming.aichat.dto.*;
import me.beiming.aichat.entity.ModelInfo;
import me.beiming.aichat.entity.Result;
import me.beiming.aichat.entity.user.User;
import me.beiming.aichat.entity.user.UserApi;
import me.beiming.aichat.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/user")
@Tag(name = "用户管理", description = "用户注册、登录、认证相关接口")
public class UserController {
    @Autowired
    private UserService userService;

    @Operation(summary = "登录", description = "用户登录接口")
    @PostMapping("/login")
    public Result<TokenResponseDto> login(@RequestBody LoginDTO dto) {
        String username = dto.getUsername();
        String password = dto.getPassword();
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return Result.error(MessageConstant.USERNAME_OR_PASSWORD_EMPTY);
        }

        return userService.login(username, password);
    }

    @Operation(summary = "注册", description = "用户注册接口")
    @PostMapping("/register")
    public Result<String> register(@RequestBody User user) {
        return userService.register(user);
    }

    @Operation(summary = "刷新令牌", description = "刷新访问令牌接口")
    @PostMapping("/refresh")
    public Result<TokenResponseDto> refresh(@RequestBody RefreshTokenDTO dto) {
        return userService.refreshToken(dto);
    }

    @Operation(
            summary = "获取可用模型列表",
            description = """
                    根据 apiKey 和 baseURL 向 AI 服务商拉取可用模型列表，同时验证凭证有效性。
                    
                    调用时机：用户输入 API 配置后，点击"验证并获取模型"按钮时调用。
                    
                    成功返回模型列表后，前端应展示下拉列表供用户选择，然后调用 /user/config 保存完整配置。
                    
                    错误场景：
                    
                    - 401/403: API Key 无效或没有权限
                    
                    - 连接失败: baseURL 地址不可达
                    
                    - 格式异常: 返回的数据不符合预期结构"""
    )
    @PostMapping("/models")
    public Result<List<ModelInfo>> fetchModels(@RequestBody UserApiDTO dto) {
        String apiKey = dto.getApiKey();
        String baseURL = dto.getBaseUrl();
        if (apiKey == null || apiKey.isEmpty()) {
            return Result.error("apiKey不能为空");
        }
        if (baseURL == null || baseURL.isEmpty()) {
            return Result.error("baseURL不能为空");
        }
        return userService.fetchModels(apiKey, baseURL);
    }

    @Operation(
            summary = "保存 API 配置",
            description = """
                    保存用户的 apiKey、baseURL、模型名称和配置名称。保存前会自动调用模型列表接口验证凭证有效性：

                    1. 凭证无效则直接返回错误，不保存

                    2. 指定的模型不在可用列表中则返回错误

                    3. 验证通过后保存新配置（保留旧配置，支持一个用户管理多套API）

                    调用时机：用户在模型列表中选择模型后，点击"保存配置"时调用。"""
    )
    @PostMapping("/config")
    public Result configApi(@RequestBody UserApiDTO dto) {
        String userId = dto.getUserId();
        String apiKey = dto.getApiKey();
        String baseURL = dto.getBaseUrl();
        String modelName = dto.getModelName();
        String configName = dto.getConfigName();
        log.debug("baseURL:{}", baseURL);
        if (userId == null || userId.isEmpty()) {
            return Result.error("用户ID不能为空");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            return Result.error("apiKey不能为空");
        }
        if (baseURL == null || baseURL.isEmpty()) {
            return Result.error("baseURL不能为空");
        }
        if (modelName == null || modelName.isEmpty()) {
            return Result.error("模型名称不能为空");
        }
        if (configName == null || configName.isEmpty()) {
            return Result.error("配置名称不能为空");
        }

        return userService.configApi(userId, apiKey, baseURL, modelName, configName);
    }

    @Operation(
            summary = "查询 API 配置",
            description = "获取用户已保存的所有 API 配置列表（apiKey、baseURL、模型名称、配置名称），用于前端展示配置详情和创建会话时一键填充"
    )
    @PostMapping("/api-config")
    public Result<List<UserApi>> getApiConfig(@RequestBody UserIdDTO dto) {
        String userId = dto.getUserId();
        if (userId == null || userId.isEmpty()) {
            return Result.error("用户ID不能为空");
        }
        return userService.getApiConfig(userId);
    }

    @Operation(
            summary = "更新 API 配置",
            description = """
                    更新已存在的 API 配置。更新前会验证：

                    1. 配置是否存在且属于当前用户

                    2. 新的 apiKey 和 baseURL 是否有效（调用模型列表接口验证）

                    3. 新的模型名称是否在可用列表中

                    验证通过后更新配置。"""
    )
    @PostMapping("/config/update")
    public Result updateConfig(@RequestBody UserApiDTO dto) {
        Long id = dto.getId();
        String userId = dto.getUserId();
        String apiKey = dto.getApiKey();
        String baseURL = dto.getBaseUrl();
        String modelName = dto.getModelName();
        String configName = dto.getConfigName();
        if (id == null) {
            return Result.error("配置ID不能为空");
        }
        if (userId == null || userId.isEmpty()) {
            return Result.error("用户ID不能为空");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            return Result.error("apiKey不能为空");
        }
        if (baseURL == null || baseURL.isEmpty()) {
            return Result.error("baseURL不能为空");
        }
        if (modelName == null || modelName.isEmpty()) {
            return Result.error("模型名称不能为空");
        }
        if (configName == null || configName.isEmpty()) {
            return Result.error("配置名称不能为空");
        }

        return userService.updateConfig(id, userId, apiKey, baseURL, modelName, configName);
    }

    @Operation(
            summary = "注销用户",
            description = """
                    注销当前用户账号：

                    1. 将用户状态设为禁用

                    2. 清除所有登录令牌（强制重新登录）

                    3. 删除所有 API 配置

                    4. 断开所有 MCP Server 连接并删除配置

                    注销后用户不可登录，数据不可恢复。"""
    )
    @PostMapping("/deactivate")
    public Result<Void> deactivate(@RequestBody UserIdDTO dto) {
        String userId = dto.getUserId();
        if (userId == null || userId.isEmpty()) {
            return Result.error(MessageConstant.USER_ID_EMPTY_ERROR);
        }
        return userService.deactivateUser(userId);
    }
}