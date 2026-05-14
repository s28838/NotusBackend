package com.notus.backend.controller;

import com.notus.backend.users.UserDto;
import com.notus.backend.users.UserService;
import com.notus.backend.users.RegisterUserRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/api/me")
    public UserDto me(
            Authentication authentication,
            HttpServletRequest request,
            @RequestParam(required = false) String name
    ) {
        String uid = (String) authentication.getPrincipal();
        String email = (String) request.getAttribute("clerk_email");

        String finalName = (String) request.getAttribute("clerk_name");
        if (finalName == null || finalName.isBlank()) {
            finalName = name;
        }

        return userService.findOrCreate(uid, email, finalName);
    }

    @PostMapping("/api/me")
    public UserDto register(
            Authentication authentication,
            HttpServletRequest request,
            @RequestBody RegisterUserRequest registerRequest
    ) {
        String uid = (String) authentication.getPrincipal();
        String email = (String) request.getAttribute("clerk_email");
        if ((email == null || email.isBlank()) && registerRequest != null) {
            email = registerRequest.email();
        }

        String finalName = (String) request.getAttribute("clerk_name");
        if ((finalName == null || finalName.isBlank()) && registerRequest != null) {
            finalName = registerRequest.name();
        }

        return userService.findOrCreate(
                uid,
                email,
                finalName,
                registerRequest != null ? registerRequest.role() : null,
                registerRequest != null ? registerRequest.teacherAccessCode() : null
        );
    }
}
