package com.eduagent.controller;

import com.eduagent.entity.*;
import com.eduagent.model.vo.Result;
import com.eduagent.service.SocialService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/social")
public class SocialController {

    private final SocialService socialService;

    public SocialController(SocialService socialService) {
        this.socialService = socialService;
    }

    private String getCurrentUserId(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) {
            throw new RuntimeException("未登录或Token已过期");
        }
        return userId;
    }

    // ==================== 学习小组 ====================

    @PostMapping("/group/create")
    public Result<StudyGroup> createGroup(@RequestBody StudyGroup group, HttpServletRequest request) {
        try {
            group.setCreatorId(getCurrentUserId(request));
            StudyGroup result = socialService.createGroup(group);
            return Result.success(result);
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg != null && msg.startsWith("DUPLICATE_NAME:")) {
                String[] parts = msg.split(":", 2);
                return Result.error(400, "小组名称「" + (parts.length > 1 ? parts[1] : "") + "」已被使用，请换一个名字");
            }
            if (msg != null && msg.startsWith("DUPLICATE_GROUP:")) {
                String[] parts = msg.split(":", 3);
                if (parts.length >= 3) {
                    return Result.error(400, "您已创建过小组「" + parts[2] + "」（ID: " + parts[1] + "），每个用户只能创建一个小组");
                }
            }
            return Result.error(msg != null ? msg : "创建失败");
        }
    }

    @PostMapping("/group/{groupId}/join")
    public Result<String> joinGroup(@PathVariable Long groupId, HttpServletRequest request) {
        String userId = getCurrentUserId(request);
        boolean success = socialService.joinGroup(groupId, userId);
        return success ? Result.success("加入成功") : Result.error("加入失败：小组不存在、已满或已是成员");
    }

    @DeleteMapping("/group/{groupId}/leave")
    public Result<String> leaveGroup(@PathVariable Long groupId, HttpServletRequest request) {
        String userId = getCurrentUserId(request);
        boolean success = socialService.leaveGroup(groupId, userId);
        return success ? Result.success("离开成功") : Result.error("离开失败：不是小组成员");
    }

    @GetMapping("/group/my")
    public Result<List<StudyGroup>> getMyGroups(HttpServletRequest request) {
        String userId = getCurrentUserId(request);
        List<StudyGroup> groups = socialService.getMyGroups(userId);
        return Result.success(groups);
    }

    @GetMapping("/group/{groupId}/members")
    public Result<List<Map<String, Object>>> getGroupMembers(@PathVariable Long groupId) {
        List<Map<String, Object>> members = socialService.getGroupMembers(groupId);
        return Result.success(members);
    }

    @GetMapping("/group/all")
    public Result<List<StudyGroup>> getAllGroups() {
        List<StudyGroup> groups = socialService.getAllPublicGroups();
        return Result.success(groups);
    }

    /** 小组排行榜（实时统计成员数+帖子数+综合评分） */
    @GetMapping("/group/rankings")
    public Result<List<Map<String, Object>>> getGroupRankings() {
        return Result.success(socialService.getAllGroupsWithStats());
    }

    @GetMapping("/group/{groupId}")
    public Result<StudyGroup> getGroupById(@PathVariable Long groupId) {
        StudyGroup group = socialService.getGroupById(groupId);
        if (group == null || "DELETED".equals(group.getStatus())) {
            return Result.error("小组不存在");
        }
        return Result.success(group);
    }

    @DeleteMapping("/group/{groupId}")
    public Result<String> deleteGroup(@PathVariable Long groupId, HttpServletRequest request) {
        String userId = getCurrentUserId(request);
        boolean success = socialService.deleteGroup(groupId, userId);
        return success ? Result.success("删除成功") : Result.error("删除失败：小组不存在或无权限");
    }

    // ==================== 帖子 ====================

    @PostMapping("/post/create")
    public Result<StudyPost> createPost(@RequestBody StudyPost post, HttpServletRequest request) {
        post.setUserId(getCurrentUserId(request));
        StudyPost result = socialService.createPost(post);
        return Result.success(result);
    }

    @GetMapping("/post/group/{groupId}")
    public Result<List<StudyPost>> getGroupPosts(@PathVariable Long groupId) {
        List<StudyPost> posts = socialService.getGroupPosts(groupId);
        return Result.success(posts);
    }

    @DeleteMapping("/post/{postId}")
    public Result<String> deletePost(@PathVariable Long postId, HttpServletRequest request) {
        String userId = getCurrentUserId(request);
        boolean success = socialService.deletePost(postId, userId);
        return success ? Result.success("删除成功") : Result.error("删除失败：帖子不存在或无权限");
    }

    // ==================== 评论 ====================

    @PostMapping("/comment/add")
    public Result<StudyComment> addComment(@RequestBody StudyComment comment, HttpServletRequest request) {
        comment.setUserId(getCurrentUserId(request));
        StudyComment result = socialService.addComment(comment);
        return Result.success(result);
    }

    @GetMapping("/comment/post/{postId}")
    public Result<List<StudyComment>> getPostComments(@PathVariable Long postId) {
        List<StudyComment> comments = socialService.getPostComments(postId);
        return Result.success(comments);
    }

    @DeleteMapping("/comment/{commentId}")
    public Result<String> deleteComment(@PathVariable Long commentId, HttpServletRequest request) {
        String userId = getCurrentUserId(request);
        boolean success = socialService.deleteComment(commentId, userId);
        return success ? Result.success("删除成功") : Result.error("删除失败：评论不存在或无权限");
    }

    // ==================== 小组资源 ====================

    @PostMapping("/group/{groupId}/resource/upload")
    public Result<GroupResource> uploadGroupResource(
            @PathVariable Long groupId,
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        String userId = getCurrentUserId(request);
        try {
            String title = (String) payload.get("title");
            String description = (String) payload.get("description");
            String resourceType = (String) payload.get("resourceType");
            String fileUrl = (String) payload.get("fileUrl");
            String fileName = (String) payload.get("fileName");
            Object sizeObj = payload.get("fileSize");
            Long fileSize = sizeObj != null ? Long.valueOf(sizeObj.toString()) : null;

            GroupResource resource = socialService.uploadGroupResource(
                    groupId, userId, title, description, resourceType, fileUrl, fileName, fileSize);
            return Result.success(resource);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/group/{groupId}/resources")
    public Result<List<GroupResource>> getGroupResources(@PathVariable Long groupId) {
        List<GroupResource> resources = socialService.getGroupResources(groupId);
        return Result.success(resources);
    }

    @DeleteMapping("/group/resource/{resourceId}")
    public Result<String> deleteGroupResource(@PathVariable Long resourceId, HttpServletRequest request) {
        String userId = getCurrentUserId(request);
        try {
            boolean success = socialService.deleteGroupResource(resourceId, userId);
            return success ? Result.success("删除成功") : Result.error("删除失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    // ==================== 同伴辅导 ====================

    @PostMapping("/tutoring/request")
    public Result<PeerTutoring> requestTutoring(
            @RequestParam String topic,
            @RequestParam String description,
            HttpServletRequest request) {
        String userId = getCurrentUserId(request);
        PeerTutoring result = socialService.requestTutoring(userId, topic, description);
        return Result.success(result);
    }

    @GetMapping("/tutoring/my")
    public Result<List<PeerTutoring>> getMyTutoring(HttpServletRequest request) {
        String userId = getCurrentUserId(request);
        List<PeerTutoring> result = socialService.getMyTutoringRequests(userId);
        return Result.success(result);
    }

    @PutMapping("/tutoring/{id}/complete")
    public Result<String> completeTutoring(
            @PathVariable Long id,
            @RequestParam int rating,
            @RequestParam(required = false) String feedback) {
        socialService.completeTutoring(id, rating, feedback != null ? feedback : "");
        return Result.success("辅导已完成");
    }

    // ==================== 成就 ====================

    @GetMapping("/achievements")
    public Result<List<UserAchievement>> getUserAchievements(HttpServletRequest request) {
        String userId = getCurrentUserId(request);
        List<UserAchievement> achievements = socialService.getUserAchievements(userId);
        return Result.success(achievements);
    }

    @PostMapping("/achievements/award")
    public Result<UserAchievement> awardAchievement(
            @RequestParam String achievementType,
            HttpServletRequest request) {
        String userId = getCurrentUserId(request);
        UserAchievement achievement = socialService.awardAchievement(userId, achievementType);
        return achievement != null ? Result.success(achievement) :
                Result.error("该成就已获得或类型无效");
    }

    // ==================== 排行榜 ====================

    @GetMapping("/leaderboard")
    public Result<List<Map<String, Object>>> getLeaderboard(
            @RequestParam(required = false, defaultValue = "") String course,
            @RequestParam(defaultValue = "10") int limit) {
        List<Map<String, Object>> leaderboard = socialService.getLeaderboard(course, limit);
        return Result.success(leaderboard);
    }
}