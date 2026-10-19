package com.pharmlink.backend.domain.delivery.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.pharmlink.backend.domain.company.dto.CompanyResponse;
import com.pharmlink.backend.domain.company.service.CompanyService;
import com.pharmlink.backend.domain.delivery.entity.Delivery;
import com.pharmlink.backend.domain.delivery.repository.DeliveryRepository;
import com.pharmlink.backend.domain.item.entity.Item;
import com.pharmlink.backend.domain.order.entity.Order;
import com.pharmlink.backend.domain.partner.entity.BusinessPartner;
import com.pharmlink.backend.domain.partner.entity.PartnerType;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

// 납품서 PDF 단위 테스트: 실제 PDF를 만들고 텍스트를 다시 읽어 한글·금액·날짜가 들어갔는지 확인한다
@ExtendWith(MockitoExtension.class)
class DeliveryDocumentServiceTest {

    private static final Long DELIVERY_ID = 35L;

    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private CompanyService companyService;
    @InjectMocks
    private DeliveryDocumentService deliveryDocumentService;

    @Test
    @DisplayName("PDF: 공급자·공급받는자·납품번호·주문번호·품목·총액·출고일(KST)이 한글로 들어가고, 납품 전이면 납품일은 공란")
    void documentContainsSpecItems() throws Exception {
        Delivery delivery = delivery();
        delivery.ship(Instant.parse("2026-09-10T15:30:00Z")); // KST 2026-09-11 00:30
        given(deliveryRepository.findDetailById(DELIVERY_ID)).willReturn(Optional.of(delivery));
        given(companyService.getCompany()).willReturn(company());

        byte[] pdf = deliveryDocumentService.createDocument(DELIVERY_ID);

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        String text = new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1);
        assertThat(text).contains("납 품 서", "팜링크약품", "220-81-12345", "김대표", "행복약국", "123-45-67890");
        assertThat(text).contains("35", "ORD-20260910-0088", "타이레놀정 500mg", "100정/병", "50,000", "125,000");
        // 출고일만 있고 납품일은 공란: KST 날짜가 출고일 한 번만 나온다
        assertThat(text.split("2026-09-11", -1)).hasSize(2);
        // 명세 9.5 PDF 포함 항목: 공급자 주소·연락처, 공급받는자 주소, 품목 열 이름
        assertThat(text).contains("서울시 송파구", "02-555-1234", "서울시 강남구");
        assertThat(text).contains("상품명", "규격", "수량", "판매단가", "품목금액", "총 납품금액", "출고일", "납품일");
    }

    @Test
    @DisplayName("PDF: 없는 납품이면 404 DELIVERY_NOT_FOUND")
    void documentOfUnknownDelivery() {
        given(deliveryRepository.findDetailById(DELIVERY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> deliveryDocumentService.createDocument(DELIVERY_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DELIVERY_NOT_FOUND);
    }

    @Test
    @DisplayName("PDF: 회사 정보를 읽을 수 없으면 500 PDF_GENERATION_FAILED")
    void documentFailsWithoutCompany() {
        given(deliveryRepository.findDetailById(DELIVERY_ID)).willReturn(Optional.of(delivery()));
        given(companyService.getCompany()).willThrow(new BusinessException(ErrorCode.COMPANY_NOT_FOUND));

        assertThatThrownBy(() -> deliveryDocumentService.createDocument(DELIVERY_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PDF_GENERATION_FAILED);
    }

    @Test
    @DisplayName("PDF: 생성 중 예상하지 못한 런타임 오류도 500 PDF_GENERATION_FAILED (공통 500으로 새지 않음)")
    void documentFailsOnUnexpectedRuntimeError() {
        given(deliveryRepository.findDetailById(DELIVERY_ID)).willReturn(Optional.of(delivery()));
        given(companyService.getCompany()).willThrow(new IllegalStateException("unexpected"));

        assertThatThrownBy(() -> deliveryDocumentService.createDocument(DELIVERY_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PDF_GENERATION_FAILED);
    }

    private static CompanyResponse company() {
        return new CompanyResponse(1L, "팜링크약품", "220-81-12345", "김대표", "제2026-서울-00123호",
                "서울시 송파구", "02-555-1234", "02-555-1235", "contact@pharmlink.co.kr", null);
    }

    // 명세 9.2 예시: 타이레놀 10 × 5,000 + 베아제 5 × 15,000 = 125,000
    private static Delivery delivery() {
        BusinessPartner customer = BusinessPartner.create(PartnerType.CUSTOMER, "행복약국", "123-45-67890",
                "02-1234-5678", "서울시 강남구", null);
        Order order = Order.create("ORD-20260910-0088", customer, 2L);
        // 규격은 상품명과 겹치지 않는 값으로 두어 규격 칸이 빠지면 테스트가 실패하게 한다
        order.addItem(item(101L, "타이레놀정 500mg", "100정/병", 5_000), 10);
        order.addItem(item(205L, "베아제정", null, 15_000), 5);
        order.approve(Instant.parse("2026-09-10T05:30:00Z"));
        Delivery delivery = Delivery.create(order);
        ReflectionTestUtils.setField(delivery, "deliveryId", DELIVERY_ID);
        return delivery;
    }

    private static Item item(Long itemId, String itemName, String spec, long unitPrice) {
        try {
            var constructor = Item.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Item item = constructor.newInstance();
            ReflectionTestUtils.setField(item, "itemId", itemId);
            ReflectionTestUtils.setField(item, "itemName", itemName);
            ReflectionTestUtils.setField(item, "spec", spec);
            ReflectionTestUtils.setField(item, "unitPrice", unitPrice);
            ReflectionTestUtils.setField(item, "unitCost", unitPrice / 2);
            ReflectionTestUtils.setField(item, "isActive", true);
            return item;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
