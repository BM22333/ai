package me.beiming.aichat.entity.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import me.beiming.aichat.constant.RoleConstant;

/**
 * 用户消息，角色固定为 USER。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserMessage extends Message {

    public UserMessage(String content) {
        super(RoleConstant.USER, content);
    }

    public UserMessage(String content, int depth) {
        super(RoleConstant.USER, content);
        this.setDepth(depth);
    }
}
