package org.example.mynote.dao;

import org.example.mynote.entity.UserDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户 DAO。
 *
 * @author MyNote
 */
@Repository
public interface UserDAO extends JpaRepository<UserDO, Long> {

    /**
     * 按登录账号查询用户。
     *
     * @param account 登录账号
     * @return 用户
     */
    Optional<UserDO> findByAccount(String account);

    /**
     * 判断账号是否已被占用。
     *
     * @param account 登录账号
     * @return true 表示已存在
     */
    boolean existsByAccount(String account);

    /**
     * 判断邮箱是否已被占用（找回密码时用于校验绑定关系）。
     *
     * @param email 邮箱
     * @return 用户，不存在返回空
     */
    Optional<UserDO> findFirstByEmail(String email);

    /**
     * 按账号与邮箱同时命中查询用户，用于自助重置密码。
     *
     * @param account 登录账号
     * @param email   绑定邮箱
     * @return 用户
     */
    Optional<UserDO> findFirstByAccountAndEmail(String account, String email);
}
