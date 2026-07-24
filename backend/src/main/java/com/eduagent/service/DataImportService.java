package com.eduagent.service;

import com.eduagent.entity.KnowledgeBase;
import com.eduagent.entity.KnowledgeEntry;
import com.eduagent.mapper.KnowledgeBaseMapper;
import com.eduagent.mapper.KnowledgeEntryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class DataImportService {

    private final KnowledgeBaseMapper baseMapper;
    private final KnowledgeEntryMapper entryMapper;

    private static final String REPO_PATH = "data/repos";
    private static final String AI_LEARN_PATH = REPO_PATH + "/Ai-Learn-master";
    private static final String FREE_BOOKS_PATH = REPO_PATH + "/free-programming-books-zh_CN-main";

    private static final Map<String, String> DOMAIN_MAP = new HashMap<>();

    static {
        DOMAIN_MAP.put("Python", "PYTHON");
        DOMAIN_MAP.put("python", "PYTHON");
        DOMAIN_MAP.put("NLP", "PYTHON");
        DOMAIN_MAP.put("BERT", "PYTHON");
        DOMAIN_MAP.put("数据分析", "PYTHON");
        DOMAIN_MAP.put("机器学习", "PYTHON");
        DOMAIN_MAP.put("数据挖掘", "PYTHON");
        DOMAIN_MAP.put("数学", "GENERAL");
        DOMAIN_MAP.put("深度学习", "PYTHON");
        DOMAIN_MAP.put("计算机视觉", "PYTHON");
    }

    public DataImportService(KnowledgeBaseMapper baseMapper, KnowledgeEntryMapper entryMapper) {
        this.baseMapper = baseMapper;
        this.entryMapper = entryMapper;
    }

    public Map<String, Object> importAll() {
        Map<String, Object> result = new HashMap<>();
        int total = 0;

        try {
            int aiLearnCount = importAiLearn();
            total += aiLearnCount;
            result.put("aiLearn", aiLearnCount);
            log.info("Ai-Learn imported: {} entries", aiLearnCount);
        } catch (Exception e) {
            log.error("Ai-Learn import failed", e);
            result.put("aiLearnError", e.getMessage());
        }

        try {
            int bookCount = importFreeBooks();
            total += bookCount;
            result.put("freeBooks", bookCount);
            log.info("Free books imported: {} entries", bookCount);
        } catch (Exception e) {
            log.error("Free books import failed", e);
            result.put("freeBooksError", e.getMessage());
        }

        result.put("total", total);
        return result;
    }

    private int importAiLearn() throws IOException {
        Path aiLearnDir = Paths.get(AI_LEARN_PATH);
        if (!Files.exists(aiLearnDir)) {
            log.warn("Ai-Learn directory not found: {}", AI_LEARN_PATH);
            return 0;
        }

        List<Path> mdFiles = new ArrayList<>();
        Files.walkFileTree(aiLearnDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.toString().toLowerCase();
                if (name.endsWith(".md") || name.endsWith(".txt")) {
                    mdFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        log.info("Found {} files in Ai-Learn", mdFiles.size());
        int count = 0;

        for (Path mdFile : mdFiles) {
            try {
                String content = Files.readString(mdFile, StandardCharsets.UTF_8);
                if (content.trim().isEmpty()) continue;

                String fileName = mdFile.getFileName().toString();
                String title = fileName.replace(".md", "").replace(".txt", "");
                String parentDir = mdFile.getParent().getFileName().toString();
                String domain = classifyDomain(parentDir, title);

                KnowledgeBase base = baseMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getDomain, domain)
                ).stream().findFirst().orElse(null);

                if (base == null) {
                    base = baseMapper.selectById(1L);
                }

                String excerpt = content.length() > 2000 ? content.substring(0, 2000) : content;

                KnowledgeEntry entry = KnowledgeEntry.builder()
                    .baseId(base.getId())
                    .title(title)
                    .content(excerpt)
                    .category(parentDir)
                    .subModule(parentDir)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

                entryMapper.insert(entry);
                count++;

                if (count % 50 == 0) {
                    log.info("Imported {} entries so far...", count);
                }
            } catch (Exception e) {
                log.warn("Failed to import file: {} - {}", mdFile, e.getMessage());
            }
        }

        return count;
    }

    private int importFreeBooks() throws IOException {
        Path booksDir = Paths.get(FREE_BOOKS_PATH);
        if (!Files.exists(booksDir)) {
            log.warn("Free books directory not found: {}", FREE_BOOKS_PATH);
            return 0;
        }

        Path readmePath = booksDir.resolve("README.md");
        if (!Files.exists(readmePath)) {
            log.warn("README.md not found in free books");
            return 0;
        }

        String content = Files.readString(readmePath, StandardCharsets.UTF_8);
        String[] lines = content.split("\n");
        int count = 0;

        KnowledgeBase generalBase = baseMapper.selectById(1L);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("* [") && line.contains("](")) {
                try {
                    int titleStart = line.indexOf("[") + 1;
                    int titleEnd = line.indexOf("](");
                    String title = line.substring(titleStart, titleEnd);

                    int urlStart = titleEnd + 2;
                    int urlEnd = line.indexOf(")", urlStart);
                    String url = line.substring(urlStart, urlEnd);

                    String metadata = "{\"url\":\"" + url + "\",\"source\":\"free-programming-books-zh_CN\"}";

                    if (title.length() > 200) title = title.substring(0, 200);

                    KnowledgeEntry entry = KnowledgeEntry.builder()
                        .baseId(generalBase.getId())
                        .title(title)
                        .content("免费中文计算机书籍: " + url)
                        .category("书籍资源")
                        .subModule("免费书籍")
                        .metadata(metadata)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

                    entryMapper.insert(entry);
                    count++;
                } catch (Exception e) {
                    // skip malformed lines
                }
            }
        }

        return count;
    }

    private String classifyDomain(String dirName, String title) {
        for (Map.Entry<String, String> entry : DOMAIN_MAP.entrySet()) {
            if (dirName.contains(entry.getKey()) || title.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "GENERAL";
    }
}