package com.notus.backend.auth.dto;

public record TeacherGoogleRegisterRequest(
        String idToken,
        String registrationToken,
        String email,
        String name,
        Boolean emailVerified
) {
    public TeacherGoogleRegisterRequest(String idToken, String registrationToken) {
        this(idToken, registrationToken, null, null, null);
    }
}
