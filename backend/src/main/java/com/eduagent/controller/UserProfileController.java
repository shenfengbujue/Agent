package com.eduagent.controller;

import com.eduagent.entity.ProfileDimension;
import com.eduagent.entity.UserProfile;
import com.eduagent.model.vo.Result;
import com.eduagent.service.ProfileDimensionService;
import com.eduagent.service.UserProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping(value = "/api/profile", produces = "application/json;charset=UTF-8")
public class UserProfileController {

    private final UserProfileService profileService;
    private final ProfileDimensionService dimensionService;

    public UserProfileController(UserProfileService profileService,
                                  ProfileDimensionService dimensionService) {
        this.profileService = profileService;
        this.dimensionService = dimensionService;
    }

    @GetMapping("/{userId}")
    public Result<Map<String, Object>> getProfile(@PathVariable Long userId) {
        Map<String, Object> profile = profileService.analyzeProfile(userId);
        return Result.success(profile);
    }

    @PutMapping("/{userId}")
    public Result<UserProfile> updateProfile(@PathVariable Long userId, @RequestBody Map<String, Object> updates) {
        UserProfile profile = profileService.updateProfile(userId, updates);
        return Result.success(profile);
    }

    @PostMapping("/{userId}/keywords")
    public Result<Map<String, Object>> extractAndUpdateKeywords(@PathVariable Long userId, @RequestBody Map<String, String> request) {
        String text = request.get("text");
        if (text == null || text.trim().isEmpty()) {
            return Result.error(400, "文本内容不能为空");
        }
        
        UserProfile profile = profileService.updateKeywords(userId, text);
        
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("keywords", profile.getKeywords());
        result.put("updatedAt", profile.getUpdatedAt());
        
        return Result.success(result);
    }

    @PostMapping("/{userId}/interests")
    public Result<Map<String, Object>> addInterest(@PathVariable Long userId, @RequestBody Map<String, String> request) {
        String interest = request.get("interest");
        if (interest == null || interest.trim().isEmpty()) {
            return Result.error(400, "兴趣内容不能为空");
        }
        
        UserProfile profile = profileService.updateUserInterests(userId, interest);
        
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("interests", profile.getInterests());
        result.put("updatedAt", profile.getUpdatedAt());
        
        return Result.success(result);
    }

    @PostMapping("/extract")
    public Result<Map<String, Object>> extractKeywords(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        if (text == null || text.trim().isEmpty()) {
            return Result.error(400, "文本内容不能为空");
        }
        
        List<String> keywords = profileService.extractKeywords(text);
        
        Map<String, Object> result = new HashMap<>();
        result.put("text", text);
        result.put("keywords", keywords);
        result.put("count", keywords.size());
        
        return Result.success(result);
    }

    /**
     * 对话式画像分析 — 接收对话文本，通过LLM提取结构化画像维度
     */
    @PostMapping("/{userId}/dialogue")
    public Result<Map<String, Object>> analyzeProfileFromDialogue(
            @PathVariable Long userId, @RequestBody Map<String, String> request) {
        String dialogueText = request.get("dialogueText");
        if (dialogueText == null || dialogueText.trim().isEmpty()) {
            return Result.error(400, "对话文本不能为空");
        }

        Map<String, Object> analysis = profileService.analyzeProfileFromDialogue(userId, dialogueText);
        return Result.success(analysis);
    }

    /**
     * 获取用户所有画像维度（结构化）
     */
    @GetMapping("/{userId}/dimensions")
    public Result<Map<String, ProfileDimension>> getUserDimensions(@PathVariable Long userId) {
        Map<String, ProfileDimension> dimensions = dimensionService.getUserDimensions(userId);
        return Result.success(dimensions);
    }

    /**
     * 生成画像文本摘要（供LLM Prompt注入）
     */
    @GetMapping("/{userId}/summary")
    public Result<String> getProfileSummary(@PathVariable Long userId) {
        String summary = dimensionService.buildProfileSummary(userId);
        return Result.success(summary);
    }
}