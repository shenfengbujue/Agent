package com.eduagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduagent.entity.LikeRecord;
import com.eduagent.entity.ResourceStats;
import com.eduagent.entity.ResourceViewLog;
import com.eduagent.mapper.LikeRecordMapper;
import com.eduagent.mapper.ResourceStatsMapper;
import com.eduagent.mapper.ResourceViewLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ResourceInteractionService {

    private final ResourceStatsMapper resourceStatsMapper;
    private final ResourceViewLogMapper resourceViewLogMapper;
    private final LikeRecordMapper likeRecordMapper;

    public ResourceInteractionService(ResourceStatsMapper resourceStatsMapper,
                                      ResourceViewLogMapper resourceViewLogMapper,
                                      LikeRecordMapper likeRecordMapper) {
        this.resourceStatsMapper = resourceStatsMapper;
        this.resourceViewLogMapper = resourceViewLogMapper;
        this.likeRecordMapper = likeRecordMapper;
    }

    public Map<String, Object> getStats(Long resourceId) {
        ResourceStats stats = resourceStatsMapper.selectById(resourceId);
        Map<String, Object> result = new HashMap<>();
        result.put("resourceId", resourceId);
        result.put("likeCount", stats == null || stats.getLikeCount() == null ? 0 : stats.getLikeCount());
        result.put("viewCount", stats == null || stats.getViewCount() == null ? 0 : stats.getViewCount());
        return result;
    }

    public Map<String, Object> getBatchStats(List<Long> resourceIds) {
        Map<String, Object> result = new HashMap<>();
        if (resourceIds == null || resourceIds.isEmpty()) {
            return result;
        }
        LambdaQueryWrapper<ResourceStats> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ResourceStats::getResourceId, resourceIds);
        List<ResourceStats> statsList = resourceStatsMapper.selectList(wrapper);
        for (ResourceStats stats : statsList) {
            Map<String, Object> item = new HashMap<>();
            item.put("likeCount", stats.getLikeCount() == null ? 0 : stats.getLikeCount());
            item.put("viewCount", stats.getViewCount() == null ? 0 : stats.getViewCount());
            result.put(String.valueOf(stats.getResourceId()), item);
        }
        return result;
    }

    public boolean hasLiked(Long resourceId, String userId) {
        LambdaQueryWrapper<LikeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LikeRecord::getUserId, userId)
                .eq(LikeRecord::getTargetType, "RESOURCE")
                .eq(LikeRecord::getTargetId, resourceId);
        return likeRecordMapper.selectCount(wrapper) > 0;
    }

    public Map<String, Object> toggleLike(Long resourceId, String userId) {
        LambdaQueryWrapper<LikeRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LikeRecord::getUserId, userId)
                .eq(LikeRecord::getTargetType, "RESOURCE")
                .eq(LikeRecord::getTargetId, resourceId);
        LikeRecord existing = likeRecordMapper.selectOne(wrapper);

        boolean liked;
        if (existing == null) {
            LikeRecord record = LikeRecord.builder()
                    .userId(userId)
                    .targetType("RESOURCE")
                    .targetId(resourceId)
                    .createdAt(LocalDateTime.now())
                    .build();
            likeRecordMapper.insert(record);
            incrementLikeCount(resourceId, 1);
            liked = true;
        } else {
            likeRecordMapper.deleteById(existing.getId());
            incrementLikeCount(resourceId, -1);
            liked = false;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("liked", liked);
        Map<String, Object> stats = getStats(resourceId);
        result.put("likeCount", stats.get("likeCount"));
        result.put("viewCount", stats.get("viewCount"));
        return result;
    }

    public boolean recordView(Long resourceId, String userId) {
        LambdaQueryWrapper<ResourceViewLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ResourceViewLog::getResourceId, resourceId)
                .eq(ResourceViewLog::getUserId, userId);
        if (resourceViewLogMapper.selectCount(wrapper) > 0) {
            return false;
        }

        ResourceViewLog log = ResourceViewLog.builder()
                .resourceId(resourceId)
                .userId(userId)
                .viewedAt(LocalDateTime.now())
                .build();
        resourceViewLogMapper.insert(log);
        incrementViewCount(resourceId, 1);
        return true;
    }

    private void incrementLikeCount(Long resourceId, int delta) {
        ResourceStats stats = resourceStatsMapper.selectById(resourceId);
        if (stats == null) {
            stats = ResourceStats.builder()
                    .resourceId(resourceId)
                    .likeCount(Math.max(0, delta))
                    .viewCount(0)
                    .updatedAt(LocalDateTime.now())
                    .build();
            resourceStatsMapper.insert(stats);
        } else {
            int newCount = Math.max(0, (stats.getLikeCount() == null ? 0 : stats.getLikeCount()) + delta);
            stats.setLikeCount(newCount);
            stats.setUpdatedAt(LocalDateTime.now());
            resourceStatsMapper.updateById(stats);
        }
    }

    private void incrementViewCount(Long resourceId, int delta) {
        ResourceStats stats = resourceStatsMapper.selectById(resourceId);
        if (stats == null) {
            stats = ResourceStats.builder()
                    .resourceId(resourceId)
                    .likeCount(0)
                    .viewCount(Math.max(0, delta))
                    .updatedAt(LocalDateTime.now())
                    .build();
            resourceStatsMapper.insert(stats);
        } else {
            int newCount = Math.max(0, (stats.getViewCount() == null ? 0 : stats.getViewCount()) + delta);
            stats.setViewCount(newCount);
            stats.setUpdatedAt(LocalDateTime.now());
            resourceStatsMapper.updateById(stats);
        }
    }
}
