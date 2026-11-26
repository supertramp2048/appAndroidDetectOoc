import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

class VerifyDexProfile {
    public static void main(String[] args) throws IOException {
        try (ZipFile apk = new ZipFile(args[0])) {
            var prof = apk.getEntry("assets/dexopt/baseline.prof");
            var profm = apk.getEntry("assets/dexopt/baseline.profm");
            if (prof == null || profm == null) throw new IOException("Missing baseline asset");
            System.out.println(prof.getName() + " bytes=" + prof.getSize());
            try (var metadata = apk.getInputStream(profm)) {
                var header = metadata.readNBytes(8);
                if (header[0] != 'p' || header[1] != 'r' || header[2] != 'm') {
                    throw new IOException("Invalid metadata magic");
                }
                System.out.println(profm.getName() + " bytes=" + profm.getSize()
                        + " version=" + new String(header, 4, 3, StandardCharsets.US_ASCII));
            }
            try (var stream = apk.getInputStream(prof)) {
                var header = ByteBuffer.wrap(stream.readNBytes(17)).order(ByteOrder.LITTLE_ENDIAN);
                if (header.getInt() != 0x006f7270 || header.getInt() != 0x00303130) {
                    throw new IOException("Expected binary profile P010");
                }
                int dexCount = Byte.toUnsignedInt(header.get());
                int bodySize = header.getInt();
                int compressedSize = header.getInt();
                byte[] compressed = stream.readNBytes(compressedSize);
                if (compressed.length != compressedSize || stream.read() != -1) throw new IOException("Bad length");
                var inflater = new InflaterInputStream(new ByteArrayInputStream(compressed));
                var body = ByteBuffer.wrap(inflater.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
                if (body.remaining() != bodySize || dexCount == 0) throw new IOException("Bad body");
                Set<String> matched = new HashSet<>();
                for (int i = 0; i < dexCount; i++) {
                    int nameLength = Short.toUnsignedInt(body.getShort());
                    body.getShort();
                    body.getInt();
                    long profileCrc = Integer.toUnsignedLong(body.getInt());
                    long profileMethodIds = Integer.toUnsignedLong(body.getInt());
                    byte[] name = new byte[nameLength];
                    body.get(name);
                    String key = new String(name, StandardCharsets.UTF_8);
                    String dexName = key.substring(Math.max(key.lastIndexOf('!'), key.lastIndexOf(':')) + 1);
                    if (dexName.endsWith(".apk")) dexName = "classes.dex";
                    ZipEntry dex = apk.getEntry(dexName);
                    if (dex == null || !matched.add(dexName) || dex.getCrc() != profileCrc) {
                        throw new IOException("DEX key or CRC mismatch: " + key);
                    }
                    try (var dexStream = apk.getInputStream(dex)) {
                        ByteBuffer dexHeader = ByteBuffer.wrap(dexStream.readNBytes(112))
                                .order(ByteOrder.LITTLE_ENDIAN);
                        if (profileMethodIds != Integer.toUnsignedLong(dexHeader.getInt(88))) {
                            throw new IOException("DEX method_ids_size mismatch: " + key);
                        }
                    }
                    System.out.printf("%s -> %s crc32=%08x method_ids=%d%n",
                            key, dexName, profileCrc, profileMethodIds);
                }
                System.out.printf("Profile-covered DEX files: %d / %d%n", matched.size(),
                        apk.stream().filter(e -> e.getName().matches("classes[0-9]*\\.dex")).count());
            }
        }
    }
}
