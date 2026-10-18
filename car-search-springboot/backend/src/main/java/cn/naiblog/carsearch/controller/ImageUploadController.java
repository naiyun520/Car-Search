package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.ApiResponse;
import cn.naiblog.carsearch.api.ApiSupport;
import cn.naiblog.carsearch.api.BusinessException;
import cn.naiblog.carsearch.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 小程序图片上传接口
 * 用于上传行驶证、身份证等图片，返回可访问的 URL
 */
@RestController
@RequestMapping("/api/v1/upload")
public class ImageUploadController {
    private static final Logger log = LoggerFactory.getLogger(ImageUploadController.class);
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_SIZE = 5 * 1024 * 1024; // 5MB
    private static final int MIN_WIDTH = 100;
    private static final int MAX_WIDTH = 4096;
    private static final int MIN_HEIGHT = 100;
    private static final int MAX_HEIGHT = 4096;

    private final Path uploadDir;
    private final String baseUrl;
    private final ApiSupport api;
    private final SecureRandom random = new SecureRandom();

    public ImageUploadController(
            @Value("${car.storage-path:./runtime/uploads}") String storagePath,
            @Value("${car.upload-base-url:}") String baseUrl,
            ApiSupport api) {
        this.uploadDir = Path.of(storagePath).toAbsolutePath().normalize();
        this.baseUrl = baseUrl;
        this.api = api;
    }

    /**
     * 上传图片
     * @param file 图片文件
     * @param type 图片类型（可选，如 license、idcard 等，用于分类存储）
     * @return 图片访问 URL
     */
    @PostMapping("/image")
    public ApiResponse<Map<String, Object>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "general") String type,
            HttpServletRequest request) {
        CurrentUser user = api.user(request);
        log.info("[ImageUpload] 用户={}, type={}, filename={}, size={}",
                user.id(), type, file.getOriginalFilename(), file.getSize());

        // 验证文件
        if (file.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("图片大小不能超过 5MB");
        }

        // 验证文件类型
        String contentType = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException("仅支持 JPG、PNG、WebP 格式的图片");
        }

        // 验证图片内容
        byte[] data;
        try {
            data = file.getBytes();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null) {
                throw new BusinessException("图片文件内容无效");
            }
            int width = image.getWidth();
            int height = image.getHeight();
            if (width < MIN_WIDTH || width > MAX_WIDTH || height < MIN_HEIGHT || height > MAX_HEIGHT) {
                throw new BusinessException(String.format("图片尺寸需在 %dx%d 至 %dx%d 像素之间",
                        MIN_WIDTH, MIN_HEIGHT, MAX_WIDTH, MAX_HEIGHT));
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ImageUpload] 读取图片失败", e);
            throw new BusinessException("图片读取失败，请重新选择");
        }

        // 生成文件名
        String ext = switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
        String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        byte[] bytes = new byte[8];
        random.nextBytes(bytes);
        String filename = HexFormat.of().formatHex(bytes) + "." + ext;

        // 创建目录并保存文件
        try {
            Path targetDir = uploadDir.resolve(type).resolve(dateDir);
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(filename).normalize();

            // 安全检查：确保目标路径在上传目录内
            if (!target.startsWith(uploadDir)) {
                throw new BusinessException("文件路径无效");
            }

            Files.write(target, data);
            log.info("[ImageUpload] 文件已保存: {}", target);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ImageUpload] 保存文件失败", e);
            throw new BusinessException("文件保存失败，请重试");
        }

        // 构建访问 URL
        String relativePath = String.format("/uploads/%s/%s/%s", type, dateDir, filename);
        String imageUrl;
        if (baseUrl != null && !baseUrl.isBlank()) {
            String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            imageUrl = base + relativePath;
        } else {
            // 相对路径，由前端拼接完整 URL
            imageUrl = relativePath;
        }

        return ApiResponse.ok(Map.of(
                "url", imageUrl,
                "filename", filename,
                "size", file.getSize()
        ), "图片上传成功");
    }
}