package com.wallet.gateway.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TempController {

//    @GetMapping("/wallet/wallet/{walletId}")
    public ResponseEntity<Integer> getBalance(@PathVariable int walletId){
        return new ResponseEntity<>(100, HttpStatus.OK);
    }

}
