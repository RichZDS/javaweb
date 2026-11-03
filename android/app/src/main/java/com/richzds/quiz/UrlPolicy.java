package com.richzds.quiz;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * 决定一个链接在哪里打开。纯 Java，不依赖 Android，单元测试直接跑。
 *
 * <ul>
 *   <li>{@link Target#APP}：同一个站点（协议、主机、端口都一样）且在网页版路径下，留在 App 里；</li>
 *   <li>{@link Target#BROWSER}：别的网站、下载页和安装包、管理后台，以及 tel:/mailto:/sms:，交给系统；</li>
 *   <li>{@link Target#BLOCK}：javascript:、intent:、file:、content: 等，什么都不做。</li>
 * </ul>
 */
public final class UrlPolicy {

    public enum Target { APP, BROWSER, BLOCK }

    private final String scheme;
    private final String host;
    private final int port;
    /** 网页版的路径前缀，以 / 结尾，如 /quiz/ */
    private final String basePath;

    public UrlPolicy(String homeUrl) {
        URI home = URI.create(homeUrl).normalize();
        this.scheme = lower(home.getScheme());
        this.host = lower(home.getHost());
        this.port = effectivePort(this.scheme, home.getPort());
        String path = home.getRawPath() == null || home.getRawPath().isEmpty() ? "/" : home.getRawPath();
        this.basePath = path.endsWith("/") ? path : path + "/";
        if ((!"https".equals(scheme) && !"http".equals(scheme)) || host == null) {
            throw new IllegalArgumentException("首页地址必须是 http(s) 网址：" + homeUrl);
        }
    }

    public Target classify(String url) {
        if (url == null) return Target.BLOCK;
        URI u;
        try {
            u = new URI(url.trim()).normalize();
        } catch (URISyntaxException e) {
            return Target.BLOCK;
        }
        String s = lower(u.getScheme());
        if (s == null) return Target.BLOCK;
        switch (s) {
            case "http":
            case "https":
                return sameOrigin(u) && inApp(u.getRawPath()) ? Target.APP : Target.BROWSER;
            case "tel":
            case "mailto":
            case "sms":
                return Target.BROWSER;
            default:
                return Target.BLOCK;
        }
    }

    /** 和网页版同一个站点（协议、主机、端口都相同）；SSL 出错时用它判断是不是我们自己的站点 */
    public boolean isSameSite(String url) {
        if (url == null) return false;
        try {
            return sameOrigin(new URI(url.trim()));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private boolean sameOrigin(URI u) {
        String s = lower(u.getScheme());
        return scheme.equals(s) && host.equals(lower(u.getHost())) && port == effectivePort(s, u.getPort());
    }

    /** 在网页版路径下，但下载页（安装包）和管理后台除外 */
    private boolean inApp(String rawPath) {
        String path = rawPath == null || rawPath.isEmpty() ? "/" : rawPath;
        String base = basePath.substring(0, basePath.length() - 1); // 不带结尾斜杠，/quiz
        if (!path.equals(base) && !path.startsWith(basePath)) return false;
        String rest = path.length() > basePath.length() ? path.substring(basePath.length()) : "";
        if (rest.equals("download") || rest.startsWith("download/")) return false;
        if (rest.equals("admin") || rest.startsWith("admin/")) return false;
        return !rest.toLowerCase(Locale.ROOT).endsWith(".apk");
    }

    private static int effectivePort(String scheme, int port) {
        if (port != -1) return port;
        if ("https".equals(scheme)) return 443;
        if ("http".equals(scheme)) return 80;
        return -1;
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }
}
