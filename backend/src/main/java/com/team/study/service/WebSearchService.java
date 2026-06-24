package com.team.study.service;

import com.team.study.dto.response.WebSearchResult;

import java.util.List;

/**
 * 联网搜索服务抽象。
 *
 * <p>实现可切换：通过 {@code search.provider} 配置选择 {@code bocha}（默认）或 {@code serper}。
 */
public interface WebSearchService {

    List<WebSearchResult> search(String query, int count);
}
