package me.beiming.aichat.entity.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import me.beiming.aichat.constant.RoleConstant;

/**
 * 系统消息，角色固定为 SYSTEM，用于承载系统提示词与注入提示词。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SystemMessage extends Message {

    public SystemMessage(String content) {
        super(RoleConstant.SYSTEM, content);
    }
}
