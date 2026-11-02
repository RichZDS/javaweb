package com.richzds.quiz.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.richzds.quiz.support.ApiTestBase;

/** 图片上传和 /uploads/** 访问。 */
class UploadApiTest extends ApiTestBase {

    /** 最小的合法 PNG 文件头 + 一些填充字节。 */
    private static byte[] png(int size) {
        byte[] b = new byte[size];
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(sig, 0, b, 0, sig.length);
        Arrays.fill(b, sig.length, size, (byte) 7);
        return b;
    }

    @Test
    void uploadImageAndReadItBack() throws Exception {
        Login u = register("upload1");
        byte[] content = png(1024);
        Resp r = call(upload("/api/files", u, "头像.PNG", "image/png", content));
        assertThat(r.status()).isEqualTo(201);
        String url = r.data().get("url").asText();
        assertThat(url).matches("/uploads/2026/11/02/[0-9a-f]{32}\\.png");

        MockHttpServletResponse file = mvc.perform(MockMvcRequestBuilders.get(url)).andReturn().getResponse();
        assertThat(file.getStatus()).as("匿名可读").isEqualTo(200);
        assertThat(file.getContentAsByteArray()).isEqualTo(content);

        // 其他格式按文件头识别
        byte[] gif = "GIF89a-------".getBytes();
        assertThat(call(upload("/api/files", u, "a.gif", "image/gif", gif)).data().get("url").asText()).endsWith(".gif");
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
        assertThat(call(upload("/api/files", u, "a.jpeg", "image/jpeg", jpg)).data().get("url").asText()).endsWith(".jpg");
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes();
        assertThat(call(upload("/api/files", u, "a.webp", "image/webp", webp)).data().get("url").asText())
                .endsWith(".webp");
    }

    @Test
    void rejectsLargeFakeAndMissingFiles() throws Exception {
        Login u = register("upload2");
        Resp big = call(upload("/api/files", u, "big.png", "image/png", png(2 * 1024 * 1024 + 1)));
        assertThat(big.status()).isEqualTo(400);
        assertThat(big.data().get("fields").get(0).get("field").asText()).isEqualTo("file");
        assertThat(call(upload("/api/files", u, "ok.png", "image/png", png(2 * 1024 * 1024))).status())
                .as("正好 2 MB 可以").isEqualTo(201);

        Resp fake = call(upload("/api/files", u, "evil.png", "image/png", "<script>alert(1)</script>".getBytes()));
        assertThat(fake.status()).as("扩展名和 Content-Type 都不算数，看文件头").isEqualTo(400);
        assertThat(call(upload("/api/files", u, "a.txt", "text/plain", "hello".getBytes())).status()).isEqualTo(400);
        assertThat(call(upload("/api/files", u, "empty.png", "image/png", new byte[0])).status()).isEqualTo(400);
        assertThat(call(post("/api/files", u, null)).status()).isEqualTo(400);
        assertThat(call(upload("/api/files", null, "a.png", "image/png", png(100))).status()).isEqualTo(401);
    }
}
