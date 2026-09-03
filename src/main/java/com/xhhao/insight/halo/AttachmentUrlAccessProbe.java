package com.xhhao.insight.halo;

import java.net.URI;
import reactor.core.publisher.Mono;

/** Performs a read-only reachability check for an attachment access URL. */
public interface AttachmentUrlAccessProbe {

    Mono<Result> probe(URI uri);

    record Result(Outcome outcome, String detail) {

        public static Result reachable(String detail) {
            return new Result(Outcome.REACHABLE, detail);
        }

        public static Result inconclusive(String detail) {
            return new Result(Outcome.INCONCLUSIVE, detail);
        }

        public static Result unreachable(String detail) {
            return new Result(Outcome.UNREACHABLE, detail);
        }
    }

    enum Outcome {
        REACHABLE,
        INCONCLUSIVE,
        UNREACHABLE
    }
}
