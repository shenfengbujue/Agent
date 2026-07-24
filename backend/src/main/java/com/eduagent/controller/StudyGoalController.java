package com.eduagent.controller;

import com.eduagent.entity.StudyGoal;
import com.eduagent.service.StudyGoalService;
import com.eduagent.model.vo.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/goals")
public class StudyGoalController {

    private final StudyGoalService studyGoalService;

    public StudyGoalController(StudyGoalService studyGoalService) {
        this.studyGoalService = studyGoalService;
    }

    @GetMapping("/user/{userId}")
    public Result<List<StudyGoal>> getGoalsByUserId(@PathVariable Long userId) {
        List<StudyGoal> goals = studyGoalService.getGoalsByUserId(userId);
        return Result.success(goals);
    }

    @GetMapping("/{id}")
    public Result<StudyGoal> getGoalById(@PathVariable Long id) {
        StudyGoal goal = studyGoalService.getGoalById(id);
        if (goal == null) {
            return Result.error("学习目标不存在");
        }
        return Result.success(goal);
    }

    @PostMapping("/user/{userId}")
    public Result<StudyGoal> createGoal(@PathVariable Long userId, @RequestBody StudyGoal goal) {
        StudyGoal created = studyGoalService.createGoal(userId, goal);
        return Result.success(created);
    }

    @PutMapping("/user/{userId}/{goalId}")
    public Result<StudyGoal> updateGoal(
            @PathVariable Long userId,
            @PathVariable Long goalId,
            @RequestBody StudyGoal update) {
        StudyGoal updated = studyGoalService.updateGoal(userId, goalId, update);
        if (updated == null) {
            return Result.error("更新失败");
        }
        return Result.success(updated);
    }

    @DeleteMapping("/user/{userId}/{goalId}")
    public Result<Boolean> deleteGoal(@PathVariable Long userId, @PathVariable Long goalId) {
        boolean deleted = studyGoalService.deleteGoal(userId, goalId);
        if (!deleted) {
            return Result.error("删除失败");
        }
        return Result.success(true);
    }
}