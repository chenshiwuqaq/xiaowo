package org.example.mynote.service.impl;

import org.example.mynote.common.BizException;
import org.example.mynote.common.Constants;
import org.example.mynote.common.ResultCode;
import org.example.mynote.common.UserContext;
import org.example.mynote.config.AppProperties;
import org.example.mynote.dao.FolderDAO;
import org.example.mynote.dao.NoteDAO;
import org.example.mynote.dao.NoteHistoryDAO;
import org.example.mynote.dao.NoteReferenceDAO;
import org.example.mynote.dao.NoteTagDAO;
import org.example.mynote.dao.TagDAO;
import org.example.mynote.dao.WritingLogDAO;
import org.example.mynote.dto.BatchIdsDTO;
import org.example.mynote.dto.BatchMoveDTO;
import org.example.mynote.dto.NoteSaveDTO;
import org.example.mynote.dto.NoteTopDTO;
import org.example.mynote.dto.NoteUpdateDTO;
import org.example.mynote.entity.FolderDO;
import org.example.mynote.entity.NoteDO;
import org.example.mynote.entity.NoteHistoryDO;
import org.example.mynote.entity.NoteTagDO;
import org.example.mynote.entity.TagDO;
import org.example.mynote.entity.WritingLogDO;
import org.example.mynote.service.NoteService;
import org.example.mynote.util.WordCountUtil;
import org.example.mynote.vo.ImportResultVO;
import org.example.mynote.vo.NoteDetailVO;
import org.example.mynote.vo.NoteHistoryVO;
import org.example.mynote.vo.NoteVO;
import org.example.mynote.vo.TagVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 笔记业务实现。
 *
 * <p>三条贯穿性原则：其一，所有读写都以 UserContext.require() 的用户为边界，
 * 前端传入的 id 只用于定位记录；其二，字数、摘要、标题推断全部复用
 * {@link WordCountUtil}，保证与前端编辑器口径一致；其三，凡"查无"与"非本人"
 * 一律抛 NOT_FOUND，不向调用方泄露资源是否存在。</p>
 *
 * @author MyNote
 */
@Service
public class NoteServiceImpl implements NoteService {

    private static final Logger log = LoggerFactory.getLogger(NoteServiceImpl.class);

    /**
     * 摘要最大长度（列定义 500，取 120 兼顾列表展示与 AI 上下文拼装）
     */
    private static final int SUMMARY_LENGTH = 120;

    /**
     * 单篇笔记保留的历史版本条数，超出即删，防 mn_note_history 膨胀
     */
    private static final int HISTORY_KEEP_COUNT = 20;

    /**
     * 自动创建标签的默认色，与前端标签调色板首色一致
     */
    private static final String DEFAULT_TAG_COLOR = "#E7A878";

    /**
     * 标题推断彻底失败时的兜底标题
     */
    private static final String UNTITLED_TITLE = "未命名笔记";

    /**
     * BOM 字符码位（U+FEFF），导入时剥离
     */
    private static final char BOM_CHAR = 0xFEFF;

    /**
     * 导出文件名的最大长度，过长会被文件系统与浏览器截断出乱码
     */
    private static final int EXPORT_NAME_MAX = 80;

    /**
     * 导出格式：Markdown 源文
     */
    private static final String EXPORT_FORMAT_MD = "md";

    private static final String EXPORT_FORMAT_TXT = "txt";

    /**
     * 与 md 同义的 Markdown 扩展名（白名单内），影响标题推断策略
     */
    private static final String DOC_EXT_MARKDOWN = "markdown";

    /**
     * 标题列长度上限，文件名兜底标题超长时按此截断
     */
    private static final int TITLE_MAX_LENGTH = 200;

    private final NoteDAO noteDAO;

    private final FolderDAO folderDAO;

    private final TagDAO tagDAO;

    private final NoteTagDAO noteTagDAO;

    private final WritingLogDAO writingLogDAO;

    private final NoteHistoryDAO noteHistoryDAO;

    private final NoteReferenceDAO noteReferenceDAO;

    private final AppProperties appProperties;

