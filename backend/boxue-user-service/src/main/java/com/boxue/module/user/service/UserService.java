package com.boxue.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.boxue.common.auth.AuthInterceptor;
import com.boxue.common.auth.JwtUtil;
import com.boxue.common.auth.UserContext;
import com.boxue.common.exception.AuthException;
import com.boxue.common.exception.BizException;
import com.boxue.module.user.dto.LoginRequest;
import com.boxue.module.user.dto.LoginResponse;
import com.boxue.module.user.dto.RegisterRequest;
import com.boxue.module.user.dto.UserVO;
import com.boxue.module.user.entity.User;
import com.boxue.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    public UserVO register(RegisterRequest request) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (count != null && count > 0) {
            throw new BizException("用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(StringUtils.hasText(request.getNickname())
                ? request.getNickname()
                : request.getUsername());
        user.setStatus(1);
        userMapper.insert(user);
        return toVO(user);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (user == null) {
            throw new BizException("用户名或密码错误");
        }
        boolean passwordOk;
        try {
            passwordOk = passwordEncoder.matches(request.getPassword(), user.getPassword());
        } catch (IllegalArgumentException e) {
            // 阶段一留下的明文密码无法用 BCrypt 校验，需要重新注册
            passwordOk = false;
        }
        if (!passwordOk) {
            throw new BizException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException("账号已被禁用");
        }

        String token = jwtUtil.createToken(user.getId(), user.getUsername());
        redisTemplate.opsForValue().set(
                AuthInterceptor.TOKEN_KEY_PREFIX + user.getId(),
                token,
                jwtUtil.expireSeconds(),
                TimeUnit.SECONDS
        );
        return new LoginResponse(token, toVO(user));
    }

    public void logout() {
        Long userId = UserContext.getUserId();
        if (userId != null) {
            redisTemplate.delete(AuthInterceptor.TOKEN_KEY_PREFIX + userId);
        }
    }

    public UserVO currentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new AuthException("请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new AuthException("用户不存在");
        }
        return toVO(user);
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        return vo;
    }
}
