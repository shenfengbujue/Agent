package com.eduagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduagent.entity.KnowledgeBase;
import com.eduagent.entity.KnowledgeEntry;
import com.eduagent.mapper.KnowledgeBaseMapper;
import com.eduagent.mapper.KnowledgeEntryMapper;
import com.eduagent.mapper.IntentRuleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper baseMapper;
    private final KnowledgeEntryMapper entryMapper;
    private final IntentRuleMapper intentRuleMapper;

    public KnowledgeBaseService(KnowledgeBaseMapper baseMapper,
                                KnowledgeEntryMapper entryMapper,
                                IntentRuleMapper intentRuleMapper) {
        this.baseMapper = baseMapper;
        this.entryMapper = entryMapper;
        this.intentRuleMapper = intentRuleMapper;
    }

    public List<KnowledgeBase> getAllBases() {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getStatus, "ACTIVE");
        return baseMapper.selectList(wrapper);
    }

    public KnowledgeBase getBaseById(Long id) {
        return baseMapper.selectById(id);
    }

    public KnowledgeBase getBaseByDomain(String domain) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getDomain, domain);
        return baseMapper.selectOne(wrapper);
    }

    public KnowledgeBase createBase(KnowledgeBase base) {
        base.setCreatedAt(LocalDateTime.now());
        base.setUpdatedAt(LocalDateTime.now());
        base.setStatus("ACTIVE");
        baseMapper.insert(base);
        log.info("创建知识库: {} (ID: {})", base.getName(), base.getId());
        return base;
    }

    public boolean updateBase(Long id, KnowledgeBase base) {
        KnowledgeBase existing = baseMapper.selectById(id);
        if (existing == null) {
            return false;
        }
        if (base.getName() != null) existing.setName(base.getName());
        if (base.getDescription() != null) existing.setDescription(base.getDescription());
        if (base.getDomain() != null) existing.setDomain(base.getDomain());
        if (base.getStatus() != null) existing.setStatus(base.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());
        return baseMapper.updateById(existing) > 0;
    }

    public boolean deleteBase(Long id) {
        return baseMapper.deleteById(id) > 0;
    }

    public List<KnowledgeEntry> getEntriesByBaseId(Long baseId) {
        return entryMapper.selectByBaseId(baseId);
    }

    public List<KnowledgeEntry> getEntriesByBaseIdAndCategory(Long baseId, String category) {
        return entryMapper.selectByBaseIdAndCategory(baseId, category);
    }

    public KnowledgeEntry getEntryById(Long id) {
        return entryMapper.selectById(id);
    }

    public KnowledgeEntry createEntry(KnowledgeEntry entry) {
        entry.setCreatedAt(LocalDateTime.now());
        entry.setUpdatedAt(LocalDateTime.now());
        entryMapper.insert(entry);
        log.info("创建知识条目: {} (ID: {})", entry.getTitle(), entry.getId());
        return entry;
    }

    public boolean updateEntry(Long id, KnowledgeEntry entry) {
        KnowledgeEntry existing = entryMapper.selectById(id);
        if (existing == null) {
            return false;
        }
        if (entry.getTitle() != null) existing.setTitle(entry.getTitle());
        if (entry.getContent() != null) existing.setContent(entry.getContent());
        if (entry.getCategory() != null) existing.setCategory(entry.getCategory());
        if (entry.getSubModule() != null) existing.setSubModule(entry.getSubModule());
        if (entry.getMetadata() != null) existing.setMetadata(entry.getMetadata());
        if (entry.getEntryType() != null) existing.setEntryType(entry.getEntryType());
        if (entry.getPlanData() != null) existing.setPlanData(entry.getPlanData());
        if (entry.getOwnerId() != null) existing.setOwnerId(entry.getOwnerId());
        existing.setUpdatedAt(LocalDateTime.now());
        return entryMapper.updateById(existing) > 0;
    }

    public boolean deleteEntry(Long id) {
        return entryMapper.deleteById(id) > 0;
    }

    public List<KnowledgeEntry> searchEntries(Long baseId, String keyword, int limit) {
        int safeLimit = Math.min(limit, 500);
        LambdaQueryWrapper<KnowledgeEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeEntry::getBaseId, baseId);
        wrapper.and(w -> w.like(KnowledgeEntry::getTitle, keyword)
                .or().like(KnowledgeEntry::getContent, keyword)
                .or().like(KnowledgeEntry::getCategory, keyword));
        wrapper.last("LIMIT " + safeLimit);
        return entryMapper.selectList(wrapper);
    }

    public List<KnowledgeEntry> searchAllEntries(String keyword, int limit) {
        int safeLimit = Math.min(limit, 500);
        LambdaQueryWrapper<KnowledgeEntry> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(KnowledgeEntry::getTitle, keyword)
                    .or().like(KnowledgeEntry::getContent, keyword)
                    .or().like(KnowledgeEntry::getCategory, keyword));
        }
        wrapper.orderByDesc(KnowledgeEntry::getCreatedAt);
        wrapper.last("LIMIT " + safeLimit);
        return entryMapper.selectList(wrapper);
    }
}