    public NoteServiceImpl(NoteDAO noteDAO, FolderDAO folderDAO, TagDAO tagDAO, NoteTagDAO noteTagDAO,
                           WritingLogDAO writingLogDAO, NoteHistoryDAO noteHistoryDAO,
                           NoteReferenceDAO noteReferenceDAO, AppProperties appProperties) {
        this.noteDAO = noteDAO;
        this.folderDAO = folderDAO;
        this.tagDAO = tagDAO;
        this.noteTagDAO = noteTagDAO;
        this.writingLogDAO = writingLogDAO;
        this.noteHistoryDAO = noteHistoryDAO;
        this.noteReferenceDAO = noteReferenceDAO;
        this.appProperties = appProperties;
    }

    /**
     * 列表按"关键词优先、目录次之、全部兜底"选择 DAO 查询，再按需叠加标签过滤。
     * 标签补全需要逐篇取关联：NoteTagDAO 只提供按笔记查询的方法，个人库量级可接受。
     */
    @Override
    public List<NoteVO> list(Long folderId, String keyword, Long tagId) {
        Long userId = UserContext.require();
        List<NoteDAO.NoteBrief> briefs;
        if (StringUtils.hasText(keyword)) {
            briefs = noteDAO.search(userId, keyword.trim());
        } else if (folderId != null) {
            briefs = noteDAO.findBriefByFolder(userId, folderId);
        } else {
            briefs = noteDAO.findAllBrief(userId);
        }
        if (tagId != null) {
            if (tagDAO.findFirstByIdAndUserId(tagId, userId) == null) {
                throw new BizException(ResultCode.NOT_FOUND);
            }
            Set<Long> noteIds = noteTagDAO.findByTagId(tagId).stream()
                    .map(NoteTagDO::getNoteId).collect(Collectors.toSet());
            briefs = briefs.stream().filter(b -> noteIds.contains(b.getId())).collect(Collectors.toList());
        }
        Map<Long, TagDO> tagMap = loadTagMap(userId);
        List<NoteVO> result = new ArrayList<>(briefs.size());
        for (NoteDAO.NoteBrief brief : briefs) {
            NoteVO vo = new NoteVO();
            fillFromBrief(vo, brief);
            attachTags(vo, tagMap);
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<NoteVO> trash() {
        Long userId = UserContext.require();
        Map<Long, TagDO> tagMap = loadTagMap(userId);
        List<NoteVO> result = new ArrayList<>();
        for (NoteDAO.NoteBrief brief : noteDAO.findTrash(userId)) {
            NoteVO vo = new NoteVO();
            fillFromBrief(vo, brief);
            attachTags(vo, tagMap);
            result.add(vo);
        }
        return result;
    }

    @Override
    public NoteDetailVO detail(Long id) {
        Long userId = UserContext.require();
        return toDetailVO(requireNote(id, userId), loadTagMap(userId));
    }

    /**
     * 新建：标题为空用正文首行推断；note_type / word_count 等列在库里是 NOT NULL，
     * 必须在实体上补默认值，不能指望数据库 DEFAULT（Hibernate 会显式插入 NULL）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteDetailVO create(NoteSaveDTO dto) {
        Long userId = UserContext.require();
        LocalDateTime now = LocalDateTime.now();
        String content = dto.getContent() == null ? "" : dto.getContent();
        NoteDO note = new NoteDO();
        note.setUserId(userId);
        note.setFolderId(resolveFolderId(dto.getFolderId(), userId));
        note.setTitle(resolveTitle(dto.getTitle(), content));
        note.setContent(content);
        note.setSummary(WordCountUtil.summarize(content, SUMMARY_LENGTH));
        note.setWordCount(WordCountUtil.count(content));
        note.setNoteType(dto.getNoteType() == null ? Constants.NOTE_TYPE_ESSAY : dto.getNoteType());
        note.setTop(false);
        note.setDeleted(false);
        note.setCreateTime(now);
        note.setUpdateTime(now);
        noteDAO.save(note);
        // 新建即有内容时按当日书写量计入，否则主页热力图会把"新开一篇长文"漏掉
        recordWritingDelta(userId, note.getId(), note.getWordCount());
        if (dto.getTagNames() != null) {
            rebuildTags(note.getId(), dto.getTagNames(), userId);
        }
        return toDetailVO(note, loadTagMap(userId));
    }

    /**
     * 更新：先按需快照旧内容，再落新值；字数增量写当日书写日志供主页看板累加。
     * deleteOlderThan 是 @Modifying 原生语句，故整方法必须事务内执行。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteDetailVO update(Long id, NoteUpdateDTO dto) {
        Long userId = UserContext.require();
        NoteDO note = requireNote(id, userId);
        String newContent = dto.getContent() == null ? nvl(note.getContent()) : dto.getContent();
        if (Boolean.TRUE.equals(dto.getSnapshot())) {
            saveSnapshot(note, dto.getSnapshotSource() == null ? Constants.HISTORY_SOURCE_SAVE
                    : dto.getSnapshotSource());
        }
        int oldWordCount = note.getWordCount() == null ? 0 : note.getWordCount();
        note.setTitle(resolveTitle(dto.getTitle(), newContent));
        note.setContent(newContent);
        note.setSummary(WordCountUtil.summarize(newContent, SUMMARY_LENGTH));
        note.setWordCount(WordCountUtil.count(newContent));
        if (dto.getFolderId() != null) {
            note.setFolderId(resolveFolderId(dto.getFolderId(), userId));
        }
        if (dto.getNoteType() != null) {
            note.setNoteType(dto.getNoteType());
        }
        note.setUpdateTime(LocalDateTime.now());
        noteDAO.save(note);
        recordWritingDelta(userId, note.getId(), note.getWordCount() - oldWordCount);
        if (dto.getTagNames() != null) {
            rebuildTags(note.getId(), dto.getTagNames(), userId);
        }
        return toDetailVO(note, loadTagMap(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long userId = UserContext.require();
        NoteDO note = requireNote(id, userId);
        note.setDeleted(true);
        note.setDeleteTime(LocalDateTime.now());
        noteDAO.save(note);
    }

    /**
     * 恢复时校验原目录：目录可能已随"删目录"逻辑删除，此时笔记落未分类而不是丢失归属。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long id) {
        Long userId = UserContext.require();
        NoteDO note = requireNote(id, userId);
        note.setDeleted(false);
        note.setDeleteTime(null);
        Long folderId = note.getFolderId();
        if (folderId != null && !Constants.UNFILED_FOLDER_ID.equals(folderId)) {
            FolderDO folder = folderDAO.findFirstByIdAndUserId(folderId, userId);
            if (folder == null || Boolean.TRUE.equals(folder.getDeleted())) {
                note.setFolderId(Constants.UNFILED_FOLDER_ID);
            }
        }
        noteDAO.save(note);
    }

    /**
     * 物理删除前先清四张关联表，否则 mn_note_tag 等表会留下悬空外键式引用。
     * 仅允许对回收站内的笔记执行，防止误调接口直接销毁在用笔记。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void permanent(Long id) {
        Long userId = UserContext.require();
        NoteDO note = requireNote(id, userId);
        if (!Boolean.TRUE.equals(note.getDeleted())) {
            throw new BizException("请先将笔记移入回收站，再执行彻底删除");
        }
        noteTagDAO.deleteByNoteId(id);
        writingLogDAO.deleteByNoteId(id);
        noteHistoryDAO.deleteByNoteId(id);
        noteReferenceDAO.deleteByNoteId(id);
        noteDAO.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void top(NoteTopDTO dto) {
        Long userId = UserContext.require();
        NoteDO note = requireNote(dto.getId(), userId);
        note.setTop(Boolean.TRUE.equals(dto.getTop()));
        note.setUpdateTime(LocalDateTime.now());
        noteDAO.save(note);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchMove(BatchMoveDTO dto) {
        Long userId = UserContext.require();
        Long folderId = resolveFolderId(dto.getFolderId(), userId);
        List<NoteDO> notes = requireOwnedNotes(dto.getIds(), userId);
        for (NoteDO note : notes) {
            note.setFolderId(folderId);
        }
        noteDAO.saveAll(notes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchTrash(BatchIdsDTO dto) {
        Long userId = UserContext.require();
        List<NoteDO> notes = requireOwnedNotes(dto.getIds(), userId);
        LocalDateTime now = LocalDateTime.now();
        for (NoteDO note : notes) {
            note.setDeleted(true);
            note.setDeleteTime(now);
        }
        noteDAO.saveAll(notes);
    }

    /**
     * 历史列表不映射 content 字段：LONGTEXT 快照体积大，列表页用不到。
     */
    @Override
    public List<NoteHistoryVO> history(Long noteId) {
        Long userId = UserContext.require();
        requireNote(noteId, userId);
        List<NoteHistoryVO> result = new ArrayList<>();
        for (NoteHistoryDO historyDO : noteHistoryDAO.findByNoteIdOrderByCreateTimeDesc(noteId)) {
            NoteHistoryVO vo = new NoteHistoryVO();
            vo.setId(historyDO.getId());
            vo.setTitle(historyDO.getTitle());
            vo.setWordCount(historyDO.getWordCount());
            vo.setSource(historyDO.getSource());
            vo.setCreateTime(historyDO.getCreateTime());
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteDetailVO restoreHistory(Long historyId) {
        Long userId = UserContext.require();
        NoteHistoryDO historyDO = noteHistoryDAO.findFirstByIdAndUserId(historyId, userId);
        if (historyDO == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        NoteDO note = requireNote(historyDO.getNoteId(), userId);
        saveSnapshot(note, Constants.HISTORY_SOURCE_SAVE);
        String content = nvl(historyDO.getContent());
        note.setTitle(StringUtils.hasText(historyDO.getTitle()) ? historyDO.getTitle() : UNTITLED_TITLE);
        note.setContent(content);
        note.setSummary(WordCountUtil.summarize(content, SUMMARY_LENGTH));
        note.setWordCount(WordCountUtil.count(content));
        note.setUpdateTime(LocalDateTime.now());
        noteDAO.save(note);
        return toDetailVO(note, loadTagMap(userId));
    }

    /**
     * 导入刻意不加 @Transactional：每个文件独立提交，
     * "单文件失败不影响整批"是靠事务边界隔离实现的，而不是靠 catch 回滚。
     */
    @Override
    public ImportResultVO importNotes(MultipartFile[] files, Long folderId, Integer noteType) {
        Long userId = UserContext.require();
        if (files == null || files.length == 0) {
            throw new BizException("请先选择要导入的文件");
        }
        Long targetFolder = resolveFolderId(folderId, userId);
        Set<String> allowedExt = allowedDocExt();
        ImportResultVO result = new ImportResultVO();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                result.setFailed(result.getFailed() + 1);
                continue;
            }
            String originalName = plainFileName(file.getOriginalFilename());
            String ext = extensionOf(originalName);
            if (!allowedExt.contains(ext)) {
                result.setFailed(result.getFailed() + 1);
                continue;
            }
            try {
                String title = importOne(file, originalName, ext, targetFolder, noteType, userId);
                result.setSuccess(result.getSuccess() + 1);
                result.getTitles().add(title);
            } catch (Exception e) {
                log.warn("导入文件失败 name={} reason={}", originalName, e.getMessage());
                result.setFailed(result.getFailed() + 1);
            }
        }
        return result;
    }

    @Override
    public ResponseEntity<byte[]> exportOne(Long id, String format) {
        Long userId = UserContext.require();
        NoteDO note = requireNote(id, userId);
        String fmt = normalizeFormat(format);
        String fileName = sanitizeFileName(note.getTitle()) + "." + fmt;
        return buildDownload(fileName, exportBytes(note, fmt));
    }

    @Override
    public ResponseEntity<byte[]> exportBatch(BatchIdsDTO dto) {
        Long userId = UserContext.require();
        List<NoteDO> notes = requireOwnedNotes(dto.getIds(), userId);
        String fmt = normalizeFormat(dto.getFormat());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        // zip 条目名默认按 ASCII 处理，中文标题必须显式指定 UTF-8，否则解压乱码
        try (ZipOutputStream zos = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            Set<String> usedNames = new HashSet<>();
            for (NoteDO note : notes) {
                String entryName = uniqueEntryName(sanitizeFileName(note.getTitle()), fmt, usedNames);
                zos.putNextEntry(new ZipEntry(entryName));
                zos.write(exportBytes(note, fmt));
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buildDownload("笔记导出.zip", buffer.toByteArray());
    }

    // ---------------------------------------------------------------- 私有方法

    /**
     * 单条导入：解码 → 定标题 → 建笔记 → 存 import 快照。标题规则按契约：
     * txt 取首个非空行；md 正文首行是 # 标题则取之，否则退回文件名。
     *
     * @return 导入后的笔记标题
     */
    private String importOne(MultipartFile file, String originalName, String ext, Long folderId,
                             Integer noteType, Long userId) throws IOException {
        String text = decodeText(file.getBytes());
        String baseName = originalName.substring(0, originalName.lastIndexOf('.') > 0
                ? originalName.lastIndexOf('.') : originalName.length());
        String firstLine = WordCountUtil.cleanTitle(WordCountUtil.firstLine(text));
        String title;
        if (EXPORT_FORMAT_MD.equals(ext) || DOC_EXT_MARKDOWN.equals(ext)) {
            boolean headingFirst = WordCountUtil.firstLine(text).startsWith("#");
            title = headingFirst && StringUtils.hasText(firstLine) ? firstLine : limit(baseName, TITLE_MAX_LENGTH);
        } else {
            title = StringUtils.hasText(firstLine) ? firstLine : limit(baseName, TITLE_MAX_LENGTH);
        }
        if (!StringUtils.hasText(title)) {
            title = UNTITLED_TITLE;
        }
        LocalDateTime now = LocalDateTime.now();
        NoteDO note = new NoteDO();
        note.setUserId(userId);
        note.setFolderId(folderId);
        note.setTitle(title);
        note.setContent(text);
        note.setSummary(WordCountUtil.summarize(text, SUMMARY_LENGTH));
        note.setWordCount(WordCountUtil.count(text));
        note.setNoteType(noteType == null ? Constants.NOTE_TYPE_ESSAY : noteType);
        note.setTop(false);
        note.setDeleted(false);
        note.setCreateTime(now);
        note.setUpdateTime(now);
        noteDAO.save(note);
        NoteHistoryDO historyDO = new NoteHistoryDO();
        historyDO.setNoteId(note.getId());
        historyDO.setUserId(userId);
        historyDO.setTitle(title);
        historyDO.setContent(text);
        historyDO.setWordCount(note.getWordCount());
        historyDO.setSource(Constants.HISTORY_SOURCE_IMPORT);
        historyDO.setCreateTime(now);
        noteHistoryDAO.save(historyDO);
        return title;
    }

    /**
     * 编码嗅探：先 UTF-8 严格解码（onMalformedInput=REPORT，遇非法字节即抛异常），
     * 失败再试 GBK。若直接 new String(bytes, GBK) 会把坏字节静默替换成乱码字符，
     * 用户要到打开文件才发现，这里让错误在导入当场暴露。
     */
    private String decodeText(byte[] bytes) {
        try {
            return stripBom(strictDecoder(StandardCharsets.UTF_8).decode(ByteBuffer.wrap(bytes)).toString());
        } catch (CharacterCodingException ignored) {
            // UTF-8 非法，继续尝试 GBK
        }
        try {
            return stripBom(strictDecoder(Charset.forName("GBK")).decode(ByteBuffer.wrap(bytes)).toString());
        } catch (CharacterCodingException e) {
            throw new BizException("无法识别文件编码，仅支持 UTF-8 与 GBK，请将文件另存为 UTF-8 后重试");
        }
    }

    /**
     * 剥离 BOM：带 BOM 的 txt 若不去掉，首行判断（是否 # 标题）与正文都会混入不可见字符。
     * 用码位比较而不是字面量，避免把不可见字符写进源码。
     */
    private String stripBom(String text) {
        return !text.isEmpty() && text.charAt(0) == BOM_CHAR ? text.substring(1) : text;
    }

    private CharsetDecoder strictDecoder(Charset charset) {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
    }

    private Set<String> allowedDocExt() {
        return Arrays.stream(appProperties.getUpload().getAllowedDoc().split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    /**
     * multipart 文件名可能带客户端全路径（IE 习惯），只取最后一段防目录穿越。
     */
    private String plainFileName(String original) {
        if (!StringUtils.hasText(original)) {
            return "unnamed";
        }
        String name = original.replace('\\', '/');
        return name.substring(name.lastIndexOf('/') + 1);
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }

    /**
     * 保存历史快照并裁剪超出保留数的旧版本。saveAndFlush 先落库再执行原生删除，
     * 保证 deleteOlderThan 的 limit 窗口能看到刚写入的这条。
     */
    private void saveSnapshot(NoteDO note, String source) {
        NoteHistoryDO historyDO = new NoteHistoryDO();
        historyDO.setNoteId(note.getId());
        historyDO.setUserId(note.getUserId());
        historyDO.setTitle(note.getTitle());
        historyDO.setContent(note.getContent());
        historyDO.setWordCount(note.getWordCount());
        historyDO.setSource(source);
        historyDO.setCreateTime(LocalDateTime.now());
        noteHistoryDAO.saveAndFlush(historyDO);
        noteHistoryDAO.deleteOlderThan(note.getId(), HISTORY_KEEP_COUNT);
    }

    /**
     * 字数增量入当日书写日志；纯搬运（增量为 0）不记，避免污染打卡统计。
     */
    private void recordWritingDelta(Long userId, Long noteId, int delta) {
        if (delta == 0) {
            return;
        }
        WritingLogDO logDO = new WritingLogDO();
        logDO.setUserId(userId);
        logDO.setNoteId(noteId);
        logDO.setLogDate(LocalDate.now());
        logDO.setWordDelta(delta);
        logDO.setCreateTime(LocalDateTime.now());
        writingLogDAO.save(logDO);
    }

    /**
     * 标签名列表 → 关联重建：同名标签复用，缺失的按默认色自动建。
     * 名称去空白、去重后入库，防止 "AI" 和 "AI " 分裂成两个标签。
     */
    private void rebuildTags(Long noteId, List<String> tagNames, Long userId) {
        noteTagDAO.deleteByNoteId(noteId);
        if (tagNames.isEmpty()) {
            return;
        }
        Set<String> distinctNames = tagNames.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String name : distinctNames) {
            TagDO tag = tagDAO.findFirstByUserIdAndName(userId, name);
            if (tag == null) {
                tag = new TagDO();
                tag.setUserId(userId);
                tag.setName(name);
                tag.setColor(DEFAULT_TAG_COLOR);
                tag.setCreateTime(LocalDateTime.now());
                tagDAO.save(tag);
            }
            NoteTagDO rel = new NoteTagDO();
            rel.setNoteId(noteId);
            rel.setTagId(tag.getId());
            noteTagDAO.save(rel);
        }
    }

    private Map<Long, TagDO> loadTagMap(Long userId) {
        return tagDAO.findByUserIdOrderByIdAsc(userId).stream()
                .collect(Collectors.toMap(TagDO::getId, Function.identity(), (a, b) -> a));
    }

    private void attachTags(NoteVO vo, Map<Long, TagDO> tagMap) {
        List<TagVO> tags = new ArrayList<>();
        for (NoteTagDO rel : noteTagDAO.findByNoteId(vo.getId())) {
            TagDO tag = tagMap.get(rel.getTagId());
            if (tag != null) {
                tags.add(toTagVO(tag));
            }
        }
        vo.setTags(tags);
    }

    private TagVO toTagVO(TagDO tag) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setColor(tag.getColor());
        return vo;
    }

    /**
     * 归属校验统一入口：不存在与不属于本用户都抛 NOT_FOUND，
     * 避免"404 与 403 的差异"泄露他人资源是否存在。
     */
    private NoteDO requireNote(Long id, Long userId) {
        NoteDO note = id == null ? null : noteDAO.findFirstByIdAndUserId(id, userId);
        if (note == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return note;
    }

    /**
     * 批量操作的归属校验：任一 id 不存在或不属于当前用户即整单 NOT_FOUND，
     * 宁可不做强弱之分，也不静默丢弃越权 id 让调用方误以为全部成功。
     */
    private List<NoteDO> requireOwnedNotes(List<Long> ids, Long userId) {
        List<Long> distinctIds = ids == null ? List.of() : ids.stream()
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "笔记 id 不能为空");
        }
        List<NoteDO> notes = noteDAO.findByIdInAndUserIdAndDeletedFalse(distinctIds, userId);
        if (notes.size() != distinctIds.size()) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return notes;
    }

    /**
     * 目录归属校验：0（未分类）恒合法；非 0 必须是本人未删除的目录。
     */
    private Long resolveFolderId(Long folderId, Long userId) {
        if (folderId == null || Constants.UNFILED_FOLDER_ID.equals(folderId)) {
            return Constants.UNFILED_FOLDER_ID;
        }
        FolderDO folder = folderDAO.findFirstByIdAndUserId(folderId, userId);
        if (folder == null || Boolean.TRUE.equals(folder.getDeleted())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return folderId;
    }

    /**
     * 标题解析：用户填了用用户的；为空则从正文首行推断（cleanTitle 会去掉 # 装饰），
     * 仍为空给兜底标题，保证 mn_note.title 的 NOT NULL 约束。
     */
    private String resolveTitle(String rawTitle, String content) {
        if (StringUtils.hasText(rawTitle)) {
            return rawTitle.trim();
        }
        String inferred = WordCountUtil.cleanTitle(WordCountUtil.firstLine(content));
        return StringUtils.hasText(inferred) ? inferred : UNTITLED_TITLE;
    }

    private void fillFromBrief(NoteVO vo, NoteDAO.NoteBrief brief) {
        vo.setId(brief.getId());
        vo.setTitle(brief.getTitle());
        vo.setSummary(brief.getSummary());
        vo.setFolderId(brief.getFolderId());
        vo.setNoteType(brief.getNoteType());
        vo.setNoteTypeName(noteTypeName(brief.getNoteType()));
        vo.setWordCount(brief.getWordCount());
        vo.setTop(brief.getTop());
        vo.setCreateTime(brief.getCreateTime());
        vo.setUpdateTime(brief.getUpdateTime());
    }

    private NoteDetailVO toDetailVO(NoteDO note, Map<Long, TagDO> tagMap) {
        NoteDetailVO vo = new NoteDetailVO();
        vo.setId(note.getId());
        vo.setTitle(note.getTitle());
        vo.setSummary(note.getSummary());
        vo.setFolderId(note.getFolderId());
        vo.setNoteType(note.getNoteType());
        vo.setNoteTypeName(noteTypeName(note.getNoteType()));
        vo.setWordCount(note.getWordCount());
        vo.setTop(note.getTop());
        vo.setCreateTime(note.getCreateTime());
        vo.setUpdateTime(note.getUpdateTime());
        vo.setContent(note.getContent());
        attachTags(vo, tagMap);
        return vo;
    }

    /**
     * 类型中文名由服务端统一翻译，避免前后端各存一份映射表漂移。
     */
    private String noteTypeName(Integer noteType) {
        if (noteType == null) {
            return null;
        }
        return switch (noteType) {
            case Constants.NOTE_TYPE_RESEARCH -> "科研";
            case Constants.NOTE_TYPE_LIFE -> "生活";
            case Constants.NOTE_TYPE_FICTION -> "小说";
            case Constants.NOTE_TYPE_ESSAY -> "随笔";
            default -> "随笔";
        };
    }

    private String normalizeFormat(String format) {
        if (!StringUtils.hasText(format) || EXPORT_FORMAT_MD.equalsIgnoreCase(format)) {
            return EXPORT_FORMAT_MD;
        }
        if (EXPORT_FORMAT_TXT.equalsIgnoreCase(format)) {
            return EXPORT_FORMAT_TXT;
        }
        throw new BizException(ResultCode.PARAM_ERROR, "导出格式只支持 md 或 txt");
    }

    /**
     * md 原样导出 Markdown 源文；txt 复用 stripMarkdown 去语法，与摘要口径同一实现。
     */
    private byte[] exportBytes(NoteDO note, String format) {
        String content = nvl(note.getContent());
        if (EXPORT_FORMAT_TXT.equals(format)) {
            content = WordCountUtil.stripMarkdown(content);
        }
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private ResponseEntity<byte[]> buildDownload(String fileName, byte[] body) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(body.length)
                .body(body);
    }

    /**
     * 文件名清洗：Windows/Linux 都禁的字符一并去掉，首尾点与空白剔除，
     * 空标题兜"未命名"，避免下载对话框出现非法文件名。
     */
    private String sanitizeFileName(String title) {
        if (!StringUtils.hasText(title)) {
            return "未命名";
        }
        String cleaned = title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").trim();
        cleaned = cleaned.replaceAll("^[.]+|[.]+$", "").trim();
        if (cleaned.isEmpty()) {
            return "未命名";
        }
        return limit(cleaned, EXPORT_NAME_MAX);
    }

    private String uniqueEntryName(String base, String format, Set<String> used) {
        String candidate = base + "." + format;
        int seq = 2;
        while (!used.add(candidate)) {
            candidate = base + "(" + seq + ")." + format;
            seq++;
        }
        return candidate;
    }

    private String limit(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private String nvl(String text) {
        return text == null ? "" : text;
    }
}
