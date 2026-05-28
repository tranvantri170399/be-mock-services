package asia.rgp.mock.agency.controller;

import asia.rgp.mock.agency.dto.ApiResponse;
import asia.rgp.mock.agency.dto.AuthResponse;
import asia.rgp.mock.agency.dto.DepositRequest;
import asia.rgp.mock.agency.dto.DepositResponse;
import asia.rgp.mock.agency.dto.GameTokenResponse;
import asia.rgp.mock.agency.dto.LoginRequest;
import asia.rgp.mock.agency.dto.PlayGameRequest;
import asia.rgp.mock.agency.dto.RegisterRequest;
import asia.rgp.mock.agency.service.JwtService;
import asia.rgp.mock.agency.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;
  private final JwtService jwtService;

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
    AuthResponse response = userService.register(request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
    AuthResponse response = userService.login(request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/deposit")
  public ResponseEntity<ApiResponse<DepositResponse>> deposit(
      @Valid @RequestBody DepositRequest request,
      @RequestHeader("Authorization") String authHeader) {
    String token = authHeader.replace("Bearer ", "");
    UUID userId = jwtService.extractUserId(token);
    DepositResponse response = userService.deposit(userId, request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/play-game")
  public ResponseEntity<ApiResponse<GameTokenResponse>> playGame(
      @Valid @RequestBody PlayGameRequest request,
      @RequestHeader("Authorization") String authHeader,
      HttpServletRequest httpRequest) {
    String token = authHeader.replace("Bearer ", "");
    UUID userId = jwtService.extractUserId(token);
    String clientIp = getClientIp(httpRequest);
    GameTokenResponse response = userService.playGame(userId, request, clientIp);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  private String getClientIp(HttpServletRequest request) {
    String ip = request.getHeader("X-Forwarded-For");
    if (ip == null || ip.isEmpty()) {
      ip = request.getHeader("X-Real-IP");
    }
    if (ip == null || ip.isEmpty()) {
      ip = request.getRemoteAddr();
    }
    return ip != null ? ip : "127.0.0.1";
  }
}
