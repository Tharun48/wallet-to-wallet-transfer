package com.wallet.wallet.rest;

import com.wallet.wallet.dto.TransferEnquiryList;
import com.wallet.wallet.dto.TransferRequestDTO;
import com.wallet.wallet.dto.TransferResponeDTO;
import com.wallet.wallet.enums.CATEGORY;
import com.wallet.wallet.enums.MONTH;
import com.wallet.wallet.enums.PAYMENT_STATUS;
import com.wallet.wallet.exceptions.InsufficientFunds;
import com.wallet.wallet.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Month;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class TransferController {

    public final TransferService transferService;

    @PostMapping("/transfer")
    public ResponseEntity<TransferResponeDTO> saveTransfer(@RequestBody TransferRequestDTO transferRequestDTO) throws InsufficientFunds {
        TransferResponeDTO transferResponeDTO = transferService.createTransfer(transferRequestDTO);
        return new ResponseEntity<>(transferResponeDTO, HttpStatus.OK);
    }

    @GetMapping("/wallet/{walletId}/history")
    public ResponseEntity<List<TransferEnquiryList>> getEnquiry(@PathVariable Integer walletId, @RequestParam(required = false) MONTH month, @RequestParam(required = false) CATEGORY category, @RequestParam(required = false) PAYMENT_STATUS paymentStatus){
        List<TransferEnquiryList> transferEnquiryListList = transferService.getEnquiryHistory(walletId,month,paymentStatus,category);
        return new ResponseEntity<>(transferEnquiryListList,HttpStatus.OK);
    }


}
