package com.richzds.quiz.service;

import java.time.Clock;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.richzds.quiz.common.BizException;
import com.richzds.quiz.common.ErrorCode;
import com.richzds.quiz.common.TimeUtil;
import com.richzds.quiz.dto.AuthDtos.AuthResult;
import com.richzds.quiz.dto.AuthDtos.ChangePasswordRequest;
import com.richzds.quiz.dto.AuthDtos.LoginRequest;
import com.richzds.quiz.dto.AuthDtos.RegisterRequest;
import com.richzds.quiz.dto.AuthDtos.UpdateMeRequest;
import com.richzds.quiz.dto.AuthDtos.UserView;
import com.richzds.quiz.entity.Category;
import com.richzds.quiz.entity.User;
import com.richzds.quiz.mapper.CategoryMapper;
import com.richzds.quiz.mapper.UserMapper;
import com.richzds.quiz.security.JwtService;
import com.richzds.quiz.security.JwtService.IssuedToken;

/** 注册、登录、个人资料和改密码。 */
@Service
public class UserService {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 错题连续答对几次自动移出的默认值（库里列默认值也是 3）。 */
    public static final int DEFAULT_WRONG_REMOVE_THRESHOLD = 3;

    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    public UserService(UserMapper userMapper, CategoryMapper categoryMapper, PasswordEncoder passwordEncoder,
                       JwtService jwtService, Clock clock) {
        this.userMapper = userMapper;
        this.categoryMapper = categoryMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    @Transactional
    public AuthResult register(RegisterRequest req) {
        if (findByUsername(req.username()) != null) {
            throw new BizException(ErrorCode.USERNAME_TAKEN);
        }
        LocalDateTime now = TimeUtil.nowUtc(clock);
        String nickname = req.nickname() == null || req.nickname().isBlank() ? req.username() : req.nickname().strip();
        User user = new User()
                .setUsername(req.username())
                .setPasswordHash(passwordEncoder.encode(req.password()))
                .setNickname(nickname)
                .setRole(ROLE_USER)
                .setStatus(STATUS_ACTIVE)
                .setWrongRemoveThreshold(DEFAULT_WRONG_REMOVE_THRESHOLD)
                .setLastActiveAt(now)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.USERNAME_TAKEN);
        }
        return issue(user);
    }

    public AuthResult login(LoginRequest req) {
        User user = findByUsername(req.username());
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
        return issue(user);
    }

    public UserView me(long userId) {
        return view(requireUser(userId));
    }

    /**
     * 改资料。只写这次传了的列：先读整行再 updateById 写回，会把并发的修改一起覆盖掉，
     * 比如管理员刚禁用账号时正好有一个改资料请求在途，账号就被写回成启用。
     */
    @Transactional
    public UserView updateMe(long userId, UpdateMeRequest req) {
        LambdaUpdateWrapper<User> set = new LambdaUpdateWrapper<User>().eq(User::getId, userId)
                .set(User::getUpdatedAt, TimeUtil.nowUtc(clock));
        if (req.nickname() != null) {
            if (req.nickname().isBlank()) {
                throw BizException.invalid("nickname", "昵称不能为空");
            }
            set.set(User::getNickname, req.nickname().strip());
        }
        if (req.avatarUrl() != null) {
            set.set(User::getAvatarUrl, req.avatarUrl().isBlank() ? null : req.avatarUrl().strip());
        }
        if (req.targetCategoryId() != null) {
            Category c = categoryMapper.selectById(req.targetCategoryId());
            if (c == null || c.getLevel() != CategoryTree.LEVEL_EXAM) {
                throw BizException.invalid("targetCategoryId", "目标考试必须是一级分类");
            }
            set.set(User::getTargetCategoryId, c.getId());
        }
        userMapper.update(null, set);
        return view(requireUser(userId));
    }

    @Transactional
    public UserView updateSettings(long userId, int wrongRemoveThreshold) {
        User user = requireUser(userId);
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getWrongRemoveThreshold, wrongRemoveThreshold)
                .set(User::getUpdatedAt, TimeUtil.nowUtc(clock)));
        return view(user.setWrongRemoveThreshold(wrongRemoveThreshold));
    }

    /** 本人的错题自动移出阈值。 */
    public static int wrongRemoveThreshold(User u) {
        return u.getWrongRemoveThreshold() == null ? DEFAULT_WRONG_REMOVE_THRESHOLD : u.getWrongRemoveThreshold();
    }

    @Transactional
    public void changePassword(long userId, ChangePasswordRequest req) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
            throw BizException.invalid("oldPassword", "原密码不正确");
        }
        // 只写密码这一列，理由同 updateMe
        userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, userId)
                .set(User::getPasswordHash, passwordEncoder.encode(req.newPassword()))
                .set(User::getUpdatedAt, TimeUtil.nowUtc(clock)));
    }

    /**
     * 锁住这个用户的行（UPDATE 一个不变的值），让同一个人的某类并发请求排队。
     * 必须在事务里第一个调用：MySQL 可重复读的快照在第一次普通读时才建立，先拿锁，之后的读就能看到
     * 前一个请求刚提交的数据。
     */
    public void lockRow(long userId) {
        userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, userId)
                .setSql("updated_at = updated_at"));
    }

    public User requireUser(long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    public UserView view(User u) {
        String targetName = null;
        if (u.getTargetCategoryId() != null) {
            Category c = categoryMapper.selectById(u.getTargetCategoryId());
            targetName = c == null ? null : c.getName();
        }
        return new UserView(u.getId(), u.getUsername(), u.getNickname(), u.getAvatarUrl(), u.getRole(),
                u.getStatus(), u.getTargetCategoryId(), targetName, wrongRemoveThreshold(u),
                TimeUtil.toInstant(u.getCreatedAt()));
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    private AuthResult issue(User user) {
        IssuedToken token = jwtService.issue(user.getId());
        return new AuthResult(token.token(), token.expiresAt(), view(user));
    }
}
