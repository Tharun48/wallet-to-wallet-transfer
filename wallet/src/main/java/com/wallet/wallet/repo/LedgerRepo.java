package com.wallet.wallet.repo;

import com.wallet.wallet.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerRepo extends JpaRepository<LedgerEntry,Integer> {
}
