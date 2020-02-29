package com.unixsocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;


public class UnixSocketHandler {

    // https://www-numi.fnal.gov/offline_software/srt_public_context/WebDocs/Errors/unix_system_errors.html
    public final static int EAGAIN = 11;            /* Try again */

    enum State {
        UNINITIALIZED,
        CONNECTED,
        IDLE,
        CONNECTING,
    }

    private State state = State.UNINITIALIZED;
    private int fd;
    private SocketAddress remoteAddress;

    public UnixSocketHandler() throws IOException {
        fd = -1;
        create();
    }

    public int getFd() {
        return fd;
    }

    /**
     * Creates a non blocking unix socket in the underlying OS.
     *
     * @throws IOException
     */
    private void create() throws IOException {
        if (fd != -1) {
            throw new IOException("socket already created");
        }

        fd = NativeUnixSocket.socket();
        if (fd < 0) {
            throw new IOException("socket create failed");
        }

        setBlocking(false);
    }

    public boolean isConnected() {
        return state == State.CONNECTED;
    }

    public boolean isConnectionPending() {
        return state == State.CONNECTING;
    }

    public boolean connect(SocketAddress remote) throws IOException {
        Util.print("connect " + remote);

        createSocketIfNeeded();

        remoteAddress = remote;
        if (doConnect(remote)) {
            state = State.CONNECTED;
            return true;
        }

        state = State.CONNECTING;
        return false;
    }

    private void createSocketIfNeeded() throws IOException {
        if (fd == -1) {
            create();
        }
    }

    private void checkCreated() throws IOException {
        if (fd == -1) {
            throw new IOException("socket not created");
        }
    }

    private boolean doConnect(SocketAddress remote) throws IOException {
        checkCreated();

        InetSocketAddress addr = (InetSocketAddress) remote;
        String host = addr.getHostName();
        int res = NativeUnixSocket.connect(fd, host);
        Util.print("connect returned " + res);

        if (res < 0) {
	    // TODO(erdal): here we could allow for async connect by checking for EAGAIN
	    // and returning false, but the caller needs to keep calling connect until ready
            int errno = -res;
	    throw new IOException("connect failed: " + errno);
        }

        return true;
    }

    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public byte[] read() throws IOException {
        if (!isConnected()) {
            throw new ClosedChannelException();
        }

        byte[] data = new byte[10 * 1024];  // TODO(erdal): can we do better?
        int n = NativeUnixSocket.read(fd, data);

        if (n < 0) {
            int errno = -n;
            throw new IOException("read error " + errno);
        }

        byte[] res = new byte[n];
        System.arraycopy(data, 0, res, 0, n);
        return res;
    }

    public int write(byte[] data) throws IOException {
        if (!isConnected()) {
            throw new ClosedChannelException();
        }

        int len = data.length;
        int n = NativeUnixSocket.write(fd, data);
        Util.print("write returned " + n);

        if (n >= 0) {
            if (n < len) {
                // TODO(erdal): what now?
                // src.position(src.position() - (r - n));
                throw new IOException("failed to write the entire thing bytes left: " + (len - n) + " out of " + len);
            }
        } else {
            int errno = -n;
            if (errno == EAGAIN) {
                // src.position(src.position() - r);
                // TODO(erdal): try again later?
                return 0;
            } else {
                throw new IOException("failed to write " + errno);
            }
        }

        return n;
    }

    public void close() throws IOException {
        Util.print("close");
        if (fd == -1) {
            return;
        }

        NativeUnixSocket.close(fd);
        fd = -1;
    }

    public void setBlocking(boolean block) throws IOException {
        Util.print("setBlocking " + block);
        if (fd == -1) {
            return;
        }

        int res = NativeUnixSocket.setBlocking(fd, block);
        if (res < 0) {
            throw new IOException("failed to set socket blocking: " + block);
        }
    }
}
