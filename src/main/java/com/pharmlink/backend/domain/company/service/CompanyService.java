package com.pharmlink.backend.domain.company.service;

import com.pharmlink.backend.domain.company.dto.CompanyResponse;
import com.pharmlink.backend.domain.company.dto.CompanyUpdateRequest;
import com.pharmlink.backend.domain.company.entity.Company;
import com.pharmlink.backend.domain.company.repository.CompanyRepository;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;

    // 회사 정보 조회. 납품서 PDF의 공급자 영역(회사명, 사업자등록번호, 주소, 연락처)에서도 사용
    // company_id = 1 행이 없으면 404 COMPANY_NOT_FOUND
    public CompanyResponse getCompany() {
        return CompanyResponse.from(findCompany());
    }

    // 회사 정보 수정 (전체 교체). 선택 필드는 비우면 null (CompanyUpdateRequest에서 변환)
    @Transactional
    public CompanyResponse updateCompany(CompanyUpdateRequest request) {
        Company company = findCompany();
        company.update(request.name(), request.businessNumber(), request.representativeName(),
                request.wholesaleLicenseNumber(), request.address(), request.phone(),
                request.fax(), request.email());
        // updated_at을 응답에 바로 반영하기 위해 flush
        return CompanyResponse.from(companyRepository.saveAndFlush(company));
    }

    private Company findCompany() {
        return companyRepository.findById(Company.COMPANY_ID)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_NOT_FOUND));
    }
}
