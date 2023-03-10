package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList() {
        //先从Redis中查
        List<String> shopTYPES = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);
        //判断是否存在，存在就返回
        if (!shopTYPES.isEmpty()){
            List<ShopType> tmp = new ArrayList<>();
            for (String types : shopTYPES) {
                ShopType shopType = JSONUtil.toBean(types, ShopType.class);
                tmp.add(shopType);
            }
            return Result.ok(tmp);
        }
        //如果不存在，查询数据库
        List<ShopType> tmp = query().orderByAsc("sort").list();
        if (tmp == null) {
            //如果数据库不存在 返回失败
            return Result.fail("店铺类型不存在！");
        }
        //如果存在写入redis
        for (ShopType shopType:tmp){
            String jsonStr = JSONUtil.toJsonStr(shopType);
            shopTYPES.add(jsonStr);
        }
        stringRedisTemplate.opsForList().leftPushAll(CACHE_SHOP_TYPE_KEY,shopTYPES);
        //返回
        return Result.ok(tmp);
    }
}
