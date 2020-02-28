//
// Created by Erdal Tuleu on 2020-01-30.
//

#ifndef UNIXSOCKET_JNI_LOG_H
#define UNIXSOCKET_JNI_LOG_H

#define  LOG_TAG    "UNIXSOCKETJNI"

#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#define  LOGW(...)  __android_log_print(ANDROID_LOG_WARN,LOG_TAG,__VA_ARGS__)
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)


#endif //UNIXSOCKET_JNI_LOG_H
