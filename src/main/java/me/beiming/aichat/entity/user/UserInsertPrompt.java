package me.beiming.aichat.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 用户预定义注入提示词，对应 user_insert_prompt 表。
 */
@Data
@TableName("user_insert_prompt")
public class UserInsertPrompt {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String promptName;
    private String content;

    /** 注入深度：插入到历史中最后一条 depth 相同的消息之后 */
    private Integer depth;

    private Date createTime;
}
