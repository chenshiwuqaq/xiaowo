package org.example.mynote.service.impl;

import org.example.mynote.common.BizException;
import org.example.mynote.common.Constants;
import org.example.mynote.common.ResultCode;
import org.example.mynote.common.UserContext;
import org.example.mynote.dao.FolderDAO;
import org.example.mynote.dao.NoteDAO;
import org.example.mynote.dto.FolderMoveDTO;
import org.example.mynote.dto.FolderSaveDTO;
import org.example.mynote.entity.FolderDO;
import org.example.mynote.entity.NoteDO;
import org.example.mynote.service.FolderService;
import org.example.mynote.vo.FolderNodeVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 目录业务实现。
 *
 * <p>目录树以"一次查询全量组装"为原则：tree 与 delete 都需要看整棵子树，
 * 逐层递归查库会变成 N+1，这里都收敛为一次 findByUser 全量取回后在内存构图。
 * 移动目录时沿 parentId 上溯检测祖先，从根上杜绝把父目录塞进自己子孙里造成的环。</p>
 *
 * @author MyNote
 */
@Service
public class FolderServiceImpl implements FolderService {

    /**
     * 祖先链上溯的保护深度：正常树远达不到，仅防脏数据成环时死循环
     */
    private static final int MAX_ANCESTOR_DEPTH = 128;

    /**
     * 同级重名的统一提示文案，创建与重命名共用
     */
    private static final String DUPLICATE_NAME_MESSAGE = "同级下已有同名目录";

    private final FolderDAO folderDAO;

    private final NoteDAO noteDAO;

    public FolderServiceImpl(FolderDAO folderDAO, NoteDAO noteDAO) {
        this.folderDAO = folderDAO;
        this.noteDAO = noteDAO;
    }

