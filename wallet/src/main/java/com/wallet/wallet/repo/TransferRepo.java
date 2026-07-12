package com.wallet.wallet.repo;

import com.wallet.wallet.enums.CATEGORY;
import com.wallet.wallet.enums.MONTH;
import com.wallet.wallet.enums.PAYMENT_STATUS;
import com.wallet.wallet.model.Transaction;
import com.wallet.wallet.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TransferRepo extends JpaRepository<Transaction,Integer> {
    @Query("select t from Transaction t " +
            "where (t.toWalletId=:walletId" +
            " and :paymentStatus IS NULL  OR t.paymentStatus=:paymentStatus" +
            " and :month IS NULL OR MONTH(t.transactionDate)=:month" +
            ")")
    List<Transaction> getEnquiryHistory(Integer walletId, Integer month, PAYMENT_STATUS paymentStatus, CATEGORY category);

}
