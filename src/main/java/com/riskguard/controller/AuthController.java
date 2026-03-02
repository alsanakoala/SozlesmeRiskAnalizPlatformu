package com.riskguard.controller;

import com.riskguard.dto.AuthRequest;
import com.riskguard.dto.AuthResponse;
import com.riskguard.entity.UserEntity;
import com.riskguard.repo.UserRepository;
import com.riskguard.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*") // Frontend'in buraya erişebilmesi için
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    // --- 1. KAYIT OL (REGISTER) ---
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody AuthRequest request) {
        // E-posta kullanılıyor mu kontrolü
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new AuthResponse(null, "Bu e-posta adresi zaten kullanımda!"));
        }

        // Yeni kullanıcıyı oluştur ve şifreyi KİRİPTOLA (Geri döndürülemez şekilde)
        UserEntity newUser = new UserEntity();
        newUser.setEmail(request.getEmail());
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        
        userRepository.save(newUser);

        return ResponseEntity.ok(new AuthResponse(null, "Kayıt başarılı! Lütfen giriş yapın."));
    }

    // --- 2. GİRİŞ YAP (LOGIN) ---
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        // Veritabanında kullanıcıyı e-postadan bul
        Optional<UserEntity> userOpt = userRepository.findByEmail(request.getEmail());

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, "Kullanıcı bulunamadı."));
        }

        UserEntity user = userOpt.get();

        // Gönderilen şifre ile veritabanındaki kriptolu şifre eşleşiyor mu?
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(null, "Hatalı şifre!"));
        }

        // Şifre doğruysa Dijital Bilet (JWT Token) üret ve gönder
        String token = jwtUtil.generateToken(user.getEmail());
        return ResponseEntity.ok(new AuthResponse(token, "Giriş başarılı!"));
    }
}