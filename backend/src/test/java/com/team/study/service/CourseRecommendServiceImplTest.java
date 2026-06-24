package com.team.study.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.study.dto.request.CourseRecommendRequest;
import com.team.study.dto.request.SavedCourseRequest;
import com.team.study.dto.response.CourseCard;
import com.team.study.dto.response.SavedCourseResponse;
import com.team.study.dto.response.WebSearchResult;
import com.team.study.entity.Material;
import com.team.study.entity.SavedCourse;
import com.team.study.repository.MaterialRepository;
import com.team.study.repository.SavedCourseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseRecommendServiceImplTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;
    @Mock
    private WebSearchService webSearchService;
    @Mock
    private DocumentIngestionService documentIngestionService;
    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private SavedCourseRepository savedCourseRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void keywordsUseMaterialContextAndChatClient() {
        loginAs(7L);
        CourseRecommendServiceImpl service = service();
        Material material = material(99L, 7L, "操作系统.pdf", 3L);
        when(materialRepository.findByIdAndUserId(99L, 7L)).thenReturn(Optional.of(material));
        when(documentIngestionService.getMaterialContext(7L, 99L, 6))
                .thenReturn("进程调度、死锁、内存分页是本资料的核心内容。");
        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("[\"操作系统 进程调度\", \"死锁\", \"内存分页\"]");

        List<String> keywords = service.keywords(99L);

        assertThat(keywords).containsExactly("操作系统 进程调度", "死锁", "内存分页");
    }

    @Test
    void keywordsFilterOutWatermarkAndPlatformNoise() {
        loginAs(7L);
        CourseRecommendServiceImpl service = service();
        Material material = material(99L, 7L, "4.8页面置换法.pdf", 3L);
        when(materialRepository.findByIdAndUserId(99L, 7L)).thenReturn(Optional.of(material));
        when(documentIngestionService.getMaterialContext(7L, 99L, 6))
                .thenReturn("页面置换算法、缺页中断是本资料核心内容。");
        // 模型受水印干扰吐出平台名 + 真实主题词
        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("[\"中国大学MOOC\", \"慕课\", \"页面置换算法\"]");

        assertThat(service.keywords(99L)).containsExactly("页面置换算法");
    }

    @Test
    void keywordsFallbackToFilenameWhenAllKeywordsAreNoise() {
        loginAs(7L);
        CourseRecommendServiceImpl service = service();
        Material material = material(99L, 7L, "4.8页面置换法.pdf", 3L);
        when(materialRepository.findByIdAndUserId(99L, 7L)).thenReturn(Optional.of(material));
        when(documentIngestionService.getMaterialContext(7L, 99L, 6))
                .thenReturn("中国大学MOOC 中国大学MOOC 中国大学MOOC 在线课程。");
        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("[\"中国大学MOOC\", \"慕课\", \"在线课程\"]");

        assertThat(service.keywords(99L)).containsExactly("4.8页面置换法");
    }

    @Test
    void keywordsFallbackToMaterialFilenameWhenContextIsBlank() {
        loginAs(7L);
        CourseRecommendServiceImpl service = service();
        Material material = material(99L, 7L, "Java并发讲义.pdf", 3L);
        when(materialRepository.findByIdAndUserId(99L, 7L)).thenReturn(Optional.of(material));
        when(documentIngestionService.getMaterialContext(7L, 99L, 6)).thenReturn(" ");

        assertThat(service.keywords(99L)).containsExactly("Java并发讲义");
        verify(chatClient, never()).prompt();
    }

    @Test
    void recommendReturnsEmptyAndSkipsChatClientWhenSearchHasNoResults() {
        loginAs(7L);
        CourseRecommendServiceImpl service = service();
        when(webSearchService.search("Java 并发 视频教程 课程", 20)).thenReturn(List.of());

        List<CourseCard> cards = service.recommend(new CourseRecommendRequest(List.of("Java 并发")));

        assertThat(cards).isEmpty();
        verify(chatClient, never()).prompt();
    }

    @Test
    void recommendParsesCourseCardsFromChatClient() {
        loginAs(7L);
        CourseRecommendServiceImpl service = service();
        when(webSearchService.search("Java 并发 视频教程 课程", 20)).thenReturn(List.of(
                new WebSearchResult("Java 并发课程", "https://example.com/java", "线程和锁")));
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("""
                        [
                          {
                            "title": "Java 并发课程",
                            "platform": "B站",
                            "url": "https://example.com/java",
                            "reason": "覆盖线程、锁和线程池。",
                            "difficulty": "进阶"
                          }
                        ]
                        """);

        List<CourseCard> cards = service.recommend(new CourseRecommendRequest(List.of("Java 并发")));

        assertThat(cards).containsExactly(new CourseCard(
                "Java 并发课程",
                "B站",
                "https://example.com/java",
                "覆盖线程、锁和线程池。",
                "进阶"));
    }

    @Test
    void recommendMergesPlatformTargetedSearchesAndFloatsWhitelistedDomains() {
        loginAs(7L);
        CourseRecommendServiceImpl service = service();
        when(webSearchService.search("操作系统 进程调度 视频教程 课程", 20)).thenReturn(List.of(
                new WebSearchResult("进程调度博客", "https://blog.csdn.net/x", "文章")));
        when(webSearchService.search("操作系统 进程调度 bilibili 视频教程", 8)).thenReturn(List.of(
                new WebSearchResult("进程调度视频", "https://www.bilibili.com/video/BV1", "视频")));
        when(webSearchService.search("操作系统 进程调度 中国大学MOOC 课程", 8)).thenReturn(List.of());
        // 模型返回坏 JSON → 走兜底，兜底顺序即重排后的顺序
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("not json");

        List<CourseCard> cards = service.recommend(new CourseRecommendRequest(List.of("操作系统 进程调度")));

        // B站(白名单更高层级)应排在 CSDN 之前
        assertThat(cards).extracting(CourseCard::url)
                .containsExactly("https://www.bilibili.com/video/BV1", "https://blog.csdn.net/x");
        assertThat(cards.get(0).platform()).isEqualTo("B站");
    }

    @Test
    void recommendDropsCardsWithUrlsNotInSearchResults() {
        loginAs(7L);
        CourseRecommendServiceImpl service = service();
        when(webSearchService.search("Java 并发 视频教程 课程", 20)).thenReturn(List.of(
                new WebSearchResult("Java 并发课程", "https://example.com/java", "线程和锁")));
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("""
                        [
                          {
                            "title": "真实课程",
                            "platform": "B站",
                            "url": "https://example.com/java",
                            "reason": "覆盖线程、锁和线程池。",
                            "difficulty": "进阶"
                          },
                          {
                            "title": "编造课程",
                            "platform": "慕课",
                            "url": "https://fake.example.org/hallucinated",
                            "reason": "模型凭空捏造的链接。",
                            "difficulty": "入门"
                          }
                        ]
                        """);

        List<CourseCard> cards = service.recommend(new CourseRecommendRequest(List.of("Java 并发")));

        assertThat(cards).extracting(CourseCard::url)
                .containsExactly("https://example.com/java");
    }

    @Test
    void recommendFallsBackToSearchResultsWhenChatClientReturnsMalformedJson() {
        loginAs(7L);
        CourseRecommendServiceImpl service = service();
        when(webSearchService.search("Java 并发 视频教程 课程", 20)).thenReturn(List.of(
                new WebSearchResult("Java 并发课程", "https://example.com/java", "线程和锁")));
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("这里是自然语言，不是 JSON");

        List<CourseCard> cards = service.recommend(new CourseRecommendRequest(List.of("Java 并发")));

        assertThat(cards).containsExactly(new CourseCard(
                "Java 并发课程",
                "网页",
                "https://example.com/java",
                "线程和锁",
                "推荐"));
    }

    @Test
    void savedCoursesAreScopedToCurrentUser() {
        loginAs(7L);
        CourseRecommendServiceImpl service = service();
        SavedCourse saved = savedCourse(11L, 7L, "Java 并发课程");
        when(savedCourseRepository.save(any(SavedCourse.class))).thenReturn(saved);
        when(savedCourseRepository.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(saved));
        when(savedCourseRepository.findByIdAndUserId(11L, 7L)).thenReturn(Optional.of(saved));

        SavedCourseResponse response = service.save(new SavedCourseRequest(
                "Java 并发课程",
                "B站",
                "https://example.com/java",
                "覆盖线程、锁和线程池。",
                "进阶",
                3L));
        List<SavedCourseResponse> list = service.listSaved();
        service.deleteSaved(11L);

        assertThat(response.title()).isEqualTo("Java 并发课程");
        assertThat(list).hasSize(1);
        verify(savedCourseRepository).findByUserIdOrderByCreatedAtDesc(7L);
        verify(savedCourseRepository).findByIdAndUserId(11L, 7L);
        verify(savedCourseRepository).delete(saved);
    }

    private CourseRecommendServiceImpl service() {
        return new CourseRecommendServiceImpl(
                chatClient,
                objectMapper,
                webSearchService,
                documentIngestionService,
                materialRepository,
                savedCourseRepository);
    }

    private void loginAs(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList()));
    }

    private Material material(Long id, Long userId, String filename, Long subjectId) {
        Material material = new Material();
        material.setId(id);
        material.setUserId(userId);
        material.setFilename(filename);
        material.setSubjectId(subjectId);
        return material;
    }

    private SavedCourse savedCourse(Long id, Long userId, String title) {
        SavedCourse course = new SavedCourse();
        course.setId(id);
        course.setUserId(userId);
        course.setTitle(title);
        course.setPlatform("B站");
        course.setUrl("https://example.com/java");
        course.setReason("覆盖线程、锁和线程池。");
        course.setDifficulty("进阶");
        course.setSubjectId(3L);
        course.setCreatedAt(LocalDateTime.of(2026, 6, 24, 10, 0));
        return course;
    }
}
