package com.caestro.server.domain.signaling.service;

import com.caestro.server.global.exception.CustomException;
import com.caestro.server.global.exception.error.ErrorCode;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * 세션 DB 기록을 실시간 경로에서 분리하는 디스패처 (#99 → #124 개편).
 * 기록이 멱등 upsert가 되면서(#124) 도착 순서가 무의미해졌으므로 순서 장치(세션코드 해시 →
 * 고정 워커)를 철거했다 — 애초에 멀티 인스턴스 크로스 기록에는 미치지 못하던 보장이었다.
 * 큐의 역할은 "비동기 버퍼(WS 스레드는 DB를 기다리지 않음, #99 실측 병목) + 신뢰성(일시 오류
 * 재시도)"이다. 경계 유지: 워커 수 ≤ Hikari 풀(10), 유한 큐(포화 시 드롭+지표).
 * SQS 등 외부 큐는 "프로세스 밖 내구성"이 필요해질 때(유실 불허 데이터 등장 또는 유실 실측)
 * 도입한다 — at-least-once 소비의 전제인 멱등 소비자는 #124로 준비되어 있다.
 */
@Slf4j
@Component
public class SessionRecordDispatcher {

    private static final int DEFAULT_WORKER_COUNT = 4;
    private static final int DEFAULT_QUEUE_CAPACITY = 1000;

    private final SignalingMetrics metrics;
    private final ThreadPoolExecutor executor;
    private final RetryConfig retryConfig;

    @Autowired // 생성자가 둘(운영/테스트용)이라 스프링이 쓸 것을 명시한다
    public SessionRecordDispatcher(SignalingMetrics metrics) {
        this(metrics, DEFAULT_WORKER_COUNT, DEFAULT_QUEUE_CAPACITY);
    }

    // 테스트에서 워커·큐 크기를 줄여 경계 동작을 검증할 수 있도록 분리
    SessionRecordDispatcher(SignalingMetrics metrics, int workerCount, int queueCapacity) {
        this.metrics = metrics;
        AtomicInteger threadSeq = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(workerCount, workerCount, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                r -> {
                    Thread t = new Thread(r, "session-record-" + threadSeq.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy());
        // 재실행이 안전한(멱등, #124) 오류만 재시도한다: 일시 DB 오류(데드락·커넥션), upsert 동시 생성
        // 경합(UNIQUE 위반 → 재실행이 update로 수렴), "세션 행이 아직 없음"(SESSION_NOT_FOUND =
        // create 기록이 다른 인스턴스에서 오는 중). 그 외 영구 오류는 즉시 실패로 격리한다.
        this.retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(200), 2.0))
                .retryOnException(e -> e instanceof TransientDataAccessException
                        || e instanceof DataAccessResourceFailureException
                        || e instanceof CannotCreateTransactionException
                        || e instanceof DataIntegrityViolationException
                        || (e instanceof CustomException ce && ce.getErrorCode() == ErrorCode.SESSION_NOT_FOUND))
                .build();
        metrics.bindRecordQueueDepth(() -> executor.getQueue().size());
    }

    /**
     * 세션 기록 작업을 큐에 넣고 즉시 반환한다.
     * 재시도까지 소진한 실패와 큐 포화 드롭은 격리해 시그널링 흐름에 영향을 주지 않고 지표로만 남긴다.
     *
     * @param sessionCode 로그·추적용 세션 코드 (#124부터는 라우팅 키가 아니다)
     * @param taskName    지표 라벨용 작업 이름 (create/join/spec/end)
     * @param task        DB 기록 작업 (멱등 — 재실행 안전)
     */
    public void dispatch(String sessionCode, String taskName, Runnable task) {
        try {
            executor.execute(() -> runWithRetry(sessionCode, taskName, task));
        } catch (RejectedExecutionException e) {
            metrics.countRecordDropped(taskName);
            log.warn("Session record dropped (queue full): task={}, session={}", taskName, sessionCode);
        }
    }

    private void runWithRetry(String sessionCode, String taskName, Runnable task) {
        Retry retry = Retry.of("record-" + taskName, retryConfig);
        retry.getEventPublisher().onRetry(event -> metrics.countRecordRetried(taskName));
        try {
            Retry.decorateRunnable(retry, task).run();
        } catch (Exception e) {
            metrics.countRecordFailed(taskName);
            log.error("Session record failed (isolated): task={}, session={}", taskName, sessionCode, e);
        }
    }

    /** 종료(롤링 배포) 시 대기 중인 기록을 마저 반영해 유실을 최소화한다. */
    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Session record queue not fully drained on shutdown: {} remain",
                        executor.getQueue().size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
