package me.beiming.aichat.mapper;

import me.beiming.aichat.entity.user.UserConversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface UserConversationMapper extends BaseMapper<UserConversation> {

    @Select("select * from user_conversation where conversation_id = #{conversationId}")
    UserConversation selectByConversationId(String conversationId);

    @Delete("delete from user_conversation where conversation_id = #{conversationId}")
    void deleteByConversationId(String conversationId);

    @Select("select * from user_conversation where user_id = #{userId}")
    List<UserConversation> selectByUserId(String userId);

}