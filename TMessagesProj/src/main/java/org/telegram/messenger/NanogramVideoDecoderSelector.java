package org.telegram.messenger;

import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;

import java.util.List;

/** Video decoders in the platform's order, but hardware ones first (some phones list a software decoder first); audio and the rest untouched, and the software ones stay as fallback. */
public final class NanogramVideoDecoderSelector implements MediaCodecSelector {

    public static final NanogramVideoDecoderSelector INSTANCE = new NanogramVideoDecoderSelector();

    private NanogramVideoDecoderSelector() {
    }

    public static boolean isVideo(String mimeType) {
        return mimeType != null && mimeType.startsWith("video/");
    }

    @Override
    public List<MediaCodecInfo> getDecoderInfos(String mimeType, boolean requiresSecureDecoder, boolean requiresTunnelingDecoder) throws MediaCodecUtil.DecoderQueryException {
        List<MediaCodecInfo> infos = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder);
        if (!isVideo(mimeType) || infos.size() < 2) {
            return infos;
        }
        List<MediaCodecInfo> ordered = NanogramMediaCapabilities.hardwareFirst(infos, info -> NanogramMediaCapabilities.isHardware(info.hardwareAccelerated, info.softwareOnly));
        if (ordered != infos && BuildVars.LOGS_ENABLED) {
            FileLog.d("video decoder " + mimeType + ": " + ordered.get(0).name + " (hardware) first");
        }
        return ordered;
    }
}
