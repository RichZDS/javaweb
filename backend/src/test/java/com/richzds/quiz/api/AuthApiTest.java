package com.richzds.quiz.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.richzds.quiz.entity.Category;
import com.richzds.quiz.support.ApiTestBase;

/** 账号、登录、令牌和权限。 */
class AuthApiTest extends ApiTestBase {

    @Test
    void registerReturns201WithTokenAndUser() throws Exception {
        Resp r = call(post("/api/auth/register", null,
                Map.of("username", "alice_01", "password", "secret123", "nickname", "爱丽丝")));
        assertThat(r.status()).isEqualTo(201);
        JsonNode data = r.data();
        assertThat(data.get("token").asText()).isNotBlank();
        assertThat(data.get("expiresAt").asText()).isEqualTo("2026-11-09T08:00:00.000Z");
        JsonNode user = data.get("user");
        assertThat(user.get("username").asText()).isEqualTo("alice_01");
        assertThat(user.get("nickname").asText()).isEqualTo("爱丽丝");
        assertThat(user.get("role").asText()).isEqualTo("USER");
        assertThat(user.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(user.get("createdAt").asText()).isEqualTo("2026-11-02T08:00:00.000Z");
        assertThat(user.has("targetCategoryId")).isTrue();
        assertThat(user.has("passwordHash")).isFalse();
    }

    @Test
    void duplicateUsernameIs409() throws Exception {
        register("bob");
        Resp r = call(post("/api/auth/register", null, Map.of("username", "bob", "password", "secret123")));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("USERNAME_TAKEN");
    }

    @Test
    void invalidRegistrationIs400WithFields() throws Exception {
        Resp r = call(post("/api/auth/register", null, Map.of("username", "a!", "password", "123")));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(r.data().get("fields").findValuesAsText("field")).contains("username", "password");
    }

    @Test
    void loginAndWrongPassword() throws Exception {
        register("carol");
        Resp ok = call(post("/api/auth/login", null, Map.of("username", "carol", "password", "secret123")));
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.data().get("token").asText()).isNotBlank();

        Resp bad = call(post("/api/auth/login", null, Map.of("username", "carol", "password", "wrong-pass")));
        assertThat(bad.status()).isEqualTo(401);
        assertThat(bad.code()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void protectedEndpointsNeedAValidToken() throws Exception {
        Resp none = call(get("/api/me", null));
        assertThat(none.status()).isEqualTo(401);
        assertThat(none.code()).isEqualTo("UNAUTHORIZED");

        Resp garbage = call(MockMvcRequestBuilders.get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def"));
        assertThat(garbage.status()).isEqualTo(401);
    }

    @Test
    void tokenExpiresAfterSevenDays() throws Exception {
        Login dave = register("dave");
        clock.advance(Duration.ofDays(7).minusMinutes(1));
        assertThat(call(get("/api/me", dave)).status()).isEqualTo(200);
        clock.advance(Duration.ofMinutes(2));
        assertThat(call(get("/api/me", dave)).status()).isEqualTo(401);
    }

    @Test
    void normalUserCannotUseAdminApi() throws Exception {
        Login user = register("eve");
        Resp r = call(get("/api/admin/categories", user));
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.code()).isEqualTo("FORBIDDEN");
        assertThat(call(get("/api/admin/categories", null)).status()).isEqualTo(401);
    }

    @Test
    void adminPassesTheAdminGate() throws Exception {
        Login admin = admin();
        assertThat(call(get("/api/me", admin)).data().get("role").asText()).isEqualTo("ADMIN");
        int status = call(get("/api/admin/categories", admin)).status();
        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void disabledUserIsRejectedOnEveryRequest() throws Exception {
        Login frank = register("frank");
        assertThat(call(get("/api/me", frank)).status()).isEqualTo(200);
        jdbc.update("UPDATE app_user SET status = 'DISABLED' WHERE id = ?", frank.id());

        Resp r = call(get("/api/me", frank));
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.code()).isEqualTo("FORBIDDEN");
        Resp login = call(post("/api/auth/login", null, Map.of("username", "frank", "password", "secret123")));
        assertThat(login.status()).isEqualTo(403);
    }

    @Test
    void updateProfileAndTargetMustBeLevelOne() throws Exception {
        Login gina = register("gina");
        Category[] t = tree("考研 408");

        Resp bad = call(put("/api/me", gina, Map.of("targetCategoryId", t[1].getId())));
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.data().get("fields").get(0).get("field").asText()).isEqualTo("targetCategoryId");

        JsonNode me = data(call(put("/api/me", gina,
                Map.of("nickname", "小吉", "avatarUrl", "/uploads/a.png", "targetCategoryId", t[0].getId()))));
        assertThat(me.get("nickname").asText()).isEqualTo("小吉");
        assertThat(me.get("avatarUrl").asText()).isEqualTo("/uploads/a.png");
        assertThat(me.get("targetCategoryId").asLong()).isEqualTo(t[0].getId());
        assertThat(me.get("targetCategoryName").asText()).isEqualTo("考研 408");
        assertThat(data(call(get("/api/me", gina))).get("targetCategoryName").asText()).isEqualTo("考研 408");
    }

    @Test
    void changePassword() throws Exception {
        Login hank = register("hank");
        Resp wrong = call(put("/api/me/password", hank, Map.of("oldPassword", "nope-nope", "newPassword", "newpass1")));
        assertThat(wrong.status()).isEqualTo(400);

        Resp ok = call(put("/api/me/password", hank, Map.of("oldPassword", "secret123", "newPassword", "newpass1")));
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.data().isNull()).isTrue();
        assertThat(call(post("/api/auth/login", null, Map.of("username", "hank", "password", "newpass1"))).status())
                .isEqualTo(200);
        assertThat(call(post("/api/auth/login", null, Map.of("username", "hank", "password", "secret123"))).status())
                .isEqualTo(401);
    }

