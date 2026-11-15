// JNI glue for AudioVoiceEnhancer. The DSP lives in the shared tgcalls fork (tgcalls/voice_enhance/).
#include <jni.h>
#include <stdint.h>

#include <voice_enhance/voice_enhance.h>

namespace {

VoiceEnhancer *handleOf(jlong handle) {
    return reinterpret_cast<VoiceEnhancer *>(static_cast<intptr_t>(handle));
}

}  // namespace

extern "C" {

JNIEXPORT jlong Java_org_telegram_utils_recorder_audio_AudioVoiceEnhancer_nCreate(
        JNIEnv *env, jclass clazz, jint profile, jint initialClass, jboolean useRnnoise, jboolean dropLatency) {
    return (jlong) (intptr_t) ve_create(profile, initialClass, useRnnoise, dropLatency);
}

JNIEXPORT jint Java_org_telegram_utils_recorder_audio_AudioVoiceEnhancer_nProcess(
        JNIEnv *env, jclass clazz, jlong handle, jobject pcm, jint sampleCount) {
    int16_t *samples = static_cast<int16_t *>(env->GetDirectBufferAddress(pcm));
    jlong capacity = env->GetDirectBufferCapacity(pcm);
    if (!samples || sampleCount < 0 || capacity < (jlong) sampleCount * 2) return -1;
    return ve_process(handleOf(handle), samples, sampleCount);
}

JNIEXPORT jint Java_org_telegram_utils_recorder_audio_AudioVoiceEnhancer_nFlush(
        JNIEnv *env, jclass clazz, jlong handle, jobject pcm, jint maxSamples) {
    int16_t *samples = static_cast<int16_t *>(env->GetDirectBufferAddress(pcm));
    jlong capacity = env->GetDirectBufferCapacity(pcm);
    if (!samples || maxSamples < 0 || capacity < (jlong) maxSamples * 2) return 0;
    return ve_flush(handleOf(handle), samples, maxSamples);
}

JNIEXPORT jint Java_org_telegram_utils_recorder_audio_AudioVoiceEnhancer_nDelaySamples(
        JNIEnv *env, jclass clazz, jlong handle) {
    return ve_delay_samples(handleOf(handle));
}

JNIEXPORT jint Java_org_telegram_utils_recorder_audio_AudioVoiceEnhancer_nGetClass(
        JNIEnv *env, jclass clazz, jlong handle) {
    return ve_get_class(handleOf(handle));
}

JNIEXPORT jint Java_org_telegram_utils_recorder_audio_AudioVoiceEnhancer_nGetLevel(
        JNIEnv *env, jclass clazz, jlong handle) {
    return ve_get_level(handleOf(handle));
}

JNIEXPORT void Java_org_telegram_utils_recorder_audio_AudioVoiceEnhancer_nSetLevel(
        JNIEnv *env, jclass clazz, jlong handle, jint level) {
    ve_set_level(handleOf(handle), level);
}

JNIEXPORT jint Java_org_telegram_utils_recorder_audio_AudioVoiceEnhancer_nTakeAverageBlockMicros(
        JNIEnv *env, jclass clazz, jlong handle) {
    return ve_take_average_block_micros(handleOf(handle));
}

JNIEXPORT void Java_org_telegram_utils_recorder_audio_AudioVoiceEnhancer_nDestroy(
        JNIEnv *env, jclass clazz, jlong handle) {
    ve_destroy(handleOf(handle));
}

}  // extern "C"

