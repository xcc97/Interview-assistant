package com.interviewassistant.server.dto;

import java.util.List;

public class ReadinessResponse {
    private String status;
    private int passed;
    private int warning;
    private int failed;
    private List<ReadinessCheckItem> checks;

    public ReadinessResponse() {
    }

    public ReadinessResponse(String status, int passed, int warning, int failed, List<ReadinessCheckItem> checks) {
        this.status = status;
        this.passed = passed;
        this.warning = warning;
        this.failed = failed;
        this.checks = checks;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getPassed() {
        return passed;
    }

    public void setPassed(int passed) {
        this.passed = passed;
    }

    public int getWarning() {
        return warning;
    }

    public void setWarning(int warning) {
        this.warning = warning;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public List<ReadinessCheckItem> getChecks() {
        return checks;
    }

    public void setChecks(List<ReadinessCheckItem> checks) {
        this.checks = checks;
    }
}
