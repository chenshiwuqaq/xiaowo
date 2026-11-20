package org.example.mynote.service;

import org.example.mynote.dto.PasswordUpdateDTO;
import org.example.mynote.dto.ProfileUpdateDTO;
import org.example.mynote.dto.SettingsDTO;
import org.example.mynote.vo.UserVO;

/**
 * 当前登录用户的资料与设置服务。
 *
 * <p>方法签名一律不接收 userId：用户身份只从 {@link org.example.mynote.common.UserContext}
 * 取，前端传什么都不认，从源头杜绝越权改他人资料。</p>
 *
 * @author MyNote
 */
public interface UserService {

    /**
     * 查询当前用户的完整资料。
     *
     * @return 用户视图对象
     */
    UserVO getProfile();

    /**
     * 更新当前用户的昵称、邮箱、签名，其余字段（含 account）不动。
     *
     * @param dto 资料更新入参
     * @return 更新后的用户视图对象
     */
    UserVO updateProfile(ProfileUpdateDTO dto);

    /**
     * 更新当前用户的外观与写作习惯设置，数值范围越界抛业务异常。
     *
     * @param dto 设置更新入参
     * @return 更新后的用户视图对象
     */
    UserVO updateSettings(SettingsDTO dto);

    /**
     * 校验旧密码后修改当前用户密码。
     *
     * @param dto 密码更新入参
     */
    void updatePassword(PasswordUpdateDTO dto);
}
