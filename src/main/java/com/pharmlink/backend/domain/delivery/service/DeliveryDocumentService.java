package com.pharmlink.backend.domain.delivery.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.pharmlink.backend.domain.company.dto.CompanyResponse;
import com.pharmlink.backend.domain.company.service.CompanyService;
import com.pharmlink.backend.domain.delivery.entity.Delivery;
import com.pharmlink.backend.domain.delivery.repository.DeliveryRepository;
import com.pharmlink.backend.domain.order.entity.Order;
import com.pharmlink.backend.domain.order.entity.OrderItem;
import com.pharmlink.backend.domain.partner.entity.BusinessPartner;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// 납품서 PDF (9.5). 공급자는 회사 정보(13장), 공급받는자는 주문 거래처, 품목은 주문 라인 스냅샷
// 한글 출력을 위해 resources/fonts의 나눔고딕(OFL)을 내장한다
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryDocumentService {

    private static final String FONT_PATH = "fonts/NanumGothic-Regular.ttf";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DeliveryRepository deliveryRepository;
    private final CompanyService companyService;

    // 폰트(약 2MB)는 처음 한 번만 읽어 재사용한다
    private volatile BaseFont baseFont;

    @Transactional(readOnly = true)
    public byte[] createDocument(Long deliveryId) {
        Delivery delivery = deliveryRepository.findDetailById(deliveryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
        try {
            return render(delivery, companyService.getCompany());
        } catch (IOException | RuntimeException e) {
            // 회사 정보 누락·폰트 누락·PDF 라이브러리 내부 오류 등 PDF를 만들 수 없는 경우는 모두 500 PDF_GENERATION_FAILED
            log.error("납품서 PDF 생성 실패: deliveryId={}", deliveryId, e);
            throw new BusinessException(ErrorCode.PDF_GENERATION_FAILED);
        }
    }

    private byte[] render(Delivery delivery, CompanyResponse company) throws IOException, DocumentException {
        BaseFont baseFont = font();
        Font title = new Font(baseFont, 20, Font.BOLD);
        Font label = new Font(baseFont, 9, Font.BOLD);
        Font body = new Font(baseFont, 9);

        Order order = delivery.getOrder();
        BusinessPartner partner = order.getPartner();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 40, 40);
        PdfWriter.getInstance(document, out);
        document.open();

        Paragraph heading = new Paragraph("납 품 서", title);
        heading.setAlignment(Element.ALIGN_CENTER);
        heading.setSpacingAfter(16);
        document.add(heading);

        // 납품번호는 delivery_id (별도 납품번호 컬럼 없음)
        PdfPTable meta = table(new float[]{1, 2, 1, 2});
        addRow(meta, label, body, "납품번호", String.valueOf(delivery.getDeliveryId()), "주문번호", order.getOrderNumber());
        addRow(meta, label, body, "출고일", date(delivery.getShippedAt()), "납품일", date(delivery.getDeliveredAt()));
        meta.setSpacingAfter(12);
        document.add(meta);

        PdfPTable parties = table(new float[]{1, 2, 1, 2});
        addRow(parties, label, body, "공급자", company.name(), "공급받는자", partner.getName());
        addRow(parties, label, body, "사업자번호", company.businessNumber(), "사업자번호", partner.getBusinessNumber());
        addRow(parties, label, body, "대표자", company.representativeName(), "주소", partner.getAddress());
        addRow(parties, label, body, "주소", company.address(), "", "");
        addRow(parties, label, body, "연락처", company.phone(), "", "");
        parties.setSpacingAfter(12);
        document.add(parties);

        // 품목 열은 명세 9.5 PDF 포함 항목 그대로: 상품명, 규격, 수량, 판매단가, 품목금액
        PdfPTable items = table(new float[]{3, 2, 1, 1.5f, 1.8f});
        for (String header : new String[]{"상품명", "규격", "수량", "판매단가", "품목금액"}) {
            items.addCell(cell(header, label, Element.ALIGN_CENTER));
        }
        for (OrderItem line : order.getItems()) {
            items.addCell(cell(line.getItem().getItemName(), body, Element.ALIGN_LEFT));
            items.addCell(cell(nullToEmpty(line.getItem().getSpec()), body, Element.ALIGN_LEFT));
            items.addCell(cell(money(line.getQuantity()), body, Element.ALIGN_RIGHT));
            items.addCell(cell(money(line.getUnitPrice()), body, Element.ALIGN_RIGHT));
            items.addCell(cell(money(line.getLineAmount()), body, Element.ALIGN_RIGHT));
        }
        PdfPCell totalLabel = cell("총 납품금액", label, Element.ALIGN_CENTER);
        totalLabel.setColspan(4);
        items.addCell(totalLabel);
        items.addCell(cell(money(order.getTotalAmount()), label, Element.ALIGN_RIGHT));
        document.add(items);

        document.close();
        return out.toByteArray();
    }

    private BaseFont font() throws IOException, DocumentException {
        BaseFont font = baseFont;
        if (font == null) {
            synchronized (this) {
                if (baseFont == null) {
                    baseFont = loadFont();
                }
                font = baseFont;
            }
        }
        return font;
    }

    private static BaseFont loadFont() throws IOException, DocumentException {
        try (InputStream in = new ClassPathResource(FONT_PATH).getInputStream()) {
            return BaseFont.createFont("NanumGothic-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true,
                    in.readAllBytes(), null);
        }
    }

    private static PdfPTable table(float[] widths) {
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        return table;
    }

    private static void addRow(PdfPTable table, Font label, Font body, String l1, String v1, String l2, String v2) {
        table.addCell(cell(l1, label, Element.ALIGN_CENTER));
        table.addCell(cell(nullToEmpty(v1), body, Element.ALIGN_LEFT));
        table.addCell(cell(l2, label, Element.ALIGN_CENTER));
        table.addCell(cell(nullToEmpty(v2), body, Element.ALIGN_LEFT));
    }

    private static PdfPCell cell(String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(align);
        cell.setPadding(5);
        return cell;
    }

    // 출고일·납품일은 KST 날짜, 아직 없으면 공란
    private static String date(Instant instant) {
        return instant == null ? "" : DATE.format(instant.atZone(KST));
    }

    // 금액·수량 천 단위 구분. NumberFormat은 스레드 안전하지 않아 공유하지 않는다
    private static String money(long value) {
        return String.format(Locale.KOREA, "%,d", value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
