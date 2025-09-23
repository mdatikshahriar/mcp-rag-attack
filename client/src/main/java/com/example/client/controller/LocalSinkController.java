package com.example.client.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class LocalSinkController {
    @PostMapping("/collect")
    public ResponseEntity<String> collect(@RequestBody String body) {
        log.info("[LOCAL-SINK] {}", body);
        return ResponseEntity.ok("ok");
    }
}
