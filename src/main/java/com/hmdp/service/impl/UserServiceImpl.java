package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.hmdp.utils.RedisConstants;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.hmdp.utils.SystemConstants;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.Verify phone number
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2. if not correct, return ERROR
            return Result.fail("手机号格式错误！");
        }

        // 3. generate verification code
        String code = RandomUtil.randomNumbers(6);

        // 4. store code to redis // set key value ex 120
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code, LOGIN_CODE_TTL, TimeUnit.MINUTES);


        // 5. send code
        log.debug("The code is : {}",code);

        // Return OK
        return Result.ok();
    }



    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. Verify Phone
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }

        // 2. Verify Code
        Object casheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String code = loginForm.getCode();
        if (casheCode == null || !casheCode.equals(code)){
            // 3. Not the same, return error
            return Result.fail("验证码错误");
        }

        // 4. Look for user based on Phone number
        User user = query().eq("phone", phone).one();

        // 5. If user exists
        if (user == null) {
            // 6. Not exist, create new user
            user = createUserWithPhone(phone);
        }

        // 7. set user info to redis
        // 7.1 generate token
        String token = UUID.randomUUID().toString(true);
        // 7.2 transform User to HashMap
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString()));
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        // 7.3 expire time
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);

        // 8. return token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
