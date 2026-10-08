package com.pharmlink.backend.domain.company.repository;

import com.pharmlink.backend.domain.company.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {
}
