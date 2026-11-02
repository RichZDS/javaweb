package com.richzds.quiz.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.richzds.quiz.common.BizException;
import com.richzds.quiz.common.TimeUtil;
import com.richzds.quiz.config.AppProperties;

/**
 * 图片上传：只收 jpg / png / webp / gif，≤ 2 MB，按文件头判断真实类型（不信扩展名和 Content-Type）。
 * 存到 {@code <UPLOAD_DIR>/yyyy/MM/dd/<随机名>.<扩展名>}（日期按北京时间），返回站内路径 {@code /uploads/...}。
 */
@Service
public class FileStorageService {

    public static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;
    public static final String URL_PREFIX = "/uploads/";
    private static final DateTimeFormatter DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final Path root;
    private final Clock clock;

    public FileStorageService(AppProperties props, Clock clock) {
        this.root = Path.of(props.upload().dir()).toAbsolutePath().normalize();
        this.clock = clock;
    }

    public Path root() {
        return root;
    }

    public String storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.invalid("file", "请选择要上传的图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw BizException.invalid("file", "图片不能超过 2 MB");
        }
        byte[] head = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            throw BizException.invalid("file", "读取上传文件失败");
        }
        String ext = imageExtension(Arrays.copyOf(head, read));
        if (ext == null) {
            throw BizException.invalid("file", "只支持 jpg、png、webp、gif 图片");
        }
        LocalDate day = TimeUtil.todayBeijing(clock);
        String relative = DIR.format(day) + "/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = root.resolve(relative).normalize();
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("保存上传文件失败", e);
        }
        return URL_PREFIX + relative;
    }

    /** 按文件头识别图片类型，不是支持的图片返回 null。 */
    static String imageExtension(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) {
            return "png";
        }
        if (h.length >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8'
                && (h[4] == '7' || h[4] == '9') && h[5] == 'a') {
            return "gif";
        }
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
