cmake_minimum_required(VERSION 3.6.0)

add_library(unix-socket-jni SHARED
        unix-socket-jni.c)

# Include libraries needed for hello-jni lib
target_link_libraries(unix-socket-jni
                      android
                      log)
