
package com.pharmlink.backend.domain.item.service;

import com.pharmlink.backend.domain.item.dto.ItemCreateRequest;
import com.pharmlink.backend.domain.item.dto.ItemResponse;
import com.pharmlink.backend.domain.item.dto.ItemUpdateRequest;
import com.pharmlink.backend.domain.item.entity.Item;
import com.pharmlink.backend.domain.item.repository.CategoryRepository;
import com.pharmlink.backend.domain.item.repository.ItemRepository;
import com.pharmlink.backend.domain.partner.service.PartnerService;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    private static final Long ITEM_ID = 1L;
    private static final Long CATEGORY_ID = 3L;
    private static final Long SUPPLIER_ID = 1L;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private PartnerService partnerService;

    @InjectMocks
    private ItemService itemService;

    @Test
    @DisplayName("상품 등록: 정상 요청이면 상품이 저장된다")
    void createItemSuccess() {

        ItemCreateRequest request = createRequest();

        given(itemRepository.existsByItemCode("TEST_MED_0001"))
                .willReturn(false);

        given(categoryRepository.existsById(CATEGORY_ID))
                .willReturn(true);

        given(itemRepository.saveAndFlush(any(Item.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ItemResponse response = itemService.createItem(request);

        assertThat(response.itemCode()).isEqualTo("TEST_MED_0001");
        assertThat(response.itemName()).isEqualTo("테스트 해열진통제 500mg");
        assertThat(response.categoryId()).isEqualTo(CATEGORY_ID);
        assertThat(response.supplierId()).isEqualTo(SUPPLIER_ID);
        assertThat(response.isActive()).isTrue();

        verify(partnerService).getActiveSupplier(SUPPLIER_ID);
        verify(itemRepository).saveAndFlush(any(Item.class));
    }

    @Test
    @DisplayName("상품 등록: 상품코드가 중복되면 ITEM_ALREADY_EXISTS")
    void createItemWithDuplicateCode() {

        given(itemRepository.existsByItemCode("TEST_MED_0001"))
                .willReturn(true);

        assertThatThrownBy(() -> itemService.createItem(createRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_ALREADY_EXISTS);

        verify(itemRepository, never()).saveAndFlush(any(Item.class));
    }

    @Test
    @DisplayName("상품 등록: 존재하지 않는 카테고리면 CATEGORY_NOT_FOUND")
    void createItemWithInvalidCategory() {

        given(categoryRepository.existsById(CATEGORY_ID))
                .willReturn(false);

        assertThatThrownBy(() -> itemService.createItem(createRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);

        verify(itemRepository, never()).saveAndFlush(any(Item.class));
    }

    @Test
    @DisplayName("상품 수정: 요청한 필드만 변경하고 나머지는 유지한다")
    void updateItemSuccess() {

        Item item = item();

        given(itemRepository.findById(ITEM_ID))
                .willReturn(Optional.of(item));

        given(itemRepository.saveAndFlush(item))
                .willReturn(item);

        ItemUpdateRequest request = new ItemUpdateRequest(
                "테스트 해열진통제 650mg",
                null,
                null,
                null,
                null,
                6000L,
                20,
                null
        );

        ItemResponse response = itemService.updateItem(ITEM_ID, request);

        assertThat(response.itemName())
                .isEqualTo("테스트 해열진통제 650mg");

        assertThat(response.unitPrice())
                .isEqualTo(6000L);

        assertThat(response.safetyStock())
                .isEqualTo(20);

        assertThat(response.categoryId())
                .isEqualTo(CATEGORY_ID);

        assertThat(response.supplierId())
                .isEqualTo(SUPPLIER_ID);

        assertThat(response.unitCost())
                .isEqualTo(3000L);
    }

    @Test
    @DisplayName("상품 수정: 존재하지 않는 상품이면 ITEM_NOT_FOUND")
    void updateItemNotFound() {

        given(itemRepository.findById(999L))
                .willReturn(Optional.empty());

        ItemUpdateRequest request = new ItemUpdateRequest(
                "수정 상품",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> itemService.updateItem(999L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("상품 비활성화: isActive가 false로 변경된다")
    void deactivateItemSuccess() {

        Item item = item();

        given(itemRepository.findById(ITEM_ID))
                .willReturn(Optional.of(item));

        given(itemRepository.saveAndFlush(item))
                .willReturn(item);

        itemService.deactivateItem(ITEM_ID);

        assertThat(item.isActive()).isFalse();

        verify(itemRepository).saveAndFlush(item);
    }

    @Test
    @DisplayName("상품 비활성화: 이미 비활성이면 다시 저장하지 않는다")
    void deactivateAlreadyInactiveItem() {

        Item item = item();
        item.deactivate();

        given(itemRepository.findById(ITEM_ID))
                .willReturn(Optional.of(item));

        itemService.deactivateItem(ITEM_ID);

        assertThat(item.isActive()).isFalse();

        verify(itemRepository, never()).saveAndFlush(any(Item.class));
    }

    @Test
    @DisplayName("상품 비활성화: 존재하지 않으면 ITEM_NOT_FOUND")
    void deactivateItemNotFound() {

        given(itemRepository.findById(999L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.deactivateItem(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_NOT_FOUND);
    }

    // 테스트용 상품 생성
    private Item item() {

        Item item = Item.create(
                "TEST_MED_0001",
                "테스트 해열진통제 500mg",
                CATEGORY_ID,
                "500mg x 10정",
                "BOX",
                3000L,
                5000L,
                10,
                SUPPLIER_ID
        );

        ReflectionTestUtils.setField(item, "itemId", ITEM_ID);

        return item;
    }

    // 테스트용 상품 등록 DTO
    private ItemCreateRequest createRequest() {

        return new ItemCreateRequest(
                "TEST_MED_0001",
                "테스트 해열진통제 500mg",
                CATEGORY_ID,
                "500mg x 10정",
                "BOX",
                3000L,
                5000L,
                10,
                SUPPLIER_ID
        );
    }
}

