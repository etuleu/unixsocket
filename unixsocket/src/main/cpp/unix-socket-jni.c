#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/epoll.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/un.h>
#include <unistd.h>

#include "log.h"


#define MAX_EVENTS 128

// Functions for connecting and reading/writing from unix sockets.
// The main way one would use this is:
// 1. create the epoll loop
// 2. create a new socket
// 3. register this new socket in the epoll loop
// 4. when a read event triggers call read from it
// 5. if an error happens, like the other side closed the connection for example, close the socket
// and cleanup any other java side data structures you might have
// This can handle basically unlimited number of sockets and is very efficient because it's
// O(number of file descriptors that have events)

// UNIX domain sockets https://troydhanson.github.io/network/Unix_domain_sockets.html

// TODO(erdal): macros for the function signatures, macros for logging

// In general if a system call returns an error we will return the errno as a negative number
// That way the caller (java in our case) can distinguish between success and fail and also
// get the error number.

/*
 * Creates an endpoint for communication and returns a file descriptor
 */
JNIEXPORT jint JNICALL
Java_com_example_hellojni_NativeUnixSocket_socket(
        JNIEnv* env,
        jclass thiz) {
    int fd;

    if ((fd = socket(AF_UNIX, SOCK_STREAM, 0)) == -1) {
        // Note that errno is undefined after a successful
        // system call or library function call: this call may well change this
        // variable, even though it succeeds, for example because it internally
        // used some other library function that failed.
        fd = -errno;
    }

    return fd;
}


/*
 * Create the epoll loop.
 */
JNIEXPORT jint JNICALL
Java_com_example_hellojni_NativeUnixSocket_epollCreate(
        JNIEnv* env,
        jclass thiz) {
    int fd;

    if ((fd = epoll_create(0)) == -1) {
        fd = -errno;
    }

    // TODO: add a wakeup socket?

    return fd;
}


/*
 * Register a new socket for reading.
 */
JNIEXPORT jint JNICALL
Java_com_example_hellojni_NativeUnixSocket_epollAdd(
        JNIEnv* env,
        jclass thiz,
        jint epollfd,
        jint fd) {
    int res;

    struct epoll_event ev;
    ev.events = EPOLLIN; // TODO: any other events we are interested in?
    ev.data.fd = fd;

    if ((res = epoll_ctl(epollfd, EPOLL_CTL_ADD, fd, &ev)) == -1) {
        res = -errno;
    }

    return res;
}


/*
 * Wait for events (read, write, error) on the already registered sockets.
 */
JNIEXPORT jintArray JNICALL
Java_com_example_hellojni_NativeUnixSocket_epollWait(
        JNIEnv* env,
        jclass thiz,
        jint epollfd) {
    int nfds;
    struct epoll_event events[MAX_EVENTS];

    if ((nfds = epoll_wait(epollfd, events, MAX_EVENTS, -1)) == -1) {
        nfds = -errno;
    }

    jintArray res = (*env)->NewIntArray(env, 2 * nfds);

    jint temp[2 * nfds];

    for (int i = 0; i < nfds; i++) {
        temp[2 * i] = events[i].data.fd;
        temp[2 * i + 1] = events[i].events;
    }

    (*env)->SetIntArrayRegion(env, res, 0, 2 * nfds, temp);

    return res;
}


/*
 * Creates an endpoint for communication and returns a file descriptor
 */
JNIEXPORT jint JNICALL
Java_com_example_hellojni_NativeUnixSocket_close(
        JNIEnv* env,
        jclass thiz,
        jint fd) {
    int res = close(fd);
    if (res < 0) {
        res = -errno;
    }
    return res;
}


/*
 * Make the socket blocking or non-blocking.
 */
JNIEXPORT jint JNICALL
Java_com_example_hellojni_NativeUnixSocket_setBlocking(
        JNIEnv* env,
        jclass thiz,
        jint fd,
        jboolean block) {
    int flags = fcntl(fd, F_GETFL);

    if (block) {
        flags &= ~O_NONBLOCK;
    } else {
        flags |= O_NONBLOCK;
    }

    return fcntl(fd, F_SETFL, flags);
}


