package com.wrx.booking.support;

/**
 * 统一管理 Redis key，避免各处手写字符串。
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    public static String slotAvailable(Long slotId) {
        return "slot:" + slotId + ":available";
    }

    public static String slotBookedUsers(Long slotId) {
        return "slot:" + slotId + ":booked-users";
    }
}