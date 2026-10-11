package boniec.online;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ServerPinger {

    private static final Pattern ONLINE = Pattern.compile("\"online\"\\s*:\\s*(\\d+)");

    private ServerPinger() {}

    static int pingOnline(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            ByteArrayOutputStream handshake = new ByteArrayOutputStream();
            DataOutputStream hs = new DataOutputStream(handshake);
            writeVarInt(hs, 0);
            writeVarInt(hs, 0);
            writeString(hs, host);
            hs.writeShort(port);
            writeVarInt(hs, 1);
            writeVarInt(out, handshake.size());
            out.write(handshake.toByteArray());

            writeVarInt(out, 1);
            writeVarInt(out, 0);

            int len = readVarInt(in);
            if (len <= 0) return -1;
            int packetId = readVarInt(in);
            if (packetId != 0) return -1;
            String json = readString(in);
            Matcher m = ONLINE.matcher(json);
            if (!m.find()) return -1;
            return Integer.parseInt(m.group(1));
        } catch (Exception e) {
            return -1;
        }
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, data.length);
        out.write(data);
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = readVarInt(in);
        byte[] data = new byte[len];
        in.readFully(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int pos = 0;
        byte current;
        do {
            current = in.readByte();
            value |= (current & 0x7F) << pos;
            pos += 7;
            if (pos > 35) throw new IOException("VarInt too big");
        } while ((current & 0x80) != 0);
        return value;
    }
}
