package com.fourohfour.backend.packages.kakao;

public interface KakaoProfileClient {

    KakaoUserProfile fetchProfile(String authorizationCode);
}

