package com.unixsocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;


public class UnixSocketManager {

    public interface DataCallback {
        public void onDataAvailable(byte[] data);
    }

    public static final int EPOLLIN = 0x001;
    public static final int EPOLLOUT = 0x004;
    public static final int EPOLLERR = 0x008;
    public static final int EPOLLHUP = 0x010;

    public final static boolean DEBUG = false;

    int epollfd;
    Map<Integer, UnixSocketHandler> handlers = new HashMap<>();
    Map<Integer, DataCallback> dataCallbacks = new HashMap<>();

    public UnixSocketManager() {
        setupEpoll();
    }

    private void setupEpoll() {
        epollfd = NativeUnixSocket.epollCreate();
        // TODO: setup wakeup socket if we need to
    }

    public void setDataCallback(UnixSocketHandler handler, DataCallback dataCallback) {
        dataCallbacks.put(handler.getFd(), dataCallback);
    }

    public void connect(UnixSocketHandler handler, String path) throws IOException {
        handlers.put(handler.getFd(), handler);
        SocketAddress address = new InetSocketAddress(path, 0);
	handler.connect(address);
	NativeUnixSocket.epollAdd(epollfd, handler.getFd());
    }

    public void setupLoop() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        doLoop();
                    } catch (Exception e) {
                        // TODO(erdal): what?
                    }
                }
            }
        }).start();
    }

    private void doLoop() throws IOException {
        int[] events = NativeUnixSocket.epollWait(epollfd);
        // we encode the results in an array for easier transmission from jni
        // every pair of entries in the array is (fd, events)
        int n = events.length / 2;
        Util.print("epoll returned events: " + events.length);

        for (int i = 0; i < n; i++) {
            int fd = events[2 * i];
            int event = events[2 * i + 1];
            Util.print("fd: " + fd + " event: " + event);

            UnixSocketHandler handler = handlers.get(fd);

            if ((event & EPOLLIN) > 0) {
                byte[] data = handler.read();
                DataCallback callback = dataCallbacks.get(fd);
                if (callback != null) {
                    // TODO(erdal): switch to main thread?
                    callback.onDataAvailable(data);
                }
            } else if ((event & EPOLLOUT) > 0) {
                Util.print("socket is writable");
                // TODO(erdal): for now we write in sync
                // handler.write();
            } else {
                Util.print("unhandled epoll event");
                // TODO(erdal): handle errors, close, cleanup
            }
        }
    }
}
