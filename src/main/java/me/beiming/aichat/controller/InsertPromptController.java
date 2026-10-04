package me.beiming.aichat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import me.beiming.aichat.dto.InsertPromptDTO;
import me.beiming.aichat.dto.InsertPromptIdDTO;
import me.beiming.aichat.dto.UpdateInsertPromptDTO;
import me.beiming.aichat.dto.UserIdDTO;
import me.beiming.aichat.entity.Result;
import me.beiming.aichat.entity.user.UserInsertPrompt;
import me.beiming.aichat.service.UserInsertPromptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ai/insertPrompt")
@Tag(name = "注入提示词管理", description = "用户预定义注入提示词的增删改查")
public class InsertPromptController {

    @Autowired
    private UserInsertPromptService userInsertPromptService;

    @Operation(summary = "创建注入提示词", description = "depth 为注入深度：插入到历史中最后一条 depth 相同的消息之后")
    @PostMapping("/create")
    public Result<UserInsertPrompt> create(@RequestBody InsertPromptDTO dto) {
        return userInsertPromptService.createInsertPrompt(
                dto.getUserId(), dto.getPromptName(), dto.getContent(), dto.getDepth());
    }

    @Operation(summary = "更新注入提示词", description = "仅更新非null字段")
    @PostMapping("/update")
    public Result<UserInsertPrompt> update(@RequestBody UpdateInsertPromptDTO dto) {
        return userInsertPromptService.updateInsertPrompt(
                dto.getId(), dto.getUserId(), dto.getPromptName(), dto.getContent(), dto.getDepth());
    }

    @Operation(summary = "删除注入提示词")
    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody InsertPromptIdDTO dto) {
        return userInsertPromptService.deleteInsertPrompt(dto.getId(), dto.getUserId());
    }

    @Operation(summary = "获取用户全部注入提示词")
    @PostMapping("/list")
    public Result<List<UserInsertPrompt>> list(@RequestBody UserIdDTO dto) {
        return userInsertPromptService.listInsertPrompts(dto.getUserId());
    }
}
