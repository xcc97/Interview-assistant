package com.interviewassistant.server.dto;

import jakarta.validation.constraints.NotBlank;

public class RegisterRequest {
    @NotBlank(message = "phone 不能为空")
    private String phone;

    @NotBlank(message = "password 不能为空")
    private String password;

    private String nickname;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
