package com.eduagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduagent.mapper.*;
import com.eduagent.entity.*;
import com.eduagent.repository.StudyGoalRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SocialService {

    private final StudyGroupMapper studyGroupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final StudyPostMapper studyPostMapper;
    private final StudyCommentMapper studyCommentMapper;
    private final PeerTutoringMapper peerTutoringMapper;
    private final UserAchievementMapper userAchievementMapper;
    private final UserMapper userMapper;
    private final GroupResourceMapper groupResourceMapper;
    private final KnowledgeEntryMapper knowledgeEntryMapper;
    private final com.eduagent.repository.StudyGoalRepository studyGoalRepository;

    public SocialService(StudyGroupMapper studyGroupMapper,
                         GroupMemberMapper groupMemberMapper,
                         StudyPostMapper studyPostMapper,
                         StudyCommentMapper studyCommentMapper,
                         PeerTutoringMapper peerTutoringMapper,
                         UserAchievementMapper userAchievementMapper,
                         UserMapper userMapper,
                         GroupResourceMapper groupResourceMapper,
                         KnowledgeEntryMapper knowledgeEntryMapper,
                         com.eduagent.repository.StudyGoalRepository studyGoalRepository) {
        this.studyGroupMapper = studyGroupMapper;
        this.groupMemberMapper = groupMemberMapper;
        this.studyPostMapper = studyPostMapper;
        this.studyCommentMapper = studyCommentMapper;
        this.peerTutoringMapper = peerTutoringMapper;
        this.userAchievementMapper = userAchievementMapper;
        this.userMapper = userMapper;
        this.groupResourceMapper = groupResourceMapper;
        this.knowledgeEntryMapper = knowledgeEntryMapper;
        this.studyGoalRepository = studyGoalRepository;
    }

    // ==================== 学习小组 ====================

    public StudyGroup createGroup(StudyGroup group) {
        // 检查小组名称全局唯一（防止不同用户创建同名小组）
        LambdaQueryWrapper<StudyGroup> nameWrapper = new LambdaQueryWrapper<>();
        nameWrapper.eq(StudyGroup::getGroupName, group.getGroupName())
                   .eq(StudyGroup::getStatus, "ACTIVE");
        StudyGroup nameDuplicate = studyGroupMapper.selectOne(nameWrapper);
        if (nameDuplicate != null) {
            throw new RuntimeException("DUPLICATE_NAME:" + group.getGroupName());
        }

        // 检查同一用户是否已有活跃小组
        LambdaQueryWrapper<StudyGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudyGroup::getCreatorId, group.getCreatorId())
                .eq(StudyGroup::getStatus, "ACTIVE");
        StudyGroup existingGroup = studyGroupMapper.selectOne(wrapper);
        if (existingGroup != null) {
            throw new RuntimeException("DUPLICATE_GROUP:" + existingGroup.getId() + ":" + existingGroup.getGroupName());
        }

        if (group.getMemberCount() == null) {
            group.setMemberCount(1);
        }
        if (group.getMaxMembers() == null) {
            group.setMaxMembers(50);
        }
        if (group.getPostCount() == null) {
            group.setPostCount(0);
        }
        if (group.getStatus() == null) {
            group.setStatus("ACTIVE");
        }
        group.setCreatedAt(LocalDateTime.now());
        studyGroupMapper.insert(group);

        GroupMember creator = GroupMember.builder()
                .groupId(group.getId())
                .userId(group.getCreatorId())
                .role("CREATOR")
                .joinedAt(LocalDateTime.now())
                .build();
        groupMemberMapper.insert(creator);

        log.info("学习小组创建成功: {} (ID: {})", group.getGroupName(), group.getId());
        return group;
    }

    public boolean joinGroup(Long groupId, String userId) {
        StudyGroup group = studyGroupMapper.selectById(groupId);
        if (group == null || !"ACTIVE".equals(group.getStatus())) {
            return false;
        }
        if (group.getMemberCount() >= group.getMaxMembers()) {
            return false;
        }

        LambdaQueryWrapper<GroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, userId);
        if (groupMemberMapper.selectCount(wrapper) > 0) {
            return false;
        }

        GroupMember member = GroupMember.builder()
                .groupId(groupId)
                .userId(userId)
                .role("MEMBER")
                .joinedAt(LocalDateTime.now())
                .build();
        groupMemberMapper.insert(member);

        group.setMemberCount(group.getMemberCount() + 1);
        studyGroupMapper.updateById(group);

        log.info("用户 {} 加入学习小组 {}", userId, groupId);
        return true;
    }

    public boolean leaveGroup(Long groupId, String userId) {
        LambdaQueryWrapper<GroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, userId);
        int deleted = groupMemberMapper.delete(wrapper);

        if (deleted > 0) {
            StudyGroup group = studyGroupMapper.selectById(groupId);
            if (group != null) {
                group.setMemberCount(Math.max(0, group.getMemberCount() - 1));
                studyGroupMapper.updateById(group);
            }
            log.info("用户 {} 离开学习小组 {}", userId, groupId);
            return true;
        }
        return false;
    }

    public List<StudyGroup> getMyGroups(String userId) {
        LambdaQueryWrapper<GroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupMember::getUserId, userId);
        List<GroupMember> memberships = groupMemberMapper.selectList(wrapper);

        if (memberships.isEmpty()) {
            return List.of();
        }

        List<Long> groupIds = memberships.stream()
                .map(GroupMember::getGroupId)
                .collect(Collectors.toList());

        LambdaQueryWrapper<StudyGroup> groupWrapper = new LambdaQueryWrapper<>();
        groupWrapper.in(StudyGroup::getId, groupIds)
                .eq(StudyGroup::getStatus, "ACTIVE");
        return studyGroupMapper.selectList(groupWrapper);
    }

    public List<Map<String, Object>> getGroupMembers(Long groupId) {
        LambdaQueryWrapper<GroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupMember::getGroupId, groupId)
                .orderByAsc(GroupMember::getJoinedAt);
        List<GroupMember> members = groupMemberMapper.selectList(wrapper);

        List<Map<String, Object>> result = new ArrayList<>();
        for (GroupMember member : members) {
            Map<String, Object> memberInfo = new HashMap<>();
            memberInfo.put("id", member.getId());
            memberInfo.put("groupId", member.getGroupId());
            memberInfo.put("userId", member.getUserId());
            memberInfo.put("role", member.getRole());
            memberInfo.put("joinedAt", member.getJoinedAt());

            try {
                User user = userMapper.selectById(Long.valueOf(member.getUserId()));
                if (user != null) {
                    memberInfo.put("username", user.getUsername());
                    memberInfo.put("nickname", user.getNickname());
                    memberInfo.put("role", member.getRole());
                    // 从 users 表取等级和连续天数
                    memberInfo.put("level", user.getLevel() != null ? user.getLevel() : 1);
                    memberInfo.put("streak", user.getLoginDays() != null ? user.getLoginDays() : 0);
                }
            } catch (Exception e) {
                memberInfo.put("username", member.getUserId());
                memberInfo.put("nickname", member.getUserId());
                memberInfo.put("level", 1);
                memberInfo.put("streak", 0);
            }

            // 统计通关数（study_goals 中 progress >= 100）
            try {
                com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StudyGoal> goalWrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                goalWrapper.eq(StudyGoal::getUserId, Long.valueOf(member.getUserId()))
                           .ge(StudyGoal::getProgress, 100);
                memberInfo.put("passCount", studyGoalRepository.selectCount(goalWrapper));
            } catch (Exception e) {
                memberInfo.put("passCount", 0L);
            }

            // 统计资源数（group_resources 中该成员在本组上传的）
            try {
                com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GroupResource> resWrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                resWrapper.eq(GroupResource::getGroupId, groupId)
                           .eq(GroupResource::getUploaderId, member.getUserId());
                memberInfo.put("resourceCount", groupResourceMapper.selectCount(resWrapper));
            } catch (Exception e) {
                memberInfo.put("resourceCount", 0L);
            }

            // 统计发言数（study_post 中 user_id + group_id）
            try {
                com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StudyPost> postWrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
                postWrapper.eq(StudyPost::getUserId, member.getUserId())
                           .eq(StudyPost::getGroupId, groupId);
                memberInfo.put("posts", studyPostMapper.selectCount(postWrapper));
            } catch (Exception e) {
                memberInfo.put("posts", 0L);
            }

            result.add(memberInfo);
        }
        return result;
    }

    public List<StudyGroup> getAllPublicGroups() {
        LambdaQueryWrapper<StudyGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudyGroup::getStatus, "ACTIVE")
                .orderByDesc(StudyGroup::getCreatedAt);
        return studyGroupMapper.selectList(wrapper);
    }

    /** 获取所有小组及其实时统计数据（用于排行榜） */
    public List<Map<String, Object>> getAllGroupsWithStats() {
        List<StudyGroup> groups = getAllPublicGroups();
        List<Map<String, Object>> result = new ArrayList<>();
        for (StudyGroup g : groups) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", g.getId());
            item.put("groupName", g.getGroupName());
            item.put("description", g.getDescription());
            item.put("course", g.getCourse());
            item.put("maxMembers", g.getMaxMembers());
            item.put("createdAt", g.getCreatedAt());

            // 实时统计成员数
            LambdaQueryWrapper<GroupMember> memberWrapper = new LambdaQueryWrapper<>();
            memberWrapper.eq(GroupMember::getGroupId, g.getId());
            long realMemberCount = groupMemberMapper.selectCount(memberWrapper);
            item.put("memberCount", (int) realMemberCount);

            // 实时统计帖子数
            LambdaQueryWrapper<StudyPost> postWrapper = new LambdaQueryWrapper<>();
            postWrapper.eq(StudyPost::getGroupId, g.getId());
            long realPostCount = studyPostMapper.selectCount(postWrapper);
            item.put("postCount", (int) realPostCount);

            // 综合评分：成员满员率50% + 帖子活跃度30% + 成员数20%
            double memberRate = g.getMaxMembers() != null && g.getMaxMembers() > 0
                    ? (double) realMemberCount / g.getMaxMembers() : 0;
            int score = (int) Math.round(memberRate * 50 + Math.min(realPostCount, 20) * 1.5 + realMemberCount * 2);
            item.put("score", score);

            result.add(item);
        }
        return result;
    }

    public StudyGroup getGroupById(Long groupId) {
        return studyGroupMapper.selectById(groupId);
    }

    public boolean deleteGroup(Long groupId, String userId) {
        StudyGroup group = studyGroupMapper.selectById(groupId);
        if (group == null) {
            return false;
        }
        if (!userId.equals(group.getCreatorId())) {
            return false;
        }
        group.setStatus("DELETED");
        studyGroupMapper.updateById(group);
        log.info("学习小组删除成功: {} (ID: {})", group.getGroupName(), groupId);
        return true;
    }

    // ==================== 帖子 ====================

    public StudyPost createPost(StudyPost post) {
        log.info("开始创建帖子: userId={}, groupId={}, title={}", post.getUserId(), post.getGroupId(), post.getTitle());
        
        if (post.getLikeCount() == null) {
            post.setLikeCount(0);
        }
        if (post.getCommentCount() == null) {
            post.setCommentCount(0);
        }
        if (post.getViewCount() == null) {
            post.setViewCount(0);
        }
        if (post.getPostType() == null) {
            post.setPostType("DISCUSSION");
        }
        if (post.getIsPinned() == null) {
            post.setIsPinned(false);
        }
        if (post.getIsSolved() == null) {
            post.setIsSolved(false);
        }
        post.setCreatedAt(LocalDateTime.now());
        
        int result = studyPostMapper.insert(post);
        log.info("帖子插入结果: {}, 生成的ID: {}", result, post.getId());
        
        StudyPost saved = studyPostMapper.selectById(post.getId());
        log.info("验证帖子是否保存成功: {}", saved != null);
        
        return post;
    }

    public List<StudyPost> getGroupPosts(Long groupId) {
        log.info("查询小组帖子: groupId={}", groupId);
        LambdaQueryWrapper<StudyPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudyPost::getGroupId, groupId)
                .orderByDesc(StudyPost::getCreatedAt);
        List<StudyPost> posts = studyPostMapper.selectList(wrapper);
        log.info("查询结果: {} 条帖子", posts.size());
        return posts;
    }

    public boolean deletePost(Long postId, String userId) {
        StudyPost post = studyPostMapper.selectById(postId);
        if (post == null) {
            return false;
        }
        
        StudyGroup group = studyGroupMapper.selectById(post.getGroupId());
        boolean isGroupOwner = group != null && userId.equals(group.getCreatorId());
        
        if (!userId.equals(post.getUserId()) && !isGroupOwner) {
            return false;
        }
        studyPostMapper.deleteById(postId);
        
        LambdaQueryWrapper<StudyComment> commentWrapper = new LambdaQueryWrapper<>();
        commentWrapper.eq(StudyComment::getPostId, postId);
        studyCommentMapper.delete(commentWrapper);
        
        log.info("帖子删除成功: ID={}, 用户={}", postId, userId);
        return true;
    }

    // ==================== 评论 ====================

    public StudyComment addComment(StudyComment comment) {
        log.info("开始添加评论: userId={}, postId={}, content={}", comment.getUserId(), comment.getPostId(), comment.getContent());
        
        if (comment.getIsAccepted() == null) {
            comment.setIsAccepted(false);
        }
        comment.setCreatedAt(LocalDateTime.now());
        
        int result = studyCommentMapper.insert(comment);
        log.info("评论插入结果: {}, 生成的ID: {}", result, comment.getId());
        
        StudyComment saved = studyCommentMapper.selectById(comment.getId());
        log.info("验证评论是否保存成功: {}", saved != null);

        // 更新帖子评论数
        StudyPost post = studyPostMapper.selectById(comment.getPostId());
        if (post != null) {
            post.setCommentCount(post.getCommentCount() == null ? 1 : post.getCommentCount() + 1);
            studyPostMapper.updateById(post);
        }

        return comment;
    }

    public List<StudyComment> getPostComments(Long postId) {
        log.info("查询帖子评论: postId={}", postId);
        LambdaQueryWrapper<StudyComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudyComment::getPostId, postId)
                .orderByAsc(StudyComment::getCreatedAt);
        List<StudyComment> comments = studyCommentMapper.selectList(wrapper);
        log.info("查询结果: {} 条评论", comments.size());
        return comments;
    }

    public boolean deleteComment(Long commentId, String userId) {
        StudyComment comment = studyCommentMapper.selectById(commentId);
        if (comment == null) {
            return false;
        }
        
        StudyPost post = studyPostMapper.selectById(comment.getPostId());
        StudyGroup group = post != null ? studyGroupMapper.selectById(post.getGroupId()) : null;
        boolean isGroupOwner = group != null && userId.equals(group.getCreatorId());
        
        if (!userId.equals(comment.getUserId()) && !isGroupOwner) {
            return false;
        }
        studyCommentMapper.deleteById(commentId);
        
        if (post != null && post.getCommentCount() != null && post.getCommentCount() > 0) {
            post.setCommentCount(post.getCommentCount() - 1);
            studyPostMapper.updateById(post);
        }
        
        log.info("评论删除成功: ID={}, 用户={}", commentId, userId);
        return true;
    }

    // ==================== 小组资源 ====================

    public GroupResource uploadGroupResource(Long groupId, String uploaderId, String title, String description, String resourceType, String fileUrl, String fileName, Long fileSize) {
        StudyGroup group = studyGroupMapper.selectById(groupId);
        if (group == null) {
            throw new RuntimeException("小组不存在");
        }
        if (!uploaderId.equals(String.valueOf(group.getCreatorId()))) {
            throw new RuntimeException("只有小组创建者才能上传资源");
        }

        GroupResource resource = GroupResource.builder()
                .groupId(groupId)
                .uploaderId(uploaderId)
                .title(title)
                .description(description)
                .resourceType(resourceType)
                .fileUrl(fileUrl)
                .fileName(fileName)
                .fileSize(fileSize)
                .createdAt(LocalDateTime.now())
                .build();
        groupResourceMapper.insert(resource);
        log.info("资源上传成功: groupId={}, title={}", groupId, title);
        return resource;
    }

    public List<GroupResource> getGroupResources(Long groupId) {
        LambdaQueryWrapper<GroupResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupResource::getGroupId, groupId)
                .orderByDesc(GroupResource::getCreatedAt);
        return groupResourceMapper.selectList(wrapper);
    }

    public boolean deleteGroupResource(Long resourceId, String userId) {
        GroupResource resource = groupResourceMapper.selectById(resourceId);
        if (resource == null) {
            return false;
        }
        StudyGroup group = studyGroupMapper.selectById(resource.getGroupId());
        if (group == null) {
            return false;
        }
        if (!userId.equals(String.valueOf(group.getCreatorId()))) {
            throw new RuntimeException("只有小组创建者才能删除资源");
        }
        groupResourceMapper.deleteById(resourceId);
        log.info("资源删除成功: ID={}, 用户={}", resourceId, userId);
        return true;
    }

    // ==================== 同伴辅导 ====================

    public PeerTutoring requestTutoring(String userId, String topic, String description) {
        PeerTutoring request = PeerTutoring.builder()
                .requesterId(userId)
                .topic(topic)
                .description(description)
                .status("OPEN")
                .createdAt(LocalDateTime.now())
                .build();
        peerTutoringMapper.insert(request);
        log.info("辅导请求已创建: topic={}, 用户={}", topic, userId);
        return request;
    }

    public List<PeerTutoring> getMyTutoringRequests(String userId) {
        LambdaQueryWrapper<PeerTutoring> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PeerTutoring::getRequesterId, userId)
                .orderByDesc(PeerTutoring::getCreatedAt);
        return peerTutoringMapper.selectList(wrapper);
    }

    public void completeTutoring(Long tutoringId, int rating, String feedback) {
        PeerTutoring tutoring = peerTutoringMapper.selectById(tutoringId);
        if (tutoring != null) {
            tutoring.setStatus("COMPLETED");
            tutoring.setRating(Math.min(5, Math.max(1, rating)));
            tutoring.setFeedback(feedback);
            peerTutoringMapper.updateById(tutoring);
            log.info("辅导完成: ID={}, 评分={}", tutoringId, rating);
        }
    }

    // ==================== 成就 ====================

    public List<UserAchievement> getUserAchievements(String userId) {
        LambdaQueryWrapper<UserAchievement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAchievement::getUserId, userId)
                .orderByDesc(UserAchievement::getEarnedAt);
        return userAchievementMapper.selectList(wrapper);
    }

    public UserAchievement awardAchievement(String userId, String achievementType) {
        // 检查是否已获得该类型成就
        LambdaQueryWrapper<UserAchievement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAchievement::getUserId, userId)
                .eq(UserAchievement::getAchievementType, achievementType);
        if (userAchievementMapper.selectCount(wrapper) > 0) {
            return null;
        }

        UserAchievement achievement = UserAchievement.builder()
                .userId(userId)
                .achievementType(achievementType)
                .title(getAchievementTitle(achievementType))
                .description(getAchievementDescription(achievementType))
                .iconUrl(getAchievementIcon(achievementType))
                .earnedAt(LocalDateTime.now())
                .build();
        userAchievementMapper.insert(achievement);
        log.info("用户 {} 获得成就: {}", userId, achievementType);
        return achievement;
    }

    private String getAchievementTitle(String type) {
        return switch (type) {
            case "FIRST_POST" -> "初次发言";
            case "HELPER" -> "热心助人";
            case "STUDY_STREAK" -> "学习连击";
            case "GROUP_LEADER" -> "小组领袖";
            case "KNOWLEDGE_MASTER" -> "知识达人";
            default -> type;
        };
    }

    private String getAchievementDescription(String type) {
        return switch (type) {
            case "FIRST_POST" -> "发布第一篇学习帖子";
            case "HELPER" -> "成功辅导3次以上同伴";
            case "STUDY_STREAK" -> "连续7天参与学习";
            case "GROUP_LEADER" -> "创建并被评选为优秀学习小组";
            case "KNOWLEDGE_MASTER" -> "解答超过10个学习问题";
            default -> "获得特殊成就";
        };
    }

    private String getAchievementIcon(String type) {
        return switch (type) {
            case "FIRST_POST" -> "/icons/first-post.png";
            case "HELPER" -> "/icons/helper.png";
            case "STUDY_STREAK" -> "/icons/streak.png";
            case "GROUP_LEADER" -> "/icons/leader.png";
            case "KNOWLEDGE_MASTER" -> "/icons/master.png";
            default -> "/icons/achievement.png";
        };
    }

    // ==================== 排行榜 ====================

    public List<Map<String, Object>> getLeaderboard(String courseName, int limit) {
        // 根据帖子数和辅导评分综合排名
        LambdaQueryWrapper<PeerTutoring> tutoringWrapper = new LambdaQueryWrapper<>();
        tutoringWrapper.eq(PeerTutoring::getStatus, "COMPLETED")
                .isNotNull(PeerTutoring::getRating);
        List<PeerTutoring> completedTutorings = peerTutoringMapper.selectList(tutoringWrapper);

        // 按tutorId聚合评分
        Map<String, Double> tutorScores = new HashMap<>();
        Map<String, Integer> tutorCounts = new HashMap<>();
        for (PeerTutoring pt : completedTutorings) {
            if (pt.getTutorId() != null) {
                tutorScores.merge(pt.getTutorId(), pt.getRating().doubleValue(), Double::sum);
                tutorCounts.merge(pt.getTutorId(), 1, Integer::sum);
            }
        }

        List<Map<String, Object>> leaderboard = new ArrayList<>();
        for (String userId : tutorScores.keySet()) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("userId", userId);
            entry.put("totalRating", tutorScores.getOrDefault(userId, 0.0));
            entry.put("tutoringCount", tutorCounts.getOrDefault(userId, 0));
            entry.put("avgRating", tutorCounts.get(userId) > 0
                    ? tutorScores.get(userId) / tutorCounts.get(userId) : 0.0);
            leaderboard.add(entry);
        }

        leaderboard.sort((a, b) -> Double.compare(
                (Double) b.get("totalRating"), (Double) a.get("totalRating")));

        return leaderboard.stream().limit(limit).collect(Collectors.toList());
    }
}