    @Test
    void wrongRemoveThresholdSetting() throws Exception {
        Login jill = register("jill");
        assertThat(data(call(get("/api/me", jill))).get("wrongRemoveThreshold").asInt()).isEqualTo(3);

        JsonNode me = data(call(put("/api/me/settings", jill, Map.of("wrongRemoveThreshold", 5))));
        assertThat(me.get("wrongRemoveThreshold").asInt()).isEqualTo(5);
        assertThat(data(call(get("/api/me", jill))).get("wrongRemoveThreshold").asInt()).isEqualTo(5);

        assertThat(call(put("/api/me/settings", jill, Map.of("wrongRemoveThreshold", 0))).status()).isEqualTo(400);
        assertThat(call(put("/api/me/settings", jill, Map.of("wrongRemoveThreshold", 11))).status()).isEqualTo(400);
        assertThat(call(put("/api/me/settings", jill, Map.of())).status()).isEqualTo(400);
    }

    @Test
    void appVersionAndApiDocsAreAnonymous() throws Exception {
        Resp v = call(get("/api/app/version?platform=android", null));
        assertThat(v.status()).isEqualTo(200);
        assertThat(v.data().get("platform").asText()).isEqualTo("android");
        assertThat(v.data().get("latestVersion").asText()).isEqualTo("1.0.0");
        assertThat(v.data().has("downloadUrl")).isTrue();

        assertThat(call(get("/api/app/version?platform=symbian", null)).status()).isEqualTo(400);
        assertThat(call(get("/v3/api-docs", null)).status()).isEqualTo(200);
    }

    @Test
    void unknownApiPathIs404ForLoggedInUser() throws Exception {
        Login ivy = register("ivy");
        Resp r = call(get("/api/does-not-exist", ivy));
        assertThat(r.status()).isEqualTo(404);
        assertThat(r.code()).isEqualTo("NOT_FOUND");
    }
}
