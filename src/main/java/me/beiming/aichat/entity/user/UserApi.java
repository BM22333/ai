package me.beiming.aichat.entity.user;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

@Data
public class UserApi {
    private Long id;
    private String userId;
    private String apiKey;
    private String modelName;
    @TableField("base_url")
    private String baseURL;
    @TableField("config_name")
    private String configName;
}
