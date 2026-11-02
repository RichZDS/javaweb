package com.richzds.quiz.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.richzds.quiz.admin.AdminSystemService;
import com.richzds.quiz.support.ApiTestBase;

/**
 * 管理员禁用账号的同时，这个人正好在改资料或改密码：改资料只写本次改的列，禁用不会被写回成启用。
 * 时序是确定的：禁用先写进去但不提交（拿着这一行的锁），再发用户的请求（鉴权读到的还是启用），
 * 等它确实卡在这一行的锁上，才提交禁用。H2 和 MySQL（CI 的 backend-mysql）都能跑。
 */
class AccountRaceApiTest extends ApiTestBase {

    @Autowired
    AdminSystemService adminSystemService;
    @Autowired
    PlatformTransactionManager txManager;

    @Test
    void profileUpdateDuringDisableKeepsTheAccountDisabled() throws Exception {
        Login admin = admin();
        Login u = register("race_me");
        Resp r = raceWithDisable(admin, u, () -> call(put("/api/me", u, Map.of("nickname", "新昵称"))));
        assertThat(r.status()).isEqualTo(200);
        assertThat(column(u, "status")).as("禁用不能被改资料写回成启用").isEqualTo("DISABLED");
        assertThat(column(u, "nickname")).as("改资料本身照常生效").isEqualTo("新昵称");
    }

    @Test
    void passwordChangeDuringDisableKeepsTheAccountDisabled() throws Exception {
        Login admin = admin();
        Login u = register("race_pw");
        String before = column(u, "password_hash");
        Resp r = raceWithDisable(admin, u, () -> call(put("/api/me/password", u,
                Map.of("oldPassword", "secret123", "newPassword", "newsecret456"))));
        assertThat(r.status()).isEqualTo(200);
        assertThat(column(u, "status")).as("禁用不能被改密码写回成启用").isEqualTo("DISABLED");
        assertThat(column(u, "password_hash")).as("密码照常改了").isNotEqualTo(before);
    }

    private Resp raceWithDisable(Login admin, Login u, Callable<Resp> request) throws Exception {
        // 先刷新 last_active_at：否则鉴权过滤器会去写这一行，在过滤器里就等锁，测不到改资料本身
        call(get("/api/me", u));
        CountDownLatch disabled = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> adminTx = pool.submit(() -> new TransactionTemplate(txManager).executeWithoutResult(s -> {
                adminSystemService.changeUserStatus(admin.id(), u.id(), "DISABLED");
                disabled.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(disabled.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Resp> userRequest = pool.submit(request);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!someoneWaitsOnRowLock() && !userRequest.isDone()) {
                assertThat(System.nanoTime()).as("等用户请求卡在锁上超时").isLessThan(deadline);
                Thread.sleep(10);
            }
            assertThat(userRequest.isDone()).as("用户请求应当卡在这一行的锁上，而不是已经结束").isFalse();
            release.countDown();
            adminTx.get(10, TimeUnit.SECONDS);
            return userRequest.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private boolean someoneWaitsOnRowLock() {
        String db = jdbc.execute((ConnectionCallback<String>) c -> c.getMetaData().getDatabaseProductName());
        String sql = db != null && db.contains("H2")
                ? "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID IS NOT NULL"
                : "SELECT COUNT(*) FROM information_schema.INNODB_TRX WHERE trx_state = 'LOCK WAIT'";
        Long n = jdbc.queryForObject(sql, Long.class);
        return n != null && n > 0;
    }

    private String column(Login u, String name) {
        return jdbc.queryForObject("SELECT " + name + " FROM app_user WHERE id = ?", String.class, u.id());
    }
}
