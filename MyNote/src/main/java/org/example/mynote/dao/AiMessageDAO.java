package org.example.mynote.dao;

import org.example.mynote.entity.AiMessageDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AI 消息 DAO。

 *
 * @author MyNote
 */
@Repository
public interface AiMessageDAO extends JpaRepository<AiMessageDO, Long> {

    /**
     * 会话内的全部消息，按 id 升序即时间正序。
     *
     * @param sessionId 会话 id
     * @return 消息列表
     */
    List<AiMessageDO> findBySessionIdOrderByIdAsc(Long sessionId);
}
