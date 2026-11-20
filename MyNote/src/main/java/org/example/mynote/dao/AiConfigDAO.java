package org.example.mynote.dao;

import org.example.mynote.entity.AiConfigDO;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AI 模型配置 DAO。api_key 字段存密文，DAO 层不解密。

 *
 * @author MyNote
 */
@Repository
public interface AiConfigDAO extends JpaRepository<AiConfigDO, Long> {

    /**
     * 用户的全部模型配置。
     *
     * @param userId 用户 id
     * @return 配置列表
     */
    List<AiConfigDO> findByUserIdOrderByCreateTimeAsc(Long userId);

    /**
     * 当前默认模型。
     *
     * @param userId 用户 id
     * @return 配置
     */
    AiConfigDO findFirstByUserIdAndDefaultConfigTrue(Long userId);

    /**
     * 按 id 与用户查询。
     *
     * @param id     配置 id
     * @param userId 用户 id
     * @return 配置
     */
    AiConfigDO findFirstByIdAndUserId(Long id, Long userId);

    /**
     * 同服务商同模型是否已配置，先于数据库唯一约束给出友好提示。
     *
     * @param userId   用户 id
     * @param provider 服务商标识
     * @param model    模型名
     * @return 配置
     */
    AiConfigDO findFirstByUserIdAndProviderCodeAndModelName(Long userId, String provider, String model);

    /**
     * 清除用户全部默认标记，再设置新的默认模型，保证同时只有一个默认。
     *
     * @param userId 用户 id
     */
    @Modifying
    @Query("update AiConfigDO c set c.defaultConfig = false where c.userId = :userId")
    void clearDefault(@Param("userId") Long userId);

    /**
     * 删除配置。
     *
     * @param id     配置 id
     * @param userId 用户 id
     */
    @Modifying
    @Query("delete from AiConfigDO c where c.id = :id and c.userId = :userId")
    void deleteByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);
}
