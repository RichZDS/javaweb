package com.richzds.quiz;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.SystemBarStyle;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.File;

/**
 * 整个 App 就这一个页面：全屏 WebView 打开线上网页版（地址见 BuildConfig.HOME_URL）。
 * 返回键先让网页后退；站外链接和下载交给系统浏览器；没网时显示本地的错误页和「重试」。
 */
public class MainActivity extends ComponentActivity {

    private static final String CAPTURE_DIR = "capture";
    private static final long CAPTURE_KEEP_MS = 24L * 60 * 60 * 1000;

    private WebView web;
    private View statusBarBg;
    private View navBarBg;
    private ProgressBar progress;
    private View errorView;
    private TextView errorTitle;
    private TextView errorDetail;

    private UrlPolicy policy;
    /** 这次主页面加载是否失败（失败时不让 onPageFinished 把错误页盖掉） */
    private boolean mainFrameFailed;

    private ValueCallback<Uri[]> fileCallback;
    private Uri captureUri;
    private File captureFile;

    private final ActivityResultLauncher<Intent> chooserLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::onChooserResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 全面屏：内容画到系统栏下面，再用下面 applyInsets 里的两条色块垫出状态栏和导航栏的位置
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        policy = new UrlPolicy(BuildConfig.HOME_URL);
        web = findViewById(R.id.web);
        statusBarBg = findViewById(R.id.status_bar_bg);
        navBarBg = findViewById(R.id.nav_bar_bg);
        progress = findViewById(R.id.progress);
        errorView = findViewById(R.id.error);
        errorTitle = findViewById(R.id.error_title);
        errorDetail = findViewById(R.id.error_detail);
        Button retry = findViewById(R.id.retry);
        retry.setOnClickListener(v -> retry());

