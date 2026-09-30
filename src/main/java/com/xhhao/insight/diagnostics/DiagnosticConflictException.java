package com.xhhao.insight.diagnostics;

/** User-actionable, deliberately safe message; never wrap an arbitrary exception message. */
public class DiagnosticConflictException extends RuntimeException {
    public DiagnosticConflictException(String message) { super(message); }
}
