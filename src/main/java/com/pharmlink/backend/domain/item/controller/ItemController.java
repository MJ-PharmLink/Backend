package com.pharmlink.backend.domain.item.controller;

import com.pharmlink.backend.domain.item.dto.ItemCreateRequest;
import com.pharmlink.backend.domain.item.dto.ItemResponse;
import com.pharmlink.backend.domain.item.dto.ItemUpdateRequest;
import com.pharmlink.backend.domain.item.service.ItemService;
import com.pharmlink.backend.global.response.ApiResponse;
import com.pharmlink.backend.global.security.AccessRole;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    // 상품 목록 조회
    @PreAuthorize(AccessRole.ALL)
    @GetMapping
    public ApiResponse<List<ItemResponse>> getItems(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize
    ) {
        Page<ItemResponse> items = itemService.getItems(page, pageSize);
        return ApiResponse.success(items);
    }

    // 상품 상세 조회
    @PreAuthorize(AccessRole.ALL)
    @GetMapping("/{id}")
    public ApiResponse<ItemResponse> getItem(
            @PathVariable("id") Long itemId
    ) {
        return ApiResponse.success(itemService.getItem(itemId));
    }

    // 상품 등록
    @PreAuthorize(AccessRole.ADMIN_WAREHOUSE)
    @PostMapping
    public ResponseEntity<ApiResponse<ItemResponse>> createItem(
            @Valid @RequestBody ItemCreateRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(itemService.createItem(request)));
    }

    // 상품 수정
    @PreAuthorize(AccessRole.ADMIN_WAREHOUSE)
    @PatchMapping("/{id}")
    public ApiResponse<ItemResponse> updateItem(
            @PathVariable("id") Long itemId,
            @Valid @RequestBody ItemUpdateRequest request
    ) {
        return ApiResponse.success(itemService.updateItem(itemId, request));
    }

    // 상품 비활성화 (Soft Delete)
    @PreAuthorize(AccessRole.ADMIN_WAREHOUSE)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateItem(
            @PathVariable("id") Long itemId
    ) {
        itemService.deactivateItem(itemId);

        return ResponseEntity.noContent().build();
    }
}

