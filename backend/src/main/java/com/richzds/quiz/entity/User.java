package com.richzds.quiz.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 用户（学员和管理员）（表 app_user）。时间字段均为 UTC。 */
@TableName("app_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private String role;
    private String status;
    private Long targetCategoryId;
    private Integer wrongRemoveThreshold;
    private LocalDateTime lastActiveAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public User setId(Long id) {
        this.id = id;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public User setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public User setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        return this;
    }

    public String getNickname() {
        return nickname;
    }

    public User setNickname(String nickname) {
        this.nickname = nickname;
        return this;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public User setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
        return this;
    }

    public String getRole() {
        return role;
    }

    public User setRole(String role) {
        this.role = role;
        return this;
    }

    public String getStatus() {
        return status;
    }

    public User setStatus(String status) {
        this.status = status;
        return this;
    }

    public Long getTargetCategoryId() {
        return targetCategoryId;
    }

    public User setTargetCategoryId(Long targetCategoryId) {
        this.targetCategoryId = targetCategoryId;
        return this;
    }

    public Integer getWrongRemoveThreshold() {
        return wrongRemoveThreshold;
    }

    public User setWrongRemoveThreshold(Integer wrongRemoveThreshold) {
        this.wrongRemoveThreshold = wrongRemoveThreshold;
        return this;
    }

    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }

    public User setLastActiveAt(LocalDateTime lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public User setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public User setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }
}
