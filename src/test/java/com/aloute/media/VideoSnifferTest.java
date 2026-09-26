package com.aloute.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class VideoSnifferTest {

    private static byte[] mp4WithBrand(String brand) {
        byte[] data = new byte[32];
        data[3] = 0x18; // kích thước hộp ftyp
        data[4] = 'f';
        data[5] = 't';
        data[6] = 'y';
        data[7] = 'p';
        byte[] b = brand.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(b, 0, data, 8, 4);
        return data;
    }

    private static byte[] ebml(String docType) {
        byte[] head = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, (byte) 0x9F, 0x42, (byte) 0x82, (byte) 0x84};
        byte[] doc = docType.getBytes(StandardCharsets.ISO_8859_1);
        byte[] data = new byte[head.length + doc.length + 8];
        System.arraycopy(head, 0, data, 0, head.length);
        System.arraycopy(doc, 0, data, head.length, doc.length);
        return data;
    }

    @ParameterizedTest
    @ValueSource(strings = {"isom", "iso2", "mp41", "mp42", "avc1", "M4V ", "dash"})
    void recognizesCommonMp4Brands(String brand) {
        var type = VideoSniffer.sniff(mp4WithBrand(brand));

        assertThat(type).isPresent();
        assertThat(type.get().extension()).isEqualTo("mp4");
        assertThat(type.get().contentType()).isEqualTo("video/mp4");
    }

    @Test
    void rejectsIsoBoxesThatAreNotVideoLikeHeicPhotos() {
        assertThat(VideoSniffer.sniff(mp4WithBrand("heic"))).isEmpty();
        assertThat(VideoSniffer.sniff(mp4WithBrand("mif1"))).isEmpty();
        assertThat(VideoSniffer.sniff(mp4WithBrand("qt  "))).as("QuickTime/HEVC chưa hỗ trợ").isEmpty();
    }

    @Test
    void recognizesWebmButNotOtherMatroskaFiles() {
        var webm = VideoSniffer.sniff(ebml("webm"));

        assertThat(webm).isPresent();
        assertThat(webm.get().extension()).isEqualTo("webm");
        assertThat(webm.get().contentType()).isEqualTo("video/webm");
        assertThat(VideoSniffer.sniff(ebml("matroska"))).isEmpty();
    }

    @Test
    void rejectsTextHtmlAndTooShortInput() {
        assertThat(VideoSniffer.sniff("<html><body>video.mp4</body></html>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(VideoSniffer.sniff("ftypisom".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(VideoSniffer.sniff(new byte[3])).isEmpty();
        assertThat(VideoSniffer.sniff(new byte[0])).isEmpty();
    }

    @Test
    void rejectsAnImageEvenIfNamedLikeAVideo() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0, 0};

        assertThat(VideoSniffer.sniff(png)).isEmpty();
    }
}
