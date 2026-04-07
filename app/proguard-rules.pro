# PlantTracker ProGuard Rules
-keepattributes *Annotation*
-keep class com.planttracker.data.model.** { *; }
-keep class com.planttracker.alarm.AlarmReceiver { *; }
