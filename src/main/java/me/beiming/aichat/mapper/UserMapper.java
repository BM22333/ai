package me.beiming.aichat.mapper;


import me.beiming.aichat.entity.user.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

public interface  UserMapper extends BaseMapper<User> {

    @Select("select * from user where username = #{username}")
    User selectByUsername(String username);

    @Select("select * from user where user_id = #{userId}")
    User selectByUserId(String userId);
}