        applyInsets();
        updateSystemBarIcons();
        setUpWebView();
        cleanOldCaptures();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (web != null && web.canGoBack()) {
                    web.goBack();
                    return;
                }
                // 网页退无可退（在首页）：交给系统，退出 App
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });

        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) {
            web.loadUrl(BuildConfig.HOME_URL);
        }
    }

    private void applyInsets() {
        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            setHeight(statusBarBg, bars.top);
            // 键盘弹出时底部垫键盘的高度，网页区域跟着变矮，输入框不会被挡住
            setHeight(navBarBg, Math.max(bars.bottom, ime.bottom));
            v.setPadding(bars.left, 0, bars.right, 0);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private static void setHeight(View view, int height) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp.height != height) {
            lp.height = height;
            view.setLayoutParams(lp);
        }
    }

    /** 状态栏是主色，用白色图标；导航栏跟随深浅色 */
    private void updateSystemBarIcons() {
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setAppearanceLightStatusBars(false);
        c.setAppearanceLightNavigationBars(!night);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 深浅色切换不重建页面，自己换导航栏和错误页的颜色
        navBarBg.setBackgroundColor(getColor(R.color.nav_bar));
        errorView.setBackgroundColor(getColor(R.color.page_bg));
        errorTitle.setTextColor(getColor(R.color.text_main));
        errorDetail.setTextColor(getColor(R.color.text_sub));
        updateSystemBarIcons();
    }

    private void setUpWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        // 登录令牌、设置都存在 localStorage，关掉 App 再开还在
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        // target=_blank 也在当前页面走 shouldOverrideUrlLoading，站外的交给浏览器
        s.setSupportMultipleWindows(false);
        // 字号用网页里「设置 → 字号」调，不跟系统字体缩放叠加（否则页面会挤乱）
        s.setTextZoom(100);
        s.setUserAgentString(WebShellRules.userAgent(s.getUserAgentString(), BuildConfig.VERSION_NAME));

        CookieManager.getInstance().setAcceptCookie(true);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);

        web.setWebViewClient(new ShellClient());
        web.setWebChromeClient(new ShellChrome());
        // 下载（如安装包）交给系统浏览器
        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, length) -> openOutside(Uri.parse(url)));
    }

    private void openOutside(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_SHORT).show();
        }
    }

    private void showWeb() {
        errorView.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);
    }

    private void showError(int titleRes, CharSequence detail) {
        progress.setVisibility(View.GONE);
        errorTitle.setText(titleRes);
        errorDetail.setText(detail);
        errorView.setVisibility(View.VISIBLE);
    }

    private void retry() {
        errorView.setVisibility(View.GONE);
        mainFrameFailed = false;
        String current = web.getUrl();
        if (current == null || policy.classify(current) != UrlPolicy.Target.APP) {
            web.loadUrl(BuildConfig.HOME_URL);
        } else {
            web.reload();
        }
    }

    private class ShellClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            switch (policy.classify(uri.toString())) {
                case APP:
                    return false;
                case BROWSER:
                    openOutside(uri);
                    return true;
                default:
                    return true;
            }
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            mainFrameFailed = false;
            progress.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            progress.setVisibility(View.GONE);
            // 第一次加载成功前 WebView 是隐藏的，露出的是启动图
            if (!mainFrameFailed) showWeb();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (!request.isForMainFrame()) return;
            mainFrameFailed = true;
            int code = error.getErrorCode();
            boolean network = code == ERROR_HOST_LOOKUP || code == ERROR_CONNECT || code == ERROR_TIMEOUT;
            showError(network ? R.string.error_network_title : R.string.error_load_title,
                    network ? getString(R.string.error_network_detail)
                            : getString(R.string.error_load_detail, String.valueOf(error.getDescription())));
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
            if (!request.isForMainFrame() || response.getStatusCode() < 500) return;
            mainFrameFailed = true;
            showError(R.string.error_load_title, getString(R.string.error_server_detail, response.getStatusCode()));
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            // 证书有问题一律不继续（不能 proceed），自己的站点出错时提示检查手机时间
            handler.cancel();
            if (policy.isSameSite(error.getUrl())) {
                mainFrameFailed = true;
                showError(R.string.error_ssl_title, getString(R.string.error_ssl_detail));
            }
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            // 网页渲染进程被系统杀掉：扔掉这个 WebView，重建页面，不让整个 App 崩溃
            ViewGroup parent = (ViewGroup) view.getParent();
            if (parent != null) parent.removeView(view);
            view.destroy();
            web = null;
            recreate();
            return true;
        }
    }

    private class ShellChrome extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progress.setProgress(newProgress);
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            // 上一次没收尾的先取消，否则网页里的文件框会卡住
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;

            Intent camera = WebShellRules.acceptsImages(params.getAcceptTypes()) ? cameraIntent() : null;
            Intent launch;
            if (camera != null && params.isCaptureEnabled()) {
                launch = camera;
            } else {
                launch = Intent.createChooser(params.createIntent(), getString(R.string.choose_image));
                if (camera != null) launch.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
            }
            try {
                chooserLauncher.launch(launch);
            } catch (ActivityNotFoundException e) {
                finishChooser(null);
                Toast.makeText(MainActivity.this, R.string.no_app_for_file, Toast.LENGTH_SHORT).show();
            }
            return true;
        }
    }

    /** 拍照入口：照片存到 cache/capture/，通过 FileProvider 交给系统相机写入；没有相机返回 null */
    private Intent cameraIntent() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) return null;
        File dir = new File(getCacheDir(), CAPTURE_DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) return null;
        captureFile = new File(dir, "photo-" + System.currentTimeMillis() + ".jpg");
        captureUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", captureFile);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, captureUri);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    private void onChooserResult(ActivityResult result) {
        Uri[] uris = WebChromeClient.FileChooserParams.parseResult(result.getResultCode(), result.getData());
        // 相机拍完通常不回 data，照片在我们给的文件里
        if ((uris == null || uris.length == 0) && result.getResultCode() == RESULT_OK
                && captureFile != null && captureFile.length() > 0) {
            uris = new Uri[]{captureUri};
        }
        finishChooser(uris);
    }

    private void finishChooser(Uri[] uris) {
        if (fileCallback != null) fileCallback.onReceiveValue(uris);
        fileCallback = null;
        captureUri = null;
        captureFile = null;
    }

    /** 删掉一天前拍的照片（上传完就没用了） */
    private void cleanOldCaptures() {
        File[] files = new File(getCacheDir(), CAPTURE_DIR).listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - CAPTURE_KEEP_MS;
        for (File f : files) {
            if (f.lastModified() < cutoff) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        // 把 Cookie 落盘，进程被杀也不丢
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            ((ViewGroup) web.getParent()).removeView(web);
            web.destroy();
            web = null;
        }
        if (fileCallback != null) finishChooser(null);
        super.onDestroy();
    }
}
