package com.application.notes.feignService;

import com.application.notes.model.Users;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "Authentication", url = "${digital.app.authentication}", path = "/api/user")
public interface AuthenticationClient {

    @GetMapping("/public/validate")
    Boolean validateToken(@RequestParam("token") String token);

    @GetMapping("/public/extractUserId")
    String extractUserId(@RequestParam String token);

    @GetMapping("/getUserByUsername")
    Users getUserByUsername(@RequestParam String username);

}
