
package com.pharmlink.backend.domain.item.entity;

import com.pharmlink.backend.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long categoryId;

    @Column(
            name = "category_name",
            nullable = false,
            length = 50,
            unique = true
    )
    private String categoryName;

    @Column(name = "description", length = 200)
    private String description;

    // 카테고리 등록
    public Category(String categoryName, String description) {
        this.categoryName = categoryName;
        this.description = description;
    }

    // 카테고리 수정
    public void update(String categoryName, String description) {
        this.categoryName = categoryName;

        if (description != null) {
            this.description = description;
        }
    }
}
