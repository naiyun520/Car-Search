package cn.naiblog.carsearch.security;

public record CurrentUser(long id, String openid, String unionid, String nickname, String avatarUrl, String phone) {}
