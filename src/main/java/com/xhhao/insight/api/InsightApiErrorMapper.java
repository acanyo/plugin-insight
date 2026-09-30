package com.xhhao.insight.api;

import com.xhhao.insight.api.export.ExportBusyException;
import com.xhhao.insight.api.export.ExportCapacityException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.springframework.http.HttpStatus;

/** Maps internal failures to stable API error codes without exposing exception messages. */
final class InsightApiErrorMapper {

    private InsightApiErrorMapper() {
    }

    static ApiError map(Throwable failure) {
        Throwable error = unwrap(failure);
        if (error instanceof com.xhhao.insight.diagnostics.DiagnosticConflictException conflict) {
            return new ApiError(HttpStatus.CONFLICT, "diagnostic_conflict", conflict.getMessage());
        }
        if (error instanceof IllegalArgumentException) {
            return new ApiError(HttpStatus.BAD_REQUEST, "bad_request", "请求参数无效");
        }
        if (error instanceof ExportBusyException) {
            return new ApiError(HttpStatus.TOO_MANY_REQUESTS, "export_busy",
                "监控数据导出任务正在执行，请稍后重试");
        }
        if (error instanceof ExportCapacityException) {
            return new ApiError(HttpStatus.INSUFFICIENT_STORAGE, "insufficient_storage",
                "可用磁盘空间不足，无法导出监控数据");
        }
        return new ApiError(HttpStatus.SERVICE_UNAVAILABLE, "service_unavailable",
            "监控服务暂时不可用");
    }

    static String failureType(Throwable failure) {
        return unwrap(failure).getClass().getSimpleName();
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
            && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    record ApiError(HttpStatus status, String code, String message) {
    }
}
