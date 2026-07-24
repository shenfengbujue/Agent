package com.eduagent.controller;

import com.eduagent.model.vo.Result;
import com.eduagent.service.PushService;
import com.eduagent.service.RecommendationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushService pushService;
    private final RecommendationService recommendationService;

    public PushController(PushService pushService, RecommendationService recommendationService) {
        this.pushService = pushService;
        this.recommendationService = recommendationService;
    }

    private Long getUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid == null) throw new RuntimeException("未登录");
        return Long.valueOf(uid.toString());
    }

    @GetMapping("/user/{userId}")
    public Result<List<Map<String, Object>>> getPushes(@PathVariable Long userId) {
        return Result.success(pushService.getPendingPushes(userId));
    }

    @GetMapping("/user/{userId}/unread-count")
    public Result<Integer> getUnreadCount(@PathVariable Long userId) {
        return Result.success(pushService.getUnreadCount(userId));
    }

    @PutMapping("/user/{userId}/{pushId}/read")
    public Result<Void> markAsRead(@PathVariable Long userId, @PathVariable String pushId) {
        pushService.markAsRead(userId, pushId);
        return Result.success(null);
    }

    /** 基于用户画像的个性化推荐 */
    @GetMapping("/user/{userId}/profile-recommend")
    public Result<List<Map<String, Object>>> getProfileRecommend(
            @PathVariable Long userId, @RequestParam(defaultValue = "5") int limit) {
        return Result.success(recommendationService.getProfileBasedRecommendations(userId, limit));
    }
}
