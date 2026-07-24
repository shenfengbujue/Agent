package com.eduagent.controller;

import com.eduagent.entity.User;
import com.eduagent.model.dto.ChangePasswordRequest;
import com.eduagent.model.dto.LoginRequest;
import com.eduagent.model.dto.RegisterRequest;
import com.eduagent.model.dto.UserProfileUpdateRequest;
import com.eduagent.model.vo.LoginVO;
import com.eduagent.model.vo.Result;
import com.eduagent.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginRequest request) {
        LoginVO loginVO = authService.login(request);
        return Result.success(loginVO);
    }

    @PostMapping("/register")
    public Result<LoginVO> register(@RequestBody RegisterRequest request) {
        LoginVO loginVO = authService.register(request);
        return Result.success(loginVO);
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> getCurrentUser(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录或Token已过期");
        }
        Map<String, Object> userData = authService.getCurrentUser(userId);
        return Result.success(userData);
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(HttpServletRequest request, @RequestBody ChangePasswordRequest changePasswordRequest) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录或Token已过期");
        }
        
        if (!changePasswordRequest.getNewPassword().equals(changePasswordRequest.getConfirmPassword())) {
            return Result.error(400, "两次输入的新密码不一致");
        }
        
        authService.changePassword(userId, changePasswordRequest.getOldPassword(), changePasswordRequest.getNewPassword());
        return Result.success(null);
    }

    @PostMapping("/profile")
    public Result<Boolean> updateProfile(HttpServletRequest request, @RequestBody UserProfileUpdateRequest profileRequest) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录或Token已过期");
        }

        boolean success = authService.updateUserProfile(
                Long.valueOf(userId),
                profileRequest.getProfileData(),
                profileRequest.getLearningGoal(),
                profileRequest.getTimeAvailability(),
                profileRequest.getLearningStyle(),
                profileRequest.getWorkPainPoints(),
                profileRequest.getSkillLevel(),
                profileRequest.getExamTime(),
                profileRequest.getKnowledgeLevel(),
                profileRequest.getWeakPoints(),
                profileRequest.getMotivation(),
                profileRequest.getAchievementStyle(),
                profileRequest.getSocialWillingness(),
                profileRequest.getFrustrationHandling()
        );

        if (success) {
            log.info("用户画像更新成功: {}", userId);
            return Result.success(true);
        }
        return Result.error(500, "更新失败");
    }

    @GetMapping("/profile")
    public Result<User> getProfile(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录或Token已过期");
        }
        User user = authService.getUserProfile(Long.valueOf(userId));
        return Result.success(user);
    }

    /**
     * 根据学习进度更新用户画像
     */
    @PostMapping("/profile/learning-progress")
    public Result<Boolean> updateProfileFromProgress(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录或Token已过期");
        }

        String topic = (String) body.getOrDefault("topic", "");
        String category = (String) body.getOrDefault("category", "");
        int progressPercentage = body.get("progressPercentage") != null
                ? ((Number) body.get("progressPercentage")).intValue() : 0;

        boolean success = authService.updateProfileFromLearningProgress(
                Long.valueOf(userId), topic, category, progressPercentage);

        if (success) {
            return Result.success(true);
        }
        return Result.error(500, "更新画像失败");
    }
}