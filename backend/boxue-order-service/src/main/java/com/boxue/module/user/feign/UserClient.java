package com.boxue.module.user.feign;

import com.boxue.common.result.Result;
import com.boxue.module.user.dto.UserVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 调用用户服务（boxue-user-service）的声明式客户端。
 * 注意：Feign 是运行时 HTTP 调用，order-service 绝不依赖 user-service 工程，DTO 自己复制一份。
 */
@FeignClient(name = "boxue-user-service")
public interface UserClient {

    /** 对应 user-service 内部接口 GET /api/users/{id}（不存在/禁用时 data=null） */
    @GetMapping("/api/users/{id}")
    Result<UserVO> getUser(@PathVariable("id") Long id);
}