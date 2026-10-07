package com.boxue.module.user.dto;

import lombok.Data;

/** 远程用户信息 DTO（order 侧复制副本，不依赖 user-service 模块） */
@Data
public class UserVO {
    private Long id;
    private String username;
    private String nickname;
}