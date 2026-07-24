package com.eduagent.controller;

import com.eduagent.entity.User;
import com.eduagent.entity.StudyGoal;
import com.eduagent.mapper.UserMapper;
import com.eduagent.repository.StudyGoalRepository;
import com.eduagent.model.vo.Result;
import com.eduagent.service.AuthService;
import com.eduagent.service.SocialService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserMapper userMapper;
    private final AuthService authService;
    private final SocialService socialService;
    private final StudyGoalRepository studyGoalRepository;

    public UserController(UserMapper userMapper, AuthService authService,
                          SocialService socialService, StudyGoalRepository studyGoalRepository) {
        this.userMapper = userMapper;
        this.authService = authService;
        this.socialService = socialService;
        this.studyGoalRepository = studyGoalRepository;
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

    @GetMapping
    public Result<List<User>> getAllUsers() {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(User::getCreatedAt);
        List<User> users = userMapper.selectList(wrapper);
        users.forEach(u -> u.setPassword(null));
        return Result.success(users);
    }

    @GetMapping("/{id}")
    public Result<User> getUserById(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        user.setPassword(null);
        return Result.success(user);
    }

    @GetMapping("/leaderboard")
    public Result<List<Map<String, Object>>> getLeaderboard(
            @RequestParam(required = false, defaultValue = "") String course,
            @RequestParam(defaultValue = "10") int limit) {
        List<Map<String, Object>> leaderboard = socialService.getLeaderboard(course, limit);
        return Result.success(leaderboard);
    }

    @GetMapping("/{userId}/progress")
    public Result<List<StudyGoal>> getUserProgress(@PathVariable Long userId) {
        LambdaQueryWrapper<StudyGoal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudyGoal::getUserId, userId)
                .orderByDesc(StudyGoal::getUpdatedAt);
        List<StudyGoal> goals = studyGoalRepository.selectList(wrapper);
        return Result.success(goals);
    }

    @PutMapping("/{userId}/progress")
    public Result<StudyGoal> createOrUpdateProgress(@PathVariable Long userId, @RequestBody StudyGoal progressData) {
        progressData.setUserId(userId);
        if (progressData.getId() != null) {
            StudyGoal existing = studyGoalRepository.selectById(progressData.getId());
            if (existing != null) {
                if (progressData.getTitle() != null) existing.setTitle(progressData.getTitle());
                if (progressData.getCategory() != null) existing.setCategory(progressData.getCategory());
                if (progressData.getProgress() != null) existing.setProgress(progressData.getProgress());
                existing.setUpdatedAt(LocalDateTime.now());
                studyGoalRepository.updateById(existing);
                return Result.success(existing);
            }
        }
        if (progressData.getProgress() == null) progressData.setProgress(0);
        progressData.setCreatedAt(LocalDateTime.now());
        progressData.setUpdatedAt(LocalDateTime.now());
        studyGoalRepository.insert(progressData);
        return Result.success(progressData);
    }

    @PutMapping("/{userId}/progress/{progressId}/complete")
    public Result<StudyGoal> completeProgress(@PathVariable Long userId, @PathVariable Long progressId) {
        StudyGoal goal = studyGoalRepository.selectById(progressId);
        if (goal == null || !goal.getUserId().equals(userId)) {
            return Result.error(404, "学习目标不存在");
        }
        goal.setProgress(100);
        goal.setUpdatedAt(LocalDateTime.now());
        studyGoalRepository.updateById(goal);
        return Result.success(goal);
    }

    @DeleteMapping("/{userId}/progress/{progressId}")
    public Result<Boolean> deleteProgress(@PathVariable Long userId, @PathVariable Long progressId) {
        StudyGoal goal = studyGoalRepository.selectById(progressId);
        if (goal == null || !goal.getUserId().equals(userId)) {
            return Result.error(404, "学习目标不存在");
        }
        studyGoalRepository.deleteById(progressId);
        return Result.success(true);
    }
}