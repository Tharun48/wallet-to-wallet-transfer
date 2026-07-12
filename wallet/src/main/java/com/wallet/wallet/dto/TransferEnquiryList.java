package com.wallet.wallet.dto;

import java.time.LocalDateTime;
import java.util.Date;

public record TransferEnquiryList(
        int toWalletId,
        double amount,
        LocalDateTime transactionDate
) {
}
