package com.boxue.module.learn.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 一条学习对话消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    /** 说话角色：user 用户 / assistant 助手 */
    private String role;
    /** 消息内容 */
    private String content;
    /** 发送时间 */
    private LocalDateTime time;
}