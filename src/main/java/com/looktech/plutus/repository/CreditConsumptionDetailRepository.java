package com.looktech.plutus.repository;

import com.looktech.plutus.domain.CreditConsumptionDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreditConsumptionDetailRepository extends JpaRepository<CreditConsumptionDetail, Long> {
    List<CreditConsumptionDetail> findByTransactionId(String transactionId);
    List<CreditConsumptionDetail> findByLedgerId(Long ledgerId);
} 