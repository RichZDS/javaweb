package com.richzds.quiz;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WebShellRulesTest {

    private static final String SYSTEM_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/141.0.0.0 Mobile Safari/537.36";

    @Test
    public void UA末尾加上App标记() {
        assertEquals(SYSTEM_UA + " MaShangShuaTiApp/1.0.0", WebShellRules.userAgent(SYSTEM_UA, "1.0.0"));
    }

    @Test
    public void 已有标记时换成新版本不重复加() {
        String once = WebShellRules.userAgent(SYSTEM_UA, "1.0.0");
        assertEquals(SYSTEM_UA + " MaShangShuaTiApp/1.0.1", WebShellRules.userAgent(once, "1.0.1"));
    }

    @Test
    public void 系统UA为空时只有标记() {
        assertEquals("MaShangShuaTiApp/1.0.0", WebShellRules.userAgent(null, "1.0.0"));
        assertEquals("MaShangShuaTiApp/1.0.0", WebShellRules.userAgent("  ", "1.0.0"));
    }

    @Test
    public void 收图片的文件框给拍照入口() {
        assertTrue(WebShellRules.acceptsImages(new String[]{"image/*"}));
        assertTrue(WebShellRules.acceptsImages(new String[]{"image/png", "image/jpeg"}));
        assertTrue(WebShellRules.acceptsImages(new String[]{".jpg,.png"}));
        assertTrue(WebShellRules.acceptsImages(new String[]{"*/*"}));
        // 没写 accept：什么文件都收，也给拍照
        assertTrue(WebShellRules.acceptsImages(null));
        assertTrue(WebShellRules.acceptsImages(new String[]{}));
        assertTrue(WebShellRules.acceptsImages(new String[]{""}));
    }

    @Test
    public void 不收图片的文件框不给拍照() {
        assertFalse(WebShellRules.acceptsImages(new String[]{"application/pdf"}));
        assertFalse(WebShellRules.acceptsImages(new String[]{".xlsx", "text/csv"}));
    }
}
