package com.finor.user;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ProfilePhotoTest {
  static final String PREFIX = "data:image/jpeg;base64,";
  String image(int width, int height) throws Exception {
    var output = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpeg", output);
    output.write("trailing-metadata-to-remove".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return PREFIX + Base64.getEncoder().encodeToString(output.toByteArray());
  }

  @Test void cropsAndNormalizesRealPhotosWithoutKeepingTrailingMetadata() throws Exception {
    String source = image(640, 480);
    String normalized = ProfilePhoto.normalize(source);
    var bytes = Base64.getDecoder().decode(normalized.substring(PREFIX.length()));
    var avatar = ImageIO.read(new ByteArrayInputStream(bytes));
    assertEquals(320, avatar.getWidth());
    assertEquals(320, avatar.getHeight());
    assertNotEquals(source, normalized);
    assertEquals((byte)0xff, bytes[bytes.length - 2]);
    assertEquals((byte)0xd9, bytes[bytes.length - 1]);
    assertTrue(normalized.length() < 350000);
  }

  @Test void rejectsNonImagesInvalidBase64AndRemoteUrls() {
    for (String value : new String[]{"https://example.com/photo.jpg", "data:image/svg+xml;base64,AAAA", PREFIX + "invalid!", PREFIX + "dGV4dA=="})
      assertThrows(ResponseStatusException.class, () -> ProfilePhoto.normalize(value));
  }

  @Test void rejectsLargeDimensionsBeforeDecodingAndOversizedBodies() throws Exception {
    assertThrows(ResponseStatusException.class, () -> ProfilePhoto.normalize(image(1025, 10)));
    assertThrows(ResponseStatusException.class, () -> ProfilePhoto.normalize(PREFIX + "A".repeat(350000)));
  }
}
