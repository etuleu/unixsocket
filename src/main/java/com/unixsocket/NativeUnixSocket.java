package com.unixsocket;

import android.util.Log;

/**
 * Native interface for all unix socket functions needed for the async socket channel.
 */
public class NativeUnixSocket {

    static {
	Log.i("NativeUnixSocket", "loading native lib unix-socket-jni");
	System.loadLibrary("unix-socket-jni");
	Log.i("NativeUnixSocket", "done loading native lib unix-socket-jni");
    }

    /*
     * Creates a unix socket.
     * Returns the file descriptor of new the socket.
     */
    public static native int socket();

    /*
     * Create the epoll object, which can be used to read/write from sockets
     * in an async manner.
     */
    public static native int epollCreate();

    /*
     * Register a new socket with the epoll object.
     */
    public static native int epollAdd(int epollfd, int fd);

    /*
     * Wait for events from the registered sockets.
     *
     * TODO: We might want a timeout parameter passed in
     */
    public static native int[] epollWait(int epollfd);

    /*
     * Unix socket connect
     */
    public static native int connect(int fd, String host);

    /*
     * Read from socket at file descriptor fd into data.
     * Returns number of bytes read or negative errno.
     */
    public static native int read(int fd, byte[] data);

    /*
     * Unix socket write
     * Returns number of bytes writtern or negative errno.
     */
    public static native int write(int fd, byte[] data);

    /*
     * Mark the socket at file descriptor fd as blocking or non-blocking.
     */
    public static native int setBlocking(int fd, boolean block);

    /*
     * Unix socket close
     */
    public static native int close(int fd);


    /*
     * Unix socket server
     * (only used in testing)
     */
    public static native int startlistening(String host);
}
