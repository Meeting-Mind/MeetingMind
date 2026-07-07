package com.meetingmind.demo.auth;

interface GoogleCredentialVerifier {
    GoogleUserInfo verify(String credential);
}
