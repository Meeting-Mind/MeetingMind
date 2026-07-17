package com.meetingmind.auth.runtime;

interface GoogleCredentialVerifier {

    AuthModels.GoogleUser verify(String credential);
}
