package com.eduagent.service;

import com.eduagent.entity.StudyGoal;
import com.eduagent.repository.StudyGoalRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class StudyGoalService {

    private final StudyGoalRepository studyGoalRepository;
    private final UserProfileService userProfileService;
    private final MemoryService memoryService;
    private final PushService pushService;

    public StudyGoalService(StudyGoalRepository studyGoalRepository,
                            UserProfileService userProfileService,
                            MemoryService memoryService,
                            PushService pushService) {
        this.studyGoalRepository = studyGoalRepository;
        this.userProfileService = userProfileService;
        this.memoryService = memoryService;
        this.pushService = pushService;
    }

    public List<StudyGoal> getGoalsByUserId(Long userId) {
        return studyGoalRepository.findByUserId(userId);
    }

    public StudyGoal getGoalById(Long id) {
        return studyGoalRepository.selectById(id);
    }

    public StudyGoal createGoal(Long userId, StudyGoal goal) {
        // 检查重复
        List<StudyGoal> existing = studyGoalRepository.findByUserId(userId);
        if (existing.stream().anyMatch(g -> g.getTitle().equals(goal.getTitle()))) {
            throw new RuntimeException("该学习目标已存在");
        }
        goal.setUserId(userId);
        if (goal.getProgress() == null) goal.setProgress(0);
        if (goal.getCurrentResourceIndex() == null) goal.setCurrentResourceIndex(0);
        goal.setCreatedAt(LocalDateTime.now());
        goal.setUpdatedAt(LocalDateTime.now());
        studyGoalRepository.insert(goal);
        return goal;
    }

    public StudyGoal updateGoal(Long userId, Long goalId, StudyGoal update) {
        StudyGoal goal = studyGoalRepository.selectById(goalId);
        if (goal == null || !goal.getUserId().equals(userId)) {
            return null;
        }
        if (update.getTitle() != null) goal.setTitle(update.getTitle());
        if (update.getIcon() != null) goal.setIcon(update.getIcon());
        if (update.getCategory() != null) goal.setCategory(update.getCategory());
        if (update.getProgress() != null) goal.setProgress(update.getProgress());
        if (update.getColor() != null) goal.setColor(update.getColor());
        if (update.getResources() != null) goal.setResources(update.getResources());
        if (update.getCurrentResourceIndex() != null) goal.setCurrentResourceIndex(update.getCurrentResourceIndex());
        if (update.getCompletedResources() != null) goal.setCompletedResources(update.getCompletedResources());
        if (update.getLastStudyTime() != null) goal.setLastStudyTime(update.getLastStudyTime());
        if (update.getCurrentStageIndex() != null) goal.setCurrentStageIndex(update.getCurrentStageIndex());
        if (update.getLearningPath() != null) goal.setLearningPath(update.getLearningPath());
        goal.setUpdatedAt(LocalDateTime.now());
        studyGoalRepository.updateById(goal);

        if (update.getProgress() != null && update.getProgress() >= 100) {
            try {
                userProfileService.updateKeywords(userId, goal.getTitle());
                if (goal.getCategory() != null && !goal.getCategory().isEmpty()) {
                    userProfileService.updateUserInterests(userId, goal.getCategory());
                }
                memoryService.updateLongTermMemory(userId, goal.getTitle(),
                        goal.getCategory() != null ? goal.getCategory() : "", 100);
                log.info("Goal completed - profile and memory updated: userId={}, goal={}", userId, goal.getTitle());

                // 触发资源推送
                try {
                    pushService.generatePushOnGoalComplete(userId, goal);
                    log.info("Goal push generated: userId={}, goal={}", userId, goal.getTitle());
                } catch (Exception e) {
                    log.warn("Failed to generate push: {}", e.getMessage());
                }
            } catch (Exception e) {
                log.warn("Failed to update profile on goal completion: {}", e.getMessage());
            }
        } else if (update.getProgress() != null) {
            // 进度有变化但未完成，也更新记忆
            try {
                memoryService.updateLongTermMemory(userId, goal.getTitle(),
                        goal.getCategory() != null ? goal.getCategory() : "", update.getProgress());
            } catch (Exception e) {
                log.warn("Failed to update memory: {}", e.getMessage());
            }
        }

        return goal;
    }

    public boolean deleteGoal(Long userId, Long goalId) {
        StudyGoal goal = studyGoalRepository.selectById(goalId);
        if (goal == null || !goal.getUserId().equals(userId)) {
            return false;
        }
        studyGoalRepository.deleteById(goalId);
        return true;
    }
}