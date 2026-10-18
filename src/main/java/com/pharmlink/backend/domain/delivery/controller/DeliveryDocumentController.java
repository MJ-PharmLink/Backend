package com.pharmlink.backend.domain.delivery.controller;

import com.pharmlink.backend.domain.delivery.service.DeliveryDocumentService;
import com.pharmlink.backend.global.security.AccessRole;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 9.5 납품서 PDF — 이 API만 공통 JSON 형식이 아닌 PDF binary를 반환한다
// produces를 걸지 않고 헤더를 직접 지정해서, 404·500 오류는 다른 API와 같은 JSON으로 나가게 한다
@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
public class DeliveryDocumentController {

    private final DeliveryDocumentService deliveryDocumentService;

    @PreAuthorize(AccessRole.ALL)
    @GetMapping("/{deliveryId}/document")
    public ResponseEntity<byte[]> getDocument(@PathVariable Long deliveryId) {
        byte[] pdf = deliveryDocumentService.createDocument(deliveryId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("delivery-" + deliveryId + ".pdf").build().toString())
                .body(pdf);
    }
}
