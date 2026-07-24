package com.eduagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduagent.mapper.UserMapper;
import com.eduagent.model.dto.LoginRequest;
import com.eduagent.model.dto.RegisterRequest;
import com.eduagent.entity.User;
import com.eduagent.model.vo.LoginVO;
import com.eduagent.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;
    private final UserProfileService userProfileService;

    public AuthService(UserMapper userMapper,
                       JwtUtil jwtUtil,
                       BCryptPasswordEncoder passwordEncoder,
                       UserProfileService userProfileService) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.userProfileService = userProfileService;
    }

    public LoginVO login(LoginRequest request) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, request.getUsername());
        User user = userMapper.selectOne(wrapper);

        if (user == null || !passwordMatches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        if ("disabled".equals(user.getStatus())) {
            throw new RuntimeException("账号已被禁用");
        }

        user.setLastLogin(LocalDateTime.now());
        
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        if (!today.equals(user.getLastLoginDate())) {
            if (user.getLoginDays() == null) {
                user.setLoginDays(1);
            } else {
                user.setLoginDays(user.getLoginDays() + 1);
            }
            user.setLastLoginDate(today);
        }
        
        if (user.getLevel() == null) {
            user.setLevel("1");
        }
        
        userMapper.updateById(user);

        String userIdStr = String.valueOf(user.getId());
        String token = jwtUtil.generateToken(userIdStr, user.getRole());

        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .loginDays(user.getLoginDays())
                .level(user.getLevel())
                .build();
    }

    public LoginVO register(RegisterRequest request) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, request.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("用户名已存在");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname() != null ? request.getNickname() : request.getUsername())
                .email(request.getEmail())
                .role("student")
                .status("active")
                .createdAt(LocalDateTime.now())
                .build();
        userMapper.insert(user);

        // 注册时自动创建用户画像记录
        try {
            userProfileService.createProfile(user.getId());
            log.info("用户画像已创建: userId={}", user.getId());
        } catch (Exception e) {
            log.warn("创建用户画像失败，将在首次访问时自动创建: {}", e.getMessage());
        }

        String userIdStr = String.valueOf(user.getId());
        String token = jwtUtil.generateToken(userIdStr, user.getRole());

        log.info("用户注册成功: {} (ID: {})", user.getUsername(), user.getId());

        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .role(user.getRole())
                .build();
    }

    public Map<String, Object> getCurrentUser(String userId) {
        User user = userMapper.selectById(Long.valueOf(userId));
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("userId", user.getId());
        userData.put("username", user.getUsername());
        userData.put("nickname", user.getNickname());
        userData.put("email", user.getEmail());
        userData.put("role", user.getRole());
        userData.put("status", user.getStatus());
        userData.put("createdAt", user.getCreatedAt());
        userData.put("lastLogin", user.getLastLogin());
        userData.put("loginDays", user.getLoginDays() != null ? user.getLoginDays() : 0);
        userData.put("level", user.getLevel() != null ? user.getLevel() : "1");
        return userData;
    }

    private boolean passwordMatches(String rawPassword, String storedPassword) {
        if (storedPassword == null) return false;
        // BCrypt 哈希验证
        if (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$")) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        return false;
    }

    public void changePassword(String userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(Long.valueOf(userId));
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        if (!passwordMatches(oldPassword, user.getPassword())) {
            throw new RuntimeException("旧密码不正确");
        }
        
        if (newPassword.length() < 6) {
            throw new RuntimeException("新密码长度至少为6位");
        }
        
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        
        log.info("用户修改密码成功: {} (ID: {})", user.getUsername(), user.getId());
    }

    public boolean updateUserProfile(Long userId,
                                     String profileData,
                                     String learningGoal,
                                     String timeAvailability,
                                     String learningStyle,
                                     String workPainPoints,
                                     String skillLevel,
                                     String examTime,
                                     String knowledgeLevel,
                                     String weakPoints,
                                     String motivation,
                                     String achievementStyle,
                                     String socialWillingness,
                                     String frustrationHandling) {
        int result = userMapper.updateProfile(userId, profileData, learningGoal,
                timeAvailability, learningStyle, workPainPoints, skillLevel,
                examTime, knowledgeLevel, weakPoints, motivation, achievementStyle,
                socialWillingness, frustrationHandling);

        try {
            Map<String, Object> profileUpdates = new HashMap<>();

            // 提取学习风格到偏好
            if (learningStyle != null && !learningStyle.isEmpty()) {
                profileUpdates.put("preferences", learningStyle);
            }

            // 构建完整的关键词列表（从所有画像字段中提取）
            List<String> allKeywords = new ArrayList<>();
            if (learningGoal != null && !learningGoal.isEmpty()) {
                allKeywords.add(learningGoal);
            }
            if (motivation != null && !motivation.isEmpty()) {
                allKeywords.add(motivation);
            }
            if (achievementStyle != null && !achievementStyle.isEmpty()) {
                allKeywords.add(achievementStyle);
            }
            if (workPainPoints != null && !workPainPoints.isEmpty()) {
                for (String pt : workPainPoints.split(",")) {
                    if (!pt.trim().isEmpty()) allKeywords.add(pt.trim());
                }
            }
            if (weakPoints != null && !weakPoints.isEmpty()) {
                for (String wp : weakPoints.split(",")) {
                    if (!wp.trim().isEmpty()) allKeywords.add(wp.trim());
                }
            }
            if (skillLevel != null && !skillLevel.isEmpty()) {
                allKeywords.add(skillLevel);
            }
            if (knowledgeLevel != null && !knowledgeLevel.isEmpty()) {
                allKeywords.add(knowledgeLevel);
            }
            if (examTime != null && !examTime.isEmpty()) {
                allKeywords.add(examTime);
            }
            if (timeAvailability != null && !timeAvailability.isEmpty()) {
                allKeywords.add(timeAvailability);
            }
            if (socialWillingness != null && !socialWillingness.isEmpty()) {
                allKeywords.add(socialWillingness);
            }
            if (frustrationHandling != null && !frustrationHandling.isEmpty()) {
                allKeywords.add(frustrationHandling);
            }
            if (!allKeywords.isEmpty()) {
                userProfileService.updateUserKeywords(userId, allKeywords);
            }

            // 提取兴趣标签
            if (learningGoal != null && !learningGoal.isEmpty()) {
                String goalLabel = mapGoalToLabel(learningGoal);
                userProfileService.updateUserInterests(userId, goalLabel);
            }
            if (learningStyle != null && !learningStyle.isEmpty()) {
                userProfileService.updateUserInterests(userId, learningStyle);
            }
            if (workPainPoints != null && !workPainPoints.isEmpty()) {
                for (String pt : workPainPoints.split(",")) {
                    if (!pt.trim().isEmpty()) userProfileService.updateUserInterests(userId, pt.trim());
                }
            }

            // 更新偏好字段
            if (profileUpdates.containsKey("preferences")) {
                userProfileService.updateProfile(userId, profileUpdates);
            }

            log.info("User profile synced to user_profile table for userId: {}", userId);
        } catch (Exception e) {
            log.warn("Failed to sync user profile to user_profile table: {}", e.getMessage());
        }

        return result > 0;
    }

    /**
     * 将学习目标映射为可读标签
     */
    private String mapGoalToLabel(String learningGoal) {
        switch (learningGoal) {
            case "career_skill": return "职场技能";
            case "exam升学": return "升学考试";
            case "hobby_self": return "兴趣自学";
            default: return learningGoal;
        }
    }

    /**
     * 根据个性化学习进度更新用户画像
     */
    public boolean updateProfileFromLearningProgress(Long userId, String topic, String category, int progressPercentage) {
        try {
            // 提取关键词
            if (topic != null && !topic.isEmpty()) {
                List<String> keywords = userProfileService.extractKeywords(topic);
                userProfileService.updateUserKeywords(userId, keywords);
                userProfileService.updateUserInterests(userId, topic);
            }

            if (category != null && !category.isEmpty()) {
                userProfileService.updateUserInterests(userId, category);
            }

            // 根据学习进度更新用户等级
            if (progressPercentage >= 100) {
                User user = userMapper.selectById(userId);
                if (user != null) {
                    int currentLevel = 1;
                    try {
                        currentLevel = Integer.parseInt(user.getLevel() != null ? user.getLevel() : "1");
                    } catch (NumberFormatException ignored) {}

                    // 每完成一个目标，等级经验值+1，每3个完成目标升1级
                    int completedCount = (user.getLoginDays() != null ? user.getLoginDays() : 0) + 1;
                    int newLevel = Math.max(currentLevel, 1 + (completedCount / 3));
                    user.setLevel(String.valueOf(newLevel));
                    user.setProfileUpdatedAt(LocalDateTime.now());
                    userMapper.updateById(user);
                    log.info("用户等级已更新: userId={}, level={}", userId, newLevel);
                }
            }

            log.info("Learning progress synced to user profile: userId={}, topic={}, progress={}%", userId, topic, progressPercentage);
            return true;
        } catch (Exception e) {
            log.error("Failed to update profile from learning progress: {}", e.getMessage());
            return false;
        }
    }

    public User getUserProfile(Long userId) {
        return userMapper.selectById(userId);
    }
}