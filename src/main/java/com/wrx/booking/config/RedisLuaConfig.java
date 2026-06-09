package com.wrx.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Redis Lua 脚本配置类。
 * 负责把 resources/lua 目录下的 Lua 脚本加载成 Spring Bean。
 */
@Configuration
public class RedisLuaConfig {

    /**
     * 加载预约预扣减 Lua 脚本。
     * 返回 Long 类型，对应 Lua 脚本中的 1、0、-1、-2。
     */
    @Bean
    public DefaultRedisScript<Long> reserveBookingScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/reserve_booking.lua"));
        script.setResultType(Long.class);
        return script;
    }
}