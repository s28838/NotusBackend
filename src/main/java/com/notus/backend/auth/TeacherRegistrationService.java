package com.notus.backend.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.notus.backend.auth.dto.EmailVerificationRequest;
import com.notus.backend.auth.dto.LoginRequest;
import com.notus.backend.auth.dto.TeacherAuthResponse;
import com.notus.backend.auth.dto.TeacherGoogleRegisterRequest;
import com.notus.backend.auth.dto.TeacherRegisterRequest;
import com.notus.backend.users.Role;
import com.notus.backend.users.StudentRepository;
import com.notus.backend.users.Teacher;
import com.notus.backend.users.TeacherRepository;
import com.notus.backend.users.UserDto;
import com.notus.backend.users.teachercode.TeacherCodeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class TeacherRegistrationService {

    private final TeacherCodeService teacherCodeService;
    private final LocalAuthUserRepository localAuthUserRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final HashService hashService;
    private final EmailVerificationService emailVerificationService;
    private final SecureRandom secureRandom = new SecureRandom();

    public TeacherRegistrationService(TeacherCodeService teacherCodeService,
                                      LocalAuthUserRepository localAuthUserRepository,
                                      TeacherRepository teacherRepository,
                                      StudentRepository studentRepository,
                                      PasswordEncoder passwordEncoder,
                                      AuthTokenService authTokenService,
                                      HashService hashService,
                                      EmailVerificationService emailVerificationService) {
        this.teacherCodeService = teacherCodeService;
        this.localAuthUserRepository = localAuthUserRepository;
        this.teacherRepository = teacherRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.authTokenService = authTokenService;
        this.hashService = hashService;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional
    public TeacherAuthResponse registerWithEmail(TeacherRegisterRequest request) {
        String email = normalizeEmail(request.email());
        validateEmail(email);
        validatePassword(request.password());
        ensureTeacherCanBeCreated(email);

        String authUserId = "local-" + UUID.randomUUID();
        String verificationToken = generateUrlToken();

        LocalAuthUser authUser = new LocalAuthUser();
        authUser.setAuthUserId(authUserId);
        authUser.setEmail(email);
        authUser.setPasswordHash(passwordEncoder.encode(request.password()));
        authUser.setEmailVerified(false);
        authUser.setEmailVerificationTokenHash(hashService.sha256(verificationToken));
        authUser.setEmailVerificationExpiresAt(Instant.now().plusSeconds(60 * 60 * 24));
        localAuthUserRepository.save(authUser);

        Teacher teacher = createTeacher(authUserId, email, request.name());
        teacherCodeService.consumeRegistrationToken(request.registrationToken(), email, authUserId);
        emailVerificationService.sendVerificationEmail(email, verificationToken);

        return new TeacherAuthResponse(
                null,
                mapTeacher(teacher),
                false,
                true,
                "Sprawdź swoją skrzynkę email i potwierdź konto."
        );
    }

    @Transactional(readOnly = true)
    public TeacherAuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        LocalAuthUser authUser = localAuthUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nieprawidłowy email lub hasło"));

        if (!passwordEncoder.matches(request.password(), authUser.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nieprawidłowy email lub hasło");
        }

        Teacher teacher = teacherRepository.findByClerkUserId(authUser.getAuthUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "To konto nie jest kontem nauczyciela"));

        if (!authUser.isEmailVerified()) {
            return new TeacherAuthResponse(
                    null,
                    mapTeacher(teacher),
                    false,
                    true,
                    "Potwierdź adres email przed zalogowaniem."
            );
        }

        return new TeacherAuthResponse(
                authTokenService.issueLocalToken(authUser.getAuthUserId(), authUser.getEmail()),
                mapTeacher(teacher),
                true,
                false,
                "Zalogowano."
        );
    }

    @Transactional
    public TeacherAuthResponse verifyEmail(EmailVerificationRequest request) {
        if (request.token() == null || request.token().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token weryfikacji email jest wymagany");
        }

        LocalAuthUser authUser = localAuthUserRepository.findByEmailVerificationTokenHash(hashService.sha256(request.token()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link weryfikacyjny jest nieprawidłowy albo wygasł"));

        if (authUser.getEmailVerificationExpiresAt() == null || !authUser.getEmailVerificationExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link weryfikacyjny jest nieprawidłowy albo wygasł");
        }

        authUser.setEmailVerified(true);
        authUser.setEmailVerificationTokenHash(null);
        authUser.setEmailVerificationExpiresAt(null);
        localAuthUserRepository.save(authUser);

        Teacher teacher = teacherRepository.findByClerkUserId(authUser.getAuthUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "To konto nie jest kontem nauczyciela"));

        return new TeacherAuthResponse(
                authTokenService.issueLocalToken(authUser.getAuthUserId(), authUser.getEmail()),
                mapTeacher(teacher),
                true,
                false,
                "Email potwierdzony."
        );
    }

    @Transactional
    public TeacherAuthResponse registerOrLoginWithGoogle(TeacherGoogleRegisterRequest request) {
        GoogleIdentity identity = decodeGoogleIdentity(request);

        var existingTeacher = teacherRepository.findByClerkUserId(identity.userId())
                .or(() -> teacherRepository.findByEmailIgnoreCase(identity.email()));
        if (existingTeacher.isPresent()) {
            return new TeacherAuthResponse(
                    null,
                    mapTeacher(existingTeacher.get()),
                    identity.emailVerified(),
                    !identity.emailVerified(),
                    identity.emailVerified() ? "Zalogowano." : "Potwierdź adres email przed zalogowaniem."
            );
        }

        if (studentRepository.findByClerkUserId(identity.userId()).isPresent()
                || studentRepository.findByEmailIgnoreCase(identity.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "To konto istnieje już jako student.");
        }

        teacherCodeService.consumeRegistrationToken(request.registrationToken(), identity.email(), identity.userId());
        Teacher teacher = createTeacher(identity.userId(), identity.email(), identity.name());

        return new TeacherAuthResponse(
                null,
                mapTeacher(teacher),
                identity.emailVerified(),
                !identity.emailVerified(),
                identity.emailVerified() ? "Zalogowano." : "Potwierdź adres email przed zalogowaniem."
        );
    }

    private Teacher createTeacher(String authUserId, String email, String name) {
        Teacher teacher = new Teacher();
        teacher.setClerkUserId(authUserId);
        teacher.setEmail(email);
        teacher.setName(resolveName(email, name));
        teacher.setRole(Role.TEACHER);
        return teacherRepository.save(teacher);
    }

    private void ensureTeacherCanBeCreated(String email) {
        if (studentRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "To konto istnieje już jako student.");
        }

        if (teacherRepository.findByEmailIgnoreCase(email).isPresent() || localAuthUserRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ten email jest już zajęty.");
        }
    }

    private GoogleIdentity decodeGoogleIdentity(TeacherGoogleRegisterRequest request) {
        if (request.idToken() == null || request.idToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nie udało się zalogować przez Google.");
        }

        try {
            DecodedJWT jwt = JWT.decode(request.idToken());
            String userId = jwt.getSubject();
            String email = normalizeEmail(firstPresent(claim(jwt, "email", "email_address"), request.email()));
            String name = firstPresent(claim(jwt, "name", "username"), request.name());
            Boolean emailVerified = jwt.getClaim("email_verified").asBoolean();

            if (userId == null || userId.isBlank() || email == null) {
                throw new IllegalArgumentException("Missing subject or email");
            }

            boolean verified = emailVerified != null ? emailVerified : request.emailVerified() == null || request.emailVerified();
            return new GoogleIdentity(userId, email, resolveName(email, name), verified);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nie udało się zalogować przez Google.");
        }
    }

    private String claim(DecodedJWT jwt, String first, String second) {
        String value = jwt.getClaim(first).asString();
        return value != null ? value : jwt.getClaim(second).asString();
    }

    private String firstPresent(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private UserDto mapTeacher(Teacher teacher) {
        return new UserDto(
                teacher.getId(),
                teacher.getEmail(),
                teacher.getName(),
                teacher.getRole(),
                null
        );
    }

    private void validateEmail(String email) {
        if (email == null || !email.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj poprawny adres email.");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8
                || !password.chars().anyMatch(Character::isDigit)
                || !password.chars().anyMatch(Character::isUpperCase)
                || !password.chars().anyMatch(Character::isLowerCase)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hasło jest zbyt słabe.");
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveName(String email, String name) {
        if (name != null && !name.isBlank()) {
            return name.trim();
        }

        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf("@"));
        }

        return "Teacher";
    }

    private String generateUrlToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record GoogleIdentity(String userId, String email, String name, boolean emailVerified) {}

}
