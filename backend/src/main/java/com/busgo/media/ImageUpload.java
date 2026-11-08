package com.busgo.media;

import com.busgo.common.exception.BusinessException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

record ImageUpload(String mime, byte[] bytes) {
    static final int LIMIT = 5 * 1024 * 1024;
    static BusinessException invalid(String message) {
        return new BusinessException("INVALID_MEDIA", message, HttpStatus.BAD_REQUEST, null);
    }
    static ImageUpload read(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > LIMIT) throw invalid("Ảnh phải có dung lượng từ 1 byte đến 5 MiB.");
        String name = String.valueOf(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        try (var input = file.getInputStream()) {
            byte[] data = input.readNBytes(LIMIT + 1);
            if (data.length != file.getSize() || data.length > LIMIT) throw invalid("Tệp ảnh chưa đầy đủ.");
            String mime = file.getContentType();
            if ("image/webp".equals(mime) && name.endsWith(".webp") && validWebp(data)) return new ImageUpload(mime,data);
            boolean png = "image/png".equals(mime) && name.endsWith(".png");
            boolean jpeg = "image/jpeg".equals(mime) && (name.endsWith(".jpg") || name.endsWith(".jpeg"));
            // Refuse truncated images and bytes appended after the image terminator.
            boolean completePng = png && data.length >= 12 && tag(data,data.length-8,"IEND")
                    && ByteBuffer.wrap(data).getInt(data.length-4)==0xae426082;
            boolean completeJpeg = jpeg && data.length >= 4 && data[data.length-2]==(byte)0xff
                    && data[data.length-1]==(byte)0xd9;
            if (completePng || completeJpeg) {
                try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
                    var readers = ImageIO.getImageReaders(stream);
                    if (readers.hasNext()) {
                        var reader = readers.next();
                        try {
                            reader.setInput(stream);
                            if (reader.getFormatName().equalsIgnoreCase(png ? "PNG" : "JPEG")
                                    && (long)reader.getWidth(0) * reader.getHeight(0) <= 16_000_000
                                    && reader.read(0) != null) return new ImageUpload(mime,data);
                        } finally { reader.dispose(); }
                    }
                }
            }
        } catch (IOException ex) { throw invalid("Không thể đọc ảnh."); }
        throw invalid("Nội dung ảnh phải khớp với định dạng PNG, JPEG hoặc WebP.");
    }
    // Validate the RIFF envelope, chunk boundaries and VP8/VP8L frame header.
    private static boolean validWebp(byte[] data) {
        if (data.length < 26) return false;
        var buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (!tag(data,0,"RIFF") || buf.getInt(4) != data.length-8 || !tag(data,8,"WEBP")) return false;
        boolean frame = false;
        int p = 12;
        while (p+8 <= data.length) {
            int n = buf.getInt(p+4), start = p+8;
            if (n < 0 || n > data.length-start) return false;
            if (tag(data,p,"VP8 ")) {
                if (frame || n <= 10 || data[start+3] != (byte)0x9d || data[start+4]!=1 || data[start+5]!=42) return false;
                int width = buf.getShort(start+6)&0x3fff, height = buf.getShort(start+8)&0x3fff;
                if (width==0 || height==0 || (long)width*height>16_000_000) return false;
                frame = true;
            } else if (tag(data,p,"VP8L")) {
                if (frame || n <= 5 || data[start]!=0x2f) return false;
                int bits=buf.getInt(start+1);
                if ((bits>>>29)!=0 || (long)((bits&16383)+1)*(((bits>>>14)&16383)+1)>16_000_000) return false;
                frame = true;
            } else if (tag(data,p,"ANIM") || tag(data,p,"ANMF")) return false;
            p=start+n+(n&1);
        }
        return frame && p==data.length;
    }
    private static boolean tag(byte[] data,int offset,String value) {
        return new String(data,offset,4,StandardCharsets.US_ASCII).equals(value);
    }
}
