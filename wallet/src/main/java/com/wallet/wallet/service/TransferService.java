package com.wallet.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.wallet.dto.TransferEnquiryList;
import com.wallet.wallet.dto.TransferRequestDTO;
import com.wallet.wallet.dto.TransferResponeDTO;
import com.wallet.wallet.enums.CATEGORY;
import com.wallet.wallet.enums.MONTH;
import com.wallet.wallet.enums.PAYMENT_STATUS;
import com.wallet.wallet.enums.TYPE;
import com.wallet.wallet.exceptions.InsufficientFunds;
import com.wallet.wallet.mapper.TransactionMapper;
import com.wallet.wallet.model.LedgerEntry;
import com.wallet.wallet.model.Transaction;
import com.wallet.wallet.model.Wallet;
import com.wallet.wallet.repo.LedgerRepo;
import com.wallet.wallet.repo.TransferRepo;
import com.wallet.wallet.repo.WalletRepo;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    public final TransferRepo transferRepo;
    public final WalletRepo walletRepo;
    public final TransactionMapper transactionMapper;
    public final LedgerRepo ledgerRepo;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /*
     * have to deal with the idempotency
     * Using the mapper get the Entity instance
     * create record in the transaction table, status as PROCESSING
     * acquire lock for the row and validate the balance
     * update the balance for the credit and debit wallets
     * ledger entry
     * update the transaction table records as SUCCESS
     * store the response in the idempotency table
     * return the response
     */
    @Transactional
    public TransferResponeDTO createTransfer(TransferRequestDTO transferRequestDTO, String idempotencyKey) throws InsufficientFunds {
        Transaction transaction = transactionMapper.toEntity(transferRequestDTO);
        transaction.setPaymentStatus(PAYMENT_STATUS.PROCESSING);
        transaction.setTransactionDate(LocalDateTime.now());
        Transaction savedTransaction = transferRepo.save(transaction);
        String requestPath = "/transfer";

        int firstWallet     = Math.min(transferRequestDTO.toWalletId(),transferRequestDTO.fromWalletId());
        int secondWallet    = Math.max(transferRequestDTO.toWalletId(),transferRequestDTO.fromWalletId());
        double transferAmount = transferRequestDTO.amount();

        /*
        first:1
        second:3
        * lock ordering inorder to avoid deadlock
        */

        Wallet firstWalletLocked = walletRepo.findByIdForUpdate(firstWallet).
                orElseThrow(()-> new EntityNotFoundException("Wallet not found for walletId " + firstWallet));

        Wallet secondWalletLocked = walletRepo.findByIdForUpdate(secondWallet).
                orElseThrow(()-> new EntityNotFoundException("Wallet not found for walletId " + secondWallet));

        Wallet fromWallet = (firstWallet==transferRequestDTO.fromWalletId()) ? firstWalletLocked : secondWalletLocked;
        Wallet toWallet = (secondWallet==transferRequestDTO.toWalletId()) ? secondWalletLocked : firstWalletLocked;

        if(fromWallet.getAvailableBalance()<transferAmount){
            savedTransaction.setPaymentStatus(PAYMENT_STATUS.FAILED);
            transferRepo.save(savedTransaction);
            throw new InsufficientFunds("Insufficient funds for transfer from-wallet-id : " + fromWallet.getWalletId());
        }

        fromWallet.setAvailableBalance(fromWallet.getAvailableBalance()-transferAmount);
        toWallet.setAvailableBalance(fromWallet.getAvailableBalance()+transferAmount);

        walletRepo.save(fromWallet);
        walletRepo.save(toWallet);

        savedTransaction.setPaymentStatus(PAYMENT_STATUS.SUCCESSFUL);
        transferRepo.save(savedTransaction);

        LedgerEntry fromLedgerEntry = new LedgerEntry();
        fromLedgerEntry.setAmount(-1*transferAmount);
        fromLedgerEntry.setType(TYPE.DEBIT);
        fromLedgerEntry.setWalletId(fromWallet.getWalletId());
        fromLedgerEntry.setTransactionId(savedTransaction.getTransactionId());

        LedgerEntry toLedgerEntry = new LedgerEntry();
        toLedgerEntry.setAmount(transferAmount);
        toLedgerEntry.setType(TYPE.CREDIT);
        toLedgerEntry.setWalletId(toWallet.getWalletId());
        toLedgerEntry.setTransactionId(savedTransaction.getTransactionId());

        ledgerRepo.save(fromLedgerEntry);
        ledgerRepo.save(toLedgerEntry);

        TransferResponeDTO transferResponeDTO =  transactionMapper.toDto(savedTransaction);

        // 2. Persist cached JSON response to Redis after DB logic succeeds
        try {
            String redisKey = "idempotency:" + requestPath + ":" + idempotencyKey;
            String jsonResponse = objectMapper.writeValueAsString(transferResponeDTO);

            // Overwrite "PROCESSING" string with actual JSON response and set a 24-hour TTL
            redisTemplate.opsForValue().set(redisKey, jsonResponse, Duration.ofHours(24));
        } catch (Exception e) {
            // Log exception without failing the database transaction
            log.error("Failed to update idempotency cache in Redis", e);
        }
        return  transferResponeDTO;
    }

    public List<TransferEnquiryList> getEnquiryHistory(int walletId, MONTH month, PAYMENT_STATUS paymentStatus, CATEGORY category){
        Integer monthVal = (month!=null) ? month.ordinal()+1 : null;
        List<Transaction> transaction = transferRepo.getEnquiryHistory(walletId,monthVal,paymentStatus,category);
        return transactionMapper.toEnquiryResponseDto(transaction);
    }


}