    /**
     * 组装整棵树：先一次取回全部目录与全部笔记简要（后者只为统计 noteCount），
     * 再在内存里按 parentId 挂接，全程两次 SQL。
     */
    @Override
    public List<FolderNodeVO> tree() {
        Long userId = UserContext.require();
        List<FolderDO> folders = folderDAO.findByUserIdAndDeletedFalseOrderBySortOrderAsc(userId);
        Map<Long, Integer> noteCounts = countNotesByFolder(userId);

        Map<Long, FolderNodeVO> nodeMap = new HashMap<>(folders.size());
        for (FolderDO folder : folders) {
            nodeMap.put(folder.getId(), toNodeVO(folder, noteCounts.getOrDefault(folder.getId(), 0)));
        }
        List<FolderNodeVO> roots = new ArrayList<>();
        for (FolderDO folder : folders) {
            FolderNodeVO node = nodeMap.get(folder.getId());
            FolderNodeVO parent = Constants.ROOT_FOLDER_ID.equals(folder.getParentId())
                    ? null : nodeMap.get(folder.getParentId());
            if (parent == null) {
                // 父目录不在未删除集合里（理论上级联删除后不会出现），当作根节点展示，
                // 宁可让目录挂在树顶也不能让它在界面上凭空消失
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FolderNodeVO create(FolderSaveDTO dto) {
        Long userId = UserContext.require();
        Long parentId = dto.getParentId() == null ? Constants.ROOT_FOLDER_ID : dto.getParentId();
        requireUsableParent(parentId, userId);
        String name = dto.getName().trim();
        if (folderDAO.findFirstByUserIdAndParentIdAndNameAndDeletedFalse(userId, parentId, name) != null) {
            throw new BizException(DUPLICATE_NAME_MESSAGE);
        }
        LocalDateTime now = LocalDateTime.now();
        FolderDO folder = new FolderDO();
        folder.setUserId(userId);
        folder.setParentId(parentId);
        folder.setName(name);
        folder.setIcon(dto.getIcon());
        folder.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        folder.setDeleted(false);
        folder.setCreateTime(now);
        folder.setUpdateTime(now);
        folderDAO.save(folder);
        return toNodeVO(folder, 0);
    }

    /**
     * 编辑目录本体信息。换父级是 move 接口的职责，这里即使前端误传 parentId 也不生效。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FolderNodeVO update(Long id, FolderSaveDTO dto) {
        Long userId = UserContext.require();
        FolderDO folder = requireFolder(id, userId);
        String name = dto.getName().trim();
        boolean renamed = !name.equals(folder.getName());
        if (renamed && folderDAO.findFirstByUserIdAndParentIdAndNameAndDeletedFalse(
                userId, folder.getParentId(), name) != null) {
            // 重命名同样受同级唯一约束，否则 tree 会出现两个同名兄弟，前端无法区分
            throw new BizException(DUPLICATE_NAME_MESSAGE);
        }
        folder.setName(name);
        if (dto.getIcon() != null) {
            folder.setIcon(dto.getIcon());
        }
        if (dto.getSortOrder() != null) {
            folder.setSortOrder(dto.getSortOrder());
        }
        folder.setUpdateTime(LocalDateTime.now());
        folderDAO.save(folder);
        long noteCount = noteDAO.countByFolders(userId, List.of(folder.getId()));
        return toNodeVO(folder, (int) noteCount);
    }

    /**
     * 移动目录：目标父级必须可用，且不能是自己的子孙——沿 parentId 上溯，
     * 途中碰到被移动节点即成环。同级重名不拦截：移动是"位置"操作，
     * 若真撞名，由用户在下次进入该层时处理（契约只对新增强制重名校验）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void move(FolderMoveDTO dto) {
        Long userId = UserContext.require();
        FolderDO folder = requireFolder(dto.getId(), userId);
        Long targetParentId = dto.getParentId();
        if (targetParentId.equals(folder.getId())) {
            throw new BizException("不能把目录移动到自身之下");
        }
        requireUsableParent(targetParentId, userId);
        if (!Constants.ROOT_FOLDER_ID.equals(targetParentId)) {
            Long cursor = targetParentId;
            int depth = 0;
            while (cursor != null && !Constants.ROOT_FOLDER_ID.equals(cursor)) {
                if (cursor.equals(folder.getId())) {
                    throw new BizException("不能把目录移动到它的子目录之下，否则目录树会出现环");
                }
                FolderDO ancestor = folderDAO.findFirstByIdAndUserId(cursor, userId);
                if (ancestor == null || ++depth > MAX_ANCESTOR_DEPTH) {
                    break;
                }
                cursor = ancestor.getParentId();
            }
        }
        folder.setParentId(targetParentId);
        folder.setUpdateTime(LocalDateTime.now());
        folderDAO.save(folder);
    }

    /**
     * 删除目录：连同全部子孙目录逻辑删除，目录下的笔记落回未分类。
     * 笔记数量不做拦截——产品语义是"目录只是容器，容器为空与否都可以扔"，
     * 需要用户三思的破坏性操作（删笔记本体）才值得设门槛。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long userId = UserContext.require();
        requireFolder(id, userId);
        List<FolderDO> folders = folderDAO.findByUserIdAndDeletedFalseOrderBySortOrderAsc(userId);
        Set<Long> subtreeIds = collectSubtreeIds(folders, id);
        LocalDateTime now = LocalDateTime.now();
        List<FolderDO> toUpdate = new ArrayList<>();
        for (FolderDO folder : folders) {
            if (subtreeIds.contains(folder.getId())) {
                folder.setDeleted(true);
                folder.setUpdateTime(now);
                toUpdate.add(folder);
            }
        }
        folderDAO.saveAll(toUpdate);
        unfileNotesInFolders(userId, subtreeIds);
    }

    // ---------------------------------------------------------------- 私有方法

    /**
     * 全量取一次未删除笔记简要，在内存里按目录聚合计数，替代逐目录 count。
     */
    private Map<Long, Integer> countNotesByFolder(Long userId) {
        Map<Long, Integer> counts = new HashMap<>();
        for (NoteDAO.NoteBrief brief : noteDAO.findAllBrief(userId)) {
            if (brief.getFolderId() != null) {
                counts.merge(brief.getFolderId(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * BFS 收集以 rootId 为根的子目录全集（含自身），直接在已取回的目录列表上构图，
     * 不再回查数据库。visited 集合防脏数据成环。
     */
    private Set<Long> collectSubtreeIds(List<FolderDO> folders, Long rootId) {
        Map<Long, List<Long>> childIndex = new HashMap<>();
        for (FolderDO folder : folders) {
            childIndex.computeIfAbsent(folder.getParentId(), k -> new ArrayList<>()).add(folder.getId());
        }
        Set<Long> collected = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (!collected.add(current)) {
                continue;
            }
            List<Long> children = childIndex.get(current);
            if (children != null) {
                queue.addAll(children);
            }
        }
        return collected;
    }

    /**
     * 把指定目录集合下的未删除笔记全部落回未分类（folderId = 0）。
     * 借助一次全量简要查询挑出目标笔记 id，再批量取实体更新，避免逐目录 count。
     */
    private void unfileNotesInFolders(Long userId, Set<Long> folderIds) {
        if (folderIds.isEmpty()) {
            return;
        }
        List<Long> noteIds = noteDAO.findAllBrief(userId).stream()
                .filter(brief -> folderIds.contains(brief.getFolderId()))
                .map(NoteDAO.NoteBrief::getId)
                .collect(Collectors.toList());
        if (noteIds.isEmpty()) {
            return;
        }
        List<NoteDO> notes = noteDAO.findByIdInAndUserIdAndDeletedFalse(noteIds, userId);
        for (NoteDO note : notes) {
            note.setFolderId(Constants.UNFILED_FOLDER_ID);
        }
        noteDAO.saveAll(notes);
    }

    /**
     * 目标父级校验：0 恒合法；非 0 必须是本人当前有效的目录。
     */
    private void requireUsableParent(Long parentId, Long userId) {
        if (parentId == null || Constants.ROOT_FOLDER_ID.equals(parentId)) {
            return;
        }
        FolderDO parent = folderDAO.findFirstByIdAndUserId(parentId, userId);
        if (parent == null || Boolean.TRUE.equals(parent.getDeleted())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
    }

    /**
     * 归属校验：不存在与越权统一 NOT_FOUND，已逻辑删除的目录同样视为不存在。
     */
    private FolderDO requireFolder(Long id, Long userId) {
        FolderDO folder = id == null ? null : folderDAO.findFirstByIdAndUserId(id, userId);
        if (folder == null || Boolean.TRUE.equals(folder.getDeleted())) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        return folder;
    }

    private FolderNodeVO toNodeVO(FolderDO folder, Integer noteCount) {
        FolderNodeVO vo = new FolderNodeVO();
        vo.setId(folder.getId());
        vo.setParentId(folder.getParentId());
        vo.setName(StringUtils.hasText(folder.getName()) ? folder.getName() : "未命名目录");
        vo.setIcon(folder.getIcon());
        vo.setSortOrder(folder.getSortOrder());
        vo.setNoteCount(noteCount);
        return vo;
    }
}
