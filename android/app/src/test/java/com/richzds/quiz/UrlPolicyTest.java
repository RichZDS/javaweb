package com.richzds.quiz;

import static com.richzds.quiz.UrlPolicy.Target.APP;
import static com.richzds.quiz.UrlPolicy.Target.BLOCK;
import static com.richzds.quiz.UrlPolicy.Target.BROWSER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UrlPolicyTest {

    private final UrlPolicy policy = new UrlPolicy("https://43.142.166.108/quiz/");

    @Test
    public void 网页版里的页面留在App里() {
        assertEquals(APP, policy.classify("https://43.142.166.108/quiz/"));
        assertEquals(APP, policy.classify("https://43.142.166.108/quiz"));
        assertEquals(APP, policy.classify("https://43.142.166.108/quiz/#/pages/me/index"));
        assertEquals(APP, policy.classify("https://43.142.166.108/quiz/index.html?x=1#/pages/login/login"));
        assertEquals(APP, policy.classify("https://43.142.166.108:443/quiz/"));
        assertEquals(APP, policy.classify("HTTPS://43.142.166.108/quiz/"));
        assertEquals(APP, policy.classify("https://43.142.166.108/quiz/uploads/avatar/1.png"));
    }

    @Test
    public void 下载页安装包和管理后台交给浏览器() {
        assertEquals(BROWSER, policy.classify("https://43.142.166.108/quiz/download/"));
        assertEquals(BROWSER, policy.classify("https://43.142.166.108/quiz/download/quiz.apk"));
        assertEquals(BROWSER, policy.classify("https://43.142.166.108/quiz/download"));
        assertEquals(BROWSER, policy.classify("https://43.142.166.108/quiz/quiz-1.0.1.apk"));
        assertEquals(BROWSER, policy.classify("https://43.142.166.108/quiz/admin/"));
        assertEquals(BROWSER, policy.classify("https://43.142.166.108/quiz/admin"));
    }

    @Test
    public void 别的网站和同主机的其他路径交给浏览器() {
        assertEquals(BROWSER, policy.classify("https://www.example.com/quiz/"));
        assertEquals(BROWSER, policy.classify("https://43.142.166.108/"));
        assertEquals(BROWSER, policy.classify("https://43.142.166.108/quizzes/"));
        assertEquals(BROWSER, policy.classify("https://43.142.166.108/other/"));
        // 协议或端口不同就不是同一个站点
        assertEquals(BROWSER, policy.classify("http://43.142.166.108/quiz/"));
        assertEquals(BROWSER, policy.classify("https://43.142.166.108:8443/quiz/"));
        // 用户信息骗不过去：真正的主机是 evil.example
        assertEquals(BROWSER, policy.classify("https://43.142.166.108@evil.example/quiz/"));
        // ../ 先规范化再判断
        assertEquals(BROWSER, policy.classify("https://43.142.166.108/quiz/../other/"));
        assertEquals(BROWSER, policy.classify("https://43.142.166.108/quiz/./download/quiz.apk"));
    }

    @Test
    public void 电话邮件短信交给系统() {
        assertEquals(BROWSER, policy.classify("tel:10086"));
        assertEquals(BROWSER, policy.classify("mailto:someone@example.com"));
        assertEquals(BROWSER, policy.classify("sms:10086"));
    }

    @Test
    public void 危险或不认识的链接不处理() {
        assertEquals(BLOCK, policy.classify("javascript:alert(1)"));
        assertEquals(BLOCK, policy.classify("intent://scan/#Intent;scheme=zxing;end"));
        assertEquals(BLOCK, policy.classify("file:///sdcard/a.html"));
        assertEquals(BLOCK, policy.classify("content://com.example/x"));
        assertEquals(BLOCK, policy.classify("data:text/html,hi"));
        assertEquals(BLOCK, policy.classify("not a url"));
        assertEquals(BLOCK, policy.classify("/quiz/relative"));
        assertEquals(BLOCK, policy.classify(null));
    }

    @Test
    public void 同一站点判断用于证书错误() {
        assertTrue(policy.isSameSite("https://43.142.166.108/quiz/api/me"));
        assertTrue(policy.isSameSite("https://43.142.166.108/anything"));
        assertFalse(policy.isSameSite("https://cdn.example.com/a.js"));
        assertFalse(policy.isSameSite(null));
    }

    @Test
    public void 换一个首页地址也能用() {
        UrlPolicy other = new UrlPolicy("https://quiz.example.com/app");
        assertEquals(APP, other.classify("https://quiz.example.com/app/#/pages/me/index"));
        assertEquals(APP, other.classify("https://quiz.example.com/app"));
        assertEquals(BROWSER, other.classify("https://quiz.example.com/application"));
        assertEquals(BROWSER, other.classify("https://quiz.example.com/app/download/quiz.apk"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void 首页地址必须是网址() {
        new UrlPolicy("file:///android_asset/index.html");
    }
}
