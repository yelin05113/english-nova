package com.nightfall.englishnova.auth.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName("users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPo {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String email;
    @TableField("avatar_url")
    private String avatarUrl;
    private String passwordHash;
    private String status;
}
