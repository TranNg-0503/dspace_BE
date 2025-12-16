package org.dspace.util;

import java.awt.image.BufferedImage;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;

public class SteganographyDecoder {

  /**
   * Extract the hidden SHA-256 hash from the PDF's first page corner
   */
  public static byte[] extractHiddenHash(PDDocument document) throws Exception {
    PDFRenderer renderer = new PDFRenderer(document);
    float dpi = 300f;

    // Render first page
    BufferedImage pageImg = renderer.renderImageWithDPI(0, dpi, ImageType.RGB);

    // Calculate 2cm in pixels (2cm / 2.54 * dpi)
    int sizePx = Math.round((2f / 2.54f) * dpi);

    int cropW = Math.min(sizePx, pageImg.getWidth());
    int cropH = Math.min(sizePx, pageImg.getHeight());

    // Crop top-left corner (same as encoding)
    BufferedImage corner = pageImg.getSubimage(0, 0, cropW, cropH);

    // SHA-256 hash is 32 bytes = 256 bits
    int hashLength = 32;
    byte[] extractedHash = extractBitsLSBBlue(corner, hashLength);

    return extractedHash;
  }

  /**
   * Extract bits from LSB of Blue channel
   */
  private static byte[] extractBitsLSBBlue(BufferedImage img, int byteCount) {
    int w = img.getWidth();
    int h = img.getHeight();

    byte[] payload = new byte[byteCount];
    int totalBits = byteCount * 8;
    int bitIndex = 0;

    outer: for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        if (bitIndex >= totalBits)
          break outer;

        int rgb = img.getRGB(x, y);
        int b = rgb & 0xFF; // Get blue channel
        int bit = b & 1; // Get LSB

        // Set bit in payload
        int byteIdx = bitIndex / 8;
        int bitPos = 7 - (bitIndex % 8);
        payload[byteIdx] |= (bit << bitPos);

        bitIndex++;
      }
    }

    return payload;
  }

  /**
   * Verify if extracted hash matches a given email
   */
  public static boolean verifyEmail(byte[] extractedHash, String email) throws Exception {
    String normalized = email.trim().toLowerCase();
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    byte[] expectedHash = md.digest(normalized.getBytes(StandardCharsets.UTF_8));

    return MessageDigest.isEqual(extractedHash, expectedHash);
  }

  /**
   * Convert bytes to hex string for display
   */
  public static String toHex(byte[] data) {
    StringBuilder sb = new StringBuilder(data.length * 2);
    for (byte b : data) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}