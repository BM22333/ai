package me.beiming.aichat.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String username; // 唯一
    private String password;
    private Date createTime;
    private Date updateTime;
    private int status; // 0：禁用，1：启用
}
