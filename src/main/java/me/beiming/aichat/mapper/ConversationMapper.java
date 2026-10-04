package me.beiming.aichat.mapper;


import me.beiming.aichat.entity.Conversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

public interface ConversationMapper extends BaseMapper<Conversation> {

    @Select("select * from conversation where conversation_id = #{conversationId}")
    Conversation selectByConversationId(String conversationId);

    @Delete("delete from conversation where conversation_id = #{conversationId}")
    void deleteByConversationId(String conversationId);
}
