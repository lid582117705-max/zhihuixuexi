package com.boxue.module.learn.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户发给 AI 助教的一句话
 */
@Data
public class ChatRequest {

    @NotBlank(message = "message 不能为空")
    private String message;
}