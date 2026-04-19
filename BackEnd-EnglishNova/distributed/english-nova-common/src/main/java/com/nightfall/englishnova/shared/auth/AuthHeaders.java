package com.nightfall.englishnova.shared.auth;

/**
 * 网关鉴权透传头常量，定义 X-Auth-User-Id 和 X-Auth-Username 请求头名称。
 */
public final class AuthHeaders {

    /** 用户 ID 请求头名称。 */
    public static final String USER_ID = "X-Auth-User-Id";
    /** 用户名请求头名称。 */
    public static final String USERNAME = "X-Auth-Username";

    private AuthHeaders() {
    }
}
