package com.jiugeng.erp.watch.collector;

import java.util.List;

public record IntegrityResult(Status status, List<Check> checks) {
    public IntegrityResult {
        checks = List.copyOf(checks);
    }

    public enum Status { COMPLETE, INCOMPLETE, FAILED }

    public record Check(String code, boolean passed, String actual) {
    }
}
