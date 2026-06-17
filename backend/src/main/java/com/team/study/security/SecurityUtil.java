package com.team.study.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 获取当前登录用户 ID 的工具类，供其他模块复用
 */
@Component
public class SecurityUtil {

    /**
     * 获取当前登录用户的 ID
     * @return userId，未登录时返回 null
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }
}
