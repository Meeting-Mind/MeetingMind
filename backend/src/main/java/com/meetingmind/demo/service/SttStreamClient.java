package com.meetingmind.demo.service;

public interface SttStreamClient extends AutoCloseable {

    void sendAudio(byte[] pcm16leMono16k);

    void finishAudio();

    @Override
    void close();
}