/*
 * Connect a unix domain socket.
 */
JNIEXPORT jint JNICALL
Java_com_example_hellojni_NativeUnixSocket_connect(
        JNIEnv* env,
        jclass thiz,
        jint fd,
        jstring hostStr) {
    int res;
    const char *host = (*env)->GetStringUTFChars(env, hostStr, 0);

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;

    if (*host == '\0') {
        *addr.sun_path = '\0';
        strncpy(addr.sun_path+1, host + 1, sizeof(addr.sun_path) - 2);
    } else {
        strncpy(addr.sun_path, host, sizeof(addr.sun_path) - 1);
    }

    if ((res = connect(fd, (struct sockaddr *)&addr, sizeof(addr))) == -1) {
        // List of standard errno values
        // https://www-numi.fnal.gov/offline_software/srt_public_context/WebDocs/Errors/unix_system_errors.html
        res = -errno;
    }

    (*env)->ReleaseStringUTFChars(env, hostStr, host);
    return res;
}


/*
 * Writes data to the socket at file descriptor fd.
 */
JNIEXPORT jint JNICALL
Java_com_example_hellojni_NativeUnixSocket_write(
        JNIEnv* env,
        jclass thiz,
        jint fd,
        jbyteArray data) {
    // first copy the java bytes into a native c style array
    int len = (*env)->GetArrayLength(env, data);
    jbyte* elements = (*env)->GetByteArrayElements(env, data, NULL);

    int res = write(fd, elements, len);
    if (res < 0) {
        res = -errno;
    }
    (*env)->ReleaseByteArrayElements(env, data, elements, JNI_ABORT);
    return res;
}


/*
 * Read from the socket at file descriptor fd and return the data as a byte array.
 */
JNIEXPORT jint JNICALL
Java_com_example_hellojni_NativeUnixSocket_read(
        JNIEnv* env,
        jclass thiz,
        jint fd,
        jbyteArray data) {

    // TODO(erdal): figure out a better size
    // or better yet try direct allocated buffers to reduce copies
    // maybe make it equal to the input byte array
    char buff[1024 * 10];
    int n = read(fd, buff, 1024 * 10);

    if (n == 0) {
        return 0;
    }
    if (n < 0) {
        return -errno;
    }

    (*env)->SetByteArrayRegion(env, data, 0, n, buff);

    return n;
}


/*
 * Create a listening unix socket (just for testing).
 */
JNIEXPORT jint JNICALL
Java_com_example_hellojni_NativeUnixSocket_startlistening(
        JNIEnv* env,
        jclass thiz,
        jstring hostStr) {

    int res;
    struct sockaddr_un addr;
    int fd,cl,rc;
    char buf[100];
    memset(&buf, 0, sizeof(buf));

    const char *host = (*env)->GetStringUTFChars(env, hostStr, 0);

    if ( (fd = socket(AF_UNIX, SOCK_STREAM, 0)) == -1) {
        res = -errno;
        return res;
    }

    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;

    if (*host == '\0') {
        *addr.sun_path = '\0';
        strncpy(addr.sun_path+1, host + 1, sizeof(addr.sun_path)-2);
    } else {
        strncpy(addr.sun_path, host, sizeof(addr.sun_path)-1);
        unlink(host);
    }

    if (bind(fd, (struct sockaddr*)&addr, sizeof(addr)) == -1) {
        res = -errno;
        return res;
    }

    if (listen(fd, 5) == -1) {
        res = -errno;
        return res;
    }

    while (1) {

        if ( (cl = accept(fd, NULL, NULL)) == -1) {
            perror("accept error");
            continue;
        }

        while ( (rc = read(cl, buf, sizeof(buf))) > 0) {
            LOGI("SERVER: read %u bytes: %.*s\n", rc, rc, buf);

            if (rc == -1) {
                LOGI("error: %d", rc);
                exit(-1);
            } else if (rc == 0) {
                LOGI("EOF\n");
                close(cl);
            }

            int n = write(cl, buf, rc);
            LOGI("SERVER wrote: %d bytes", n);
            // fflush(fd);
            LOGI("SERVER flushed");
        }
    }
}
