package com.notus.backend.users;

public record RegisterUserRequest(
        Role role,
        String name,
        String email,
        String teacherAccessCode
) {
}
