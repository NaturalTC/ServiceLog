package dev.servicelog.photo;

import dev.servicelog.exception.InvalidRequestException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

// Turns an uploaded photo into a clean JPEG of a sensible size, plus a thumbnail.
//
// Re-encoding drops everything except the pixels, including hidden metadata such as the
// GPS location a phone camera writes into its photos. The phone already shrinks photos
// and turns them upright before uploading; this is the server's own guarantee.
final class ImageProcessor {

    static final int FULL_SIZE = 2000;
    static final int THUMB_SIZE = 480;
    // Refuse images that would take huge memory to decode (a "decompression bomb")
    private static final long MAX_PIXELS = 50_000_000L;
    private static final float QUALITY = 0.85f;

    record Processed(byte[] full, int width, int height, byte[] thumb) {
    }

    private ImageProcessor() {
    }

    static Processed process(byte[] upload) {
        if (!isJpeg(upload) && !isPng(upload)) {
            throw new InvalidRequestException("Only JPEG and PNG photos can be added");
        }
        BufferedImage image = decode(upload);
        BufferedImage full = scale(image, FULL_SIZE);
        return new Processed(encode(full), full.getWidth(), full.getHeight(), encode(scale(image, THUMB_SIZE)));
    }

    private static BufferedImage decode(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw unreadable();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true); // ignore metadata entirely
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    throw new InvalidRequestException("That photo is too large");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidRequestException invalid) {
                throw invalid;
            }
            throw unreadable();
        }
    }

    // Fits the image inside size x size, never enlarging it; always RGB on white (no transparency in JPEG)
    private static BufferedImage scale(BufferedImage source, int size) {
        double ratio = Math.min(1.0, (double) size / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] encode(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't encode a JPEG", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static boolean isJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    private static InvalidRequestException unreadable() {
        return new InvalidRequestException("That photo couldn't be read. Try taking it again.");
    }
}
