package me.beiming.aichat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.ai.chat.memory.ChatMemory;

@Mapper
public interface ContextMemoryMapper extends BaseMapper<ChatMemory> {

    @Delete("delete from spring_ai_chat_memory where conversation_id = #{conversationId}")
    void deleteByConversationId(String conversationId);
}
