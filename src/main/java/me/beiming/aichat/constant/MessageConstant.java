package me.beiming.aichat.constant;

public class MessageConstant {
    public static final String ACCOUNT_LOCKED = "账户已锁定，请%s分钟后再试";
    public static final String USERNAME_OR_PASSWORD_ERROR = "用户名或密码错误！";
    public static final String USERNAME_OR_PASSWORD_ERROR_WITH_REMAINING = "用户名或密码错误！剩余尝试次数：%d";
    public static final String USER_DISABLED = "用户已被禁用！";
    public static final String PASSWORD_ERROR_TOO_MANY = "密码错误次数过多，账户已锁定30分钟";
    public static final String REFRESH_TOKEN_EMPTY = "刷新令牌不能为空";
    public static final String REFRESH_TOKEN_INVALID = "刷新令牌无效或已过期";
    public static final String USER_NOT_EXIST = "用户不存在";
    public static final String USER_ALREADY_EXIST = "用户已存在！";
    public static final String REGISTER_SUCCESS = "注册成功！";
    public static final String USERNAME_OR_PASSWORD_EMPTY = "用户名或密码不能为空";
    public static final String CONVERSATION_NOT_FOUND_ERROR = "会话不存在，请重新创建会话";
    public static final String CONVERSATION_DELETED_SUCCESSY = "会话删除成功";

    public static final String USER_ID_EMPTY_ERROR = "用户ID不能为空";
    public static final String API_KEY_EMPTY_ERROR = "Api-Key不能为空";

    public static final String TOKEN_EXPIRED = "令牌已过期";
    public static final String TOKEN_MISSING = "令牌缺失";

    public static final String MESSAGE_INDEX_OUT_OF_BOUNDS = "消息索引越界，历史共 %d 条消息，索引范围 0-%d";
    public static final String MESSAGE_EDIT_SUCCESS = "消息编辑成功（已截断后续 %d 条消息，需重新发送后续对话）";
    public static final String MESSAGE_DELETE_SUCCESS = "消息删除成功";
    public static final String HISTORY_EMPTY = "当前会话没有历史消息";
    public static final String EDIT_CANNOT_BE_EMPTY = "编辑消息时，新的消息内容不能为空";
}