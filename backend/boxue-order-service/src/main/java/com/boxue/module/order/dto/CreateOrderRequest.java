package com.boxue.module.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateOrderRequest {

    @NotNull(message = "课程 ID 不能为空")
    private Long courseId;
}
