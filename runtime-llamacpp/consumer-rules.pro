# JNI: the native bridge class and callback interface are looked up by name from C++.
-keep class dev.exynoslm.runtime.llamacpp.LlamaNative { *; }
-keep interface dev.exynoslm.runtime.llamacpp.LlamaTokenCallback { *; }
