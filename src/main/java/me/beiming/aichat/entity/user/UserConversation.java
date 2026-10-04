package me.beiming.aichat.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("user_conversation")
public class UserConversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String conversationId;
    private String userId;

    public UserConversation(String conversationId, String userId) {
        this.conversationId = conversationId;
        this.userId = userId;
    }
}