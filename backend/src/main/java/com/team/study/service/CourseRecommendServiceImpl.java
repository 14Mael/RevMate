package com.team.study.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import com.team.study.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseRecommendServiceImpl implements CourseRecommendService {

    private static final int KEYWORD_CONTEXT_CHUNKS = 6;
    private static final int DEFAULT_RECOMMEND_COUNT = 6;
    /** 通用搜索一批作为重排原料 */
    private static final int SEARCH_FETCH_COUNT = 20;
    /** 每个平台定向搜索的数量 */
    private static final int TARGETED_FETCH_COUNT = 8;
    /** 重排后喂给模型的候选上限 */
    private static final int MODEL_CANDIDATE_COUNT = 12;
    /** 把搜索意图往「视频/课程」方向偏，压制纯文档结果 */
    private static final String SEARCH_INTENT_SUFFIX = " 视频教程 课程";
    /** 额外定向搜索的平台关键词：博查通用搜索不爱给视频/MOOC，主动各搜一次再合并 */
    private static final List<String> PLATFORM_BOOSTERS = List.of("bilibili 视频教程", "中国大学MOOC 课程");

    /** 关键词噪声词：水印/平台名等，提炼时若混入需滤掉（小写匹配，包含即丢） */
    private static final List<String> KEYWORD_NOISE = List.of(
            "中国大学mooc", "mooc", "慕课", "在线课程", "文库", "水印", "课件", "版权");

    /** 文档农场/SEO 垃圾站：命中即丢弃（按 host 子串匹配，可按需增减） */
    private static final List<String> DENY_DOMAINS = List.of(
            "docin.com", "taodocs.com", "book118.com", "wenku.baidu.com",
            "doc88.com", "renrendoc.com", "360doc.com", "mayiwenku.com");

    /** 优质平台白名单，按优先级分层（靠前的层级排得更前） */
    private static final List<List<String>> DOMAIN_TIERS = List.of(
            List.of("bilibili.com"),                                                                  // 视频
            List.of("icourse163.org", "xuetangx.com", "imooc.com", "study.163.com", "ke.qq.com"),     // 国内 MOOC
            List.of("juejin.cn", "segmentfault.com", "infoq.cn", "cloud.tencent.com",
                    "developer.aliyun.com", "zhuanlan.zhihu.com", "blog.csdn.net"),                    // 技术社区/官方
            List.of("coursera.org", "youtube.com", "edx.org", "khanacademy.org"));                    // 国际

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final WebSearchService webSearchService;
    private final DocumentIngestionService documentIngestionService;
    private final MaterialRepository materialRepository;
    private final SavedCourseRepository savedCourseRepository;

    @Override
    @Transactional(readOnly = true)
    public List<String> keywords(Long materialId) {
        Long userId = currentUserId();
        Material material = materialRepository.findByIdAndUserId(materialId, userId)
                .orElseThrow(() -> new IllegalArgumentException("资料不存在或无权访问"));
        String context = documentIngestionService.getMaterialContext(userId, materialId, KEYWORD_CONTEXT_CHUNKS);
        if (context == null || context.isBlank()) {
            return List.of(stripExtension(material.getFilename()));
        }

        String filename = stripExtension(material.getFilename());
        String output = chatClient.prompt()
                .user("""
                        请提炼这份复习资料所讲的「学科知识点/主题」关键词 2 到 3 个，用于搜索相关课程。
                        只提学科主题词（如「页面置换算法」「操作系统」「虚拟内存」）。
                        务必忽略以下噪声，绝不能把它们当作关键词：
                        - 水印、网站名、平台名（如「中国大学MOOC」「慕课」「在线课程」「文库」）
                        - 页码、章节号、文件名后缀、版权声明等无关文字
                        参考文件名（可能含主题线索）：%s
                        严格输出 JSON 字符串数组，不要解释。

                        复习资料正文:
                        %s
                        """.formatted(filename, context))
                .call()
                .content();
        List<String> parsed = parseStringArray(output).stream()
                .filter(this::isMeaningfulKeyword)
                .toList();
        return parsed.isEmpty() ? List.of(filename) : parsed;
    }

    @Override
    public List<CourseCard> recommend(CourseRecommendRequest request) {
        String query = normalizeKeywords(request == null ? null : request.keywords());
        if (query.isBlank()) {
            return List.of();
        }

        // 通用搜索 + 各平台定向搜索，合并去重后重排，最大概率把视频/MOOC 捞上来
        List<WebSearchResult> merged = new ArrayList<>(
                webSearchService.search(query + SEARCH_INTENT_SUFFIX, SEARCH_FETCH_COUNT));
        for (String booster : PLATFORM_BOOSTERS) {
            merged.addAll(safeSearch(query + " " + booster, TARGETED_FETCH_COUNT));
        }
        // 按质量重排：丢弃垃圾站、优质平台靠前，再截取喂给模型的候选
        List<WebSearchResult> candidates = rerankByQuality(dedupByUrl(merged)).stream()
                .limit(MODEL_CANDIDATE_COUNT)
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        String output = chatClient.prompt()
                .system("""
                        你是学习资源推荐助手。用户给你真实搜索结果，请只基于这些结果整理课程推荐。
                        优先推荐视频课程和系统化课程，其次才是文章。
                        严格输出 JSON 数组，每项字段为 title, platform, url, reason, difficulty。
                        difficulty 使用 入门、进阶、高阶 或 推荐。
                        不要输出 Markdown，不要编造搜索结果之外的链接。
                        """)
                .user(buildRecommendPrompt(query, candidates))
                .call()
                .content();

        List<CourseCard> cards = parseCourseCards(output);
        List<CourseCard> verified = keepRealUrls(cards, candidates);
        return verified.isEmpty() ? fallbackCards(candidates) : verified.stream().limit(DEFAULT_RECOMMEND_COUNT).toList();
    }

    /** 定向搜索容错：单个平台搜索失败不影响整体推荐 */
    private List<WebSearchResult> safeSearch(String query, int count) {
        try {
            return webSearchService.search(query, count);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** 按 URL 去重，保留首次出现顺序 */
    private List<WebSearchResult> dedupByUrl(List<WebSearchResult> results) {
        LinkedHashMap<String, WebSearchResult> unique = new LinkedHashMap<>();
        for (WebSearchResult result : results) {
            String url = clean(result.url());
            if (!url.isBlank()) {
                unique.putIfAbsent(url, result);
            }
        }
        return new ArrayList<>(unique.values());
    }

    /** 按质量重排：剔除垃圾文档站，优质平台按层级靠前，同层保留原始相关性顺序 */
    private List<WebSearchResult> rerankByQuality(List<WebSearchResult> results) {
        return results.stream()
                .filter(result -> {
                    String host = hostOf(result.url());
                    return !host.isBlank() && DENY_DOMAINS.stream().noneMatch(host::contains);
                })
                .sorted(Comparator.comparingInt(result -> tierOf(hostOf(result.url()))))
                .toList();
    }

    /** 域名所在的优先级层级，数字越小越靠前；不在白名单的归为最后一层 */
    private int tierOf(String host) {
        if (host.isBlank()) {
            return DOMAIN_TIERS.size();
        }
        for (int tier = 0; tier < DOMAIN_TIERS.size(); tier++) {
            for (String domain : DOMAIN_TIERS.get(tier)) {
                if (host.contains(domain)) {
                    return tier;
                }
            }
        }
        return DOMAIN_TIERS.size();
    }

    private String hostOf(String url) {
        String value = clean(url);
        if (value.isBlank()) {
            return "";
        }
        try {
            String host = URI.create(value).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /** 由域名推断展示用的平台名，兜底卡片用 */
    private String platformOf(String host) {
        if (host.contains("bilibili")) return "B站";
        if (host.contains("icourse163")) return "中国大学MOOC";
        if (host.contains("xuetangx")) return "学堂在线";
        if (host.contains("imooc")) return "慕课网";
        if (host.contains("study.163")) return "网易云课堂";
        if (host.contains("ke.qq")) return "腾讯课堂";
        if (host.contains("coursera")) return "Coursera";
        if (host.contains("youtube")) return "YouTube";
        if (host.contains("juejin")) return "掘金";
        return "网页";
    }

    /** 守住核心承诺：只保留链接确实来自真实搜索结果的卡片，丢弃模型编造的 URL */
    private List<CourseCard> keepRealUrls(List<CourseCard> cards, List<WebSearchResult> searchResults) {
        Set<String> realUrls = searchResults.stream()
                .map(result -> clean(result.url()))
                .filter(url -> !url.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return cards.stream()
                .filter(card -> realUrls.contains(clean(card.url())))
                .toList();
    }

    @Override
    @Transactional
    public SavedCourseResponse save(SavedCourseRequest request) {
        Long userId = currentUserId();
        SavedCourse course = new SavedCourse();
        course.setUserId(userId);
        course.setTitle(required(request.title(), "课程标题不能为空"));
        course.setPlatform(clean(request.platform()));
        course.setUrl(required(request.url(), "课程链接不能为空"));
        course.setReason(clean(request.reason()));
        course.setDifficulty(clean(request.difficulty()));
        course.setSubjectId(request.subjectId());
        return toResponse(savedCourseRepository.save(course));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavedCourseResponse> listSaved() {
        Long userId = currentUserId();
        return savedCourseRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteSaved(Long id) {
        Long userId = currentUserId();
        SavedCourse course = savedCourseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("收藏课程不存在或无权访问"));
        savedCourseRepository.delete(course);
    }

    private Long currentUserId() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new IllegalArgumentException("未登录");
        }
        return userId;
    }

    private String normalizeKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return "";
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String keyword : keywords) {
            String text = clean(keyword);
            if (!text.isBlank()) {
                normalized.add(text);
            }
        }
        return String.join(" ", normalized);
    }

    private String buildRecommendPrompt(String query, List<WebSearchResult> results) {
        StringBuilder builder = new StringBuilder("用户搜索主题: ").append(query).append("\n\n搜索结果:\n");
        for (int i = 0; i < results.size(); i++) {
            WebSearchResult result = results.get(i);
            builder.append(i + 1)
                    .append(". 标题: ").append(result.title()).append('\n')
                    .append("   链接: ").append(result.url()).append('\n')
                    .append("   摘要: ").append(result.snippet()).append("\n\n");
        }
        return builder.toString();
    }

    private List<String> parseStringArray(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(extractJsonArray(output), new TypeReference<List<String>>() {});
            return values.stream()
                    .map(this::clean)
                    .filter(text -> !text.isBlank())
                    .limit(3)
                    .toList();
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private List<CourseCard> parseCourseCards(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> values = objectMapper.readValue(
                    extractJsonArray(output),
                    new TypeReference<List<Map<String, Object>>>() {});
            return values.stream()
                    .map(this::mapCourseCard)
                    .filter(card -> !card.title().isBlank() && !card.url().isBlank())
                    .toList();
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private CourseCard mapCourseCard(Map<String, Object> map) {
        return new CourseCard(
                clean(map.get("title")),
                clean(map.get("platform")),
                clean(map.get("url")),
                clean(map.get("reason")),
                clean(map.get("difficulty")));
    }

    private List<CourseCard> fallbackCards(List<WebSearchResult> results) {
        return results.stream()
                .limit(DEFAULT_RECOMMEND_COUNT)
                .map(result -> new CourseCard(
                        result.title(),
                        platformOf(hostOf(result.url())),
                        result.url(),
                        result.snippet(),
                        "推荐"))
                .toList();
    }

    private String extractJsonArray(String raw) {
        String trimmed = raw.trim();
        if (trimmed.contains("```")) {
            int start = trimmed.indexOf("```");
            int contentStart = trimmed.indexOf('\n', start) + 1;
            int end = trimmed.indexOf("```", contentStart);
            if (end > start && contentStart > start) {
                trimmed = trimmed.substring(contentStart, end).trim();
            }
        }

        int arrayStart = trimmed.indexOf('[');
        int arrayEnd = trimmed.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return trimmed.substring(arrayStart, arrayEnd + 1);
        }
        return trimmed;
    }

    private SavedCourseResponse toResponse(SavedCourse course) {
        return new SavedCourseResponse(
                course.getId(),
                course.getTitle(),
                course.getPlatform(),
                course.getUrl(),
                course.getReason(),
                course.getDifficulty(),
                course.getSubjectId(),
                course.getCreatedAt());
    }

    private String required(String value, String message) {
        String text = clean(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    private String clean(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    /** 过滤水印/平台名等噪声关键词 */
    private boolean isMeaningfulKeyword(String keyword) {
        String lower = clean(keyword).toLowerCase(Locale.ROOT);
        return !lower.isBlank() && KEYWORD_NOISE.stream().noneMatch(lower::contains);
    }

    private String stripExtension(String filename) {
        String text = clean(filename);
        int dot = text.lastIndexOf('.');
        return dot > 0 ? text.substring(0, dot) : text;
    }
}
