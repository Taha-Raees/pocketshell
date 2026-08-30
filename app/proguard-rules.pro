# PocketShell app proguard rules (M1: minify disabled; rules prepared for future)
# Vendored terminal modules keep their classes via -keep if minification is enabled later.
-keep class com.termux.terminal.** { *; }
-keep class com.termux.view.** { *; }
