# R8 configuration for the release build.
#
# This file records only what R8 cannot work out for itself. Nearly every dependency here ships
# its own consumer rules inside the artifact (Hilt, Room, Media3, kotlinx.serialization, Coil), so
# adding blanket `-keep` rules for them would not fix anything -- it would only defeat shrinking
# and grow the APK. Verify a suspected R8 problem against
# app/build/outputs/mapping/release/usage.txt (what was removed) and seeds.txt (what was kept)
# before adding a rule here.

# ---------------------------------------------------------------------------------------------
# Crash diagnosability. NOT optional, and previously commented out.
#
# Without these two attributes R8 strips line numbers from the shipped dex, so a production stack
# trace retraces to a method and stops -- "crash somewhere in PlaybackViewModel" for a class with
# 65 methods is not a lead. Keeping them costs a small amount of APK size and gives back the exact
# source line.
#
# -renamesourcefileattribute is the other half: with SourceFile kept, every frame would otherwise
# print its real .kt filename, handing back part of what obfuscation just removed. Collapsing them
# all to the literal "SourceFile" keeps the line numbers while discarding the filenames, which is
# exactly the trade retrace is designed around.
#
# The mapping file that decodes these traces is app/build/outputs/mapping/release/mapping.txt. It
# is NOT reproducible from a later build -- archive it with every release you distribute, or the
# traces it decodes become permanently unreadable.
# ---------------------------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------------------------
# androidx.window resolves its OEM foldable/hinge extensions reflectively at runtime, so R8 has no
# call graph to follow and would remove them. This is the one library here whose entry points are
# genuinely invisible to static analysis.
# ---------------------------------------------------------------------------------------------
-keep class androidx.window.extensions.** { *; }
-dontwarn androidx.window.extensions.**
-keep class androidx.window.sidecar.** { *; }
-dontwarn androidx.window.sidecar.**
