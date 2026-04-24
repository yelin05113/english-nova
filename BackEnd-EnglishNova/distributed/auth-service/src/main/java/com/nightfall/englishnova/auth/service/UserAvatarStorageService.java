package com.nightfall.englishnova.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class UserAvatarStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private final Path imageDirectory;

    public UserAvatarStorageService() {
        this.imageDirectory = resolveProjectRoot().resolve("upload").resolve("images").normalize();
    }

    public String store(long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的头像图片");
        }

        String extension = resolveExtension(file.getOriginalFilename());
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("头像文件必须是图片");
        }

        try {
            Files.createDirectories(imageDirectory);
            String filename = "user-" + userId + "-" + UUID.randomUUID() + "." + extension;
            Path target = imageDirectory.resolve(filename).normalize();
            if (!target.startsWith(imageDirectory)) {
                throw new IllegalArgumentException("无效的头像文件名");
            }
            file.transferTo(target);
            return "/upload/images/" + filename;
        } catch (IOException exception) {
            throw new IllegalStateException("头像保存失败，请稍后重试", exception);
        }
    }

    public String resourceLocation() {
        return imageDirectory.toUri().toString();
    }

    private Path resolveProjectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path cursor = current;
        while (cursor != null) {
            if (Files.exists(cursor.resolve("docker-compose.yml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        return current;
    }

    private String resolveExtension(String originalFilename) {
        String filename = originalFilename == null ? "" : originalFilename;
        int dotIndex = filename.lastIndexOf('.');
        String extension = dotIndex >= 0 ? filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("头像仅支持 jpg、png、webp 图片");
        }
        return extension;
    }
}
