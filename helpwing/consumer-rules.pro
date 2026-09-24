# Wire models are decoded by kotlinx.serialization; keep their generated serializers.
-keep,includedescriptorclasses class ru.fanyagin.helpwing.core.**$$serializer { *; }
-keepclassmembers class ru.fanyagin.helpwing.core.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
