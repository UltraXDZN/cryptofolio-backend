package com.cryptofolio.backend.repository;

import com.cryptofolio.backend.model.Coin;
import com.cryptofolio.backend.model.PortfolioProfile;
import com.cryptofolio.backend.model.PortfolioRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRecordRepository extends JpaRepository<PortfolioRecord, Long> {

    List<PortfolioRecord> findByProfile(PortfolioProfile profile);

    List<PortfolioRecord> findByProfileAndCoin(PortfolioProfile profile, Coin coin);

    Optional<PortfolioRecord> findByIdAndProfile(Long id, PortfolioProfile profile);
}
