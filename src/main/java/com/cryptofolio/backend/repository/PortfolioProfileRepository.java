package com.cryptofolio.backend.repository;

import com.cryptofolio.backend.model.PortfolioProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioProfileRepository extends JpaRepository<PortfolioProfile, Long> {

    Optional<PortfolioProfile> findByHashId(String hashId);
}
