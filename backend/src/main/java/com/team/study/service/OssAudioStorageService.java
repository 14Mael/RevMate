package com.team.study.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class OssAudioStorageService {

    private static final Logger log = LoggerFactory.getLogger(OssAudioStorageService.class);

    private static final Pattern UNSAFE_FILENAME_CHARS = Pattern.compile("[/\\\\:<>\"|?*\\p{Cntrl}]+");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneOffset.UTC);

    private final RestTemplate restTemplate;
    private final String endpoint;
    private final String bucket;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final Duration signedUrlExpire;

    public OssAudioStorageService(
            RestTemplate restTemplate,
            @Value("${aliyun.oss.endpoint:}") String endpoint,
            @Value("${aliyun.oss.bucket:}") String bucket,
            @Value("${aliyun.oss.access-key-id:}") String accessKeyId,
            @Value("${aliyun.oss.access-key-secret:}") String accessKeySecret,
            @Value("${aliyun.oss.signed-url-expire:PT1H}") Duration signedUrlExpire) {
        this.restTemplate = restTemplate;
        this.endpoint = normalizeEndpoint(endpoint);
        this.bucket = bucket;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.signedUrlExpire = signedUrlExpire;
    }

    public String uploadAndCreateSignedUrl(Path file, String filename) {
        validateConfiguration();
        if (file == null || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("音频文件不存在");
        }

        String objectKey = createObjectKey(filename);
        String contentType = resolveContentType(filename);
        String uploadUrl = createSignedUrl("PUT", objectKey, contentType);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        try {
            restTemplate.exchange(
                    URI.create(uploadUrl),
                    HttpMethod.PUT,
                    new HttpEntity<>(new FileSystemResource(file), headers),
                    Void.class);
        } catch (HttpStatusCodeException e) {
            log.error("OSS 上传失败: status={}, objectKey={}, contentType={}, 响应体={}",
                    e.getStatusCode(), objectKey, contentType, e.getResponseBodyAsString());
            throw new IllegalStateException("OSS 上传失败: " + e.getStatusCode()
                    + " " + extractOssErrorCode(e.getResponseBodyAsString()), e);
        }
        return createSignedUrl("GET", objectKey, "");
    }

    private String createObjectKey(String filename) {
        String safeFilename = safeFilename(filename);
        return "audio/" + DAY_FORMAT.format(Instant.now()) + "/"
                + UUID.randomUUID() + "-" + safeFilename;
    }

    private String safeFilename(String filename) {
        String baseName = filename == null || filename.isBlank()
                ? "audio.bin"
                : Path.of(filename).getFileName().toString();
        String safe = UNSAFE_FILENAME_CHARS.matcher(baseName).replaceAll("_").trim();
        if (safe.isBlank()) {
            return "audio.bin";
        }
        return safe.length() > 120 ? safe.substring(safe.length() - 120) : safe;
    }

    private String createSignedUrl(String method, String objectKey, String contentType) {
        long expires = Instant.now().plus(signedUrlExpire).getEpochSecond();
        String stringToSign = method + "\n\n" + contentType + "\n" + expires + "\n"
                + "/" + bucket + "/" + objectKey;
        String signature = hmacSha1(stringToSign, accessKeySecret);
        return "https://" + bucket + "." + endpoint + "/" + encodeObjectKey(objectKey)
                + "?OSSAccessKeyId=" + encode(accessKeyId)
                + "&Expires=" + expires
                + "&Signature=" + encode(signature);
    }

    private String hmacSha1(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("生成 OSS 签名 URL 失败", e);
        }
    }

    private String encodeObjectKey(String objectKey) {
        return Stream.of(objectKey.split("/"))
                .map(this::encode)
                .collect(Collectors.joining("/"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String resolveContentType(String filename) {
        String ext = "";
        if (filename != null && filename.contains(".")) {
            ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        }
        return switch (ext) {
            case "wav" -> "audio/wav";
            case "m4a" -> "audio/mp4";
            case "webm" -> "audio/webm";
            case "ogg" -> "audio/ogg";
            default -> "audio/mpeg";
        };
    }

    private String normalizeEndpoint(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceFirst("^https?://", "").replaceAll("/+$", "");
    }

    private void validateConfiguration() {
        requireConfigured(endpoint, "阿里云 OSS endpoint 未配置");
        requireConfigured(bucket, "阿里云 OSS bucket 未配置");
        requireConfigured(accessKeyId, "阿里云 AccessKeyId 未配置");
        requireConfigured(accessKeySecret, "阿里云 AccessKeySecret 未配置");
    }

    private void requireConfigured(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
    }

    private String extractOssErrorCode(String responseBody) {
        if (responseBody == null) {
            return "";
        }
        var matcher = Pattern.compile("<Code>(.*?)</Code>").matcher(responseBody);
        return matcher.find() ? matcher.group(1) : "";
    }
}
