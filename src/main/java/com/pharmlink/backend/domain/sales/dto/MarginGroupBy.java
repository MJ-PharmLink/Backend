package com.pharmlink.backend.domain.sales.dto;

// 마진 집계(11.3) group_by 값. 지정하지 않으면 summary만 반환
public enum MarginGroupBy {
    PARTNER,
    ITEM
}
