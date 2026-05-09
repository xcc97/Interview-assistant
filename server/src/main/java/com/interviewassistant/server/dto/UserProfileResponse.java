package com.interviewassistant.server.dto;

public class UserProfileResponse {
    private String userId;
    private String phone;
    private String nickname;
    private String status;
    private String role;
    private String currentPlanName;
    private int remainingMinutes;
    private int remainingSeconds;
    private int usedMinutes;
    private int usedSeconds;
    private String expiryTime;

    public UserProfileResponse() {
    }

    public UserProfileResponse(String userId, String phone, String nickname, String status, String role,
                               String currentPlanName, int remainingMinutes, int remainingSeconds,
                               int usedMinutes, int usedSeconds, String expiryTime) {
        this.userId = userId;
        this.phone = phone;
        this.nickname = nickname;
        this.status = status;
        this.role = role;
        this.currentPlanName = currentPlanName;
        this.remainingMinutes = remainingMinutes;
        this.remainingSeconds = remainingSeconds;
        this.usedMinutes = usedMinutes;
        this.usedSeconds = usedSeconds;
        this.expiryTime = expiryTime;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getCurrentPlanName() {
        return currentPlanName;
    }

    public void setCurrentPlanName(String currentPlanName) {
        this.currentPlanName = currentPlanName;
    }

    public int getRemainingMinutes() {
        return remainingMinutes;
    }

    public void setRemainingMinutes(int remainingMinutes) {
        this.remainingMinutes = remainingMinutes;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(int remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }

    public int getUsedMinutes() {
        return usedMinutes;
    }

    public void setUsedMinutes(int usedMinutes) {
        this.usedMinutes = usedMinutes;
    }

    public int getUsedSeconds() {
        return usedSeconds;
    }

    public void setUsedSeconds(int usedSeconds) {
        this.usedSeconds = usedSeconds;
    }

    public String getExpiryTime() {
        return expiryTime;
    }

    public void setExpiryTime(String expiryTime) {
        this.expiryTime = expiryTime;
    }
}
