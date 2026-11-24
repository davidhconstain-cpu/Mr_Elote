package com.vivanetpay.integration.android;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Detección de las IPv4 PRIVADAS del equipo donde el teléfono puede alcanzar el receptor (DP-03: misma red Wi-Fi).
 * La lógica de filtrado es pura ({@link #filter(List)}) para poder probarla sin red; {@link #detect()} solo
 * traduce {@link NetworkInterface} a {@link Iface}.
 * <p>
 * Se aceptan solo 10.0.0.0/8, 172.16.0.0/12 y 192.168.0.0/16. Se excluyen loopback, link-local (169.254/16),
 * CGNAT/VPN (100.64/10), interfaces apagadas o virtuales (Docker, WSL, Hyper-V, VirtualBox, VMware, VPN, Tailscale,
 * ZeroTier, etc.), públicas y comodines.
 */
public final class LanAddresses {
    private static final Pattern IPV4 = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");
    private static final Pattern VIRTUAL_NAME = Pattern.compile(
            "docker|veth|virbr|vmnet|vmware|virtualbox|vbox|hyper-v|vethernet|wsl|tailscale|zerotier|wireguard|"
                    + "openvpn|vpn|\\btun\\d|\\btap\\d|\\bbr-|\\bppp|isatap|teredo|loopback|pseudo|bluetooth|npcap|"
                    + "miniport|virtual|host-only|anyconnect|nordlynx|hamachi");

    /** Interfaz simplificada (para pruebas y para no depender de NetworkInterface en la lógica). */
    public record Iface(String name, String displayName, boolean up, boolean loopback, boolean virtual,
                        List<String> ipv4) {
        public Iface {
            ipv4 = List.copyOf(ipv4);
        }
    }

    /** Candidata para elegir. */
    public record Candidate(String ip, String interfaceName) {
        public String label() {
            return ip + "  (" + interfaceName + ")";
        }
    }

    private LanAddresses() {
    }

    /** true si es una IPv4 literal privada RFC 1918 (10/8, 172.16/12, 192.168/16). */
    public static boolean isPrivateIpv4(String ip) {
        int[] o = octets(ip);
        if (o == null) {
            return false;
        }
        return o[0] == 10 || (o[0] == 172 && o[1] >= 16 && o[1] <= 31) || (o[0] == 192 && o[1] == 168);
    }

    private static int[] octets(String ip) {
        if (ip == null) {
            return null;
        }
        var m = IPV4.matcher(ip.trim());
        if (!m.matches()) {
            return null;
        }
        int[] o = new int[4];
        for (int i = 0; i < 4; i++) {
            o[i] = Integer.parseInt(m.group(i + 1));
            if (o[i] > 255) {
                return null;
            }
        }
        return o;
    }

    public static boolean looksVirtual(String name, String displayName) {
        String n = ((name == null ? "" : name) + " " + (displayName == null ? "" : displayName)).toLowerCase(Locale.ROOT);
        return VIRTUAL_NAME.matcher(n).find();
    }

    /** Filtra y ordena (Wi-Fi/Ethernet primero, luego por IP). */
    public static List<Candidate> filter(List<Iface> ifaces) {
        List<Candidate> out = new ArrayList<>();
        for (Iface i : ifaces) {
            if (!i.up() || i.loopback() || i.virtual() || looksVirtual(i.name(), i.displayName())) {
                continue;
            }
            for (String ip : i.ipv4()) {
                if (isPrivateIpv4(ip) && out.stream().noneMatch(c -> c.ip().equals(ip.trim()))) {
                    String label = i.displayName() != null && !i.displayName().isBlank() ? i.displayName() : i.name();
                    out.add(new Candidate(ip.trim(), label));
                }
            }
        }
        out.sort(java.util.Comparator.comparingInt((Candidate c) -> wifiRank(c.interfaceName())).thenComparing(Candidate::ip));
        return List.copyOf(out);
    }

    private static int wifiRank(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return n.contains("wi-fi") || n.contains("wifi") || n.contains("wlan") || n.contains("wireless") ? 0 : 1;
    }

    /** Detecta en el equipo. Devuelve lista vacía si no hay red o falla la consulta. */
    public static List<Candidate> detect() {
        List<Iface> ifaces = new ArrayList<>();
        try {
            var en = NetworkInterface.getNetworkInterfaces();
            if (en == null) {
                return List.of();
            }
            for (NetworkInterface ni : Collections.list(en)) {
                List<String> ips = new ArrayList<>();
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address) {
                        ips.add(a.getHostAddress());
                    }
                }
                boolean up;
                boolean loop;
                try {
                    up = ni.isUp();
                    loop = ni.isLoopback();
                } catch (SocketException e) {
                    continue;
                }
                ifaces.add(new Iface(ni.getName(), ni.getDisplayName(), up, loop, ni.isVirtual(), ips));
            }
        } catch (SocketException e) {
            return List.of();
        }
        return filter(ifaces);
    }

    /** Valida una IP elegida para exponer el receptor: debe ser IPv4 privada literal (nunca pública ni comodín). */
    public static String requirePrivate(String ip) {
        if (!isPrivateIpv4(ip)) {
            throw new IllegalArgumentException("Solo se admiten direcciones privadas de la red local (10.x, 172.16-31.x, 192.168.x): " + ip);
        }
        try {
            InetAddress.getByName(ip.trim());
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Dirección IP inválida: " + ip);
        }
        return ip.trim();
    }
}
