package io.github.gaming32.javayield.ecj;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;

import io.github.gaming32.javayield.transform.YieldTransformer;

public class AgentUtils {
    public static byte[] maybeTransform(byte[] inputBytes) {
        try (FileWriter fw = new FileWriter("test13")) {} catch (Exception e) {}
        final byte[] outputBytes = YieldTransformer.transformClass(inputBytes);
        return outputBytes != null ? outputBytes : inputBytes;
    }

    public static void writeToByteArrayOutputStream(FileOutputStream pop, byte[] b, int off, int len, ByteArrayOutputStream os) {
        os.write(b, off, len);
    }

    public static void transformAndSave(FileOutputStream output, ByteArrayOutputStream input) throws IOException {
        output.write(maybeTransform(input.toByteArray()));
        output.flush();
    }
}
