package org.dspace.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;

public class PdfBoxUtils {

  public static InputStream addWatermark(PDDocument document) throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    String watermarkText = "HCMUTE - CONFIDENTIAL";
    PDFont font = PDType1Font.HELVETICA_BOLD;
    int fontSize = 60;

    for (PDPage page : document.getPages()) {
      try (PDPageContentStream cs = new PDPageContentStream(
          document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

        PDExtendedGraphicsState graphicsState = new PDExtendedGraphicsState();
        graphicsState.setNonStrokingAlphaConstant(0.3f);
        cs.setGraphicsStateParameters(graphicsState);

        cs.setNonStrokingColor(0f, 0f, 0f);

        float pageWidth = page.getMediaBox().getWidth();
        float pageHeight = page.getMediaBox().getHeight();

        float textWidth = font.getStringWidth(watermarkText) / 1000 * fontSize;
        float textHeight = font.getFontDescriptor().getFontBoundingBox().getHeight() / 1000 * fontSize;

        float centerX = pageWidth / 2;
        float centerY = pageHeight / 2;

        cs.beginText();
        cs.setFont(font, fontSize);

        cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(45), centerX, centerY));
        cs.newLineAtOffset(-textWidth / 2, -textHeight / 4);

        cs.showText(watermarkText);
        cs.endText();
      }
    }

    document.save(output);
    return new ByteArrayInputStream(output.toByteArray());
  }

  public static InputStream addHiddenWatermark(
      PDDocument document,
      String hiddenEmail, // watermark ẩn
      String visibleTimestamp // watermark hiện
  ) throws Exception {

    // ==============================
    // (NEW) Nhúng hash email vào ảnh 2x2cm trang đầu
    // ==============================
    try {
      if (document.getNumberOfPages() > 0 && hiddenEmail != null) {

        String normalized = normalize(hiddenEmail);
        String emailHashHex = sha256Hex(normalized);

        // 👉 NHÚNG HASH VÀO ẢNH
        embedEmailHashIntoCornerImage(document, hiddenEmail);

        // 👉 GHI LOG RA FILE
        writeWatermarkLog(
            emailHashHex,
            document.getNumberOfPages());
      }
    } catch (Exception ex) {
      System.out.println("WARN: embedEmailHashIntoCornerImage failed: " + ex.getMessage());
    }

    // ==============================
    // Watermark hiện/ẩn như cũ
    // ==============================
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    PDFont hiddenFont = PDType1Font.HELVETICA_BOLD;
    int hiddenFontSize = 1;

    PDFont visibleFont = PDType1Font.HELVETICA_BOLD;
    int visibleFontSize = 7;

    for (PDPage page : document.getPages()) {

      PDRectangle mediaBox = page.getMediaBox();
      float pageWidth = mediaBox.getWidth();
      float marginBottom = 30;

      // 1) VẼ TIMESTAMP HIỆN
      try (PDPageContentStream cs = new PDPageContentStream(
          document, page, AppendMode.APPEND, true, true)) {

        float timestampWidth = visibleFont.getStringWidth(visibleTimestamp) / 1000 * visibleFontSize;
        float timestampCenterX = (pageWidth - timestampWidth) / 2;

        PDExtendedGraphicsState visibleState = new PDExtendedGraphicsState();
        visibleState.setNonStrokingAlphaConstant(1f);
        cs.setGraphicsStateParameters(visibleState);

        cs.beginText();
        cs.setFont(visibleFont, visibleFontSize);
        cs.newLineAtOffset(timestampCenterX, marginBottom);
        cs.showText(visibleTimestamp);
        cs.endText();
      }

      // 2) VẼ WATERMARK ẨN
      try (PDPageContentStream csHidden = new PDPageContentStream(
          document, page, AppendMode.APPEND, true, true)) {

        float hiddenWidth = hiddenFont.getStringWidth(hiddenEmail) / 1000 * hiddenFontSize;
        float hiddenCenterX = (pageWidth - hiddenWidth) / 2;

        PDExtendedGraphicsState hiddenState = new PDExtendedGraphicsState();
        hiddenState.setNonStrokingAlphaConstant(0.01f);
        csHidden.setGraphicsStateParameters(hiddenState);

        csHidden.setNonStrokingColor(0f, 0f, 0f);

        csHidden.beginText();
        csHidden.setFont(hiddenFont, hiddenFontSize);
        csHidden.newLineAtOffset(hiddenCenterX, marginBottom);
        csHidden.showText(hiddenEmail);
        csHidden.endText();
      }
    }

    document.save(output);
    return new ByteArrayInputStream(output.toByteArray());
  }

  // ============================================================
  // NEW: Embed SHA-256(hiddenEmail or extracted email) into 2x2cm corner image
  // (page 1)
  // ============================================================

  private static void embedEmailHashIntoCornerImage(PDDocument document, String hiddenEmailOrText) throws Exception {
    // Nếu bạn muốn chỉ hash email thật: String email =
    // parseEmail(hiddenEmailOrText); else hash full string
    String normalized = normalize(hiddenEmailOrText);
    byte[] hash = sha256(normalized);

    // Render page 1 to image
    PDFRenderer renderer = new PDFRenderer(document);

    float dpi = 300f; // đủ chi tiết
    BufferedImage pageImg = renderer.renderImageWithDPI(0, dpi, ImageType.RGB);

    // 2cm -> pixel
    int sizePx = Math.round((2f / 2.54f) * dpi); // 2cm
    if (sizePx <= 0)
      return;

    // Ensure crop bounds
    int cropW = Math.min(sizePx, pageImg.getWidth());
    int cropH = Math.min(sizePx, pageImg.getHeight());

    // Crop top-left in image coordinates (rendered image: origin top-left)
    BufferedImage corner = pageImg.getSubimage(0, 0, cropW, cropH);

    // Embed bits into LSB of Blue channel
    embedBitsLSBBlue(corner, hash);

    // Draw back into PDF at top-left (PDF coords origin bottom-left)
    PDPage page = document.getPage(0);
    PDRectangle box = page.getMediaBox();

    // 2cm -> points (1 inch = 72 pt; 1 cm = 72/2.54)
    float cmToPt = 72f / 2.54f;
    float sizePt = 2f * cmToPt;

    float xPt = 0f;
    float yPt = box.getHeight() - sizePt; // top-left

    PDImageXObject ximage = LosslessFactory.createFromImage(document, corner);

    try (PDPageContentStream cs = new PDPageContentStream(
        document, page, AppendMode.APPEND, true, true)) {

      cs.drawImage(ximage, xPt, yPt, sizePt, sizePt);
    }
  }

  private static void embedBitsLSBBlue(BufferedImage img, byte[] payload) {
    int w = img.getWidth();
    int h = img.getHeight();

    int totalBits = payload.length * 8;
    int bitIndex = 0;

    outer: for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        if (bitIndex >= totalBits)
          break outer;

        int bit = (payload[bitIndex / 8] >> (7 - (bitIndex % 8))) & 1;
        bitIndex++;

        int rgb = img.getRGB(x, y);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        b = (b & 0xFE) | bit; // set LSB

        int newRgb = (r << 16) | (g << 8) | b;
        img.setRGB(x, y, newRgb);
      }
    }
  }

  private static byte[] sha256(String s) throws Exception {
    if (s == null)
      s = "";
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    return md.digest(s.getBytes(StandardCharsets.UTF_8));
  }

  private static String normalize(String s) {
    if (s == null)
      return "";
    return s.trim().toLowerCase();
  }

  private static final String WATERMARK_LOG_PATH = "/dspace/log/watermark.log";

  private static synchronized void writeWatermarkLog(
      String emailHash,
      int pageCount) {
    try (PrintWriter out = new PrintWriter(
        new FileWriter(WATERMARK_LOG_PATH, true))) {

      out.println(
          Instant.now().toString()
              + " | EMAIL_HASH=" + emailHash
              + " | PAGES=" + pageCount);

    } catch (Exception e) {
      System.err.println("Failed to write watermark log: " + e.getMessage());
    }
  }

  private static String sha256Hex(String s) throws Exception {
    return toHex(sha256(s));
  }

  private static String toHex(byte[] data) {
    StringBuilder sb = new StringBuilder(data.length * 2);
    for (byte b : data)
      sb.append(String.format("%02x", b));
    return sb.toString();
  }

  // Nếu bạn muốn hash CHỈ EMAIL (không hash cả câu "Downloaded by: ..."):
  // private static String parseEmail(String watermarkText) {
  // // ví dụ watermarkText: "Downloaded by: abc@xyz.com on 2025-..."
  // if (watermarkText == null) return "";
  // int idx = watermarkText.indexOf("Downloaded by:");
  // if (idx >= 0) {
  // String t = watermarkText.substring(idx + "Downloaded by:".length()).trim();
  // int on = t.indexOf(" on ");
  // if (on > 0) t = t.substring(0, on).trim();
  // return t;
  // }
  // return watermarkText;
  // }
}
