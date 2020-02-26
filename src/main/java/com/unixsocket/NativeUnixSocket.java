package com.unixsocket;


/**
 * Native interface for all unix socket functions needed for the async socket channel.
 */
public class NativeUnixSocket {

    static {
	System.loadLibrary("unix-socket-jni");
    }

    /*
     * Creates a unix socket.
     * Returns the file descriptor of new the socket.
     *
     */
    public static native int socket();

    /*
     * TODO
     * Maybe a more abstract name would be better
     *
     */
    public static native int epollCreate();

    /*
     * TODO
     *
     *
     */
    public static native int epollAdd(int epollfd, int fd);

    // TODO: epollRemove ? epollModify?

    /*
     * TODO
     *
     * We might want a timeout parameter passed in
     */
    public static native int[] epollWait(int epollfd);

    /*
     * Unix socket connect
     *
     */
    public static native int connect(int fd, String host);

    /*
     * Read from socket at file descriptor fd into data.
     * Returns number of bytes read or negative error.
     *
     */
    public static native int read(int fd, byte[] data);

    /*
     * Unix socket write
     *
     */
    public static native int write(int fd, byte[] data);

    /*
     * Mark the socket at file descriptor fd as blocking or non-blocking.
     *
     */
    public static native int setBlocking(int fd, boolean block);

    /*
     * Unix socket close
     *
     */
    public static native int close(int fd);


    /*
     * Unix socket server
     *
     */
    public static native int startlistening(String host);
}
