package com.pharmlink.backend.domain.item.service;

import com.pharmlink.backend.domain.item.dto.ItemResponse;
import com.pharmlink.backend.domain.item.entity.Item;
import com.pharmlink.backend.domain.item.repository.ItemRepository;
import com.pharmlink.backend.domain.item.repository.CategoryRepository;
import com.pharmlink.backend.domain.partner.service.PartnerService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentCaptor.forClass;

@ExtendWith(MockitoExtension.class)
class ItemSearchServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private PartnerService partnerService;

    @InjectMocks
    private ItemService itemService;

    private Item sampleItem() {
        Item item = Item.create(
                "MED001",
                "테스트 의약품",
                3L,
                "500mg",
                "BOX",
                3000L,
                5000L,
                10,
                1L
        );
        ReflectionTestUtils.setField(item, "itemId", 1L);
        return item;
    }

    @Test
    @DisplayName("상품 목록: 검색 조건을 전달하면 결과가 반환된다")
    void searchItemsWithFilters() {
        when(itemRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(sampleItem())));

        Page<ItemResponse> result = itemService.getItems(
                1, 20, "의약품", 3L, 1L, false
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).itemCode())
                .isEqualTo("MED001");

        var pageableCaptor = forClass(Pageable.class);
        verify(itemRepository).findAll(
                any(Specification.class),
                pageableCaptor.capture()
        );

        assertThat(pageableCaptor.getValue().getPageNumber())
                .isZero();
        assertThat(pageableCaptor.getValue().getPageSize())
                .isEqualTo(20);
    }

    @Test
    @DisplayName("상품 목록: 페이지 크기는 최대 100개로 제한된다")
    void pageSizeLimitedTo100() {
        when(itemRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        itemService.getItems(
                2, 500, null, null, null, null
        );

        var pageableCaptor = forClass(Pageable.class);
        verify(itemRepository).findAll(
                any(Specification.class),
                pageableCaptor.capture()
        );

        assertThat(pageableCaptor.getValue().getPageNumber())
                .isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize())
                .isEqualTo(100);
    }

    @Test
    @DisplayName("상품 목록: 검색 조건이 없어도 조회할 수 있다")
    void searchWithoutFilters() {
        when(itemRepository.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        Page<ItemResponse> result = itemService.getItems(
                null, null, null, null, null, null
        );

        assertThat(result.getContent()).isEmpty();

        verify(itemRepository).findAll(
                any(Specification.class),
                any(Pageable.class)
        );
    }
}
