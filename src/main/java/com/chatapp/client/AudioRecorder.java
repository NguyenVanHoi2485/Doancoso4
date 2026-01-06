package com.chatapp.client;

import javax.sound.sampled.*;
import java.io.*;

public class AudioRecorder {
    private TargetDataLine targetLine;
    private File audioFile;

    // Định dạng âm thanh chuẩn (WAV)
    private AudioFormat getAudioFormat() {
        float sampleRate = 16000; // 16kHz (Đủ rõ cho giọng nói, dung lượng vừa phải)
        int sampleSizeInBits = 16;
        int channels = 1; // Mono (1 kênh)
        boolean signed = true;
        boolean bigEndian = false;
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }

    // Bắt đầu ghi âm
    public void startRecording(String fileName) {
        try {
            AudioFormat format = getAudioFormat();
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("Microphone not supported");
                return;
            }

            targetLine = (TargetDataLine) AudioSystem.getLine(info);
            targetLine.open(format);
            targetLine.start();

            audioFile = new File(fileName);

            // Chạy luồng ghi âm riêng để không chặn giao diện
            new Thread(() -> {
                try (AudioInputStream ais = new AudioInputStream(targetLine)) {
                    AudioSystem.write(ais, AudioFileFormat.Type.WAVE, audioFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    // Dừng ghi âm và trả về file đã ghi
    public File stopRecording() {
        if (targetLine != null) {
            targetLine.stop();
            targetLine.close();
        }
        return audioFile;
    }

    // Phát âm thanh từ file
    public static void playAudio(File file) {
        new Thread(() -> {
            try (AudioInputStream audioStream = AudioSystem.getAudioInputStream(file)) {
                Clip clip = AudioSystem.getClip();
                clip.open(audioStream);
                clip.start();
                // Chờ phát xong thì đóng (tùy chọn)
                Thread.sleep(clip.getMicrosecondLength() / 1000);
            } catch (Exception e) {
                System.err.println("Lỗi phát âm thanh: " + e.getMessage());
            }
        }).start();
    }
}