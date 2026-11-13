package com.pharmlink.backend.domain.user.entity;

import com.pharmlink.backend.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// users
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
        indexes = @Index(name = "idx_users_role_active", columnList = "role, is_active"))
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String username;

    // BCrypt 해시
    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean isActive;

    // password는 반드시 PasswordEncoder로 인코딩한 값을 넘긴다
    public static User create(String username, String encodedPassword, String name, Role role) {
        User user = new User();
        user.username = username;
        user.password = encodedPassword;
        user.name = name;
        user.role = role;
        user.isActive = true;
        return user;
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changeRole(Role role) {
        this.role = role;
    }

    // encodedPassword는 반드시 PasswordEncoder로 인코딩한 값을 넘긴다
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
