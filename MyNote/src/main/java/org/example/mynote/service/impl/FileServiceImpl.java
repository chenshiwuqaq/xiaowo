package org.example.mynote.service.impl;

import org.example.mynote.common.BizException;
import org.example.mynote.common.UserContext;
import org.example.mynote.config.AppProperties;
import org.example.mynote.service.FileService;
import org.example.mynote.vo.UploadVO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 上传文件服务实现。
 *
 * <p>安全前提是"客户端提供的任何东西都不参与路径拼接"：文件名一律 UUID 重命名，
 * 原文件名只被用来取最后一个点之后的扩展名做白名单核对；type 也只接受枚举内的两个值。
 * 落盘路径完全由服务端构造，目录穿越在构造层面就不可能发生，而不是靠事后过滤。</p>
 *
 * @author MyNote
 */
@Service
public class FileServiceImpl implements FileService {

    /**
     * 上传类型：头像
     */
    private static final String TYPE_AVATAR = "avatar";

    /**
     * 上传类型：正文图片
     */
    private static final String TYPE_IMAGE = "image";

    /**
     * 归档子目录的时间格式，按月分层避免单目录文件过多
     */
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private final AppProperties appProperties;

    /**
     * 图片扩展名白名单（小写），avatar 与 image 同口径——两者最终都以图片被浏览器展示
     */
    private final Set<String> allowedImageExtensions;

    public FileServiceImpl(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.allowedImageExtensions = Arrays.stream(appProperties.getUpload().getAllowedImage().split(","))
                .map(ext -> ext.trim().toLowerCase(Locale.ROOT))
                .filter(ext -> !ext.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 保存上传的图片并返回可访问的相对路径。
     *
     * <p>流程：先确认登录态（匿名者不应能往服务器磁盘写文件），再校验文件非空、类型合法、
     * 扩展名在白名单内，最后以 type/yyyyMM/UUID.ext 落盘。超大文件由 Spring multipart
     * 限制先行拦截（全局异常处理器已给出中文提示），这里不重复判断体积。</p>
     *
     * @param file 上传的图片，multipart 字段名 file
     * @param type 业务类型：avatar/image，缺省按 image 处理
     * @return 访问地址、服务端文件名与大小
     */
    @Override
    public UploadVO upload(MultipartFile file, String type) {
        UserContext.require();
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的文件，不能提交空文件");
        }
        String safeType = normalizeType(type);
        String extension = resolveAllowedExtension(file.getOriginalFilename());

        String month = LocalDateTime.now().format(MONTH_FORMAT);
        File dir = Paths.get(appProperties.getUpload().getDir())
                .toAbsolutePath().normalize()
                .resolve(safeType).resolve(month).toFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BizException("上传目录创建失败，请检查服务器磁盘与权限");
        }

        // 文件名只用 UUID 重命名（toString 自带连字符），扩展名已经过白名单核对
        String fileName = UUID.randomUUID().toString() + "." + extension;
        File dest = new File(dir, fileName);
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            // 可预期的存储失败（磁盘满、权限变更），转成中文业务提示，不透出堆栈细节
            throw new BizException("文件保存失败，请稍后重试");
        }

        String relativePath = safeType + "/" + month + "/" + fileName;
        UploadVO vo = new UploadVO();
        vo.setUrl(appProperties.getUpload().getUrlPrefix() + "/" + relativePath);
        vo.setName(fileName);
        vo.setSize(file.getSize());
        return vo;
    }

    /**
     * 归一化上传类型：空值按 image，非法值直接拒绝。
     *
     * <p>type 会被拼进落盘目录与访问 URL，因此绝不能原样使用——像 "../../tomcat/conf"
     * 这类字符串一旦放行就是目录穿越。只认 avatar/image 两个字面量，从根上封死。</p>
     *
     * @param type 客户端提交的类型
     * @return 校验后的类型
     */
    private String normalizeType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return TYPE_IMAGE;
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        if (!TYPE_AVATAR.equals(normalized) && !TYPE_IMAGE.equals(normalized)) {
            throw new BizException("不支持的上传类型，仅允许 avatar 或 image");
        }
        return normalized;
    }

    /**
     * 从原始文件名中取扩展名并核对白名单。
     *
     * <p>只用 lastIndexOf('.') 取"最后一个点之后的部分"做判断，文件名本身随即丢弃，
     * 不参与任何路径拼接；取不到扩展名或不在白名单（大小写不敏感）都拒绝。
     * 白名单是最终防线：像 "evil.png.exe" 这种只留 "exe"，天然被挡。</p>
     *
     * @param originalFilename 客户端原始文件名，可能为 null 或含路径分隔符
     * @return 已转小写的合法扩展名（不含点）
     */
    private String resolveAllowedExtension(String originalFilename) {
        String extension = "";
        if (originalFilename != null) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot >= 0 && dot < originalFilename.length() - 1) {
                extension = originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
            }
        }
        if (!allowedImageExtensions.contains(extension)) {
            throw new BizException("不支持的图片格式，仅允许上传："
                    + String.join("、", allowedImageExtensions));
        }
        return extension;
    }
}
