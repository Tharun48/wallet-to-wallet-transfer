package com.wallet.wallet.mapper;

import com.wallet.wallet.dto.TransferEnquiryList;
import com.wallet.wallet.dto.TransferRequestDTO;
import com.wallet.wallet.dto.TransferResponeDTO;
import com.wallet.wallet.model.Transaction;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TransactionMapper {
    Transaction toEntity(TransferRequestDTO transferRequestDTO);
    TransferResponeDTO toDto(Transaction transaction);
    List<TransferEnquiryList> toEnquiryResponseDto(List<Transaction> transaction);
}
