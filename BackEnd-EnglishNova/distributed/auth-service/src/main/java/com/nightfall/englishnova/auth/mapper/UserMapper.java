package com.nightfall.englishnova.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nightfall.englishnova.auth.domain.po.UserPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper extends BaseMapper<UserPo> {

    int countByUsername(@Param("username") String username);

    int countByEmail(@Param("email") String email);

    UserPo findByAccount(@Param("account") String account, @Param("emailAccount") String emailAccount);
}
