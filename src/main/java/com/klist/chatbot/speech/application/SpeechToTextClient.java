package com.klist.chatbot.speech.application;

import java.time.Duration;

public interface SpeechToTextClient {

    String transcribe(SpeechAudio audio, Duration timeout);
}
