package com.busgo.media;

import static org.assertj.core.api.Assertions.*;
import com.busgo.common.exception.BusinessException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ImageUploadTest {
    @Test void pngAndJpegRequireMatchingDecodedFormatAndTerminator() throws Exception {
        for(String format:new String[]{"png","jpeg"}) {
            var out=new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),format,out);
            var bytes=out.toByteArray();
            assertThat(ImageUpload.read(file(format,bytes)).bytes()).isEqualTo(bytes);
            assertThatThrownBy(()->ImageUpload.read(file(format,Arrays.copyOf(bytes,bytes.length-2))))
                .isInstanceOf(BusinessException.class);
            assertThatThrownBy(()->ImageUpload.read(file(format,Arrays.copyOf(bytes,bytes.length+8))))
                .isInstanceOf(BusinessException.class);
        }
    }
    private MockMultipartFile file(String format,byte[] bytes) {
        return new MockMultipartFile("file","../not-a-storage-path."+format,"image/"+format,bytes);
    }
}
