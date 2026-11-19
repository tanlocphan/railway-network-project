package com.railway.setup.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Is something listening on a TCP port? */
public final class Net {

    private Net() {
    }

    public static boolean isOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 600);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
