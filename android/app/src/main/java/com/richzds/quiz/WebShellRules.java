package com.richzds.quiz;

import java.util.Locale;

/** 套壳里几条小规则，纯 Java，单元测试直接跑。 */
public final class WebShellRules {

    /** 网页版靠这个标记认出自己在套壳里（app/src/logic/shell.ts 里是同一个名字） */
    public static final String UA_MARK = "MaShangShuaTiApp";

    private WebShellRules() {
    }

    /** 系统 User-Agent 末尾加上「MaShangShuaTiApp/版本号」；已经有标记时换成新版本，不重复加 */
    public static String userAgent(String systemUa, String versionName) {
        String base = systemUa == null ? "" : systemUa.replaceAll("\\s*" + UA_MARK + "/\\S*", "").trim();
        String mark = UA_MARK + "/" + versionName;
        return base.isEmpty() ? mark : base + " " + mark;
    }

    /**
     * 网页的 &lt;input type=file accept=...&gt; 是否收图片：收图片时选择器里给一个「拍照」入口。
     * accept 为空表示什么文件都收，也算。
     */
    public static boolean acceptsImages(String[] acceptTypes) {
        if (acceptTypes == null || acceptTypes.length == 0) return true;
        boolean anyNonEmpty = false;
        for (String raw : acceptTypes) {
            if (raw == null) continue;
            for (String part : raw.split(",")) {
                String t = part.trim().toLowerCase(Locale.ROOT);
                if (t.isEmpty()) continue;
                anyNonEmpty = true;
                if (t.equals("*/*") || t.startsWith("image/")) return true;
                if (t.equals(".jpg") || t.equals(".jpeg") || t.equals(".png") || t.equals(".webp") || t.equals(".gif")) {
                    return true;
                }
            }
        }
        return !anyNonEmpty;
    }
}
