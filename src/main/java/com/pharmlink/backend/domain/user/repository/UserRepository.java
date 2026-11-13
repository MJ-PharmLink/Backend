package com.pharmlink.backend.domain.user.repository;

import com.pharmlink.backend.domain.user.entity.Role;
import com.pharmlink.backend.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByRole(Role role);

    // 사용자 목록: role이 null이면 전체 역할, includeInactive가 false면 활성 계정만
    @Query("""
            select u from User u
            where (:role is null or u.role = :role)
              and (:includeInactive = true or u.isActive = true)
            """)
    Page<User> search(@Param("role") Role role,
                      @Param("includeInactive") boolean includeInactive,
                      Pageable pageable);
}
