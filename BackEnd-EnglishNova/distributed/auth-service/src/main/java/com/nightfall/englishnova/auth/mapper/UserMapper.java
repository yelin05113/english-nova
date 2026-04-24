package com.nightfall.englishnova.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nightfall.englishnova.auth.domain.po.UserPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper extends BaseMapper<UserPo> {

    int countByUsername(@Param("username") String username);

    int countByEmail(@Param("email") String email);

    int countByUsernameExceptId(@Param("username") String username, @Param("userId") long userId);

    UserPo findByAccount(@Param("account") String account, @Param("emailAccount") String emailAccount);

    int updateProfile(@Param("userId") long userId, @Param("username") String username, @Param("avatarUrl") String avatarUrl);
}
