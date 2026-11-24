package com.vivanetpay.integration.android;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LanAddressesTest {
    private static LanAddresses.Iface iface(String name, String display, boolean up, boolean loop, boolean virt, String... ips) {
        return new LanAddresses.Iface(name, display, up, loop, virt, List.of(ips));
    }

    @Test
    void soloDireccionesPrivadasRfc1918() {
        for (String ok : new String[]{"10.0.0.5", "172.16.0.1", "172.31.255.254", "192.168.1.20"}) {
            assertThat(LanAddresses.isPrivateIpv4(ok)).as(ok).isTrue();
        }
        for (String bad : new String[]{"8.8.8.8", "172.15.0.1", "172.32.0.1", "192.169.1.1", "169.254.10.2", "127.0.0.1",
                "0.0.0.0", "100.64.1.1", "255.255.255.255", "300.1.1.1", "192.168.1", "::1", "", null, "192.168.1.20 "}) {
            assertThat(LanAddresses.isPrivateIpv4(bad == null ? null : bad.equals("192.168.1.20 ") ? "x" + bad : bad)).as(String.valueOf(bad)).isFalse();
        }
    }

    @Test
    void filtraLoopbackVirtualesApagadasLinkLocalYPublicas() {
        var found = LanAddresses.filter(List.of(
                iface("lo", "Loopback", true, true, false, "127.0.0.1"),
                iface("eth3", "Ethernet", true, false, false, "192.168.1.20"),
                iface("wlan0", "Wi-Fi", true, false, false, "192.168.1.33", "169.254.7.7"),
                iface("eth9", "Ethernet 2", false, false, false, "10.1.1.1"),
                iface("docker0", "docker0", true, false, false, "172.17.0.1"),
                iface("vEthernet (WSL)", "Hyper-V Virtual Ethernet Adapter", true, false, false, "172.28.80.1"),
                iface("ZeroTier One", "ZeroTier Virtual Port", true, false, false, "10.147.17.5"),
                iface("tailscale0", "Tailscale", true, false, false, "100.101.102.103"),
                iface("tun0", "OpenVPN TAP", true, false, false, "10.8.0.2"),
                iface("sub", "sub", true, false, true, "192.168.5.5"),
                iface("eth5", "Ethernet 5", true, false, false, "203.0.113.9"),
                iface("eth6", "Ethernet 6", true, false, false, "192.168.1.20")));
        assertThat(found).extracting(LanAddresses.Candidate::ip).containsExactly("192.168.1.33", "192.168.1.20");
        assertThat(found.getFirst().interfaceName()).isEqualTo("Wi-Fi");
    }

    @Test
    void requirePrivateRechazaPublicasYComodines() {
        assertThat(LanAddresses.requirePrivate(" 192.168.1.20 ")).isEqualTo("192.168.1.20");
        for (String bad : new String[]{"8.8.8.8", "0.0.0.0", "::", "127.0.0.1", "example.com"}) {
            assertThatThrownBy(() -> LanAddresses.requirePrivate(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void detectNoLanzaYSoloDevuelvePrivadas() {
        assertThat(LanAddresses.detect()).allSatisfy(c -> assertThat(LanAddresses.isPrivateIpv4(c.ip())).isTrue());
    }
}
