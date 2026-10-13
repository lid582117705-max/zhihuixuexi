package com.boxue.module.user.controller;

import com.boxue.common.result.Result;
import com.boxue.module.user.dto.LoginRequest;
import com.boxue.module.user.dto.LoginResponse;
import com.boxue.module.user.dto.RegisterRequest;
import com.boxue.module.user.dto.UserVO;
import com.boxue.module.user.service.UserService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户服务-用户管理", description = "注册 / 登录 / 当前用户 / 退出")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "用户注册", description = "BCrypt 加密存储")
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(userService.register(request));
    }

    @Operation(summary = "用户登录", description = "校验密码，签发 JWT，会话写 Redis")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(userService.login(request));
    }

    @Operation(summary = "当前用户信息", description = "需要登录，身份来自网关 X-User-Id")
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(userService.currentUser());
    }

    /** 内部接口：按 id 查用户（订单服务 Feign 调用；不存在/禁用时 data=null） */
    @Operation(summary = "按 id 查用户（内部接口）", description = "订单服务 Feign 调用；不存在/禁用时 data=null")
    @GetMapping("/{id}")
    public Result<UserVO> getById(@PathVariable Long id) {
        return Result.ok(userService.getById(id));
    }

    @Operation(summary = "退出登录", description = "删除 Redis 会话白名单")
    @PostMapping("/logout")
    public Result<Void> logout() {
        userService.logout();
        return Result.ok();
    }
}