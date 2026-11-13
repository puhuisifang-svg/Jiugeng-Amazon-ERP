package com.jiugeng.erp;

/** Shared syntax rules for optional mainland contact numbers; not proof of ownership. */
public final class ChinaPhone {
    private ChinaPhone() {}
    public static String compact(String value) { return value == null ? "" : value.trim().replace(" ", ""); }
    public static boolean mobile(String value) {
        String s=compact(value);
        return s.isEmpty() || s.matches("(?:\\+86|0086)?1[3-9][0-9]{9}");
    }
    public static boolean contact(String value) {
        String s=compact(value);
        return mobile(value) || s.matches("(?:\\+86|0086)?(?:0[1-9][0-9]{1,2}-?[1-9][0-9]{6,7}(?:-[0-9]{1,6})?|(?:400|800)-?[0-9]{3}-?[0-9]{4}|100[0-9]{2}|9[56][0-9]{3,4})");
    }
    public static final String MESSAGE="请填写有效的中国手机号或固定电话（需区号，如 0592-1234567，分机用 - 分隔）";
}
