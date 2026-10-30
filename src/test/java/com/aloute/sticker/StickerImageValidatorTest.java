package com.aloute.sticker;

import com.aloute.exception.admin.InvalidAdminActionException;
import com.aloute.service.sticker.StickerImageValidator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import static org.assertj.core.api.Assertions.*;

class StickerImageValidatorTest {
    private byte[] fixture(String name) throws Exception { try(var in=getClass().getResourceAsStream("/sticker/"+name)){return in.readAllBytes();} }
    private MockMultipartFile file(String name,byte[] bytes) { return new MockMultipartFile("files",name,"application/octet-stream",bytes); }
    @Test void acceptsStaticPngAndWebpWithoutChangingPixelsOrAlpha() throws Exception {
        BufferedImage image=new BufferedImage(40,24,BufferedImage.TYPE_INT_ARGB);image.setRGB(10,10,0x80ff0000);
        var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);
        var validated=StickerImageValidator.validate(file("image.png",out.toByteArray()));
        assertThat(validated.bytes()).isEqualTo(out.toByteArray());assertThat(ImageIO.read(new ByteArrayInputStream(validated.bytes())).getRGB(10,10)).isEqualTo(0x80ff0000);
        var webp=StickerImageValidator.validate(file("image.webp",fixture("static.webp")));
        assertThat(webp.extension()).isEqualTo("webp");assertThat(webp.bytes()).isEqualTo(fixture("static.webp"));
    }
    @Test void rejectsAnimationSpoofedFormatsAndOversizeImages() throws Exception {
        for(String name:new String[]{"animated.webp","animated.png"}) {
            byte[] bytes=fixture(name);assertThatThrownBy(()->StickerImageValidator.validate(file(name,bytes))).isInstanceOf(InvalidAdminActionException.class).hasMessageContaining("ảnh động");
        }
        assertThatThrownBy(()->StickerImageValidator.validate(file("fake.png","not an image".getBytes()))).isInstanceOf(InvalidAdminActionException.class);
        assertThatThrownBy(()->StickerImageValidator.validate(file("large.png",new byte[StickerImageValidator.MAX_BYTES+1]))).isInstanceOf(InvalidAdminActionException.class).hasMessageContaining("10 MB");
        var wide=new BufferedImage(2049,1,BufferedImage.TYPE_INT_ARGB);var out=new ByteArrayOutputStream();ImageIO.write(wide,"png",out);
        assertThatThrownBy(()->StickerImageValidator.validate(file("wide.png",out.toByteArray()))).isInstanceOf(InvalidAdminActionException.class).hasMessageContaining("2048");
        assertThatThrownBy(()->StickerImageValidator.validate(file("broken.webp",new byte[]{'R','I','F','F',4,0,0,0,'W','E','B','P'}))).isInstanceOf(InvalidAdminActionException.class);
    }
    @Test void acceptsValidImageLargerThanPreviousLimit() throws Exception {
        var image = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_RGB);
        var random = new java.util.Random(42);
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, random.nextInt());
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        byte[] bytes = out.toByteArray();
        assertThat(bytes.length).isGreaterThan(2 * 1024 * 1024).isLessThan(StickerImageValidator.MAX_BYTES);
        assertThat(StickerImageValidator.validate(file("large.png", bytes)).bytes()).isEqualTo(bytes);
    }
}
