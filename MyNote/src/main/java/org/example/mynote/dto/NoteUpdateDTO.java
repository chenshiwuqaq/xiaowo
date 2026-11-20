package org.example.mynote.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 更新笔记入参。
 *
 * <p>编辑器保存时整单提交（标题、正文、目录、类型、标签），字段为 null 表示"不修改"，
 * 避免半表单提交把未传字段清空。snapshot 为 true 时先把旧正文写入历史版本再覆盖，
 * 这是长文写作者"改崩了能回退"的保底开关。</p>
 *
 * @author MyNote
 */
@Data
public class NoteUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 标题，为空时由正文首行推断
     */
    @Size(max = 200, message = "标题长度不能超过 200 字")
    private String title;

    /**
     * Markdown 正文，null 表示不修改
     */
    private String content;

    /**
     * 归属目录 id，null 表示不修改
     */
    @Min(value = 0, message = "目录 id 不能为负数")
    private Long folderId;

    /**
     * 笔记类型，null 表示不修改
     */
    @Min(value = 1, message = "笔记类型取 1-4")
    @Max(value = 4, message = "笔记类型取 1-4")
    private Integer noteType;

    /**
     * 标签名列表，null 表示不动标签，传列表则整体重建关联
     */
    @Size(max = 20, message = "单篇笔记最多绑定 20 个标签")
    private List<@Size(max = 50, message = "标签名不能超过 50 字") String> tagNames;

    /**
     * 是否在覆盖前保存一份历史版本
     */
    private Boolean snapshot;

    /**
     * 快照来源：save 手动保存 / ai AI 改写覆盖前。仅在 snapshot=true 时生效，
     * 留空按 save 处理。回收站列表据此区分"是我改的还是助手改的"。
     */
    @Size(max = 20, message = "快照来源标识过长")
    private String snapshotSource;
}
