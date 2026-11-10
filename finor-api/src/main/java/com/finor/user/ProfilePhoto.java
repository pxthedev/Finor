package com.finor.user;

import jakarta.validation.constraints.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Small normalized JPEGs, kept separate from financial profile reads. */
public final class ProfilePhoto {
  private ProfilePhoto() {}
  public record Upload(@NotBlank @Size(max = 350000) String dataUrl) {}
  public record Value(String dataUrl) {}

  public static String normalize(String dataUrl) {
    String prefix = "data:image/jpeg;base64,";
    if (dataUrl == null || dataUrl.length() > 350000 || !dataUrl.startsWith(prefix))
      throw invalid();
    try {
      byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(prefix.length()));
      try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
        var readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) throw invalid();
        var reader = readers.next();
        try {
          reader.setInput(input, true, true);
          int width = reader.getWidth(0), height = reader.getHeight(0);
          if (!reader.getFormatName().equalsIgnoreCase("JPEG") || width < 1 || height < 1 || width > 1024 || height > 1024)
            throw invalid();
          var source = reader.read(0);
          var avatar = new BufferedImage(320, 320, BufferedImage.TYPE_INT_RGB);
          var graphics = avatar.createGraphics();
          try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int side = Math.min(width, height), x = (width - side) / 2, y = (height - side) / 2;
            graphics.drawImage(source, 0, 0, 320, 320, x, y, x + side, y + side, null);
          } finally { graphics.dispose(); }
          var output = new ByteArrayOutputStream();
          ImageIO.write(avatar, "jpeg", output);
          return prefix + Base64.getEncoder().encodeToString(output.toByteArray());
        } finally { reader.dispose(); }
      }
    } catch (IOException | IllegalArgumentException e) { throw invalid(); }
  }

  private static ResponseStatusException invalid() {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha uma foto válida e tente novamente.");
  }
}
