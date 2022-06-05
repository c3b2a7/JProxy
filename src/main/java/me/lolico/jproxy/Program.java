package me.lolico.jproxy;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

public class Program {

    private static final String BIND = "--bind";
    private static final String UPSTREAM = "--upstream";

    public static void main(String[] args) throws Exception {
        List<String> list = Arrays.asList(args);
        String bind = null;
        if (list.contains(BIND)) {
            int index = list.indexOf(BIND);
            bind = list.get(index + 1);
        }
        if (bind == null) {
            System.out.println("""
                    Usage: java -jar JProxy.jar [--bind]
                           java -jar JProxy.jar [--bind] [--upstream]
                    
                    Example:
                           - Start the transparent proxy by the given address
                               java -jar JProxy.jar --bind localhost:8000
                           - Start the transparent proxy and forward all packets to upstream by the given address
                               java -jar JProxy.jar --bind localhost:9000 --upstream localhost:8000
                    """);
            return;
        }

        String[] addrAndPort = bind.split(":");
        ProxyServer server = new ProxyServer(new InetSocketAddress(addrAndPort[0], Integer.parseInt(addrAndPort[1])));
        if (list.contains(UPSTREAM)) {
            int index = list.indexOf(UPSTREAM);
            addrAndPort = list.get(index + 1).split(":");
            server.setUpstream(new InetSocketAddress(addrAndPort[0], Integer.parseInt(addrAndPort[1])));
        }
        server.open();
    }
}