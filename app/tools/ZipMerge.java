import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Merges extra files (dex) into an APK while keeping Android 16's packaging rules.
 *
 * Android 16 rejects an APK whose resources.arsc is compressed or not 4-byte
 * aligned at install time. aapt2's output is fine, but generic zip updaters
 * (including .NET's ZipArchive) rewrite the central directory and can change
 * compression. This tool rebuilds the archive entry by entry and forces
 * resources.arsc to STORED; zipalign then fixes the offsets.
 *
 * Usage: ZipMerge <input.apk> <output.apk> [name=path ...]
 */
public final class ZipMerge {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: ZipMerge <input.apk> <output.apk> [name=path ...]");
            System.exit(2);
        }
        File in = new File(args[0]);
        File out = new File(args[1]);

        List<String[]> extras = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            int eq = args[i].indexOf('=');
            if (eq <= 0) throw new IllegalArgumentException("bad extra: " + args[i]);
            extras.add(new String[]{args[i].substring(0, eq), args[i].substring(eq + 1)});
        }

        Set<String> seen = new HashSet<>();
        try (ZipFile zip = new ZipFile(in);
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out))) {
            java.util.Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String name = e.getName();
                if (name.startsWith("META-INF/")
                        && (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA"))) {
                    continue;
                }
                seen.add(name);
                byte[] data = readAll(zip.getInputStream(e));
                put(zos, name, data);
            }
            for (String[] extra : extras) {
                if (seen.contains(extra[0])) throw new IllegalStateException("duplicate entry " + extra[0]);
                seen.add(extra[0]);
                put(zos, extra[0], readAll(new FileInputStream(extra[1])));
            }
        }
        System.out.println("wrote " + out.length() + " bytes to " + out.getPath());
    }

    private static void put(ZipOutputStream zos, String name, byte[] data) throws Exception {
        ZipEntry e = new ZipEntry(name);
        boolean store = name.equals("resources.arsc");
        if (store) {
            CRC32 crc = new CRC32();
            crc.update(data, 0, data.length);
            e.setMethod(ZipEntry.STORED);
            e.setSize(data.length);
            e.setCompressedSize(data.length);
            e.setCrc(crc.getValue());
        } else {
            e.setMethod(ZipEntry.DEFLATED);
        }
        zos.putNextEntry(e);
        zos.write(data);
        zos.closeEntry();
    }

    private static byte[] readAll(InputStream in) throws Exception {
        try (InputStream i = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 20);
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = i.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
