package com.wallet.wallet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.wallet.dto.WalletRequestDTO;
import com.wallet.wallet.dto.WalletResponseDTO;
import com.wallet.wallet.mapper.WalletMapper;
import com.wallet.wallet.model.Wallet;
import com.wallet.wallet.repo.WalletRepo;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletMapper walletMapper;
    private final WalletRepo walletRepo;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    public WalletResponseDTO createWallet(WalletRequestDTO walletRequestDTO, String idempotencyKey){
            Wallet wallet = walletMapper.toEntity(walletRequestDTO);
            wallet.setBlockAmount(0.0);
            LocalDateTime date = LocalDateTime.now();
            wallet.setCreatedAt(date);
            Wallet walletSaved = walletRepo.save(wallet);
            WalletResponseDTO walletResponseDTO = new WalletResponseDTO(wallet.getName(), walletSaved.getWalletId(), wallet.getTotalBalance(), date);
            // 2. Persist cached JSON response to Redis after DB logic succeeds
            try {
                String requestPath = "/WALLET/wallet";
                String redisKey = "idempotency:" + requestPath + ":" + idempotencyKey;
                String jsonResponse = objectMapper.writeValueAsString(walletResponseDTO);

                // Overwrite "PROCESSING" string with actual JSON response and set a 24-hour TTL
                redisTemplate.opsForValue().set(redisKey, jsonResponse, Duration.ofHours(24));
            } catch (Exception e) {
                // Log exception without failing the database transaction
                log.error("Failed to update idempotency cache in Redis", e);
            }
            return  walletResponseDTO;
    }

    public double getAmount(int walletId){
        Optional<Wallet> walletOptional = walletRepo.findById(walletId);
        Wallet wallet = walletOptional.orElseThrow(()-> new EntityNotFoundException("Wallet not found for walletId " + walletId));
        return wallet.getAvailableBalance();
    }


}
