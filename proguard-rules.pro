# sherpa-onnx's native code finds its Kotlin classes and fields by name.
-keep class com.k2fsa.sherpa.onnx.** { *; }
# ...and calls the generation callback through invoke([F)Ljava/lang/Integer;. If R8 drops it, generation goes on
# silently with no audio.
-keepclassmembers class * implements kotlin.jvm.functions.Function1 { java.lang.Integer invoke(float[]); }
