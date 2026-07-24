package com.eduagent.config;

import com.eduagent.entity.*;
import com.eduagent.mapper.*;
import com.eduagent.service.DataMigrationService;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;

@Slf4j
@Configuration
public class DataInitializer {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Bean
    CommandLineRunner initTestUsers(UserMapper userMapper) {
        return args -> {
            Long count = userMapper.selectCount(null);
            if (count > 0) {
                log.info("Users already exist ({}), skipping user initialization", count);
                return;
            }

            userMapper.insert(createUser("admin", "admin123", "系统管理员", "admin@example.com", "admin"));
            userMapper.insert(createUser("testuser", "123456", "测试同学", "test@example.com", "student"));
            userMapper.insert(createUser("student1", "123456", "学生小明", "student1@example.com", "student"));

            log.info("3 test users created successfully");
        };
    }

    @Bean
    CommandLineRunner initStudyGroups(StudyGroupMapper groupMapper, GroupMemberMapper memberMapper, UserMapper userMapper) {
        return args -> {
            Long count = groupMapper.selectCount(null);
            if (count > 0) {
                log.info("Study groups already exist ({}), skipping group initialization", count);
                return;
            }

            StudyGroup group1 = new StudyGroup();
            group1.setGroupName("Python学习小组");
            group1.setDescription("一起学习Python编程，从入门到精通");
            group1.setCourse("Python程序设计");
            group1.setCreatorId("1");
            group1.setMemberCount(3);
            group1.setMaxMembers(50);
            group1.setPostCount(0);
            group1.setStatus("ACTIVE");
            group1.setCreatedAt(LocalDateTime.now());
            groupMapper.insert(group1);

            StudyGroup group2 = new StudyGroup();
            group2.setGroupName("算法刷题小组");
            group2.setDescription("每日算法练习，备战面试");
            group2.setCourse("数据结构与算法");
            group2.setCreatorId("2");
            group2.setMemberCount(3);
            group2.setMaxMembers(50);
            group2.setPostCount(0);
            group2.setStatus("ACTIVE");
            group2.setCreatedAt(LocalDateTime.now());
            groupMapper.insert(group2);

            GroupMember m1 = new GroupMember();
            m1.setGroupId(group1.getId());
            m1.setUserId("1");
            m1.setRole("CREATOR");
            m1.setJoinedAt(LocalDateTime.now());
            memberMapper.insert(m1);

            GroupMember m2 = new GroupMember();
            m2.setGroupId(group1.getId());
            m2.setUserId("2");
            m2.setRole("MEMBER");
            m2.setJoinedAt(LocalDateTime.now());
            memberMapper.insert(m2);

            GroupMember m3 = new GroupMember();
            m3.setGroupId(group1.getId());
            m3.setUserId("3");
            m3.setRole("MEMBER");
            m3.setJoinedAt(LocalDateTime.now());
            memberMapper.insert(m3);

            GroupMember m4 = new GroupMember();
            m4.setGroupId(group2.getId());
            m4.setUserId("2");
            m4.setRole("CREATOR");
            m4.setJoinedAt(LocalDateTime.now());
            memberMapper.insert(m4);

            GroupMember m5 = new GroupMember();
            m5.setGroupId(group2.getId());
            m5.setUserId("1");
            m5.setRole("MEMBER");
            m5.setJoinedAt(LocalDateTime.now());
            memberMapper.insert(m5);

            GroupMember m6 = new GroupMember();
            m6.setGroupId(group2.getId());
            m6.setUserId("3");
            m6.setRole("MEMBER");
            m6.setJoinedAt(LocalDateTime.now());
            memberMapper.insert(m6);

            log.info("2 study groups created with 3 members each");
        };
    }

    private User createUser(String username, String password, String nickname, String email, String role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setEmail(email);
        user.setRole(role);
        user.setStatus("active");
        user.setCreatedAt(LocalDateTime.now());
        user.setLevel("L1");
        user.setLoginDays(0);
        return user;
    }

    @Bean
    CommandLineRunner migrateProfiles(DataMigrationService migrationService) {
        return args -> {
            try {
                Map<String, Object> r = migrationService.migrateAllProfiles();
                log.info("画像迁移完成: {}", r);
            } catch (Exception e) { log.warn("画像迁移跳过: {}", e.getMessage()); }
        };
    }
}