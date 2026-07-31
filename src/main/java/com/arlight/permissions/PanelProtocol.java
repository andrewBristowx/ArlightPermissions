package com.arlight.permissions;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class PanelProtocol {
    static final String CHANNEL = "arlightpermissions:panel";
    private static final int MAX_PAYLOAD_BYTES = 65_536;
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private PanelProtocol() {}
    static String enc(String s) { return s == null || s.isEmpty() ? "" : B64.encodeToString(s.getBytes(StandardCharsets.UTF_8)); }
    static String dec(String s) { return s == null || s.isEmpty() ? "" : new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8); }
    static void send(JavaPlugin plugin, Player player, String message) {
        byte[] text = message.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(text.length + 5);
        writeVarInt(out, text.length); out.writeBytes(text);
        player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
    }
    static String read(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Payload inválido");
        }
        int[] pos={0}; int len=readVarInt(data,pos);
        if(len<0 || len>MAX_PAYLOAD_BYTES || pos[0]+len>data.length) throw new IllegalArgumentException("Payload inválido");
        return new String(data,pos[0],len,StandardCharsets.UTF_8);
    }
    private static void writeVarInt(ByteArrayOutputStream out,int value){while((value&-128)!=0){out.write(value&127|128);value>>>=7;}out.write(value);}
    private static int readVarInt(byte[] d,int[] p){int n=0,r=0,b;do{if(p[0]>=d.length||n==5)throw new IllegalArgumentException("VarInt inválido");b=d[p[0]++]&255;r|=(b&127)<<(7*n++);}while((b&128)!=0);return r;}
}
