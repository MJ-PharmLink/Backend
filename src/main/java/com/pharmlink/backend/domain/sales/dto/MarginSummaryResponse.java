package com.pharmlink.backend.domain.sales.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pharmlink.backend.domain.sales.service.MarginCalculator;

import java.math.BigDecimal;
import java.util.List;

// 마진 집계 응답 (11.3). group_by를 지정하지 않으면 groups 필드 없이 summary만 보낸다
public record MarginSummaryResponse(
        Amounts summary,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<?> groups
) {
    // 합계 마진율 = 합계 마진 ÷ 합계 매출 × 100 (소수 2자리, 합계 매출 0이면 0.00) — DB 설계서 집계 margin_rate 규칙
    public record Amounts(long salesAmount, long costAmount, long marginAmount, BigDecimal marginRate) {
        static Amounts of(MarginRow row) {
            long margin = row.sales() - row.cost();
            return new Amounts(row.sales(), row.cost(), margin, MarginCalculator.marginRate(row.sales(), margin));
        }
    }

    public record PartnerGroup(Long partnerId, String partnerName, long salesAmount, long costAmount,
                               long marginAmount, BigDecimal marginRate) {
        public static PartnerGroup from(MarginRow row) {
            Amounts amounts = Amounts.of(row);
            return new PartnerGroup(row.id(), row.name(), amounts.salesAmount(), amounts.costAmount(),
                    amounts.marginAmount(), amounts.marginRate());
        }
    }

    public record ItemGroup(Long itemId, String itemCode, String itemName, long salesAmount, long costAmount,
                            long marginAmount, BigDecimal marginRate) {
        public static ItemGroup from(MarginRow row) {
            Amounts amounts = Amounts.of(row);
            return new ItemGroup(row.id(), row.code(), row.name(), amounts.salesAmount(), amounts.costAmount(),
                    amounts.marginAmount(), amounts.marginRate());
        }
    }

    public static MarginSummaryResponse of(MarginRow total, List<?> groups) {
        return new MarginSummaryResponse(Amounts.of(total), groups);
    }
}
