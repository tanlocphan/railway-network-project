package com.railway.setup.tools;

import com.railway.setup.ui.Theme;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the Setup.exe icon (.ico with PNG images, the format Windows Vista+
 * reads) from the same drawing as the window icon. Used by scripts/build-setup.ps1:
 * <pre>java -cp SetupApp.jar com.railway.setup.tools.IconGen tools/launcher/setup.ico</pre>
 */
public final class IconGen {

    private IconGen() {
    }

    public static void main(String[] args) throws IOException {
        Path out = Paths.get(args.length > 0 ? args[0] : "setup.ico");
        int[] sizes = {16, 20, 24, 32, 40, 48, 64, 128, 256};
        List<byte[]> pngs = new ArrayList<>();
        for (int size : sizes) {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(Theme.appIcon(size), "png", png);
            pngs.add(png.toByteArray());
        }
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        try (OutputStream file = Files.newOutputStream(out); DataOutputStream data = new DataOutputStream(file)) {
            ByteBuffer header = ByteBuffer.allocate(6 + 16 * sizes.length).order(ByteOrder.LITTLE_ENDIAN);
            header.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
            int offset = 6 + 16 * sizes.length;
            for (int i = 0; i < sizes.length; i++) {
                header.put((byte) (sizes[i] >= 256 ? 0 : sizes[i]));
                header.put((byte) (sizes[i] >= 256 ? 0 : sizes[i]));
                header.put((byte) 0).put((byte) 0);
                header.putShort((short) 1).putShort((short) 32);
                header.putInt(pngs.get(i).length).putInt(offset);
                offset += pngs.get(i).length;
            }
            data.write(header.array());
            for (byte[] png : pngs) {
                data.write(png);
            }
        }
        System.out.println("Icon written: " + out.toAbsolutePath());
    }
}
