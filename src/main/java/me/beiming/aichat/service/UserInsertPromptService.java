package me.beiming.aichat.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import me.beiming.aichat.entity.Result;
import me.beiming.aichat.entity.user.UserInsertPrompt;
import me.beiming.aichat.mapper.UserInsertPromptMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 用户预定义注入提示词管理。
 */
@Slf4j
@Service
public class UserInsertPromptService {

    @Autowired
    private UserInsertPromptMapper userInsertPromptMapper;

    /**
     * 创建注入提示词。
     *
     * @param userId     用户ID（必填）
     * @param promptName 提示词名称（必填）
     * @param content    提示词内容（必填）
     * @param depth      注入深度，null 时取 0
     */
    public Result<UserInsertPrompt> createInsertPrompt(String userId, String promptName, String content, Integer depth) {
        if (userId == null || userId.trim().isEmpty()) {
            return Result.error("用户ID不能为空");
        }
        if (promptName == null || promptName.trim().isEmpty()) {
            return Result.error("提示词名称不能为空");
        }
        if (content == null || content.trim().isEmpty()) {
            return Result.error("提示词内容不能为空");
        }

        UserInsertPrompt prompt = new UserInsertPrompt();
        prompt.setUserId(userId);
        prompt.setPromptName(promptName.trim());
        prompt.setContent(content);
        prompt.setDepth(depth != null && depth >= 0 ? depth : 0);
        prompt.setCreateTime(new Date());
        userInsertPromptMapper.insert(prompt);
        log.info("用户 {} 创建注入提示词: id={}, name={}, depth={}", userId, prompt.getId(), promptName, prompt.getDepth());
        return Result.success(prompt);
    }

    /**
     * 更新注入提示词（部分更新，仅更新非 null 字段）。
     */
    public Result<UserInsertPrompt> updateInsertPrompt(Long id, String userId, String promptName, String content, Integer depth) {
        UserInsertPrompt prompt = userInsertPromptMapper.selectById(id);
        if (prompt == null) {
            return Result.error("提示词不存在");
        }
        if (!prompt.getUserId().equals(userId)) {
            return Result.error("无权修改该提示词");
        }
        if (promptName != null && !promptName.trim().isEmpty()) {
            prompt.setPromptName(promptName.trim());
        }
        if (content != null && !content.trim().isEmpty()) {
            prompt.setContent(content);
        }
        if (depth != null && depth >= 0) {
            prompt.setDepth(depth);
        }
        userInsertPromptMapper.updateById(prompt);
        log.info("用户 {} 更新注入提示词: id={}", userId, id);
        return Result.success(prompt);
    }

    /**
     * 删除注入提示词。
     */
    public Result<Void> deleteInsertPrompt(Long id, String userId) {
        UserInsertPrompt prompt = userInsertPromptMapper.selectById(id);
        if (prompt == null) {
            return Result.error("提示词不存在");
        }
        if (!prompt.getUserId().equals(userId)) {
            return Result.error("无权删除该提示词");
        }
        userInsertPromptMapper.deleteById(id);
        log.info("用户 {} 删除注入提示词: id={}", userId, id);
        return Result.success();
    }

    /**
     * 获取用户的全部注入提示词，按 id 升序。
     */
    public Result<List<UserInsertPrompt>> listInsertPrompts(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return Result.error("用户ID不能为空");
        }
        QueryWrapper<UserInsertPrompt> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByAsc("id");
        List<UserInsertPrompt> prompts = userInsertPromptMapper.selectList(wrapper);
        return Result.success(prompts);
    }
}
