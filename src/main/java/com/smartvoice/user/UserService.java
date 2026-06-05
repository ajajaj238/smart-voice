package com.smartvoice.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartvoice.user.dto.LoginRequest;
import com.smartvoice.user.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(RegisterRequest request) {
        var wrapper = new LambdaQueryWrapper<User>();
        wrapper.eq(User::getUsername, request.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("Username already exists");
        }
        User user = User.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .build();
        userMapper.insert(user);
        return user;
    }

    public User authenticate(LoginRequest request) {
        var wrapper = new LambdaQueryWrapper<User>();
        wrapper.eq(User::getUsername, request.getUsername());
        User user = userMapper.selectOne(wrapper);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid username or password");
        }
        return user;
    }

    public User getById(String id) {
        User user = userMapper.selectById(id);
        if (user == null) throw new RuntimeException("User not found");
        return user;
    }
}
