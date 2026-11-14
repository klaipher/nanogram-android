# Narrow replacement for `-keep class org.telegram.tgnet.** { *; }` (ANDROID-R8-1). Not applied by default: to try it,
# remove that line from proguard-rules.pro, add this file to proguardFiles in TMessagesProj_App/build.gradle, then run
# a release build and the login, send, receive, sticker, wallet and call smoke test.
# JNI entry points (ConnectionsManager, NativeByteBuffer, RequestTimeDelegate, RequestDelegate) keep their own rules.
-keepnames class org.telegram.tgnet.**
-keepclassmembernames class org.telegram.tgnet.** { <fields>; }
# WalletEngineBridge.cpp sets these fields by name through GetFieldID.
-keep class org.telegram.tgnet.tl.TL_wallet** { *; }
