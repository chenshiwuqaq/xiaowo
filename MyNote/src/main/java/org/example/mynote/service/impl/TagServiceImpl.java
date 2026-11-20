package org.example.mynote.service.impl;

import org.example.mynote.common.BizException;
import org.example.mynote.common.ResultCode;
import org.example.mynote.common.UserContext;
import org.example.mynote.dao.NoteTagDAO;
import org.example.mynote.dao.TagDAO;
import org.example.mynote.dto.TagSaveDTO;
import org.example.mynote.dto.TagUpdateDTO;
import org.example.mynote.entity.NoteTagDO;
import org.example.mynote.entity.TagDO;
import org.example.mynote.service.TagService;
import org.example.mynote.vo.TagVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 标签业务实现。
 *
 * <p>标签池按用户隔离：查询与唯一性判断都以 UserContext 的 userId 为准。
 * 删除标签只解除 mn_note_tag 关联，绝不动笔记本身——标签是"视角"，
 * 笔记是"资产"，视角可以随时撤掉。</p>
 *
 * @author MyNote
 */
@Service
public class TagServiceImpl implements TagService {

    /**
     * 新标签默认色，与笔记保存时自动建标签的缺省色一致
     */
    private static final String DEFAULT_TAG_COLOR = "#E7A878";

    private static final String DUPLICATE_NAME_MESSAGE = "已有同名标签";

    private final TagDAO tagDAO;

    private final NoteTagDAO noteTagDAO;

    public TagServiceImpl(TagDAO tagDAO, NoteTagDAO noteTagDAO) {
        this.tagDAO = tagDAO;
        this.noteTagDAO = noteTagDAO;
    }

    /**
     * 列表附带引用笔记数：DAO 只提供了按标签查关联的方法，
     * 个人库标签量级很小，逐标签统计不会成为瓶颈。
     */
    @Override
    public List<TagVO> list() {
        Long userId = UserContext.require();
        List<TagVO> result = new ArrayList<>();
        for (TagDO tag : tagDAO.findByUserIdOrderByIdAsc(userId)) {
            TagVO vo = toVO(tag);
            vo.setNoteCount(noteTagDAO.findByTagId(tag.getId()).size());
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TagVO create(TagSaveDTO dto) {
        Long userId = UserContext.require();
        String name = dto.getName().trim();
        if (tagDAO.findFirstByUserIdAndName(userId, name) != null) {
            throw new BizException(DUPLICATE_NAME_MESSAGE);
        }
        TagDO tag = new TagDO();
        tag.setUserId(userId);
        tag.setName(name);
        tag.setColor(StringUtils.hasText(dto.getColor()) ? dto.getColor().trim() : DEFAULT_TAG_COLOR);
        tag.setCreateTime(LocalDateTime.now());
        tagDAO.save(tag);
        TagVO vo = toVO(tag);
        vo.setNoteCount(0);
        return vo;
    }

    /**
     * 改名保持同用户唯一；颜色传空串视为"不改"而非"清空"，因为列是 NOT NULL。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TagVO update(Long id, TagUpdateDTO dto) {
        Long userId = UserContext.require();
        TagDO tag = requireTag(id, userId);
        String name = dto.getName().trim();
        if (!name.equals(tag.getName()) && tagDAO.findFirstByUserIdAndName(userId, name) != null) {
            throw new BizException(DUPLICATE_NAME_MESSAGE);
        }
        tag.setName(name);
        if (StringUtils.hasText(dto.getColor())) {
            tag.setColor(dto.getColor().trim());
        }
        tagDAO.save(tag);
        TagVO vo = toVO(tag);
        vo.setNoteCount(noteTagDAO.findByTagId(tag.getId()).size());
        return vo;
    }

    /**
     * 删除标签前先删光它与笔记的关联，防止 mn_note_tag 残留指向已删标签的悬空行。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long userId = UserContext.require();
        TagDO tag = requireTag(id, userId);
        List<NoteTagDO> relations = noteTagDAO.findByTagId(tag.getId());
        if (!relations.isEmpty()) {
            noteTagDAO.deleteAll(relations);
        }
        tagDAO.delete(tag);
    }

    /**
     * 不存在与越权统一 NOT_FOUND，不区分"没有"和"不是你的"。
     */
    private TagDO requireTag(Long id, Long userId) {
        TagDO tag = id == null ? null : tagDAO.findFirstByIdAndUserId(id, userId);
        if (tag == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return tag;
    }

    private TagVO toVO(TagDO tag) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setColor(tag.getColor());
        return vo;
    }
}
